package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.model.SettingsForm;
import com.logilink.emailanalyzer.service.AppSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.web.bind.annotation.ResponseBody;
import java.util.Map;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final AppSettingsService appSettingsService;

    @GetMapping
    public String settings(Model model) {
        if (!model.containsAttribute("settingsForm")) {
            model.addAttribute("settingsForm", SettingsForm.from(appSettingsService.getOrCreate()));
        }
        return "settings/form";
    }

    @PostMapping
    public String saveSettings(
            @Valid @ModelAttribute("settingsForm") SettingsForm settingsForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.settingsForm", bindingResult);
            redirectAttributes.addFlashAttribute("settingsForm", settingsForm);
            return "redirect:/settings";
        }

        appSettingsService.save(settingsForm);
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/settings";
    }

    @PostMapping("/test-db")
    @ResponseBody
    public Map<String, Object> testDb(@ModelAttribute SettingsForm form) {
        boolean success = appSettingsService.testDatabaseConnection(
                form.getDbHost(),
                form.getDbPort(),
                form.getDbName(),
                form.getDbUsername(),
                form.getDbPassword()
        );
        return Map.of("success", success, "message", success ? "Connection successful!" : "Connection failed. Please check your settings.");
    }
}
