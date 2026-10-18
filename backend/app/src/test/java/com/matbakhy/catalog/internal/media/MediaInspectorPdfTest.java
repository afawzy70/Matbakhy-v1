package com.matbakhy.catalog.internal.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.Test;

/** SEC-001: active PDF content is found after name decoding and inside compressed object streams. */
class MediaInspectorPdfTest {

    private static byte[] raw(String catalogExtra) {
        return com.matbakhy.app.support.SyntheticPdf.minimal(catalogExtra);
    }

    private static byte[] generated(boolean withScript) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            if (withScript) {
                doc.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('synthetic')"));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out); // PDFBox 3 writes compressed object streams by default
            return out.toByteArray();
        }
    }

    @Test
    void the_existing_minimal_fixture_and_a_plain_generated_pdf_are_accepted() throws Exception {
        assertThatCode(() -> MediaInspector.inspectPdf(raw(""))).doesNotThrowAnyException();
        assertThatCode(() -> MediaInspector.inspectPdf(generated(false))).doesNotThrowAnyException();
    }

    @Test
    void literal_active_names_are_refused() {
        assertThatThrownBy(() -> MediaInspector.inspectPdf(raw("/OpenAction << /S /JavaScript /JS (void(0)) >>")))
                .isInstanceOf(MediaInspector.Rejected.class).hasFieldOrPropertyWithValue("reason", "ACTIVE_CONTENT");
    }

    @Test
    void escaped_names_do_not_hide_active_content() {
        // /J#61vaScript decodes to /JavaScript and /J#53 to /JS; the literal byte scan cannot see them.
        byte[] escaped = raw("/OpenAction << /S /J#61vaScript /J#53 (void(0)) >>");
        assertThat(new String(escaped, StandardCharsets.ISO_8859_1)).doesNotContain("/JavaScript");
        assertThatThrownBy(() -> MediaInspector.inspectPdf(escaped)).isInstanceOf(MediaInspector.Rejected.class)
                .hasFieldOrPropertyWithValue("reason", "ACTIVE_CONTENT");
    }

    @Test
    void active_content_inside_a_compressed_object_stream_is_found() throws Exception {
        byte[] doc = generated(true);
        String text = new String(doc, StandardCharsets.ISO_8859_1);
        assertThat(text).as("fixture must really hide the action in a stream").doesNotContain("/JavaScript");
        assertThatThrownBy(() -> MediaInspector.inspectPdf(doc)).isInstanceOf(MediaInspector.Rejected.class)
                .hasFieldOrPropertyWithValue("reason", "ACTIVE_CONTENT");
    }

    @Test
    void garbage_that_only_looks_like_a_pdf_is_unreadable() {
        assertThatThrownBy(() -> MediaInspector.inspectPdf("%PDF-1.7 not really\n%%EOF".getBytes(StandardCharsets.ISO_8859_1)))
                .isInstanceOf(MediaInspector.Rejected.class).hasFieldOrPropertyWithValue("reason", "UNREADABLE");
    }
}
