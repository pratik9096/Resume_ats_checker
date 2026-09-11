package com.atschecker.resumeats.repository;

import com.atschecker.resumeats.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
}
