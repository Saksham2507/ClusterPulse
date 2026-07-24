package com.clusterpulse.daemon.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Top-level Alertmanager webhook payload (schema version 4).
 * Unknown fields are ignored via Jackson config in application.yml.
 */
public record AlertmanagerWebhook(
        String version,
        String status,
        String receiver,
        Map<String, String> groupLabels,
        Map<String, String> commonLabels,
        Map<String, String> commonAnnotations,
        List<Alert> alerts
) {
}
