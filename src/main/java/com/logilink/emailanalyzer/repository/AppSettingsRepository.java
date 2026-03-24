package com.logilink.emailanalyzer.repository;

import com.logilink.emailanalyzer.domain.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
    Optional<AppSettings> findFirstByActiveTrue();
    boolean existsByActiveTrueAndIdNot(Long id);

    @Modifying
    @Query("update AppSettings s set s.active = false where s.active = true")
    int deactivateAll();
}
