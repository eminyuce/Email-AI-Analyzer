package com.logilink.emailanalyzer.mapper;

import com.logilink.emailanalyzer.domain.EmailAnalysis;
import com.logilink.emailanalyzer.model.EmailAnalysisReportDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EmailAnalysisMapper {

    EmailAnalysisReportDto toReportDto(EmailAnalysis entity);
}
