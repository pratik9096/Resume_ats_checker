package com.atschecker.resumeats.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, explainable scoring engine. Deliberately kept rule-based
 * (no AI) so the match score is reproducible and easy to justify - the
 * Groq-powered suggestions are layered on top separately in GroqService.
 */
@Service
public class MatchingService {

    private final List<String> skillsDictionary = new ArrayList<>();

    @PostConstruct
    public void loadDictionary() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("skills-dictionary.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty()) {
                    skillsDictionary.add(line);
                }
            }
        }
    }

    public static class MatchResult {
        public int matchScorePercent;
        public List<String> matchedKeywords = new ArrayList<>();
        public List<String> missingKeywords = new ArrayList<>();
        public List<String> atsWarnings = new ArrayList<>();
    }

    public MatchResult match(String resumeText, String jobDescription) {
        String resumeLower = normalize(resumeText);
        String jdLower = normalize(jobDescription);

        // 1) Find which dictionary skills appear in the JD at all
        Set<String> jdSkills = new LinkedHashSet<>();
        for (String skill : skillsDictionary) {
            if (containsWholePhrase(jdLower, skill)) {
                jdSkills.add(skill);
            }
        }

        // Fallback: if the JD doesn't match our dictionary well (e.g. a very
        // niche/non-tech JD), also pull frequent capitalized/technical-looking
        // tokens directly from the JD so the tool still produces useful output.
        if (jdSkills.size() < 3) {
            jdSkills.addAll(extractFallbackKeywords(jobDescription));
        }

        MatchResult result = new MatchResult();
        for (String skill : jdSkills) {
            if (containsWholePhrase(resumeLower, skill)) {
                result.matchedKeywords.add(skill);
            } else {
                result.missingKeywords.add(skill);
            }
        }

        int total = result.matchedKeywords.size() + result.missingKeywords.size();
        result.matchScorePercent = total == 0 ? 0
                : (int) Math.round((result.matchedKeywords.size() * 100.0) / total);

        result.atsWarnings = runAtsFormatChecks(resumeText);
        return result;
    }

    private String normalize(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9+#./\\s]", " ");
    }

    private boolean containsWholePhrase(String haystack, String phrase) {
        // Word-boundary-ish match so "java" doesn't match inside "javascript"
        String escaped = Pattern.quote(phrase);
        Pattern p = Pattern.compile("(?<![a-z0-9])" + escaped + "(?![a-z0-9])");
        Matcher m = p.matcher(haystack);
        return m.find();
    }

    /**
     * Very light heuristic fallback for JDs whose key terms aren't in our
     * static dictionary (e.g. niche/non-software roles). Picks frequent
     * multi-character tokens that look like nouns/skills rather than
     * common English filler words.
     */
    private List<String> extractFallbackKeywords(String jobDescription) {
        Set<String> stopwords = Set.of("the", "and", "for", "with", "you", "your", "will",
                "are", "our", "this", "that", "have", "has", "job", "role", "work", "team",
                "years", "experience", "ability", "strong", "must", "should", "candidate",
                "skills", "requirements", "responsibilities", "about", "company", "position");

        Map<String, Integer> freq = new HashMap<>();
        for (String word : normalize(jobDescription).split("\\s+")) {
            if (word.length() > 3 && !stopwords.contains(word)) {
                freq.merge(word, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Standard, well-documented ATS formatting best practices.
     * These are hardcoded rules, not AI - structural checks are more
     * reliable done deterministically than left to an LLM.
     */
    private List<String> runAtsFormatChecks(String resumeText) {
        List<String> warnings = new ArrayList<>();
        String lower = resumeText.toLowerCase();

        boolean hasEmail = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}").matcher(lower).find();
        if (!hasEmail) {
            warnings.add("No email address detected in plain text - make sure contact info isn't inside a header/image, since many ATS parsers skip those.");
        }

        boolean hasPhone = Pattern.compile("(\\+?\\d[\\d\\s-]{8,}\\d)").matcher(resumeText).find();
        if (!hasPhone) {
            warnings.add("No phone number detected in plain text.");
        }

        String[] expectedSections = {"experience", "education", "skills", "project"};
        for (String section : expectedSections) {
            if (!lower.contains(section)) {
                warnings.add("Couldn't find a clear '" + capitalize(section) + "' section heading - use standard section titles so ATS software can categorize your content correctly.");
            }
        }

        int wordCount = resumeText.trim().split("\\s+").length;
        if (wordCount < 150) {
            warnings.add("Resume text seems quite short (" + wordCount + " words) - it may be too sparse, or content might be trapped in a table/image that ATS can't read.");
        }
        if (wordCount > 1200) {
            warnings.add("Resume text is quite long (" + wordCount + " words) - consider trimming to 1-2 pages worth of content.");
        }

        return warnings;
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
