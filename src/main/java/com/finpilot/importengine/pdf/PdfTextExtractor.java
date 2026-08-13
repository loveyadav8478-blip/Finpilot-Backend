package com.finpilot.importengine.pdf;

import com.finpilot.importengine.exceptions.InvalidPdfPasswordException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor {

    public String extractText(byte[] pdfBytes) throws IOException {
        return extractText(pdfBytes, null);
    }

    public String extractText(byte[] pdfBytes, String password) throws IOException {
        try {
            PDDocument pdDocument;
            if (password != null && !password.isEmpty()) {
                pdDocument = Loader.loadPDF(pdfBytes, password);
            } else {
                pdDocument = Loader.loadPDF(pdfBytes);
            }

            try (pdDocument) {
                if (pdDocument.isEncrypted()) {
                    // Document was decrypted with the provided password, or failed
                }
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(pdDocument);
            }
        } catch (InvalidPasswordException e) {
            throw new InvalidPdfPasswordException("PDF is password protected or password was incorrect", e);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("password")) {
                throw new InvalidPdfPasswordException("PDF is password protected or password was incorrect", e);
            }
            throw e;
        }
    }
}

