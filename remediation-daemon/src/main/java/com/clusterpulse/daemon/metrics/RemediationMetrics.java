package com.clusterpulse.daemon.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Publishes the daemon's own metrics so Prometheus can scrape them at
 * /actuator/prometheus and Grafana can chart the self-healing loop:
 *
 *   clusterpulse_alerts_received_total{status}
 *   clusterpulse_remediations_total{runbook,outcome}
 *   clusterpulse_runbook_duration_seconds{runbook}
 */
@Component
public class RemediationMetrics {

    private final MeterRegistry registry;

    public RemediationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void alertReceived(String status) {
        registry.counter("clusterpulse_alerts_received_total", "status", nz(status)).increment();
    }

    public void remediation(String runbook, String outcome) {
        registry.counter("clusterpulse_remediations_total",
                "runbook", nz(runbook), "outcome", nz(outcome)).increment();
    }

    public void recordDuration(String runbook, long millis) {
        registry.timer("clusterpulse_runbook_duration_seconds", "runbook", nz(runbook))
                .record(millis, TimeUnit.MILLISECONDS);
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? "unknown" : s;
    }
}
