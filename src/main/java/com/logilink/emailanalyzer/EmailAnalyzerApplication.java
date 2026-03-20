package com.logilink.emailanalyzer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class EmailAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailAnalyzerApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            log.info("✅ E-posta analizi başlatıldı – Ollama + 1080 Ti aktif");
        };
    }
}
