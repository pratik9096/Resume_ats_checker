package com.atschecker.resumeats.repository;

import com.atschecker.resumeats.model.ScanHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScanHistoryRepository extends JpaRepository<ScanHistory, Long> {
    Optional<ScanHistory> findTopByJobUrlOrderByScannedAtDesc(String jobUrl);
    List<ScanHistory> findByResumeIdOrderByScannedAtDesc(Long resumeId);
}
