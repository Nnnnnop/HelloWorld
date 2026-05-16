package com.example.polyusigwebsite.service;

import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class DocumentPreviewService {
    private static final Logger log = LoggerFactory.getLogger(DocumentPreviewService.class);
    private static final Set<String> OFFICE_EXTENSIONS = Set.of("docx", "xlsx", "pptx");
    /** UTF-8 text / source / markup → PDF via PDFBox + Unicode font (not LibreOffice; avoids missing CJK glyphs). */
    private static final Set<String> TEXT_PREVIEW_EXTENSIONS = Set.copyOf(Arrays.asList(
            "txt", "json", "csv", "tsv", "log", "md", "markdown", "htm", "html", "xhtml",
            "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "editorconfig",
            "c", "h", "cc", "cpp", "cxx", "hh", "hpp", "hxx", "cs", "java", "kt", "kts",
            "rs", "go", "py", "pyw", "rb", "php", "swift", "scala", "gradle", "groovy", "nim",
            "js", "mjs", "cjs", "jsx", "ts", "tsx", "vue", "svelte",
            "css", "scss", "sass", "less",
            "sql", "sh", "bash", "zsh", "fish",
            "bat", "cmd", "ps1",
            "dockerignore", "graphql", "gql", "env", "plist", "proto", "thrift", "r", "jl",
            "dart", "lua", "ex", "exs", "erl", "hs", "clj", "cljs", "edn", "fs", "fsx",
            "http", "tf", "hcl", "rst", "adoc"
    ));

    private static final int TEXT_PREVIEW_MAX_CHARS = 500_000;
    private static final float TEXT_FONT_SIZE = 10f;
    private static final float TEXT_LEADING = 12f;
    private static final float PAGE_MARGIN = 40f;

    private final Path previewDir;
    private final Path profileDir;
    private final List<String> sofficeCandidates;
    private final long convertTimeoutSeconds;

    public DocumentPreviewService(
            @Value("${app.preview-dir:preview-cache}") String previewDir,
            @Value("${app.office.soffice-path:soffice}") String sofficePath,
            @Value("${app.office.convert-timeout-seconds:60}") long convertTimeoutSeconds
    ) throws IOException {
        this.previewDir = Paths.get(previewDir).toAbsolutePath().normalize();
        Files.createDirectories(this.previewDir);
        this.profileDir = this.previewDir.resolve(".soffice-profile");
        Files.createDirectories(this.profileDir);
        this.sofficeCandidates = resolveSofficeCandidates(sofficePath);
        this.convertTimeoutSeconds = convertTimeoutSeconds;
    }

    public boolean shouldConvertToPdf(String originalFileName) {
        String ext = extensionOf(originalFileName);
        return OFFICE_EXTENSIONS.contains(ext) || isUtf8TextSourcePreview(originalFileName);
    }

    /** Extension or well-known basename (e.g. {@code Dockerfile}) for UTF-8 → PDF rendering. */
    private boolean isUtf8TextSourcePreview(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return false;
        }
        if (TEXT_PREVIEW_EXTENSIONS.contains(extensionOf(originalFileName))) {
            return true;
        }
        String base = originalFileName;
        int s = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (s >= 0) {
            base = base.substring(s + 1);
        }
        return switch (base.toLowerCase(Locale.ROOT)) {
            case "dockerfile", "makefile", "rakefile", "gemfile", "jenkinsfile", "containerfile" -> true;
            default -> false;
        };
    }

    public Path resolvePreviewPath(Path sourcePath) {
        String sourceFileName = sourcePath.getFileName().toString();
        int dot = sourceFileName.lastIndexOf('.');
        String baseName = dot > 0 ? sourceFileName.substring(0, dot) : sourceFileName;
        Path outputPdf = previewDir.resolve(baseName + ".pdf");

        try {
            if (Files.exists(outputPdf)
                    && Files.getLastModifiedTime(outputPdf).toMillis() >= Files.getLastModifiedTime(sourcePath).toMillis()) {
                return outputPdf;
            }
        } catch (IOException ignored) {
            // continue to conversion
        }

        try {
            if (isUtf8TextSourcePreview(sourceFileName)) {
                convertUtf8TextToPdfSafe(sourcePath, outputPdf);
            } else {
                convertToPdfOfficeSafe(sourcePath, outputPdf, sourceFileName);
            }
        } catch (Exception ex) {
            log.warn(
                    "Preview PDF generation fallback for {}: {}",
                    sourceFileName,
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()
            );
            try {
                writeAsciiFallbackPreviewPdf(
                        outputPdf,
                        "Preview unavailable",
                        List.of(
                                "Could not generate a faithful preview PDF for:",
                                asciiPreviewLine(sourceFileName),
                                "You can download the original file instead.",
                                trimFailureSingleLine(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())
                        )
                );
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        if (!Files.exists(outputPdf)) {
            try {
                writeAsciiFallbackPreviewPdf(
                        outputPdf,
                        "Preview unavailable",
                        List.of(
                                "Preview output was missing after conversion.",
                                asciiPreviewLine(sourceFileName),
                                "You can download the original file instead."
                        )
                );
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
        return outputPdf;
    }

    private void convertUtf8TextToPdfSafe(Path sourcePath, Path outputPdf) throws Exception {
        convertUtf8TextToPdf(sourcePath, outputPdf);
    }

    private void convertToPdfOfficeSafe(Path sourcePath, Path outputPdf, String sourceFileName) throws Exception {
        convertToPdf(sourcePath);
        if (!Files.isRegularFile(outputPdf)) {
            throw new IllegalStateException(
                    "Office preview PDF missing for " + sourceFileName + " (expected " + outputPdf.getFileName() + ")");
        }
    }

    private static String asciiPreviewLine(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), 240));
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            int w = Character.charCount(cp);
            if (cp >= 0x20 && cp <= 0x7E) {
                sb.appendCodePoint(cp);
            } else if (Character.isWhitespace(cp)) {
                sb.append(' ');
            } else {
                sb.append('?');
            }
            i += w;
        }
        String collapsed = sb.toString().replaceAll("\\s+", " ").trim();
        int max = 160;
        if (collapsed.length() <= max) {
            return collapsed;
        }
        return collapsed.substring(0, max) + "...";
    }

    private static String trimFailureSingleLine(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        String oneLine = detail.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        int max = 220;
        if (oneLine.length() <= max) {
            return oneLine;
        }
        return oneLine.substring(0, max) + "...";
    }



    private static final int TAB_AS_SPACES = 4;

    private static List<String> wrapAsciiToWidth(PDType1Font font, float size, float maxW, String text) throws IOException {
        List<String> out = new ArrayList<>();
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            out.add("");
            return out;
        }
        String[] words = t.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String trial = line.isEmpty() ? word : line + " " + word;
            float w = font.getStringWidth(trial) / 1000f * size;
            if (w <= maxW) {
                line = new StringBuilder(trial);
            } else if (line.isEmpty()) {
                out.add(word.length() > 200 ? word.substring(0, 200) : word);
            } else {
                out.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    /** Standard-14 Helvetica only (ASCII); keeps /preview returning a valid PDF when conversion fails. */
    private void writeAsciiFallbackPreviewPdf(Path dest, String heading, List<String> bodyLines) throws IOException {
        if (dest.getParent() != null) {
            Files.createDirectories(dest.getParent());
        }
        Path parent = dest.getParent() != null ? dest.getParent() : previewDir;
        Path tmp = Files.createTempFile(parent, "previewfb-", ".pdf");
        try {
            PDType1Font helv = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float margin = 50f;
            float pageW = PDRectangle.A4.getWidth();
            float pageH = PDRectangle.A4.getHeight();
            float maxTextW = pageW - 2 * margin;
            float titleSize = 12f;
            float bodySize = 10f;
            float lineStep = bodySize + 4f;

            List<String> lines = new ArrayList<>();
            lines.add(asciiPreviewLine(heading.isBlank() ? "Preview" : heading));
            lines.add("");
            if (bodyLines != null) {
                for (String p : bodyLines) {
                    if (p == null || p.isBlank()) {
                        lines.add("");
                        continue;
                    }
                    lines.addAll(wrapAsciiToWidth(helv, bodySize, maxTextW, asciiPreviewLine(p)));
                }
            }
            if (lines.size() > 90) {
                lines = new ArrayList<>(lines.subList(0, 89));
                lines.add("...(truncated)...");
            }

            try (PDDocument doc = new PDDocument()) {
                int idx = 0;
                while (idx < lines.size()) {
                    PDPage page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    float y = pageH - margin;
                    float x = margin;
                    float minY = margin;
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        while (idx < lines.size() && y >= minY) {
                            boolean titleLine = idx == 0;
                            float fontSz = titleLine ? titleSize : bodySize;
                            String t = lines.get(idx++);
                            cs.beginText();
                            cs.setFont(helv, fontSz);
                            cs.newLineAtOffset(x, y);
                            cs.showText(t.isBlank() ? " " : t);
                            cs.endText();
                            y -= titleLine ? titleSize + 10f : lineStep;
                        }
                    }
                }
                doc.save(tmp.toFile());
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Bundled Noto Sans SC does not cover every Unicode code point (e.g. U+FFFD, many format chars, most emoji).
     * Heuristic pass before the font probes in {@link #coerceTextToRenderableGlyphs}.
     */
    private static String sanitizeTextForEmbeddedFont(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String expanded = content.replace("\t", " ".repeat(TAB_AS_SPACES));
        StringBuilder sb = new StringBuilder(expanded.length());
        for (int i = 0; i < expanded.length(); ) {
            int cp = expanded.codePointAt(i);
            int w = Character.charCount(cp);
            if (cp == '\n' || cp == '\r') {
                sb.appendCodePoint(cp);
            } else if (cp < 0x20 || cp == 0x7F) {
                sb.append(' ');
            } else if (cp == 0xFFFD || cp == 0xFFFE || cp == 0xFFFF) {
                sb.append(' ');
            } else if (cp == 0xFEFF
                    || cp == 0x200B
                    || cp == 0x200C
                    || cp == 0x200D
                    || cp == 0x2060
                    || cp == 0x00AD) {
                // ZWNBSP / ZWSP / ZWNJ / ZWJ / WJ / soft hyphen: drop (no printable glyph needed)
            } else if (looksLikeUnsupportedEmojiLikeSymbol(cp)) {
                sb.append(' ');
            } else {
                sb.appendCodePoint(cp);
            }
            i += w;
        }
        return sb.toString();
    }

    /** Blocks that often lack glyphs when CJK fonts are embedded for PDF previews: strip before font probe. */
    private static boolean looksLikeUnsupportedEmojiLikeSymbol(int cp) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
        return b == Character.UnicodeBlock.DINGBATS
                || b == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS
                || b == Character.UnicodeBlock.MISCELLANEOUS_TECHNICAL
                || b == Character.UnicodeBlock.MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A
                || b == Character.UnicodeBlock.MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B
                || b == Character.UnicodeBlock.SUPPLEMENTAL_MATHEMATICAL_OPERATORS
                || b == Character.UnicodeBlock.SUPPLEMENTAL_ARROWS_A
                || b == Character.UnicodeBlock.SUPPLEMENTAL_ARROWS_B
                || b == Character.UnicodeBlock.SUPPLEMENTAL_ARROWS_C
                || b == Character.UnicodeBlock.ENCLOSED_ALPHANUMERIC_SUPPLEMENT
                || b == Character.UnicodeBlock.ENCLOSED_IDEOGRAPHIC_SUPPLEMENT
                || b == Character.UnicodeBlock.EMOTICONS
                || b == Character.UnicodeBlock.ALCHEMICAL_SYMBOLS
                || b == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS
                || b == Character.UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS
                || b == Character.UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS
                || b == Character.UnicodeBlock.SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A;
    }

    /** Final pass: replace any remaining code points the chosen font cannot encode. */
    private static String coerceTextToRenderableGlyphs(PDFont font, String content) throws IOException {
        StringBuilder sb = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int w = Character.charCount(cp);
            String ch = content.substring(i, i + w);
            if (cp == '\n' || cp == '\r') {
                sb.append(ch);
            } else {
                try {
                    font.getStringWidth(ch);
                    sb.append(ch);
                } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
                    sb.append(' ');
                    if (log.isTraceEnabled()) {
                        log.trace("PDF preview replaced unmappable character U+{}", Integer.toHexString(cp).toUpperCase(Locale.ROOT));
                    }
                }
            }
            i += w;
        }
        return sb.toString();
    }

    private static float stringWidthPdf(PDFont font, float fontSize, String s) {
        try {
            return font.getStringWidth(s) / 1000f * fontSize;
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            return Float.NaN;
        }
    }

    /**
     * Renders UTF-8 text as a multi-page PDF using an embedded Unicode/CJK-capable font.
     */
    private void convertUtf8TextToPdf(Path sourcePath, Path outputPdf) throws IOException {
        byte[] raw = Files.readAllBytes(sourcePath);
        String content = new String(raw, StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        content = sanitizeTextForEmbeddedFont(content);
        if (content.length() > TEXT_PREVIEW_MAX_CHARS) {
            content = content.substring(0, TEXT_PREVIEW_MAX_CHARS) + "\n\n...(truncated for preview)...";
        }

        try (PDDocument doc = new PDDocument()) {
            PDType0Font font = loadUnicodeFont(doc);
            content = coerceTextToRenderableGlyphs(font, content);
            PDRectangle mediaBox = PDRectangle.A4;
            float maxWidth = mediaBox.getWidth() - 2 * PAGE_MARGIN;
            List<String> physicalLines = wrapToLines(content, font, TEXT_FONT_SIZE, maxWidth);

            if (physicalLines.isEmpty()) {
                physicalLines = List.of(" ");
            }

            PDRectangle box = mediaBox;
            float usableHeight = box.getHeight() - 2 * PAGE_MARGIN;
            int linesPerPage = Math.max(1, (int) (usableHeight / TEXT_LEADING));

            PDPageContentStream cs = null;
            try {
                int idx = 0;
                while (idx < physicalLines.size()) {
                    PDPage page = new PDPage(box);
                    doc.addPage(page);
                    if (cs != null) {
                        cs.endText();
                        cs.close();
                    }
                    cs = new PDPageContentStream(doc, page);
                    cs.beginText();
                    cs.setFont(font, TEXT_FONT_SIZE);
                    cs.setLeading(TEXT_LEADING);
                    cs.newLineAtOffset(PAGE_MARGIN, box.getHeight() - PAGE_MARGIN);

                    int onPage = 0;
                    while (idx < physicalLines.size() && onPage < linesPerPage) {
                        String line = physicalLines.get(idx++);
                        cs.showText(line.isBlank() ? " " : line);
                        cs.newLine();
                        onPage++;
                    }
                }
                if (cs != null) {
                    cs.endText();
                    cs.close();
                    cs = null;
                }
                doc.save(outputPdf.toFile());
            } finally {
                if (cs != null) {
                    try {
                        cs.close();
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
            }
        }
    }

    private List<String> wrapToLines(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> out = new ArrayList<>();
        String[] paragraphs = text.split("\r?\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder cur = new StringBuilder();
            int i = 0;
            while (i < paragraph.length()) {
                int cp = paragraph.codePointAt(i);
                int cc = Character.charCount(cp);
                String ch = paragraph.substring(i, i + cc);
                String trial = cur + ch;
                float w = stringWidthPdf(font, fontSize, trial);
                if ((Float.isNaN(w) || w > maxWidth) && !cur.isEmpty()) {
                    out.add(cur.toString());
                    cur = new StringBuilder(ch);
                    float cw = stringWidthPdf(font, fontSize, ch);
                    if (Float.isNaN(cw) || cw > maxWidth) {
                        out.add(" ");
                        cur = new StringBuilder();
                    }
                } else if (Float.isNaN(w) || w > maxWidth) {
                    out.add(" ");
                    cur = new StringBuilder();
                } else {
                    cur.append(ch);
                }
                i += cc;
            }
            if (!cur.isEmpty()) {
                out.add(cur.toString());
            }
        }
        return out;
    }

    /** Classpath fonts (packed in JAR). TTF or OTF both work with PDFBox. */
    private static final List<String> BUNDLED_FONT_RESOURCES = List.of(
            "/fonts/NotoSansSC-Regular.otf",
            "/fonts/NotoSansSC-Regular.ttf"
    );

    private PDType0Font loadUnicodeFont(PDDocument doc) throws IOException {
        for (String resourcePath : BUNDLED_FONT_RESOURCES) {
            try (InputStream bundled = getClass().getResourceAsStream(resourcePath)) {
                if (bundled != null) {
                    log.debug("Using bundled font {} for text preview PDF", resourcePath);
                    return PDType0Font.load(doc, bundled);
                }
            }
        }

        List<Path> candidates = new ArrayList<>();
        String windir = System.getenv("WINDIR");
        if (windir != null && !windir.isBlank()) {
            Path wd = Paths.get(windir, "Fonts");
            candidates.add(wd.resolve("msyh.ttf"));
            candidates.add(wd.resolve("msyh.ttc"));
            candidates.add(wd.resolve("simhei.ttf"));
            candidates.add(wd.resolve("mingliu.ttc"));
            candidates.add(wd.resolve("simsun.ttc"));
        }
        candidates.add(Paths.get("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"));
        candidates.add(Paths.get("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"));

        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            try {
                String lower = p.toString().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".ttc")) {
                    PDType0Font loaded = loadFirstCompatibleFace(doc, p);
                    if (loaded != null) {
                        log.debug("Using system font {} for text preview PDF", p);
                        return loaded;
                    }
                } else {
                    PDType0Font f = PDType0Font.load(doc, p.toFile());
                    log.debug("Using system font {} for text preview PDF", p);
                    return f;
                }
            } catch (Exception ex) {
                log.trace("Skip font {}: {}", p, ex.getMessage());
            }
        }

        throw new IOException(
                "No Unicode/CJK font for text/code preview PDF. Add NotoSansSC-Regular.ttf or .otf under src/main/resources/fonts/, "
                        + "install fonts-noto-cjk in the image, or mount system fonts."
        );
    }

    private PDType0Font loadFirstCompatibleFace(PDDocument doc, Path ttcPath) throws IOException {
        AtomicReference<PDType0Font> loaded = new AtomicReference<>();
        try (TrueTypeCollection coll = new TrueTypeCollection(ttcPath.toFile())) {
            coll.processAllFonts((TrueTypeFont ttf) -> {
                if (loaded.get() != null) {
                    return;
                }
                try {
                    loaded.set(PDType0Font.load(doc, ttf, true));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw new IOException(ex.getCause());
        }
        return loaded.get();
    }

    private void convertToPdf(Path sourcePath) {
        List<String> failures = new ArrayList<>();
        for (String sofficeExecutable : sofficeCandidates) {
            Process process = null;
            try {
                List<String> command = List.of(
                        sofficeExecutable,
                        "--headless",
                        "--nologo",
                        "--nolockcheck",
                        "--nodefault",
                        "--norestore",
                        "-env:UserInstallation=" + toFileUri(profileDir),
                        "--convert-to",
                        "pdf",
                        "--outdir",
                        previewDir.toString(),
                        sourcePath.toString()
                );
                ProcessBuilder builder = new ProcessBuilder(command);
                process = builder.start();
                boolean finished = process.waitFor(convertTimeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    failures.add(sofficeExecutable + " => timed out");
                    continue;
                }
                if (process.exitValue() != 0) {
                    String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    String detail = (error + " " + output).trim();
                    failures.add(sofficeExecutable + " => exit " + process.exitValue() + " " + trimFailure(detail));
                    continue;
                }
                log.debug("Preview conversion succeeded using {}", sofficeExecutable);
                return;
            } catch (IOException ex) {
                failures.add(sofficeExecutable + " => " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                log.warn("Cannot run office converter executable: {}", sofficeExecutable);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Document conversion interrupted", ex);
            } finally {
                if (process != null) {
                    log.debug("Preview conversion finished for {}", sourcePath.getFileName());
                }
            }
        }

        throw new IllegalStateException("Cannot run office converter. Details: " + failures.stream().collect(Collectors.joining(" | ")));
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private List<String> resolveSofficeCandidates(String configuredPath) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String sanitized = sanitizeExecutable(configuredPath);
        if (!sanitized.isBlank()) {
            candidates.add(sanitized);
        }
        candidates.add("soffice");

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            candidates.add("C:\\Program Files\\LibreOffice\\program\\soffice.com");
            candidates.add("C:\\Program Files\\LibreOffice\\program\\soffice.exe");
            candidates.add("C:\\Program Files (x86)\\LibreOffice\\program\\soffice.com");
            candidates.add("C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe");
        }
        return List.copyOf(candidates);
    }

    private String sanitizeExecutable(String executable) {
        if (executable == null) {
            return "";
        }
        String trimmed = executable.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String toFileUri(Path path) {
        return path.toUri().toString();
    }

    private String trimFailure(String detail) {
        if (detail == null || detail.isBlank()) {
            return "(no output)";
        }
        String oneLine = detail.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
        int max = 300;
        if (oneLine.length() <= max) {
            return oneLine;
        }
        return oneLine.substring(0, max) + "...";
    }
}
