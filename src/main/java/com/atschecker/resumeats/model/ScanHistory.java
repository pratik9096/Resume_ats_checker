package com.atschecker.resumeats.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "scan_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long resumeId;

    @Column(length = 2048)
    private String jobUrl;

    @Column(length = 500)
    private String jobTitle;

    private Integer matchScore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String missingKeywords;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String aiSuggestions;

    private LocalDateTime scannedAt;

    @PrePersist
    public void prePersist() {
        this.scannedAt = LocalDateTime.now();
    }
}
