package com.matbakhy.catalog.internal.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Content checks that never trust the client: the type is sniffed from the bytes, image dimensions are read
 * from the header before any pixel decoding (decompression-bomb guard), and public variants are re-encoded
 * from decoded pixels, which drops EXIF/GPS and any appended payload.
 */
final class MediaInspector {

    static final String JPEG = "image/jpeg";
    static final String PNG = "image/png";
    static final String PDF = "application/pdf";

    record Image(int width, int height) {
    }

    static final class Rejected extends Exception {
        final String reason;

        Rejected(String reason) {
            super(reason, null, false, false);
            this.reason = reason;
        }
    }

    private static final List<String> PDF_ACTIVE = List.of("/JavaScript", "/JS", "/Launch", "/EmbeddedFile", "/RichMedia", "/XFA",
            "/AA", "/SubmitForm", "/ImportData");

    private MediaInspector() {
    }

    static Optional<String> sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D && b[5] == 0x0A
                && b[6] == 0x1A && b[7] == 0x0A) {
            return Optional.of(PNG);
        }
        if (b.length >= 5 && new String(b, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    /** Header dimensions, bounds, then a full decode to prove the image is well formed. */
    static Image inspectImage(byte[] content, MediaPolicy.Limits limits) throws Rejected {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (in == null) {
                throw new Rejected("UNREADABLE");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new Rejected("UNREADABLE");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                // A decoder warning (for example a truncated stream) means the file is not well formed.
                boolean[] warned = {false};
                reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w < limits.minSide() || h < limits.minSide() || w > limits.maxSide() || h > limits.maxSide()
                        || (long) w * h > limits.maxPixels()) {
                    throw new Rejected("DIMENSIONS");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null || warned[0] || decoded.getWidth() != w || decoded.getHeight() != h) {
                    throw new Rejected("UNREADABLE");
                }
                return new Image(w, h);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw new Rejected("UNREADABLE");
        }
    }

    /** Name objects that make a PDF active (scripts, launches, embedded files, forms that submit). Compared after PDF name decoding. */
    private static final java.util.Set<String> PDF_ACTIVE_NAMES = java.util.Set.of("JavaScript", "JS", "Launch", "EmbeddedFile",
            "EmbeddedFiles", "RichMedia", "XFA", "AA", "SubmitForm", "ImportData", "OpenAction", "GoToE", "GoToR", "Encrypt");
    private static final int PDF_MAX_OBJECTS = 100_000;

    /**
     * PDFs are parsed structurally (SEC-001): every object, including those inside compressed object streams, is walked and
     * name escapes such as {@code /J#61vaScript} are decoded by the parser before comparison. Unparseable, encrypted or
     * active documents are refused. The raw-byte scan stays as a first, cheap line.
     */
    static void inspectPdf(byte[] content) throws Rejected {
        String text = new String(content, StandardCharsets.ISO_8859_1);
        if (!text.contains("%%EOF")) {
            throw new Rejected("UNREADABLE");
        }
        for (String marker : PDF_ACTIVE) {
            if (text.contains(marker)) {
                throw new Rejected("ACTIVE_CONTENT");
            }
        }
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(content)) {
            if (doc.isEncrypted()) {
                throw new Rejected("UNREADABLE");
            }
            org.apache.pdfbox.cos.COSDocument cos = doc.getDocument();
            java.util.Set<org.apache.pdfbox.cos.COSBase> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            java.util.ArrayDeque<org.apache.pdfbox.cos.COSBase> work = new java.util.ArrayDeque<>();
            work.add(cos.getTrailer());
            for (org.apache.pdfbox.cos.COSObjectKey key : cos.getXrefTable().keySet()) {
                work.add(cos.getObjectFromPool(key));
            }
            while (!work.isEmpty()) {
                org.apache.pdfbox.cos.COSBase node = work.poll();
                if (node instanceof org.apache.pdfbox.cos.COSObject ref) {
                    node = ref.getObject();
                }
                if (node == null || !seen.add(node)) {
                    continue;
                }
                if (seen.size() > PDF_MAX_OBJECTS) {
                    throw new Rejected("UNREADABLE");
                }
                if (node instanceof org.apache.pdfbox.cos.COSName name) {
                    if (PDF_ACTIVE_NAMES.contains(name.getName())) {
                        throw new Rejected("ACTIVE_CONTENT");
                    }
                } else if (node instanceof org.apache.pdfbox.cos.COSDictionary dict) {
                    for (java.util.Map.Entry<org.apache.pdfbox.cos.COSName, org.apache.pdfbox.cos.COSBase> e : dict.entrySet()) {
                        work.add(e.getKey());
                        work.add(e.getValue());
                    }
                } else if (node instanceof org.apache.pdfbox.cos.COSArray array) {
                    for (org.apache.pdfbox.cos.COSBase item : array) {
                        work.add(item);
                    }
                }
            }
        } catch (Rejected r) {
            throw r;
        } catch (IOException | RuntimeException e) {
            throw new Rejected("UNREADABLE");
        }
    }

    /** Decoded pixels re-encoded as a baseline JPEG on white, longest side at most {@code maxSide}. */
    static byte[] publicVariant(byte[] content, int maxSide) throws Rejected {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                throw new Rejected("UNREADABLE");
            }
            double scale = Math.min(1.0, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
            int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage target = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = target.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.drawImage(source, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.85f);
                writer.write(null, new IIOImage(target, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new Rejected("UNREADABLE");
        }
    }
}
