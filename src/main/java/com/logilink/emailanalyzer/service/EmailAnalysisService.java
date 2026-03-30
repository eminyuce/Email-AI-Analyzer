package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.repository.EmailAnalysisRepository;
import com.logilink.emailanalyzer.repository.EmailAnalysisSpecification;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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
            Integer scoreMin,
            Integer scoreMax,
            Boolean actionNeeded,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            List<String> stakeholders,
            Long settingId,
            Pageable pageable
    ) {
        Specification<EmailAnalysis> spec = EmailAnalysisSpecification.filter(
                keyword,
                criticalityLevels,
                scoreMin,
                scoreMax,
                actionNeeded,
                dateFrom,
                dateTo,
                stakeholders,
                settingId
        );
        return repository.findAll(spec, pageable);
    }

    public List<EmailAnalysis> searchAll(
            String keyword,
            List<String> criticalityLevels,
            Integer scoreMin,
            Integer scoreMax,
            Boolean actionNeeded,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            List<String> stakeholders,
            Long settingId
    ) {
        Specification<EmailAnalysis> spec = EmailAnalysisSpecification.filter(
                keyword,
                criticalityLevels,
                scoreMin,
                scoreMax,
                actionNeeded,
                dateFrom,
                dateTo,
                stakeholders,
                settingId
        );
        return repository.findAll(spec, Sort.by(Sort.Direction.DESC, "processedAt"));
    }

    public Optional<EmailAnalysis> findById(Long id) {
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

    /**
     * Deletes analyses by primary key. Removed rows no longer block re-processing
     * {@link com.logilink.emailanalyzer.service.AnalysisService} uses {@code existsByEmailId}.
     */
    @Transactional
    public long deleteByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return 0;
        }
        List<EmailAnalysis> existing = repository.findAllById(distinct);
        if (existing.isEmpty()) {
            return 0;
        }
        repository.deleteAllInBatch(existing);
        return existing.size();
    }

    @Transactional
    public long deleteIncompleteCriticality() {
        return repository.deleteWhereCriticalityIncomplete();
    }
}
