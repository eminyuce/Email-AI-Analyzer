package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.service.AppSettingsService;
import com.logilink.emailanalyzer.service.EmailAnalysisService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Controller
@RequestMapping("/emails")
@PreAuthorize("hasRole('ADMIN')")
public class EmailAnalysisController {

    private final EmailAnalysisService service;
    private final AppSettingsService appSettingsService;

    public EmailAnalysisController(EmailAnalysisService service, AppSettingsService appSettingsService) {
        this.service = service;
        this.appSettingsService = appSettingsService;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> criticalityLevels,
            @RequestParam(required = false) String scoreMin,
            @RequestParam(required = false) String scoreMax,
            @RequestParam(required = false) Boolean actionNeeded,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) List<String> stakeholders,
            @RequestParam(required = false) String settingId,
            @PageableDefault(size = 20, sort = "processedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model
    ) {
        Long settingIdLong = StringUtils.isBlank(settingId) ? null : Long.valueOf(settingId.trim());
        Integer scoreMinInt = parseScoreBound(scoreMin, 0, 100);
        Integer scoreMaxInt = parseScoreBound(scoreMax, 0, 100);
        if (scoreMinInt != null && scoreMaxInt != null && scoreMinInt > scoreMaxInt) {
            Integer swap = scoreMinInt;
            scoreMinInt = scoreMaxInt;
            scoreMaxInt = swap;
        }
        Page<EmailAnalysis> page = service.search(
                keyword,
                criticalityLevels,
                scoreMinInt,
                scoreMaxInt,
                actionNeeded,
                dateFrom,
                dateTo,
                stakeholders,
                settingIdLong,
                pageable);

        model.addAttribute("page", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("criticalityLevels", criticalityLevels);
        model.addAttribute("scoreMin", scoreMinInt);
        model.addAttribute("scoreMax", scoreMaxInt);
        model.addAttribute("actionNeeded", actionNeeded);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("stakeholders", stakeholders);
        model.addAttribute("settingId", settingIdLong);
        model.addAttribute("settingsProfiles", appSettingsService.listAll());

        return "email/list";
    }

    @GetMapping("/report")
    public String report(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> criticalityLevels,
            @RequestParam(required = false) String scoreMin,
            @RequestParam(required = false) String scoreMax,
            @RequestParam(required = false) Boolean actionNeeded,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) List<String> stakeholders,
            @RequestParam(required = false) String settingId,
            Model model
    ) {
        Long settingIdLong = StringUtils.isBlank(settingId) ? null : Long.valueOf(settingId.trim());
        Integer scoreMinInt = parseScoreBound(scoreMin, 0, 100);
        Integer scoreMaxInt = parseScoreBound(scoreMax, 0, 100);
        if (scoreMinInt != null && scoreMaxInt != null && scoreMinInt > scoreMaxInt) {
            Integer swap = scoreMinInt;
            scoreMinInt = scoreMaxInt;
            scoreMaxInt = swap;
        }

        List<EmailAnalysis> filteredEmails = service.searchAll(
                keyword,
                criticalityLevels,
                scoreMinInt,
                scoreMaxInt,
                actionNeeded,
                dateFrom,
                dateTo,
                stakeholders,
                settingIdLong
        );

        long totalEmails = filteredEmails.size();
        long actionRequiredCount = filteredEmails.stream().filter(e -> Boolean.TRUE.equals(e.getActionNeeded())).count();
        long criticalCount = filteredEmails.stream().filter(e -> "CRITICAL".equalsIgnoreCase(e.getCriticalityLevel())).count();
        long highCount = filteredEmails.stream().filter(e -> "HIGH".equalsIgnoreCase(e.getCriticalityLevel())).count();
        long mediumCount = filteredEmails.stream().filter(e -> "MEDIUM".equalsIgnoreCase(e.getCriticalityLevel())).count();
        long lowCount = filteredEmails.stream().filter(e -> "LOW".equalsIgnoreCase(e.getCriticalityLevel())).count();
        long criticalEmailCount = criticalCount + highCount;
        long receivedTodayCount = filteredEmails.stream()
                .filter(e -> e.getEmailDate() != null && LocalDate.now().equals(e.getEmailDate().toLocalDate()))
                .count();
        long distributionMax = Stream.of(criticalCount, highCount, mediumCount, lowCount)
                .max(Long::compareTo)
                .orElse(1L);
        if (distributionMax == 0) {
            distributionMax = 1;
        }

        double averageScore = filteredEmails.stream()
                .map(EmailAnalysis::getCriticalityScore)
                .filter(v -> v != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0D);
        double actionRequiredPercentage = totalEmails == 0 ? 0D : (actionRequiredCount * 100.0) / totalEmails;

        double avgBusinessPriority = filteredEmails.stream()
                .map(EmailAnalysis::getBreakdown)
                .filter(b -> b != null && b.getBusinessPriority() != null)
                .mapToInt(b -> b.getBusinessPriority())
                .average()
                .orElse(0D);
        double avgUrgency = filteredEmails.stream()
                .map(EmailAnalysis::getBreakdown)
                .filter(b -> b != null && b.getUrgency() != null)
                .mapToInt(b -> b.getUrgency())
                .average()
                .orElse(0D);
        double avgActionRequired = filteredEmails.stream()
                .map(EmailAnalysis::getBreakdown)
                .filter(b -> b != null && b.getActionRequired() != null)
                .mapToInt(b -> b.getActionRequired())
                .average()
                .orElse(0D);
        double avgFinancialImpact = filteredEmails.stream()
                .map(EmailAnalysis::getBreakdown)
                .filter(b -> b != null && b.getFinancialImpact() != null)
                .mapToInt(b -> b.getFinancialImpact())
                .average()
                .orElse(0D);

        List<EmailAnalysis> topCriticalEmails = filteredEmails.stream()
                .filter(e -> e.getCriticalityScore() != null)
                .sorted(Comparator.comparing(EmailAnalysis::getCriticalityScore).reversed())
                .limit(5)
                .toList();
        List<EmailAnalysis> actionRequiredEmails = filteredEmails.stream()
                .filter(e -> Boolean.TRUE.equals(e.getActionNeeded()))
                .sorted(Comparator.comparing(
                        (EmailAnalysis e) -> e.getCriticalityScore() == null ? Integer.MIN_VALUE : e.getCriticalityScore()
                ).reversed())
                .limit(10)
                .toList();

        List<String> insights = buildInsights(
                totalEmails,
                actionRequiredPercentage,
                actionRequiredCount,
                criticalEmailCount,
                avgUrgency,
                avgFinancialImpact,
                filteredEmails
        );

        model.addAttribute("keyword", keyword);
        model.addAttribute("criticalityLevels", criticalityLevels);
        model.addAttribute("scoreMin", scoreMinInt);
        model.addAttribute("scoreMax", scoreMaxInt);
        model.addAttribute("actionNeeded", actionNeeded);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("stakeholders", stakeholders);
        model.addAttribute("settingId", settingIdLong);
        model.addAttribute("settingsProfiles", appSettingsService.listAll());
        model.addAttribute("totalEmails", totalEmails);
        model.addAttribute("averageScore", formatOneDecimal(averageScore));
        model.addAttribute("criticalEmailCount", criticalEmailCount);
        model.addAttribute("actionRequiredCount", actionRequiredCount);
        model.addAttribute("actionRequiredPercentage", Math.round(actionRequiredPercentage));
        model.addAttribute("receivedTodayCount", receivedTodayCount);
        model.addAttribute("criticalCount", criticalCount);
        model.addAttribute("highCount", highCount);
        model.addAttribute("mediumCount", mediumCount);
        model.addAttribute("lowCount", lowCount);
        model.addAttribute("distributionMax", distributionMax);
        model.addAttribute("avgBusinessPriority", formatOneDecimal(avgBusinessPriority));
        model.addAttribute("avgUrgency", formatOneDecimal(avgUrgency));
        model.addAttribute("avgActionRequired", formatOneDecimal(avgActionRequired));
        model.addAttribute("avgFinancialImpact", formatOneDecimal(avgFinancialImpact));
        model.addAttribute("avgBusinessPriorityPct", toPercent(avgBusinessPriority, 40));
        model.addAttribute("avgUrgencyPct", toPercent(avgUrgency, 30));
        model.addAttribute("avgActionRequiredPct", toPercent(avgActionRequired, 20));
        model.addAttribute("avgFinancialImpactPct", toPercent(avgFinancialImpact, 10));
        model.addAttribute("topCriticalEmails", topCriticalEmails);
        model.addAttribute("actionRequiredEmails", actionRequiredEmails);
        model.addAttribute("insights", insights);
        return "email/report";
    }

