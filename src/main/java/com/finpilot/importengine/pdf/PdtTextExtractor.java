package com.finpilot.importengine.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

public class PdtTextExtractor {
//    public static void main(String[] args) throws IOException {
//        PDDocument d = Loader.loadPDF(new File("C:/Users/Love Yadav/OneDrive/Desktop/FinPilot ML/unlocked.pdf"));
//        PDFTextStripper s = new PDFTextStripper();
//        String txt = s.getText(d);
//        System.out.println(txt);
//    }

    public String extractText(byte[] pdfBytes) throws IOException {
        try(PDDocument pdDocument = Loader.loadPDF(pdfBytes)){
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.getText(pdDocument);
            stripper.setSortByPosition(true);
            return stripper.getText(pdDocument);
        }
    }
}
