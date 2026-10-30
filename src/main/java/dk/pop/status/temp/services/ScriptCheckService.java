package dk.pop.status.temp.services;

import dk.pop.status.temp.config.MonitoringProperties.ServiceCheck;
import dk.pop.status.temp.domain.StatusLog;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ScriptCheckService {
    // Keep draining after this limit so verbose scripts cannot block on a full pipe.
    private static final int OUTPUT_LIMIT_BYTES = 64 * 1024;

    public StatusLog check(ServiceCheck service) throws InterruptedException {
        Instant checkedAt = Instant.now();
        long started = System.nanoTime();
        Capture stdout = new Capture();
        Capture stderr = new Capture();
        Process process = null;
        Thread outputReader = null;
        Thread errorReader = null;
        Integer exitCode = null;
        String error = null;
        try {
            process = new ProcessBuilder(service.command()).start();
            process.getOutputStream().close();
            Process running = process;
            outputReader = Thread.ofVirtual().start(() -> stdout.read(running.getInputStream()));
            errorReader = Thread.ofVirtual().start(() -> stderr.read(running.getErrorStream()));
            if (process.waitFor(service.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue();
                if (exitCode < 0 || exitCode > 2) {
                    error = "Check script exited with code " + exitCode;
                }
            } else {
                error = "Check timed out after " + service.timeout();
            }
        } catch (IOException e) {
            error = "Could not execute check: " + e.getMessage();
        } finally {
            if (process != null) {
                stop(process);
            }
        }

        // Bounded joins also cover a script that exits but leaves its pipes open in a child.
        boolean outputFinished = finish(outputReader);
        boolean errorFinished = finish(errorReader);
        if (!outputFinished || !errorFinished) {
            error = error == null ? "Check output did not close after the script exited" : error;
        }
        if (error == null && (stdout.failed() || stderr.failed())) {
            error = "Could not read check output";
        }
        String status = error != null ? "error" : switch (exitCode) {
            case 0 -> "healthy";
            case 1 -> "sick";
            case 2 -> "down";
            default -> "error";
        };
        return new StatusLog(null, service.id(), service.name(), status, checkedAt,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), exitCode,
                stdout.text(), stderr.text(), error,
                stdout.truncated() || stderr.truncated() || !outputFinished || !errorFinished);
    }

    private static boolean finish(Thread reader) throws InterruptedException {
        if (reader == null) {
            return true;
        }
        reader.join(Duration.ofSeconds(1));
        if (reader.isAlive()) {
            reader.interrupt();
            return false;
        }
        return true;
    }

    private static void stop(Process process) {
        // Capture descendants before stopping their parent so timeout cleanup includes ping.
        List<ProcessHandle> children = process.descendants().toList();
        children.forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private static final class Capture {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean truncated;
        private boolean failed;

        void read(InputStream stream) {
            try (stream) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    append(buffer, count);
                }
            } catch (IOException e) {
                synchronized (this) {
                    failed = true;
                }
            }
        }

        private synchronized void append(byte[] buffer, int count) {
            int kept = Math.min(count, OUTPUT_LIMIT_BYTES - bytes.size());
            bytes.write(buffer, 0, kept);
            truncated |= kept < count;
        }

        synchronized String text() {
            return bytes.toString(StandardCharsets.UTF_8);
        }

        synchronized boolean truncated() {
            return truncated;
        }

        synchronized boolean failed() {
            return failed;
        }
    }
}
