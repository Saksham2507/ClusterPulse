package com.clusterpulse.daemon.web;

import com.clusterpulse.daemon.service.RemediationService;
import com.clusterpulse.daemon.web.dto.AlertmanagerWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint that Alertmanager POSTs to when an alert fires or resolves.
 * Configured in alertmanager.yml as:
 *   webhook_configs:
 *     - url: http://remediation-daemon:8088/api/v1/alerts
 */
@RestController
@RequestMapping("/api/v1")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RemediationService service;

    public WebhookController(RemediationService service) {
        this.service = service;
    }

    @PostMapping("/alerts")
    public ResponseEntity<Map<String, Object>> receive(@RequestBody AlertmanagerWebhook webhook) {
        int count = (webhook.alerts() == null) ? 0 : webhook.alerts().size();
        log.info("Webhook received: status={} alerts={}", webhook.status(), count);
        service.handleWebhook(webhook);
        return ResponseEntity.ok(Map.of("received", count, "status", "processed"));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
