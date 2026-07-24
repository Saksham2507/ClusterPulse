package com.clusterpulse.daemon.service;

import com.clusterpulse.daemon.config.ClusterPulseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Executes a runbook shell script in a hardened way.
 *
 * Security decisions worth explaining in an interview:
 *   1. Scripts are resolved inside a fixed runbooks directory and the
 *      canonical path is checked to prevent path traversal.
 *   2. Arguments (node/instance names from alert labels) are validated against
 *      a strict allow-list regex before being passed anywhere.
 *   3. We use ProcessBuilder with an argument list, NOT a shell string, so
 *      label values are never interpreted by a shell (no command injection).
 *   4. Every execution has a hard timeout and is force-killed if it overruns.
 */
@Service
public class RunbookExecutor {

    private static final Logger log = LoggerFactory.getLogger(RunbookExecutor.class);

    private final ClusterPulseProperties props;
    private final Pattern argPattern;

    public RunbookExecutor(ClusterPulseProperties props) {
        this.props = props;
        this.argPattern = Pattern.compile(props.getArgPattern());
    }

    public RunbookResult execute(String script, List<String> args) {
        long start = System.currentTimeMillis();
        try {
            Path dir = Paths.get(props.getRunbooksDir()).toAbsolutePath().normalize();
            Path scriptPath = dir.resolve(script).normalize();

            if (!scriptPath.startsWith(dir)) {
                return fail(start, "path traversal blocked for: " + script);
            }
            if (!Files.isRegularFile(scriptPath)) {
                return fail(start, "runbook not found: " + scriptPath);
            }
            for (String a : args) {
                if (a != null && !argPattern.matcher(a).matches()) {
                    return fail(start, "rejected unsafe argument: " + a);
                }
            }

            List<String> cmd = new ArrayList<>();
            cmd.add("/bin/bash");
            cmd.add(scriptPath.toString());
            cmd.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            // Drain output on a separate thread so a slow/hung process still
            // hits the timeout below instead of blocking the read forever.
            StringBuilder out = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        out.append(line).append('\n');
                    }
                } catch (Exception ignored) {
                    // process ended / stream closed
                }
            }, "runbook-reader");
            reader.setDaemon(true);
            reader.start();

            boolean finished = proc.waitFor(props.getRunbookTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                reader.join(1000);
                return new RunbookResult(false, -1, out.toString().strip(),
                        elapsed(start), "runbook timed out after " + props.getRunbookTimeoutSeconds() + "s");
            }
            reader.join(2000);

            int code = proc.exitValue();
            return new RunbookResult(code == 0, code, out.toString().strip(),
                    elapsed(start), code == 0 ? "ok" : "non-zero exit code");

        } catch (Exception e) {
            log.error("Runbook execution failed for {}", script, e);
            return fail(start, "exception: " + e.getMessage());
        }
    }

    private RunbookResult fail(long start, String message) {
        return new RunbookResult(false, -1, "", elapsed(start), message);
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
