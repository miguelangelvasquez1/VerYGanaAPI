package com.verygana2.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class ProfanityFilterService {

    private final Set<String> bannedWords;

    public ProfanityFilterService(@Value("classpath:profanity/es-banned-words.txt") Resource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            this.bannedWords = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toCollection(HashSet::new));
        }
    }

    public boolean containsProfanity(String text) {
        String normalized = normalize(text);
        return bannedWords.stream().anyMatch(normalized::contains);
    }

    private String normalize(String text) {
        String lower = text.toLowerCase();
        lower = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        lower = lower.replaceAll("[0@]", "o")
                     .replaceAll("[1!]", "i")
                     .replaceAll("[3]", "e")
                     .replaceAll("[4]", "a")
                     .replaceAll("[$]", "s")
                     .replaceAll("[^a-z\\s]", "");
        return lower;
    }
}