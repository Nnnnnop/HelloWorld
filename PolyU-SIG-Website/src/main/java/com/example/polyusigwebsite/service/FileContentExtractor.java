package com.example.polyusigwebsite.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts searchable plain text only for content-based indexing. Supported extensions:
 * pdf, doc, docx, xls, xlsx, pptx, ppt, json, txt — all others return empty body text.
 *
 * For {@code .zip} archives, {@link #extractForIndexing(Path, String)} also appends
 * member paths (filtered, capped) so site search can match inner file names without
 * downloading the ZIP.
 */
@Component
public class FileContentExtractor {

    private static final int MAX_CONTENT_LENGTH = 20_000;
    /** Stop accumulating raw extraction past this to limit memory on huge Office files */
    private static final int SOFT_CAP = MAX_CONTENT_LENGTH * 3;

    /** Maximum inner paths indexed per ZIP — same limit as ZIP browsing API list size */
    private static final int ZIP_INDEX_MAX_ENTRIES = 3000;

    /** Hard cap so ES / DB do not receive megabyte concatenations of ZIP paths */
    private static final int MAX_INDEX_CHARS = 200_000;

    public String extract(Path filePath, String originalFileName) {
        if (filePath == null || originalFileName == null || originalFileName.isBlank()) {
            return "";
        }
        String extension = extensionOf(originalFileName);

        try {
            return switch (extension) {
                case "pdf" -> normalizeAndTrim(extractPdf(filePath));
                case "docx" -> normalizeAndTrim(extractDocx(filePath));
                case "doc" -> normalizeAndTrim(extractDoc(filePath));
                case "xls", "xlsx" -> normalizeAndTrim(extractSpreadsheet(filePath));
                case "pptx" -> normalizeAndTrim(extractPptx(filePath));
                case "ppt" -> normalizeAndTrim(extractPpt(filePath));
                case "txt", "json" -> normalizeAndTrim(Files.readString(filePath, StandardCharsets.UTF_8));
                default -> "";
            };
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Plain text used for indexing and persisted {@code contentText}: base extraction plus,
     * for ZIP files, newline-separated inner entry paths suitable for substring search.
     */
    public String extractForIndexing(Path filePath, String originalFileName) {
        String base = extract(filePath, originalFileName);
        if (!"zip".equals(extensionOf(originalFileName))) {
            return base;
        }
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return base;
        }
        String listing = readZipEntryPathsForIndexing(filePath);
        if (listing.isBlank()) {
            return base;
        }
        if (base.isBlank()) {
            return capIndexLength(listing);
        }
        return capIndexLength(base + "\n" + listing);
    }

    private static String capIndexLength(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_INDEX_CHARS ? text : text.substring(0, MAX_INDEX_CHARS);
    }

    private static String readZipEntryPathsForIndexing(Path filePath) {
        try (ZipFile zf = new ZipFile(filePath.toFile(), StandardCharsets.UTF_8)) {
            var sorted = zf.stream()
                    .filter(ze -> isSafeZipEntryPath(ze.getName()))
                    .filter(ze -> !ze.getName().startsWith("__MACOSX/"))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .limit(ZIP_INDEX_MAX_ENTRIES)
                    .toList();

            StringJoiner joiner = new StringJoiner("\n");
            for (ZipEntry ze : sorted) {
                String rawName = ze.getName();
                boolean dir = ze.isDirectory() || rawName.endsWith("/");
                String pathStr = rawName;
                if (dir && pathStr.endsWith("/")) {
                    pathStr = pathStr.substring(0, pathStr.length() - 1);
                }
                pathStr = sanitizeOneLine(pathStr);
                if (!pathStr.isBlank()) {
                    joiner.add(pathStr);
                }
            }
            return joiner.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String sanitizeOneLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u0000', ' ').trim();
    }

    private static boolean isSafeZipEntryPath(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (name.contains("..")) {
            return false;
        }
        if (name.startsWith("/") || name.startsWith("\\")) {
            return false;
        }
        for (String seg : name.split("/")) {
            if ("..".equals(seg)) {
                return false;
            }
        }
        return true;
    }

    private String extractPdf(Path path) throws Exception {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDoc(Path path) throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path));
             HWPFDocument document = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractSpreadsheet(Path path) throws Exception {
        StringBuilder sb = new StringBuilder(4096);
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path));
             Workbook wb = WorkbookFactory.create(in)) {
            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                if (sheet == null) continue;
                for (Row row : sheet) {
                    if (row == null) continue;
                    for (Cell cell : row) {
                        if (cell == null) continue;
                        String v = formatter.formatCellValue(cell);
                        if (!v.isBlank()) {
                            sb.append(v).append(' ');
                            if (sb.length() >= SOFT_CAP) {
                                return sb.toString();
                            }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private String extractPptx(Path path) throws Exception {
        StringBuilder sb = new StringBuilder(4096);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path));
             XMLSlideShow ppt = new XMLSlideShow(in)) {
            for (XSLFSlide slide : ppt.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    appendTextShape(sb, shape);
                    if (sb.length() >= SOFT_CAP) return sb.toString();
                }
            }
        }
        return sb.toString();
    }

    private void appendTextShape(StringBuilder sb, XSLFShape shape) {
        if (shape instanceof XSLFTextShape textShape) {
            String t = textShape.getText();
            if (t != null && !t.isBlank()) {
                sb.append(t).append(' ');
            }
        } else if (shape instanceof org.apache.poi.xslf.usermodel.XSLFGroupShape group) {
            for (XSLFShape child : group.getShapes()) {
                appendTextShape(sb, child);
                if (sb.length() >= SOFT_CAP) break;
            }
        }
    }

    private String extractPpt(Path path) throws Exception {
        StringBuilder sb = new StringBuilder(4096);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path));
             HSLFSlideShow ppt = new HSLFSlideShow(in)) {
            for (HSLFSlide slide : ppt.getSlides()) {
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        String t = textShape.getText();
                        if (t != null && !t.isBlank()) {
                            sb.append(t).append(' ');
                        }
                    }
                    if (sb.length() >= SOFT_CAP) return sb.toString();
                }
            }
        }
        return sb.toString();
    }

    private String normalizeAndTrim(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String withoutNulls = text.replace('\u0000', ' ');
        String normalized = withoutNulls.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]+", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_CONTENT_LENGTH ? normalized : normalized.substring(0, MAX_CONTENT_LENGTH);
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }
}
