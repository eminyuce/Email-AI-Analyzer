package com.logilink.emailanalyzer.repository;

import com.logilink.emailanalyzer.domain.EmailAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailAnalysisRepository extends JpaRepository<EmailAnalysis, String>, JpaSpecificationExecutor<EmailAnalysis> {
    List<EmailAnalysis> findTop20ByOrderByProcessedAtDesc();
}
