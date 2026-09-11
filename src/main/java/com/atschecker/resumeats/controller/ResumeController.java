package com.atschecker.resumeats.controller;

import com.atschecker.resumeats.model.Resume;
import com.atschecker.resumeats.repository.ResumeRepository;
import com.atschecker.resumeats.service.PdfExtractionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeRepository resumeRepository;
    private final PdfExtractionService pdfExtractionService;

    public ResumeController(ResumeRepository resumeRepository, PdfExtractionService pdfExtractionService) {
        this.resumeRepository = resumeRepository;
        this.pdfExtractionService = pdfExtractionService;
    }

    /**
     * Upload a resume PDF. Returns a resumeId which the Chrome extension
     * stores locally (chrome.storage.local) and reuses for every future scan.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        if (file == null || file.isEmpty()) {
            response.put("error", "No file uploaded.");
            return ResponseEntity.badRequest().body(response);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            response.put("error", "Only PDF files are supported.");
            return ResponseEntity.badRequest().body(response);
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            response.put("error", "File too large. Please upload a PDF under 5MB.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String extractedText = pdfExtractionService.extractText(file);

            Resume resume = new Resume();
            resume.setFileName(filename);
            resume.setExtractedText(extractedText);
            Resume saved = resumeRepository.save(resume);

            response.put("resumeId", saved.getId());
            response.put("fileName", saved.getFileName());
            response.put("message", "Resume uploaded and parsed successfully.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getResume(@PathVariable Long id) {
        return resumeRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
