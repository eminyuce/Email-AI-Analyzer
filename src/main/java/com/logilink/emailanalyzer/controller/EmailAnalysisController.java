package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.service.EmailAnalysisService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/emails")
public class EmailAnalysisController {

    private final EmailAnalysisService service;

    public EmailAnalysisController(EmailAnalysisService service) {
        this.service = service;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> criticalityLevels,
            @RequestParam(required = false) Boolean actionNeeded,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) List<String> stakeholders,
            @PageableDefault(size = 20, sort = "processedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model
    ) {
        Page<EmailAnalysis> page = service.search(keyword, criticalityLevels, actionNeeded, dateFrom, dateTo, stakeholders, pageable);

        model.addAttribute("page", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("criticalityLevels", criticalityLevels);
        model.addAttribute("actionNeeded", actionNeeded);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("stakeholders", stakeholders);

        return "email/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        EmailAnalysis email = service.findById(id)
                .orElseThrow(() -> new RuntimeException("Email not found: " + id));
        model.addAttribute("email", email);
        return "email/detail";
    }
}
