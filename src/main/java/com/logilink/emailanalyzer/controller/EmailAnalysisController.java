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
            @RequestParam(required = false) Boolean actionNeeded,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) List<String> stakeholders,
            @RequestParam(required = false) String settingId,
            @PageableDefault(size = 20, sort = "processedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model
    ) {
        Long settingIdLong = StringUtils.isBlank(settingId) ? null : Long.valueOf(settingId.trim());
        Page<EmailAnalysis> page = service.search(
                keyword, criticalityLevels, actionNeeded, dateFrom, dateTo, stakeholders, settingIdLong, pageable);

        model.addAttribute("page", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("criticalityLevels", criticalityLevels);
        model.addAttribute("actionNeeded", actionNeeded);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("stakeholders", stakeholders);
        model.addAttribute("settingId", settingIdLong);
        model.addAttribute("settingsProfiles", appSettingsService.listAll());

        return "email/list";
    }

    @GetMapping("/{emailId}")
    public String detail(@PathVariable String emailId, Model model) {
        EmailAnalysis email = service.findById(emailId)
                .orElseThrow(() -> new RuntimeException("Email not found: " + emailId));
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
}
