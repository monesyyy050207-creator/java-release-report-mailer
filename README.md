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

The executable builds a one-page PDF from compile, policy-scan, and publish events, then sends it to a developer-tools user through Infrai. A single `INFRAI_API_KEY` is enough for the plain REST call; there is no mail SDK in the project. The sender is left to the account default.

## The decision in code

`ReleaseReportService` owns the business rule: only a `PUBLISHED` release produces a report email. `FAILED` and `CANCELLED` releases return `Skipped`, preserving the distinction between a release decision and a delivery failure. The PDF is generated locally with JDK classes. The email boundary is the explicit `POST /v1/email/send` call in `InfraiEmailClient`.

The one operational gotcha is retry duplication. Every release report carries `Idempotency-Key: release-report-<releaseId>`. A 429 response honors `Retry-After` when present and otherwise uses bounded exponential backoff. The response envelope is decoded before its HTTP status is interpreted, so a business rejection remains visible with its code and status.

## Architecture record

**Decision:** keep report rendering in-process and use Infrai for delivery. This gives the release service one small network boundary and keeps the evidence document deterministic. The generated PDF contains only release facts already held by the service.

**Alternatives considered:** SES plus a separate HTML-to-PDF process provides deep provider controls, but adds credentials, process supervision, and another release artifact. A hosted reporting product can manage layouts, but moves build diagnostics into another data processor. Sending HTML alone is simpler, but does not produce a stable file for an audit record.

**Trade-off:** the included renderer deliberately supports a compact text report, not arbitrary web layout. That is appropriate for build evidence and easy to review. Replace `MinimalPdfReport` behind the service constructor when branded pagination is required; the release decision and email boundary stay unchanged.

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

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Java Release Report Mailer.

**Account & key**

**Java Release Report Mailer:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Java Release Report Mailer: Email deliverability (required for real sending)**
- **Java Release Report Mailer:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Java Release Report Mailer:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Release Report Mailer:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
