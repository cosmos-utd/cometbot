package com.cometbot.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyllabusTextExtractorTest {

    private final SyllabusTextExtractor extractor = new SyllabusTextExtractor();

    @Test
    void readsTextFromPdf() throws Exception {
        byte[] pdf = pdfWithText("Homework 1 due September 10");
        assertThat(extractor.extract("syllabus.pdf", "application/pdf", pdf))
                .contains("Homework 1 due September 10");
    }

    @Test
    void readsPlainText() {
        byte[] txt = "  Quiz 1 - Sep 3  ".getBytes(StandardCharsets.UTF_8);
        assertThat(extractor.extract("notes.txt", null, txt)).isEqualTo("Quiz 1 - Sep 3");
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertThatThrownBy(() -> extractor.extract("syllabus.docx", "application/octet-stream", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    void rejectsPdfWithNoText() throws Exception {
        byte[] pdf = pdfWithText(null);
        assertThatThrownBy(() -> extractor.extract("scan.pdf", null, pdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no text");
    }

    private static byte[] pdfWithText(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (text != null) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
