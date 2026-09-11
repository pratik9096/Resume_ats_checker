package com.atschecker.resumeats.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class PdfExtractionService {

    /**
     * Extracts raw text from an uploaded PDF resume.
     * Throws IOException if the file isn't a readable PDF (e.g. scanned
     * image-only PDF, corrupted file) - the controller turns this into a
     * clean 400 error for the user.
     */
    public String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.isEncrypted()) {
                throw new IOException("This PDF is password-protected. Please upload an unprotected file.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (text == null || text.trim().length() < 30) {
                throw new IOException(
                    "Couldn't extract readable text from this PDF. " +
                    "It may be a scanned/image-only resume - please upload a text-based PDF."
                );
            }
            return text;
        }
    }
}
