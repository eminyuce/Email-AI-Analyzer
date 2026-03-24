package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.common.AppConstants;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AiAgentSessionMetricsService {

    private final AtomicInteger activeSessions = new AtomicInteger(0);

    public AiAgentSessionMetricsService(MeterRegistry meterRegistry) {
        Gauge.builder(AppConstants.Metrics.ACTIVE_SESSIONS_METRIC, activeSessions, AtomicInteger::get)
                .description("Current number of active AI agent sessions")
                .register(meterRegistry);
    }

    /**
     * Tracks session start and returns latest active count.
     */
    public int sessionStarted() {
        return activeSessions.incrementAndGet();
    }

    /**
     * Tracks session end and prevents negative gauge values.
     */
    public int sessionEnded() {
        return activeSessions.updateAndGet(current -> Math.max(current - 1, 0));
    }

    public void setActiveSessions(int sessions) {
        activeSessions.set(Math.max(sessions, 0));
    }

    public int getActiveSessions() {
        return activeSessions.get();
    }
}
