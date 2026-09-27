package reportdelivery;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MinimalPdfReport {
    public byte[] render(ReleaseReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Release report: " + report.releaseId());
        lines.add("Outcome: " + report.outcome());
        lines.add("Occurred: " + report.occurredAt());
        lines.add("");
        for (ReleaseReport.BuildEvent event : report.events()) {
            lines.add(event.stage() + " | " + event.status() + " | " + event.durationMillis() + " ms");
            lines.add("  " + event.diagnostic());
        }

        StringBuilder stream = new StringBuilder("BT\n/F1 11 Tf\n50 790 Td\n");
        for (String line : lines) {
            stream.append('(').append(pdfEscape(ascii(line))).append(") Tj\n0 -16 Td\n");
        }
        stream.append("ET\n");

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + stream.toString().getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + stream + "endstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) write(out, String.format("%010d 00000 n \n", offsets.get(i)));
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static String ascii(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static String pdfEscape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }
}
