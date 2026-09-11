package com.atschecker.resumeats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class GroqService {

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.model:openai/gpt-oss-20b}")
    private String groqModel;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int MAX_JD_WORDS = 3000;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class SuggestionResult {
        public boolean available;
        public List<String> suggestions = new ArrayList<>();
        public String errorMessage;
    }

    public SuggestionResult getSuggestions(String resumeText, String jobDescription) {
        SuggestionResult result = new SuggestionResult();

        if (groqApiKey == null || groqApiKey.isBlank()) {
            result.available = false;
            result.errorMessage = "Groq API key not configured on the server.";
            return result;
        }

        try {
            String truncatedJd = truncateWords(jobDescription, MAX_JD_WORDS);
            String truncatedResume = truncateWords(resumeText, MAX_JD_WORDS);
            String prompt = buildPrompt(truncatedResume, truncatedJd);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            String bodyJson = objectMapper.writeValueAsString(new Object() {
                public final String model = groqModel;
                public final Object[] messages = new Object[]{
                        new Object() {
                            public final String role = "user";
                            public final String content = prompt;
                        }
                };
                public final double temperature = 0.4;
                public final int max_tokens = 1024;
                public final String reasoning_effort = "low";
            });

            HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_URL, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode message = root.path("choices").get(0).path("message");
            String content = message.path("content").asText("");

            if (content.isBlank()) {
                content = message.path("reasoning").asText("");
            }

            result.suggestions = parseSuggestions(content);

            if (result.suggestions.isEmpty()) {
                result.available = false;
                result.errorMessage = "AI returned an empty response - try analyzing again.";
                return result;
            }

            result.available = true;
            return result;

        } catch (RestClientException e) {
            result.available = false;
            result.errorMessage = "Could not reach Groq API right now. Try again shortly.";
            return result;
        } catch (Exception e) {
            result.available = false;
            result.errorMessage = "Unexpected error generating AI suggestions.";
            return result;
        }
    }

    private String buildPrompt(String resumeText, String jobDescription) {
        return "You are an ATS resume reviewer. Compare the RESUME to the JOB DESCRIPTION below.\n" +
                "List exactly 5 specific, actionable suggestions to improve the resume's match with this JD.\n" +
                "Each suggestion must be one short line, starting with a dash, focused on a concrete skill, " +
                "keyword, or phrasing change. Do not add any introduction or conclusion text - only the 5 lines.\n\n" +
                "JOB DESCRIPTION:\n" + jobDescription + "\n\n" +
                "RESUME:\n" + resumeText;
    }

    private List<String> parseSuggestions(String content) {
        List<String> lines = new ArrayList<>();
        for (String line : content.split("\n")) {
            String cleaned = line.trim().replaceFirst("^[-*\\d.\\s]+", "").trim();
            if (!cleaned.isEmpty()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private String truncateWords(String text, int maxWords) {
        String[] words = text.trim().split("\\s+");
        if (words.length <= maxWords) return text;
        return String.join(" ", java.util.Arrays.copyOfRange(words, 0, maxWords));
    }
}