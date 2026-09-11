package com.atschecker.resumeats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeResponse {

    private int matchScorePercent;
    private List<String> matchedKeywords;
    private List<String> missingKeywords;
    private List<String> atsWarnings;

    // AI part is separated so a Groq failure never blocks the score result
    private List<String> aiSuggestions;
    private boolean aiSuggestionsAvailable;
    private String aiErrorMessage;

    private boolean previouslyScanned;
    private String previousScanSummary;
}
