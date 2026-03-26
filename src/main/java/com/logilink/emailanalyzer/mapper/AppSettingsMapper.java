package com.logilink.emailanalyzer.mapper;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.model.SettingsForm;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface AppSettingsMapper {

    @Mapping(
            target = "llmProvider",
            expression = "java(com.logilink.emailanalyzer.common.LlmProviderType.fromSettingsValue(settings.getLlmProvider()).toSettingsValue())"
    )
    SettingsForm toSettingsForm(AppSettings settings);
}
