package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @NotNull(message = "Missing required field: startDate")
    @JsonProperty("start_date")
    @JsonAlias("startDate")
    private LocalDateTime startDate;

    @NotNull(message = "Missing required field: endDate")
    @JsonProperty("end_date")
    @JsonAlias("endDate")
    private LocalDateTime endDate;

    @NotNull(message = "Missing required field: maxEmails")
    @Min(value = 1, message = "maxEmails must be at least 1.")
    @Max(value = 25, message = "maxEmails must be at most 25.")
    @JsonProperty("max_emails")
    @JsonAlias("maxEmails")
    private Integer maxEmails;
}