    private static Integer parseScoreBound(String raw, int minAllowed, int maxAllowed) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.min(maxAllowed, Math.max(minAllowed, v));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> buildInsights(
            long totalEmails,
            double actionRequiredPercentage,
            long actionRequiredCount,
            long criticalEmailCount,
            double avgUrgency,
            double avgFinancialImpact,
            List<EmailAnalysis> filteredEmails
    ) {
        List<String> insights = new ArrayList<>();
        insights.add(String.format(Locale.US,
                "%.0f%% of analyzed emails require action (%d out of %d).",
                actionRequiredPercentage,
                actionRequiredCount,
                totalEmails));

        long recentHighCritical = filteredEmails.stream()
                .filter(e -> e.getProcessedAt() != null && e.getProcessedAt().isAfter(LocalDateTime.now().minusDays(7)))
                .filter(e -> "CRITICAL".equalsIgnoreCase(e.getCriticalityLevel()) || "HIGH".equalsIgnoreCase(e.getCriticalityLevel()))
                .count();
        long previousHighCritical = filteredEmails.stream()
                .filter(e -> e.getProcessedAt() != null
                        && e.getProcessedAt().isAfter(LocalDateTime.now().minusDays(14))
                        && e.getProcessedAt().isBefore(LocalDateTime.now().minusDays(7)))
                .filter(e -> "CRITICAL".equalsIgnoreCase(e.getCriticalityLevel()) || "HIGH".equalsIgnoreCase(e.getCriticalityLevel()))
                .count();

        if (recentHighCritical > previousHighCritical) {
            insights.add("High-criticality rate is increasing compared with the previous week. Review priorities proactively.");
        } else if (criticalEmailCount == 0) {
            insights.add("Most analyzed emails are low risk right now; no high-criticality alerts detected.");
        } else {
            insights.add("High-criticality volume is stable across recent periods. Continue monitoring for spikes.");
        }

        if (avgUrgency >= 20) {
            insights.add("Average urgency is high, indicating meaningful time pressure on incoming communication.");
        } else if (avgFinancialImpact <= 3) {
            insights.add("Financial impact stays low on average, suggesting more operational than financial pressure.");
        } else {
            insights.add("Urgency and financial impact are moderate overall, with a balanced risk profile.");
        }
        return insights;
    }

