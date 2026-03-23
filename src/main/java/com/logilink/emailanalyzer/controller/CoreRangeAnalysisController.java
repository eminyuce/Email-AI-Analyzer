package com.logilink.emailanalyzer.controller;

import com.logilink.emailanalyzer.model.ApiValidationError;
import com.logilink.emailanalyzer.model.CoreRangeAnalysisRequest;
import com.logilink.emailanalyzer.model.CoreRangeAnalysisResponse;
import com.logilink.emailanalyzer.service.CoreRangeAnalysisService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.BindingResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/core-test")
@PreAuthorize("hasRole('ADMIN')")
public class CoreRangeAnalysisController {

    private final CoreRangeAnalysisService coreRangeAnalysisService;

    public CoreRangeAnalysisController(CoreRangeAnalysisService coreRangeAnalysisService) {
        this.coreRangeAnalysisService = coreRangeAnalysisService;
    }

    @PostMapping(path = "/analyze-range", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> analyzeRange(@Valid @RequestBody CoreRangeAnalysisRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(validationErrors(bindingResult));
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            CoreRangeAnalysisResponse response = new CoreRangeAnalysisResponse();
            response.setSuccess(false);
            response.setMessage("startDate must be before endDate.");
            response.setRequestedMaxEmails(request.getMaxEmails());
            response.setRangeStart(request.getStartDate());
            response.setRangeEnd(request.getEndDate());
            return ResponseEntity.badRequest().body(response);
        }

        try {
            CoreRangeAnalysisResponse response = coreRangeAnalysisService.analyzeByDateRange(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getMaxEmails()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            CoreRangeAnalysisResponse response = new CoreRangeAnalysisResponse();
            response.setSuccess(false);
            response.setMessage("Core date-range analysis failed: " + e.getMessage());
            response.setRequestedMaxEmails(request.getMaxEmails());
            response.setRangeStart(request.getStartDate());
            response.setRangeEnd(request.getEndDate());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private List<ApiValidationError> validationErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        bindingResult.getFieldErrors()
                .forEach(fieldError -> errors.put(fieldError.getField(), fieldError.getDefaultMessage()));

        List<ApiValidationError> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : errors.entrySet()) {
            result.add(new ApiValidationError(entry.getKey(), entry.getValue()));
        }
        return result;
    }
}
