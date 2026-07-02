package com.verygana2.services.email;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class EmailTemplateLoader {

    private static final String LAYOUT_TEMPLATE = "layout-base.html";

    @Value("${sendgrid.logo-url}")
    private String logoUrl;

    public String render(String templateName, Map<String, String> vars) {
        try {
            String contentHtml = applyVars(loadTemplate(templateName), vars);

            Map<String, String> layoutVars = new HashMap<>(vars);
            layoutVars.put("content", contentHtml);
            layoutVars.putIfAbsent("logoUrl", logoUrl);
            layoutVars.putIfAbsent("year", String.valueOf(Year.now().getValue()));

            return applyVars(loadTemplate(LAYOUT_TEMPLATE), layoutVars);
        } catch (IOException ex) {
            throw new RuntimeException("Cannot load email template: " + templateName, ex);
        }
    }

    private String loadTemplate(String templateName) throws IOException {
        ClassPathResource resource = new ClassPathResource("templates/email/" + templateName);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private String applyVars(String html, Map<String, String> vars) {
        String result = html;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }
}
