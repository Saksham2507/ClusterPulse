package com.clusterpulse.daemon.service;

/**
 * Outcome of a single runbook execution.
 */
public record RunbookResult(
        boolean success,
        int exitCode,
        String output,
        long durationMillis,
        String message
) {
}
