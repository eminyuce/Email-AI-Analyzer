package com.logilink.emailanalyzer.repository;

import com.logilink.emailanalyzer.domain.EmailAnalysis;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EmailAnalysisSpecification {

    public static Specification<EmailAnalysis> filter(
            String keyword,
            List<String> criticalityLevels,
            Boolean actionNeeded,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            List<String> stakeholders
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("subject")), likePattern),
                        cb.like(cb.lower(root.get("sender")), likePattern)
                ));
            }

            if (criticalityLevels != null && !criticalityLevels.isEmpty()) {
                predicates.add(root.get("criticalityLevel").in(criticalityLevels));
            }

            if (actionNeeded != null) {
                predicates.add(cb.equal(root.get("actionNeeded"), actionNeeded));
            }

            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("emailDate"), dateFrom));
            }

            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("emailDate"), dateTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
