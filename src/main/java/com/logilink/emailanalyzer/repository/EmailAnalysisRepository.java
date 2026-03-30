package com.logilink.emailanalyzer.repository;

import com.logilink.emailanalyzer.domain.EmailAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailAnalysisRepository extends JpaRepository<EmailAnalysis, Long>, JpaSpecificationExecutor<EmailAnalysis> {
    List<EmailAnalysis> findTop20ByOrderByProcessedAtDesc();

    boolean existsByEmailId(String emailId);

    /**
     * Rows with no score, no level, blank level, or whitespace-only level (incomplete AI output).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM EmailAnalysis e
            WHERE e.criticalityScore IS NULL
               OR e.criticalityLevel IS NULL
               OR LENGTH(TRIM(e.criticalityLevel)) = 0
            """)
    int deleteWhereCriticalityIncomplete();
}
