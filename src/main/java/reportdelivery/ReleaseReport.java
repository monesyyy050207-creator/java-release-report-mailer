package reportdelivery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReleaseReport(
        String releaseId,
        String recipient,
        Instant occurredAt,
        Outcome outcome,
        List<BuildEvent> events) {

    public enum Outcome { PUBLISHED, FAILED, CANCELLED }

    public record BuildEvent(String stage, String status, long durationMillis, String diagnostic) {
        public BuildEvent {
            require(stage, "stage");
            require(status, "status");
            require(diagnostic, "diagnostic");
            if (durationMillis < 0) throw new IllegalArgumentException("durationMillis must be non-negative");
        }
    }

    public ReleaseReport {
        require(releaseId, "releaseId");
        require(recipient, "recipient");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(outcome, "outcome");
        events = List.copyOf(events);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
