package com.matbakhy.app.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Smallest structurally valid PDF (catalog, page tree, one page, correct xref) for upload tests; synthetic content only. */
public final class SyntheticPdf {

    private SyntheticPdf() {
    }

    /** {@code catalogExtra} is placed inside the catalog dictionary, e.g. an /OpenAction entry for negative tests. */
    public static byte[] minimal(String catalogExtra) {
        String[] objects = {
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R " + catalogExtra + " >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>\nendobj\n"};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        int[] offsets = new int[objects.length];
        for (int i = 0; i < objects.length; i++) {
            offsets[i] = out.size();
            write(out, objects[i]);
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.length + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        table.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(out, table.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }
}
