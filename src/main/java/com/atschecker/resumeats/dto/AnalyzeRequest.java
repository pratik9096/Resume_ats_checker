package com.atschecker.resumeats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AnalyzeRequest {

    @NotNull(message = "resumeId is required")
    private Long resumeId;

    @NotBlank(message = "jobDescription text is required")
    private String jobDescription;

    private String jobUrl;

    private String jobTitle;
}
