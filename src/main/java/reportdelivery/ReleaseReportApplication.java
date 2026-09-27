package reportdelivery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReleaseReportApplication {
    public static void main(String[] args) throws Exception {
        Settings settings = Settings.load(System.getenv(), args);
        ReleaseReport report = new ReleaseReport(
                settings.releaseId(), settings.recipient(), Instant.now(), ReleaseReport.Outcome.PUBLISHED,
                List.of(
                        new ReleaseReport.BuildEvent("compile", "passed", 842, "Java 21 release build"),
                        new ReleaseReport.BuildEvent("dependency-scan", "passed", 391, "No policy violations"),
                        new ReleaseReport.BuildEvent("publish", "passed", 517, "Artifact promoted")));
        ReleaseReportService service = new ReleaseReportService(
                new MinimalPdfReport(), new InfraiEmailClient(settings.apiKey()));
        ReleaseReportService.DeliveryResult result = service.deliver(report);
        if (result instanceof ReleaseReportService.DeliveryResult.Sent sent) {
            System.out.println("release report sent: message_id=" + sent.messageId() + ", pdf_bytes=" + sent.pdfBytes());
        }
    }

    record Settings(String apiKey, String recipient, String releaseId) {
        static Settings load(Map<String, String> environment, String[] args) {
            Map<String, String> cli = parseArgs(args);
            return new Settings(
                    required(cli.getOrDefault("api-key", environment.get("INFRAI_API_KEY")), "INFRAI_API_KEY"),
                    required(cli.getOrDefault("to", environment.get("REPORT_EMAIL_TO")), "REPORT_EMAIL_TO"),
                    cli.getOrDefault("release", environment.getOrDefault("RELEASE_ID", "local-001")));
        }

        private static Map<String, String> parseArgs(String[] args) {
            java.util.HashMap<String, String> values = new java.util.HashMap<>();
            List<String> tokens = new ArrayList<>(List.of(args));
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (!token.startsWith("--") || i + 1 >= tokens.size()) throw new IllegalArgumentException("Expected --name value");
                values.put(token.substring(2), tokens.get(++i));
            }
            return Map.copyOf(values);
        }

        private static String required(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
            return value;
        }
    }
}
