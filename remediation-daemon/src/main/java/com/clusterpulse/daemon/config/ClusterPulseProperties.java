package com.clusterpulse.daemon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Bound from the "clusterpulse.*" block in application.yml.
 *
 * mappings: alertname -> runbook script filename. This doubles as the
 * whitelist of scripts the daemon is allowed to execute.
 */
@ConfigurationProperties(prefix = "clusterpulse")
public class ClusterPulseProperties {

    /** Directory containing the runbook shell scripts. */
    private String runbooksDir = "./runbooks";

    /** Minimum seconds between two runs of the same runbook for the same node. */
    private int cooldownSeconds = 60;

    /** Hard timeout for a single runbook execution. */
    private int runbookTimeoutSeconds = 30;

    /** Runbook arguments must match this pattern or they are rejected. */
    private String argPattern = "[A-Za-z0-9_.:-]+";

    /** alertname -> runbook script filename (also the execution whitelist). */
    private Map<String, String> mappings = new HashMap<>();

    public String getRunbooksDir() {
        return runbooksDir;
    }

    public void setRunbooksDir(String runbooksDir) {
        this.runbooksDir = runbooksDir;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getRunbookTimeoutSeconds() {
        return runbookTimeoutSeconds;
    }

    public void setRunbookTimeoutSeconds(int runbookTimeoutSeconds) {
        this.runbookTimeoutSeconds = runbookTimeoutSeconds;
    }

    public String getArgPattern() {
        return argPattern;
    }

    public void setArgPattern(String argPattern) {
        this.argPattern = argPattern;
    }

    public Map<String, String> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, String> mappings) {
        this.mappings = mappings;
    }
}
