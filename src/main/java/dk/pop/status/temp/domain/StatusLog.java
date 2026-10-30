package dk.pop.status.temp.domain;

import java.time.Instant;

public record StatusLog(
        Long id,
        String serviceKey,
        String serviceName,
        String status,
        Instant checkedAt,
        long durationMs,
        Integer exitCode,
        String stdout,
        String stderr,
        String errorMessage,
        boolean outputTruncated
) { }
