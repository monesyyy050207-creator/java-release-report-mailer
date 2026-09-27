package reportdelivery;

import java.io.IOException;

public final class ReleaseReportService {
    private final MinimalPdfReport pdfReport;
    private final InfraiEmailClient emailClient;

    public ReleaseReportService(MinimalPdfReport pdfReport, InfraiEmailClient emailClient) {
        this.pdfReport = pdfReport;
        this.emailClient = emailClient;
    }

    public DeliveryResult deliver(ReleaseReport report) throws IOException, InterruptedException {
        if (report.outcome() != ReleaseReport.Outcome.PUBLISHED) return new DeliveryResult.Skipped(report.outcome());
        byte[] pdf = pdfReport.render(report);
        InfraiEmailClient.SendReceipt receipt = emailClient.sendPdf(
                report.recipient(),
                "Release " + report.releaseId() + " report",
                "<p>The signed-off release report is attached. Retain it with the deployment record.</p>",
                "release-" + report.releaseId() + ".pdf",
                pdf,
                "release-report-" + report.releaseId());
        return new DeliveryResult.Sent(receipt.messageId(), pdf.length);
    }

    public sealed interface DeliveryResult {
        record Sent(String messageId, int pdfBytes) implements DeliveryResult {}
        record Skipped(ReleaseReport.Outcome outcome) implements DeliveryResult {}
    }
}
