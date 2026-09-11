package com.atschecker.resumeats.controller;

import com.atschecker.resumeats.dto.AnalyzeRequest;
import com.atschecker.resumeats.dto.AnalyzeResponse;
import com.atschecker.resumeats.model.Resume;
import com.atschecker.resumeats.model.ScanHistory;
import com.atschecker.resumeats.repository.ResumeRepository;
import com.atschecker.resumeats.repository.ScanHistoryRepository;
import com.atschecker.resumeats.service.GroqService;
import com.atschecker.resumeats.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private static final int MIN_JD_WORDS = 30;

    private final ResumeRepository resumeRepository;
    private final ScanHistoryRepository scanHistoryRepository;
    private final MatchingService matchingService;
    private final GroqService groqService;

    public AnalyzeController(ResumeRepository resumeRepository,
                              ScanHistoryRepository scanHistoryRepository,
                              MatchingService matchingService,
                              GroqService groqService) {
        this.resumeRepository = resumeRepository;
        this.scanHistoryRepository = scanHistoryRepository;
        this.matchingService = matchingService;
        this.groqService = groqService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@Valid @RequestBody AnalyzeRequest request) {

        // Scenario 8: reject junk/too-short input before spending an AI call
        int wordCount = request.getJobDescription().trim().split("\\s+").length;
        if (wordCount < MIN_JD_WORDS) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "This doesn't look like a full job description. Please paste the complete JD text (at least a few sentences).");
            return ResponseEntity.badRequest().body(error);
        }

        Optional<Resume> resumeOpt = resumeRepository.findById(request.getResumeId());
        if (resumeOpt.isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Resume not found. Please upload your resume again from the extension popup.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        Resume resume = resumeOpt.get();

        // Scenario 10: check if this exact job URL was already scanned before
        boolean previouslyScanned = false;
        String previousSummary = null;
        if (request.getJobUrl() != null && !request.getJobUrl().isBlank()) {
            Optional<ScanHistory> prior = scanHistoryRepository.findTopByJobUrlOrderByScannedAtDesc(request.getJobUrl());
            if (prior.isPresent()) {
                previouslyScanned = true;
                ScanHistory p = prior.get();
                previousSummary = "You already checked this job on " +
                        p.getScannedAt().format(DateTimeFormatter.ofPattern("dd MMM, HH:mm")) +
                        " - Score was " + p.getMatchScore() + "%.";
            }
        }

        // 1) Rule-based match score + ATS checks (always runs, always reliable)
        MatchingService.MatchResult matchResult = matchingService.match(resume.getExtractedText(), request.getJobDescription());

        // 2) AI suggestions (independent call - failure here must NOT break the score)
        GroqService.SuggestionResult aiResult = groqService.getSuggestions(resume.getExtractedText(), request.getJobDescription());

        // Save scan history
        ScanHistory history = new ScanHistory();
        history.setResumeId(resume.getId());
        history.setJobUrl(request.getJobUrl());
        history.setJobTitle(request.getJobTitle());
        history.setMatchScore(matchResult.matchScorePercent);
        history.setMissingKeywords(String.join(", ", matchResult.missingKeywords));
        history.setAiSuggestions(aiResult.available ? String.join(" | ", aiResult.suggestions) : null);
        scanHistoryRepository.save(history);

        AnalyzeResponse response = AnalyzeResponse.builder()
                .matchScorePercent(matchResult.matchScorePercent)
                .matchedKeywords(matchResult.matchedKeywords)
                .missingKeywords(matchResult.missingKeywords)
                .atsWarnings(matchResult.atsWarnings)
                .aiSuggestions(aiResult.available ? aiResult.suggestions : List.of())
                .aiSuggestionsAvailable(aiResult.available)
                .aiErrorMessage(aiResult.errorMessage)
                .previouslyScanned(previouslyScanned)
                .previousScanSummary(previousSummary)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{resumeId}")
    public ResponseEntity<List<ScanHistory>> getHistory(@PathVariable Long resumeId) {
        return ResponseEntity.ok(scanHistoryRepository.findByResumeIdOrderByScannedAtDesc(resumeId));
    }
}
