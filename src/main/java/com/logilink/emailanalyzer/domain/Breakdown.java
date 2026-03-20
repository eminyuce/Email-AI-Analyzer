package com.logilink.emailanalyzer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Breakdown implements Serializable {
    private Integer businessPriority;
    private Integer urgency;
    private Integer actionRequired;
    private Integer financialImpact;
}
