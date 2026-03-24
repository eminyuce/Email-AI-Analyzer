package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.repository.EmailAnalysisRepository;
import com.logilink.emailanalyzer.repository.EmailAnalysisSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class EmailAnalysisService {

    private final EmailAnalysisRepository repository;

    public EmailAnalysisService(EmailAnalysisRepository repository) {
        this.repository = repository;
    }

    public Page<EmailAnalysis> findLatest(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("processedAt").descending());
        return repository.findAll(pageable);
    }

    public Page<EmailAnalysis> search(
            String keyword,
            List<String> criticalityLevels,
            Boolean actionNeeded,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            List<String> stakeholders,
            Pageable pageable
    ) {
        Specification<EmailAnalysis> spec = EmailAnalysisSpecification.filter(
                keyword,
                criticalityLevels,
                actionNeeded,
                dateFrom,
                dateTo,
                stakeholders
        );
        return repository.findAll(spec, pageable);
    }

    public Optional<EmailAnalysis> findById(String id) {
        return repository.findById(id);
    }

    @Transactional
    public long deleteAll() {
        long total = repository.count();
        if (total > 0) {
            repository.deleteAllInBatch();
        }
        return total;
    }
}
