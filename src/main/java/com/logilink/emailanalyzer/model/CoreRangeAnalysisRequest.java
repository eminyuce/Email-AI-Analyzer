package com.logilink.emailanalyzer.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreRangeAnalysisRequest {

    @NotNull(message = "startDate is required.")
    private LocalDateTime startDate;

    @NotNull(message = "endDate is required.")
    private LocalDateTime endDate;

    @NotNull(message = "maxEmails is required.")
    @Min(value = 1, message = "maxEmails must be at least 1.")
    @Max(value = 25, message = "maxEmails must be at most 25.")
    private Integer maxEmails;
}
