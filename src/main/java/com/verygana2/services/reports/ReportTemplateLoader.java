package com.verygana2.services.reports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Carga plantillas HTML de reportes y sustituye placeholders {{var}}.
 * Mismo enfoque (sin motor de plantillas) que {@code ContractTemplateLoader}.
 */
@Component
public class ReportTemplateLoader {

    public String render(String templateName, Map<String, String> vars) {
        try {
            String html = StreamUtils.copyToString(
                    new ClassPathResource("templates/reports/" + templateName).getInputStream(),
                    StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                html = html.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
            }
            return html;
        } catch (IOException ex) {
            throw new RuntimeException("Cannot load report template: " + templateName, ex);
        }
    }
}