    private static double formatOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static int toPercent(double value, int max) {
        if (max <= 0) {
            return 0;
        }
        int percent = (int) Math.round((value * 100.0) / max);
        return Math.max(0, Math.min(100, percent));
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        EmailAnalysis email = service.findById(id)
                .orElseThrow(() -> new RuntimeException("Email not found: " + id));
        model.addAttribute("email", email);
        return "email/detail";
    }

    @PostMapping("/delete-all")
    public String deleteAll(RedirectAttributes redirectAttributes) {
        long deletedCount = service.deleteAll();
        redirectAttributes.addFlashAttribute("deletedAll", true);
        redirectAttributes.addFlashAttribute("deletedCount", deletedCount);
        return "redirect:/emails";
    }

    @PostMapping("/delete-selected")
    public String deleteSelected(
            @RequestParam(required = false) List<Long> ids,
            RedirectAttributes redirectAttributes
    ) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("deleteSelectedError", true);
            return "redirect:/emails";
        }
        long deletedCount = service.deleteByIds(ids);
        redirectAttributes.addFlashAttribute("deletedSelected", true);
        redirectAttributes.addFlashAttribute("deletedSelectedCount", deletedCount);
        return "redirect:/emails";
    }

    @PostMapping("/delete-incomplete")
    public String deleteIncomplete(RedirectAttributes redirectAttributes) {
        long deletedCount = service.deleteIncompleteCriticality();
        redirectAttributes.addFlashAttribute("deletedIncomplete", true);
        redirectAttributes.addFlashAttribute("deletedIncompleteCount", deletedCount);
        return "redirect:/emails";
    }
}
