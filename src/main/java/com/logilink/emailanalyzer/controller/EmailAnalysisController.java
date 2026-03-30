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

import java.time.LocalDateTime;
import java.util.List;

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
