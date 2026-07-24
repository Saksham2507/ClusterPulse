package com.clusterpulse.daemon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ClusterPulse Remediation Daemon.
 *
 * Receives Alertmanager webhooks, maps firing alerts to remediation runbooks,
 * executes those runbooks (safely), and exposes its own metrics to Prometheus.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DaemonApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaemonApplication.class, args);
    }
}
