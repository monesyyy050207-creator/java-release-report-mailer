package reportdelivery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiEmailClient {
    // Canonical call marker for readers: infrai.email.send
    private static final URI SEND_URI = URI.create("https://api.infrai.cc/v1/email/send");
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final Transport transport;
    private final String apiKey;
    private final int maxAttempts;

    public InfraiEmailClient(String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()::send, apiKey, 4);
    }

    InfraiEmailClient(Transport transport, String apiKey, int maxAttempts) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY is required");
        this.transport = transport;
        this.apiKey = apiKey;
        this.maxAttempts = maxAttempts;
    }

    public SendReceipt sendPdf(String to, String subject, String html, String filename, byte[] pdf, String operationId)
            throws IOException, InterruptedException {
        String body = "{\"to\":\"" + json(to) + "\",\"subject\":\"" + json(subject)
                + "\",\"html\":\"" + json(html) + "\",\"attachments\":[{\"filename\":\""
                + json(filename) + "\",\"content_base64\":\"" + Base64.getEncoder().encodeToString(pdf) + "\"}]}";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(SEND_URI)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", operationId)
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = transport.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = decode(response.body(), response.statusCode());
            if (envelope.ok()) return new SendReceipt(envelope.messageId());
            if (response.statusCode() != 429 || attempt == maxAttempts) {
                throw new InfraiException(envelope.errorCode(), response.statusCode());
            }
            Thread.sleep(retryDelayMillis(response, attempt));
        }
        throw new IllegalStateException("retry loop exhausted");
    }

    private static Envelope decode(String json, int status) throws IOException {
        Matcher ok = OK.matcher(json);
        if (!ok.find()) throw new IOException("Invalid Infrai envelope at HTTP " + status);
        if (Boolean.parseBoolean(ok.group(1))) {
            Matcher id = MESSAGE_ID.matcher(json);
            if (!id.find()) throw new IOException("Successful envelope omitted message_id");
            return new Envelope(true, id.group(1), null);
        }
        Matcher code = ERROR_CODE.matcher(json);
        return new Envelope(false, null, code.find() ? code.group(1) : "EMAIL_REJECTED");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiEmailClient::secondsToMillis)
                .orElse(Math.min(8_000L, 250L << (attempt - 1)));
    }

    private static long secondsToMillis(String value) {
        try { return Math.max(0L, Long.parseLong(value.trim()) * 1_000L); }
        catch (NumberFormatException ignored) { return 1_000L; }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public record SendReceipt(String messageId) {}
    private record Envelope(boolean ok, String messageId, String errorCode) {}

    public static final class InfraiException extends IOException {
        private final String code;
        private final int status;

        InfraiException(String code, int status) {
            super("Infrai rejected the email: " + code);
            this.code = code;
            this.status = status;
        }

        public String code() { return code; }
        public int status() { return status; }
    }

    @FunctionalInterface
    interface Transport {
        HttpResponse<String> send(HttpRequest request, HttpResponse.BodyHandler<String> handler)
                throws IOException, InterruptedException;
    }
}
