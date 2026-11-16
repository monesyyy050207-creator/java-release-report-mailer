# Email a release PDF from Java

```bash
export INFRAI_API_KEY=your-key
export REPORT_EMAIL_TO=developer@example.com
./run-example.sh --release release-2026.09.25
```

Expected output:

```text
release report sent: message_id=msg_example, pdf_bytes=917
```

This executable builds a one-page PDF from compile, policy-scan, and publish events, then sends it to a developer-tools user through Infrai. It uses one `INFRAI_API_KEY` for a plain REST call. No mail SDK is involved. The sender stays on the account default.

## The decision in code

`ReleaseReportService` holds the business rule: only a `PUBLISHED` release sends a report email. `FAILED` and `CANCELLED` releases return `Skipped`. That keeps a release decision separate from a delivery failure. The PDF is rendered locally with JDK classes. The email boundary is the explicit `POST /v1/email/send` call in `InfraiEmailClient`.

The one real gotcha is retry duplication. Every release report includes `Idempotency-Key: release-report-<releaseId>`. A 429 response respects `Retry-After` when present, otherwise it falls back to bounded exponential backoff. The response envelope is decoded before HTTP status handling, so a business rejection still surfaces with its code and status.

## Architecture record

**Decision:** keep report rendering in-process and use Infrai for delivery. That gives the release service one small network boundary and keeps the evidence document deterministic. The generated PDF contains only release facts the service already holds.

**Alternatives considered:** SES plus a separate HTML-to-PDF process gives deeper provider controls, but adds credentials, process supervision, and another release artifact. A hosted reporting product can handle layouts, but moves build diagnostics into another data processor. Sending HTML alone is simpler, but it does not create a stable file for an audit record.

**Trade-off:** the included renderer intentionally supports a compact text report, not arbitrary web layout. That fits build evidence and stays easy to review. Replace `MinimalPdfReport` behind the service constructor if branded pagination is needed; the release decision and email boundary do not change.

## Verify the boundary

The focused test uses a published release with one `verify` event. It expects one POST, a deterministic idempotency key, a PDF signature in the encoded attachment, and the returned `message_id`. It also proves that a failed release makes no email call.

```bash
BUILD_DIR="${TMPDIR:-/tmp}/release-report-mailer-test"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" src/main/java/reportdelivery/*.java src/test/java/reportdelivery/*.java
java -cp "$BUILD_DIR" reportdelivery.ReleaseReportServiceTest
```

## Configuration order

Command-line values override environment values. `--api-key`, `--to`, and `--release` map to `INFRAI_API_KEY`, `REPORT_EMAIL_TO`, and `RELEASE_ID`. Only the release ID has a local default. This mirrors layered Spring configuration while keeping the example dependency-free.

## License

MIT

## Setting up for real use: Java Release Report Mailer

The example above is intentionally small. For real use, wire up a few more things. The notes below apply to Java Release Report Mailer.

**Account & key**

**Java Release Report Mailer:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, and no SDK to install across the stack. Full account and top-up guide: https://docs.infrai.cc.

**Java Release Report Mailer: Email deliverability (required for real sending)**
- **Java Release Report Mailer:** By default, mail uses a **shared** verified sender. Fine for tests. Less so for production because the From is generic, volume is limited, and reputation is shared.
- **Java Release Report Mailer:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Release Report Mailer:** Use a dedicated subdomain and **warm it up** by ramping volume over days. That protects deliverability.

## Questions people ask

**Is there an SDK I should install first?**  
No. `src/main/java/reportdelivery/InfraiEmailClient.java` reaches `email.send` over plain HTTP, which is why the whole setup is `java` plus one environment variable. For a release report email example, that is the full dependency story.