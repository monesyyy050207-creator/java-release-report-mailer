package reportdelivery;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

public final class ReleaseReportServiceTest {
    public static void main(String[] args) throws Exception {
        skipsFailedRelease();
        sendsPublishedReleaseWithPdfBoundary();
        System.out.println("ReleaseReportServiceTest passed");
    }

    private static void skipsFailedRelease() throws Exception {
        CapturingTransport transport = new CapturingTransport();
        ReleaseReportService service = new ReleaseReportService(new MinimalPdfReport(), new InfraiEmailClient(transport, "test-key", 1));
        var result = service.deliver(report(ReleaseReport.Outcome.FAILED));
        check(result instanceof ReleaseReportService.DeliveryResult.Skipped, "failed release must be skipped");
        check(transport.requests == 0, "skip must not call email API");
    }

    private static void sendsPublishedReleaseWithPdfBoundary() throws Exception {
        CapturingTransport transport = new CapturingTransport();
        ReleaseReportService service = new ReleaseReportService(new MinimalPdfReport(), new InfraiEmailClient(transport, "test-key", 1));
        var result = service.deliver(report(ReleaseReport.Outcome.PUBLISHED));
        check(result instanceof ReleaseReportService.DeliveryResult.Sent sent && sent.messageId().equals("msg_42"), "message id must be surfaced");
        check(transport.request.method().equals("POST"), "method must be explicit POST");
        check(transport.request.uri().getPath().equals("/v1/email/send"), "email path mismatch");
        check(transport.request.headers().firstValue("Idempotency-Key").orElse("").equals("release-report-rel-42"), "stable retry key mismatch");
        check(transport.body.contains("release-rel-42.pdf"), "PDF filename missing");
        check(transport.body.contains("JVBERi0xLjQ"), "base64 PDF signature missing");
    }

    private static ReleaseReport report(ReleaseReport.Outcome outcome) {
        return new ReleaseReport("rel-42", "tools@example.com", Instant.parse("2026-09-25T09:00:00Z"), outcome,
                List.of(new ReleaseReport.BuildEvent("verify", "passed", 120, "Policy checks passed")));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class CapturingTransport implements InfraiEmailClient.Transport {
        int requests;
        HttpRequest request;
        String body;

        @Override
        public HttpResponse<String> send(HttpRequest request, HttpResponse.BodyHandler<String> handler) {
            this.requests++;
            this.request = request;
            this.body = request.bodyPublisher().orElseThrow().toString();
            this.body = readBody(request);
            return new StubResponse(request, 200, "{\"ok\":true,\"data\":{\"message_id\":\"msg_42\"},\"error\":null,\"metadata\":{}}");
        }

        private static String readBody(HttpRequest request) {
            StringBuilder value = new StringBuilder();
            request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
                public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                public void onNext(java.nio.ByteBuffer item) { value.append(java.nio.charset.StandardCharsets.UTF_8.decode(item)); }
                public void onError(Throwable throwable) { throw new RuntimeException(throwable); }
                public void onComplete() {}
            });
            return value.toString();
        }
    }

    private record StubResponse(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {
        public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
        public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
        public java.net.URI uri() { return request.uri(); }
        public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
    }
}
