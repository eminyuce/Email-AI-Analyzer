package com.logilink.emailanalyzer.service;

import com.logilink.emailanalyzer.common.AppConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ObservabilityMetricsService {

    private final Counter failedTransactionsCounter;
    private final AtomicInteger activeOrders;

    public ObservabilityMetricsService(MeterRegistry meterRegistry) {
        failedTransactionsCounter =
                Counter.builder(AppConstants.Metrics.TRANSACTIONS_FAILED_METRIC)
                        .description("Number of failed transactions")
                        .tag(AppConstants.Metrics.ENV_TAG_KEY, AppConstants.Metrics.ENV_TAG_VALUE)
                        .register(meterRegistry);

        activeOrders = new AtomicInteger(0);
        Gauge.builder(AppConstants.Metrics.ORDERS_ACTIVE_METRIC, activeOrders, AtomicInteger::get)
                .description("Number of active orders")
                .tag(AppConstants.Metrics.ENV_TAG_KEY, AppConstants.Metrics.ENV_TAG_VALUE)
                .register(meterRegistry);
    }

    /**
     * Increments failed transaction counter metric.
     */
    public void incrementFailedTransactions() {
        failedTransactionsCounter.increment();
    }

    public int incrementActiveOrders() {
        return activeOrders.incrementAndGet();
    }

    public int decrementActiveOrders() {
        return activeOrders.decrementAndGet();
    }

    public void setActiveOrders(int currentActiveOrders) {
        activeOrders.set(Math.max(currentActiveOrders, 0));
    }

    public int getActiveOrders() {
        return activeOrders.get();
    }
}
