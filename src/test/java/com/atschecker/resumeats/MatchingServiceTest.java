package com.atschecker.resumeats;

import com.atschecker.resumeats.service.MatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchingServiceTest {

    private MatchingService matchingService;

    @BeforeEach
    void setUp() throws Exception {
        matchingService = new MatchingService();
        matchingService.loadDictionary();
    }

    @Test
    void fullMatch_shouldScoreHigh() {
        String resume = "Experienced in Java, Spring Boot, MySQL, REST API, Git, Docker.";
        String jd = "Looking for a developer skilled in Java, Spring Boot, MySQL, REST API.";

        MatchingService.MatchResult result = matchingService.match(resume, jd);

        assertTrue(result.matchScorePercent >= 90, "Expected a high match score, got " + result.matchScorePercent);
        assertTrue(result.missingKeywords.isEmpty());
    }

    @Test
    void partialMatch_shouldDetectMissingSkills() {
        String resume = "Experienced in Java and MySQL.";
        String jd = "Looking for a developer skilled in Java, Docker, Kubernetes, AWS.";

        MatchingService.MatchResult result = matchingService.match(resume, jd);

        assertTrue(result.matchScorePercent < 100);
        assertTrue(result.missingKeywords.contains("docker"));
        assertTrue(result.missingKeywords.contains("kubernetes"));
    }

    @Test
    void javaShouldNotFalsePositiveMatchJavascript() {
        String resume = "Experienced in JavaScript and React only, no backend Java experience.";
        String jd = "Looking for a Java backend developer.";

        MatchingService.MatchResult result = matchingService.match(resume, jd);

        assertTrue(result.missingKeywords.contains("java"),
                "Plain 'java' skill should be reported missing even though 'javascript' is present");
    }

    @Test
    void atsWarnings_flagMissingSections() {
        String resume = "I know Java and Spring Boot."; // no email, no sections
        MatchingService.MatchResult result = matchingService.match(resume, "Java developer needed with Spring Boot skills.");

        assertFalse(result.atsWarnings.isEmpty());
    }
}
