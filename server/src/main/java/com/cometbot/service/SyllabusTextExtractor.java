package com.cometbot.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Turns an uploaded syllabus file (PDF or plain text) into text. */
@Component
public class SyllabusTextExtractor {

    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    public String extract(String filename, String contentType, byte[] bytes) {
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("file is larger than 10 MB");
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);

        String text;
        if (name.endsWith(".pdf") || type.startsWith("application/pdf")) {
            text = pdfText(bytes);
        } else if (name.endsWith(".txt") || name.endsWith(".md") || type.startsWith("text/")) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("unsupported file type; upload a .pdf or .txt syllabus");
        }

        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(
                    "no text found in the file (scanned PDFs without a text layer aren't supported)");
        }
        return text;
    }

    private String pdfText(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            throw new IllegalArgumentException("could not read the PDF: " + e.getMessage(), e);
        }
    }
}
