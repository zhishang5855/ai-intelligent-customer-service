package com.example.aics.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParserServiceTest {

    private final DocumentParserService parserService = new DocumentParserService();

    @TempDir
    Path tempDir;

    @Test
    void parsePdfExtractsText() throws Exception {
        Path pdf = tempDir.resolve("policy.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(80, 700);
                stream.showText("Warranty policy text");
                stream.endText();
            }
            document.save(pdf.toFile());
        }

        String text = parserService.parse(pdf, "policy.pdf");

        assertTrue(text.contains("Warranty policy text"));
    }

    @Test
    void parseDocxExtractsParagraphText() throws Exception {
        Path docx = tempDir.resolve("manual.docx");
        try (XWPFDocument document = new XWPFDocument();
             OutputStream outputStream = Files.newOutputStream(docx)) {
            document.createParagraph().createRun().setText("Training appointment policy");
            document.write(outputStream);
        }

        String text = parserService.parse(docx, "manual.docx");

        assertTrue(text.contains("Training appointment policy"));
    }
}
