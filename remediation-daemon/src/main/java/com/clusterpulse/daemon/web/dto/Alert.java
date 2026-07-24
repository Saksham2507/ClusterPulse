package com.clusterpulse.daemon.web.dto;

import java.util.Map;

/**
 * A single alert inside an Alertmanager webhook.
 * Labels/annotations carry the routing information we care about:
 *   - labels.alertname  -> which runbook to run
 *   - labels.instance   -> which node the runbook acts on
 *   - annotations.runbook -> optional explicit runbook override
 */
public record Alert(
        String status,
        Map<String, String> labels,
        Map<String, String> annotations,
        String startsAt,
        String endsAt,
        String fingerprint
) {
    public String alertname() {
        return labels == null ? null : labels.get("alertname");
    }

    public String instance() {
        return labels == null ? null : labels.get("instance");
    }

    public String severity() {
        return labels == null ? null : labels.get("severity");
    }

    public String runbookOverride() {
        return annotations == null ? null : annotations.get("runbook");
    }
}
