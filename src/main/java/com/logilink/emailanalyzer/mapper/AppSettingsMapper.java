package com.logilink.emailanalyzer.mapper;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.model.SettingsForm;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface AppSettingsMapper {

    SettingsForm toSettingsForm(AppSettings settings);
}
