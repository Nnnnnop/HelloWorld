package com.example.polyusigwebsite.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Component
public class FileContentExtractor {

    private static final int MAX_CONTENT_LENGTH = 20_000;
    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of(
            "txt", "md", "json", "xml", "yaml", "yml", "sql",
            "java", "py", "c", "cpp", "js", "ts", "go", "rs", "r", "m"
    );

    public String extract(Path filePath, String originalFileName) {
        if (filePath == null || originalFileName == null || originalFileName.isBlank()) {
            return "";
        }
        String extension = extensionOf(originalFileName);

        try {
            return switch (extension) {
                case "pdf" -> normalizeAndTrim(extractPdf(filePath));
                case "docx" -> normalizeAndTrim(extractDocx(filePath));
                default -> PLAIN_TEXT_EXTENSIONS.contains(extension)
                        ? normalizeAndTrim(Files.readString(filePath, StandardCharsets.UTF_8))
                        : "";
            };
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractPdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
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

    private String trimToLimit(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_CONTENT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_CONTENT_LENGTH);
    }
}
