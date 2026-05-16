package com.example.polyusigwebsite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class DocumentPreviewService {
    private static final Logger log = LoggerFactory.getLogger(DocumentPreviewService.class);
    private static final Set<String> OFFICE_EXTENSIONS = Set.of("docx", "xlsx", "pptx");

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
        return OFFICE_EXTENSIONS.contains(ext);
    }

    public Path resolvePreviewPath(Path sourcePath) {
        String sourceFileName = sourcePath.getFileName().toString();
        int dot = sourceFileName.lastIndexOf('.');
        String baseName = dot > 0 ? sourceFileName.substring(0, dot) : sourceFileName;
        Path outputPdf = previewDir.resolve(baseName + ".pdf");

        try {
            if (Files.exists(outputPdf) && Files.getLastModifiedTime(outputPdf).toMillis() >= Files.getLastModifiedTime(sourcePath).toMillis()) {
                return outputPdf;
            }
        } catch (IOException ignored) {
            // continue to conversion
        }

        convertToPdf(sourcePath);

        if (!Files.exists(outputPdf)) {
            throw new IllegalStateException("Preview PDF was not generated: " + outputPdf.getFileName());
        }
        return outputPdf;
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
