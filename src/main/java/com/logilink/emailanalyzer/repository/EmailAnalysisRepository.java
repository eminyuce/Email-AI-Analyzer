package com.logilink.emailanalyzer.repository;

import com.logilink.emailanalyzer.domain.EmailAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface EmailAnalysisRepository extends JpaRepository<EmailAnalysis, Long>, JpaSpecificationExecutor<EmailAnalysis> {
    List<EmailAnalysis> findTop20ByOrderByProcessedAtDesc();

    boolean existsByEmailId(String emailId);

    @Query("select e.emailId from EmailAnalysis e where e.emailId in :ids")
    Set<String> findExistingEmailIdsIn(@Param("ids") Collection<String> ids);

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
