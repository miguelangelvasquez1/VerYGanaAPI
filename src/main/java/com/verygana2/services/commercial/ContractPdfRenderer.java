package com.verygana2.services.commercial;

import java.io.ByteArrayOutputStream;

import org.springframework.stereotype.Component;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.verygana2.exceptions.StorageException;

@Component
public class ContractPdfRenderer {

    public byte[] renderToPdf(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new StorageException("Error generando el PDF del contrato", e);
        }
    }
}
