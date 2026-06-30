package com.verygana2.services.email;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class EmailTemplateLoader {

    public String render(String templateName, Map<String, String> vars) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/email/" + templateName);
            String html = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                html = html.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
            }
            return html;
        } catch (IOException ex) {
            throw new RuntimeException("Cannot load email template: " + templateName, ex);
        }
    }
}
