package com.clusterpulse.daemon.service;

import com.clusterpulse.daemon.config.ClusterPulseProperties;
import com.clusterpulse.daemon.metrics.RemediationMetrics;
import com.clusterpulse.daemon.web.dto.Alert;
import com.clusterpulse.daemon.web.dto.AlertmanagerWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns incoming alerts into remediation actions.
 *
 * Flow per alert:
 *   firing   -> pick a runbook, honour cooldown, execute, record metrics
 *   resolved -> log recovery and clear the cooldown so the node is armed again
 */
@Service
public class RemediationService {

    private static final Logger log = LoggerFactory.getLogger(RemediationService.class);

    private final ClusterPulseProperties props;
    private final RunbookExecutor executor;
    private final RemediationMetrics metrics;

    /** cooldown key ("alertname|instance") -> last execution time. */
    private final Map<String, Instant> lastRun = new ConcurrentHashMap<>();

    public RemediationService(ClusterPulseProperties props,
                              RunbookExecutor executor,
                              RemediationMetrics metrics) {
        this.props = props;
        this.executor = executor;
        this.metrics = metrics;
    }

    public void handleWebhook(AlertmanagerWebhook webhook) {
        if (webhook == null || webhook.alerts() == null) {
            log.warn("Received webhook with no alerts");
            return;
        }
        for (Alert alert : webhook.alerts()) {
            metrics.alertReceived(alert.status());
            if ("firing".equalsIgnoreCase(alert.status())) {
                handleFiring(alert);
            } else if ("resolved".equalsIgnoreCase(alert.status())) {
                log.info("RESOLVED  alert={} instance={} -> clearing cooldown, node recovered",
                        alert.alertname(), alert.instance());
                lastRun.remove(cooldownKey(alert));
            }
        }
    }

    private void handleFiring(Alert alert) {
        String alertname = alert.alertname();
        String script = resolveRunbook(alert);

        if (script == null) {
            log.warn("No runbook mapped for alert '{}' (instance={}) -- skipping",
                    alertname, alert.instance());
            metrics.remediation(alertname, "no_runbook");
            return;
        }

        String key = cooldownKey(alert);
        Instant last = lastRun.get(key);
        if (last != null && Instant.now().isBefore(last.plusSeconds(props.getCooldownSeconds()))) {
            log.info("Cooldown active for {} -- skipping duplicate remediation", key);
            metrics.remediation(script, "cooldown");
            return;
        }
        lastRun.put(key, Instant.now());

        List<String> args = (alert.instance() == null) ? List.of() : List.of(alert.instance());
        log.info("FIRING    alert={} instance={} severity={} -> executing runbook '{}'",
                alertname, alert.instance(), alert.severity(), script);

        RunbookResult result = executor.execute(script, args);
        metrics.recordDuration(script, result.durationMillis());
        metrics.remediation(script, result.success() ? "success" : "failure");

        if (result.success()) {
            log.info("REMEDIATED runbook={} instance={} ({} ms)\n{}",
                    script, alert.instance(), result.durationMillis(), result.output());
        } else {
            log.error("FAILED    runbook={} instance={} reason='{}' output='{}'",
                    script, alert.instance(), result.message(), result.output());
        }
    }

    /**
     * An alert may carry an explicit "runbook" annotation, but we only honour it
     * if it is already in our configured whitelist. Otherwise we fall back to the
     * alertname mapping. This keeps Alertmanager from being able to ask us to run
     * an arbitrary script.
     */
    private String resolveRunbook(Alert alert) {
        String override = alert.runbookOverride();
        if (override != null && !override.isBlank()) {
            if (props.getMappings().containsValue(override)) {
                return override;
            }
            log.warn("runbook annotation '{}' is not whitelisted -- ignoring", override);
        }
        return props.getMappings().get(alert.alertname());
    }

    private String cooldownKey(Alert alert) {
        return alert.alertname() + "|" + alert.instance();
    }
}
