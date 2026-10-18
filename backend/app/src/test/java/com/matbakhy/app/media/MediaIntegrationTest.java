package com.matbakhy.app.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.catalog.api.ObjectStorage;
import com.matbakhy.catalog.api.ObjectStorage.Bucket;
import com.matbakhy.catalog.internal.media.MediaService;
import com.matbakhy.shared.error.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TC-010, TC-113 against PostgreSQL and the S3 emulator through the real security chain: signed private uploads,
 * server-side size/checksum/type/dimension checks, fail-closed scanning, metadata-free public variants only for
 * approved public media, private documents readable only by owner or scoped reviewer, scoped deletes.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class MediaIntegrationTest {

    static final String GPS_MARKER = "MATBAKHY-SYNTHETIC-GPS-30.0444N-31.2357E";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired ObjectStorage storage;
    @Autowired MediaService media;
    @Autowired com.matbakhy.catalog.internal.media.MediaCleanup cleanup;

    final HttpClient http = HttpClient.newHttpClient();
    String ownerSubject;
    String otherSubject;
    String reviewerSubject;

    @BeforeEach
    void setUp() {
        ownerSubject = UUID.randomUUID().toString();
        otherSubject = UUID.randomUUID().toString();
        reviewerSubject = UUID.randomUUID().toString();
        UUID reviewer = logins.login(reviewerSubject, "Reviewer").getPrincipal().id();
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, 'cook.review', 'test grant')",
                UUID.randomUUID(), reviewer);
    }

    // ---- fixtures (synthetic content generated in the test) ----

    static byte[] image(String format, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0xC2410C));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.fillOval(w / 4, h / 4, w / 2, h / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** JPEG with an APP1/Exif segment carrying a synthetic location marker, as phone cameras add. */
    static byte[] jpegWithExif() throws Exception {
        byte[] plain = image("jpeg", 800, 600);
        byte[] payload = ("Exif\0\0" + GPS_MARKER).getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer b = ByteBuffer.allocate(plain.length + 4 + payload.length);
        b.put((byte) 0xFF).put((byte) 0xD8).put((byte) 0xFF).put((byte) 0xE1).putShort((short) (payload.length + 2)).put(payload);
        b.put(plain, 2, plain.length - 2);
        return b.array();
    }

    /** Small PNG whose header claims 30000 x 30000 pixels (valid CRC): a decompression-bomb shape. */
    static byte[] pngClaimingHugeDimensions() throws Exception {
        byte[] png = image("png", 400, 400);
        ByteBuffer b = ByteBuffer.wrap(png);
        b.putInt(16, 30000).putInt(20, 30000);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        b.putInt(29, (int) crc.getValue());
        return png;
    }

    static byte[] pdf(String extra) {
        return com.matbakhy.app.support.SyntheticPdf.minimal(extra);
    }

    static String sha256(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    // ---- helpers ----

    private AccountAuthentication as(String subject) {
        return logins.login(subject, "User");
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private MockHttpServletResponse ticket(String subject, String purpose, String type, long bytes, String sha) throws Exception {
        return call(post("/api/v1/uploads").content("""
                {"purpose":"%s","filename":"../../photo.jpg","contentType":"%s","bytes":%d,"sha256":"%s"}"""
                .formatted(purpose, type, bytes, sha)), as(subject));
    }

    private UUID upload(String subject, String purpose, String declaredType, byte[] declared, byte[] actual) throws Exception {
        MockHttpServletResponse t = ticket(subject, purpose, declaredType, declared.length, sha256(declared));
        assertThat(t.getStatus()).as(t.getContentAsString()).isEqualTo(201);
        JsonNode ticket = body(t);
        assertThat(ticket.get("uploadUrl").asString()).contains("X-Amz-Signature=").contains("X-Amz-Expires=600")
                .doesNotContain("photo.jpg");
        HttpResponse<String> put = http.send(HttpRequest.newBuilder(URI.create(ticket.get("uploadUrl").asString()))
                .header("Content-Type", declaredType).PUT(HttpRequest.BodyPublishers.ofByteArray(actual)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(put.statusCode()).as(put.body()).isEqualTo(200);
        return UUID.fromString(ticket.get("uploadId").asString());
    }

    private JsonNode complete(String subject, UUID id) throws Exception {
        MockHttpServletResponse r = call(post("/api/v1/uploads/" + id + "/complete").content("{}"), as(subject));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private String privateKey(UUID id) {
        return jdbc.queryForObject("SELECT private_key FROM media_asset WHERE id = ?", String.class, id);
    }

    // ---- approved public media ----

    @Test
    void approved_meal_photo_gets_a_metadata_free_public_variant_and_nothing_else_is_public() throws Exception {
        byte[] photo = jpegWithExif();
        assertThat(new String(photo, StandardCharsets.ISO_8859_1)).contains(GPS_MARKER);
        UUID id = upload(ownerSubject, "MEAL_PHOTO", "image/jpeg", photo, photo);
        JsonNode done = complete(ownerSubject, id);
        assertThat(done.get("state").asString()).isEqualTo("APPROVED");
        assertThat(done.get("width").asInt()).isEqualTo(800);
        assertThat(done.get("privateReadUrl").isNull()).isTrue();
        String publicUrl = done.get("publicUrl").asString();
        assertThat(publicUrl).endsWith("/matbakhy-public/media/" + id + ".jpg").doesNotContain("X-Amz");

        HttpResponse<byte[]> served = http.send(HttpRequest.newBuilder(URI.create(publicUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(served.statusCode()).isEqualTo(200);
        byte[] variant = served.body();
        assertThat(variant[0] & 0xff).isEqualTo(0xFF);
        assertThat(new String(variant, StandardCharsets.ISO_8859_1)).doesNotContain(GPS_MARKER).doesNotContain("Exif");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(variant)).getWidth()).isEqualTo(800);
        assertThat(jdbc.queryForObject("SELECT original_filename FROM media_asset WHERE id = ?", String.class, id))
                .as("path components stripped").isEqualTo("photo.jpg");

        // Repeating completion is idempotent; other accounts cannot see or complete it.
        assertThat(complete(ownerSubject, id).get("version")).isEqualTo(done.get("version"));
        assertThat(call(get("/api/v1/uploads/" + id), as(otherSubject)).getStatus()).isEqualTo(404);
        assertThat(call(post("/api/v1/uploads/" + id + "/complete").content("{}"), as(otherSubject)).getStatus()).isEqualTo(404);
        assertThat(call(get("/api/v1/uploads/" + id), as(reviewerSubject)).getStatus()).isEqualTo(404);
    }

    // ---- TC-010: spoofed, oversized, malformed and malicious uploads ----

    @Test
    void spoofed_types_are_rejected_and_never_published() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        UUID htmlId = upload(ownerSubject, "MEAL_PHOTO", "image/jpeg", html, html);
        JsonNode r1 = complete(ownerSubject, htmlId);
        assertThat(r1.get("state").asString()).isEqualTo("REJECTED");
        assertThat(r1.get("rejectionReason").asString()).isEqualTo("UNSUPPORTED_TYPE");

        byte[] png = image("png", 640, 480);
        UUID pngAsJpeg = upload(ownerSubject, "MEAL_PHOTO", "image/jpeg", png, png);
        JsonNode r2 = complete(ownerSubject, pngAsJpeg);
        assertThat(r2.get("rejectionReason").asString()).isEqualTo("TYPE_MISMATCH");

        byte[] pdf = pdf("");
        UUID pdfAsPhoto = upload(ownerSubject, "MEAL_PHOTO", "image/png", pdf, pdf);
        assertThat(complete(ownerSubject, pdfAsPhoto).get("rejectionReason").asString()).isEqualTo("UNSUPPORTED_TYPE");

        for (UUID id : new UUID[] {htmlId, pngAsJpeg, pdfAsPhoto}) {
            JsonNode status = body(call(get("/api/v1/uploads/" + id), as(ownerSubject)));
            assertThat(status.get("publicUrl").isNull()).isTrue();
            assertThat(status.get("privateReadUrl").isNull()).isTrue();
            assertThat(storage.size(Bucket.PUBLIC, "media/" + id + ".jpg")).isEmpty();
            assertThat(storage.size(Bucket.PRIVATE, privateKey(id))).as("rejected bytes are not retained").isEmpty();
        }
        assertThat(call(post("/api/v1/uploads").content(
                "{\"purpose\":\"MEAL_PHOTO\",\"filename\":\"x.svg\",\"contentType\":\"image/svg+xml\",\"bytes\":10,\"sha256\":\""
                        + sha256(new byte[] {1}) + "\"}"), as(ownerSubject)).getStatus()).isEqualTo(422);
    }

    @Test
    void oversize_size_mismatch_checksum_and_dimension_violations_are_rejected() throws Exception {
        MockHttpServletResponse tooBig = ticket(ownerSubject, "MEAL_PHOTO", "image/jpeg", 8L * 1024 * 1024 + 1, sha256(new byte[] {1}));
        assertThat(tooBig.getStatus()).isEqualTo(422);
        assertThat(tooBig.getContentAsString()).contains("SIZE_LIMIT");

        byte[] photo = image("jpeg", 640, 480);
        byte[] bigger = new byte[photo.length + 5000];
        System.arraycopy(photo, 0, bigger, 0, photo.length);
        UUID sizeId = upload(ownerSubject, "MEAL_PHOTO", "image/jpeg", photo, bigger);
        assertThat(complete(ownerSubject, sizeId).get("rejectionReason").asString()).isEqualTo("SIZE_MISMATCH");

        byte[] tampered = photo.clone();
        tampered[tampered.length / 2] ^= 0x55;
        UUID shaId = upload(ownerSubject, "MEAL_PHOTO", "image/jpeg", photo, tampered);
        assertThat(complete(ownerSubject, shaId).get("rejectionReason").asString()).isEqualTo("CHECKSUM_MISMATCH");

        byte[] tiny = image("jpeg", 100, 100);
        UUID tinyId = upload(ownerSubject, "MEAL_PHOTO", "image/jpeg", tiny, tiny);
        assertThat(complete(ownerSubject, tinyId).get("rejectionReason").asString()).isEqualTo("DIMENSIONS");

        byte[] bomb = pngClaimingHugeDimensions();
        UUID bombId = upload(ownerSubject, "MEAL_PHOTO", "image/png", bomb, bomb);
        assertThat(complete(ownerSubject, bombId).get("rejectionReason").asString()).isEqualTo("DIMENSIONS");

        byte[] truncated = java.util.Arrays.copyOf(photo, photo.length / 3);
        UUID truncId = upload(ownerSubject, "MEAL_PHOTO", "image/jpeg", truncated, truncated);
        assertThat(complete(ownerSubject, truncId).get("rejectionReason").asString()).isEqualTo("UNREADABLE");
    }

    @Test
    void infected_content_is_rejected_and_audited_and_an_unknown_scan_stays_quarantined() throws Exception {
        byte[] photo = image("jpeg", 640, 480);
        byte[] infected = concat(photo, "EICAR-STANDARD-ANTIVIRUS-TEST-FILE".getBytes(StandardCharsets.US_ASCII));
        UUID infectedId = upload(ownerSubject, "MEAL_PHOTO", "image/jpeg", infected, infected);
        assertThat(complete(ownerSubject, infectedId).get("rejectionReason").asString()).isEqualTo("MALWARE");
        UUID owner = as(ownerSubject).getPrincipal().id();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND action = 'media.rejected' AND outcome = 'DENIED'",
                Integer.class, owner)).isEqualTo(1);

        byte[] unscannable = concat(photo, "MATBAKHY-TEST-SCANNER-UNAVAILABLE".getBytes(StandardCharsets.US_ASCII));
        UUID pendingId = upload(ownerSubject, "MEAL_PHOTO", "image/jpeg", unscannable, unscannable);
        MockHttpServletResponse r = call(post("/api/v1/uploads/" + pendingId + "/complete").content("{}"), as(ownerSubject));
        assertThat(r.getStatus()).isEqualTo(503);
        assertThat(r.getContentAsString()).contains("DEPENDENCY_UNAVAILABLE");
        JsonNode status = body(call(get("/api/v1/uploads/" + pendingId), as(ownerSubject)));
        assertThat(status.get("state").asString()).isEqualTo("QUARANTINED");
        assertThat(status.get("publicUrl").isNull()).isTrue();
        assertThat(storage.size(Bucket.PUBLIC, "media/" + pendingId + ".jpg")).as("no quarantined asset is public").isEmpty();
    }

    // ---- private verification documents ----

    /** SEC-002 / CR-003 runtime replay: the still-valid upload ticket cannot change what an approved document serves. */
    @Test
    void replaying_the_upload_ticket_after_approval_does_not_change_the_approved_document() throws Exception {
        byte[] doc = pdf("");
        MockHttpServletResponse t = ticket(ownerSubject, "COOK_DOCUMENT", "application/pdf", doc.length, sha256(doc));
        JsonNode ticket = body(t);
        URI uploadUrl = URI.create(ticket.get("uploadUrl").asString());
        UUID id = UUID.fromString(ticket.get("uploadId").asString());
        assertThat(http.send(HttpRequest.newBuilder(uploadUrl).header("Content-Type", "application/pdf").PUT(HttpRequest.BodyPublishers.ofByteArray(doc)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        String read = complete(ownerSubject, id).get("privateReadUrl").asString();

        byte[] evil = doc.clone();
        evil[evil.length - 2] ^= 0x01; // same length, same type, different bytes
        http.send(HttpRequest.newBuilder(uploadUrl).header("Content-Type", "application/pdf").PUT(HttpRequest.BodyPublishers.ofByteArray(evil)).build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<byte[]> fetched = http.send(HttpRequest.newBuilder(URI.create(read)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).as("approved bytes unchanged by the replayed ticket").isEqualTo(doc);
    }

    @Test
    void private_documents_have_no_public_url_and_only_owner_or_scoped_reviewer_of_a_submitted_revision_can_read() throws Exception {
        byte[] doc = pdf("");
        UUID docId = upload(ownerSubject, "COOK_DOCUMENT", "application/pdf", doc, doc);
        JsonNode done = complete(ownerSubject, docId);
        assertThat(done.get("state").asString()).isEqualTo("APPROVED");
        assertThat(done.get("publicUrl").isNull()).isTrue();
        String read = done.get("privateReadUrl").asString();
        assertThat(read).contains("X-Amz-Signature=").contains("X-Amz-Expires=300").contains("matbakhy-private");
        HttpResponse<byte[]> fetched = http.send(HttpRequest.newBuilder(URI.create(read)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).isEqualTo(doc);
        // CR-003: approved bytes are served from a key the (still valid) upload ticket cannot write.
        assertThat(privateKey(docId)).isEqualTo("documents/" + docId);
        cleanup.runDue();
        assertThat(storage.size(Bucket.PRIVATE, "uploads/" + docId)).as("not cleaned while the upload ticket can still write").isPresent();
        jdbc.update("UPDATE media_cleanup SET next_attempt_at = now() WHERE object_key = ?", "uploads/" + docId); // ticket expiry elapsed
        cleanup.runDue();
        assertThat(storage.size(Bucket.PRIVATE, "uploads/" + docId)).as("upload-ticket key no longer holds the document").isEmpty();
        assertThat(storage.size(Bucket.PRIVATE, privateKey(docId))).as("the approved document itself is never cleaned up").isPresent();
        cleanup.enqueue(Bucket.PRIVATE, privateKey(docId), "MISTAKE");
        cleanup.runDue();
        assertThat(storage.size(Bucket.PRIVATE, privateKey(docId))).as("referenced objects are never deleted by cleanup").isPresent();
        assertThat(jdbc.queryForObject("SELECT state FROM media_cleanup WHERE object_key = ?", String.class, privateKey(docId)))
                .isEqualTo("SKIPPED_REFERENCED");

        // Not attached to a submitted revision: invisible to other customers and to reviewers.
        assertThat(call(get("/api/v1/uploads/" + docId), as(otherSubject)).getStatus()).isEqualTo(404);
        assertThat(call(get("/api/v1/uploads/" + docId), as(reviewerSubject)).getStatus()).isEqualTo(404);

        UUID zone = UUID.randomUUID();
        jdbc.update("INSERT INTO service_zone (id, code, name_ar, name_en, city_ar, city_en, active, delivery_fee_minor) "
                + "VALUES (?, ?, 'منطقة', 'Zone', 'مدينة', 'City', true, 3000)", zone, "z-" + zone.toString().substring(0, 8));
        MockHttpServletResponse draft = call(put("/api/v1/cook/application").content("""
                {"publicName":"Synthetic Kitchen","bio":"Test meals","contactPhone":"01001234567","privateLocation":"Synthetic street 1",
                 "pickupEnabled":true,"zoneIds":["%s"],"documentIds":["%s"]}""".formatted(zone, docId)), as(ownerSubject));
        assertThat(draft.getStatus()).as(draft.getContentAsString()).isEqualTo(200);
        assertThat(call(get("/api/v1/uploads/" + docId), as(reviewerSubject)).getStatus()).as("draft-only stays private").isEqualTo(404);
        MockHttpServletResponse submitted = call(post("/api/v1/cook/application/submit").content(
                "{\"expectedVersion\":" + body(draft).get("version").asLong() + "}"), as(ownerSubject));
        assertThat(submitted.getStatus()).isEqualTo(200);

        UUID reviewer = as(reviewerSubject).getPrincipal().id();
        MockHttpServletResponse reviewerRead = call(get("/api/v1/uploads/" + docId), as(reviewerSubject));
        assertThat(reviewerRead.getStatus()).isEqualTo(200);
        assertThat(body(reviewerRead).get("privateReadUrl").asString()).contains("X-Amz-Signature=");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND action = 'access.media' AND object_id = ?",
                Integer.class, reviewer, docId.toString())).isEqualTo(1);
        assertThat(call(get("/api/v1/uploads/" + docId), as(otherSubject)).getStatus()).as("no permission, still hidden").isEqualTo(404);

        UUID owner = as(ownerSubject).getPrincipal().id();
        assertThatThrownBy(() -> media.delete(owner, docId)).isInstanceOf(ApiException.class).hasMessageContaining("preserved record");
        JsonNode reviewView = body(call(get("/api/v1/ops/cooks/" + body(submitted).get("id").asString()), as(reviewerSubject)));
        assertThat(reviewView.get("revisions").get(0).get("documentIds").get(0).asString()).isEqualTo(docId.toString());
    }

    @Test
    void documents_with_active_content_are_rejected_and_unapproved_documents_cannot_be_attached() throws Exception {
        byte[] scripted = pdf("/OpenAction << /S /JavaScript /JS (app.alert(1)) >>");
        UUID scriptedId = upload(ownerSubject, "COOK_DOCUMENT", "application/pdf", scripted, scripted);
        assertThat(complete(ownerSubject, scriptedId).get("rejectionReason").asString()).isEqualTo("ACTIVE_CONTENT");

        byte[] other = pdf("");
        UUID othersDoc = upload(otherSubject, "COOK_DOCUMENT", "application/pdf", other, other);
        complete(otherSubject, othersDoc);
        byte[] pending = pdf("");
        UUID pendingDoc = upload(ownerSubject, "COOK_DOCUMENT", "application/pdf", pending, pending);
        for (UUID doc : new UUID[] {scriptedId, othersDoc, pendingDoc}) {
            MockHttpServletResponse r = call(put("/api/v1/cook/application").content("{\"documentIds\":[\"" + doc + "\"]}"), as(ownerSubject));
            assertThat(r.getStatus()).isEqualTo(422);
            assertThat(r.getContentAsString()).contains("DOCUMENT_NOT_APPROVED");
        }
    }

    // ---- lifecycle limits and scoped deletes ----

    @Test
    void upload_not_received_is_a_conflict_until_the_ticket_expires() throws Exception {
        byte[] photo = image("jpeg", 640, 480);
        UUID id = UUID.fromString(body(ticket(ownerSubject, "MEAL_PHOTO", "image/jpeg", photo.length, sha256(photo))).get("uploadId").asString());
        MockHttpServletResponse early = call(post("/api/v1/uploads/" + id + "/complete").content("{}"), as(ownerSubject));
        assertThat(early.getStatus()).isEqualTo(409);
        jdbc.update("UPDATE media_asset SET ticket_expires_at = created_at WHERE id = ?", id);
        assertThat(complete(ownerSubject, id).get("rejectionReason").asString()).isEqualTo("MISSING_OBJECT");
    }

    @Test
    void pending_ticket_count_is_limited() throws Exception {
        byte[] b = new byte[] {1, 2, 3};
        for (int i = 0; i < 10; i++) {
            assertThat(ticket(ownerSubject, "MEAL_PHOTO", "image/jpeg", 1000, sha256(b)).getStatus()).isEqualTo(201);
        }
        MockHttpServletResponse limited = ticket(ownerSubject, "MEAL_PHOTO", "image/jpeg", 1000, sha256(b));
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(ticket(otherSubject, "MEAL_PHOTO", "image/jpeg", 1000, sha256(b)).getStatus()).isEqualTo(201);
    }

    @Test
    void only_the_owner_can_delete_and_deletion_removes_public_and_private_objects() throws Exception {
        byte[] photo = image("png", 640, 480);
        UUID id = upload(ownerSubject, "MEAL_PHOTO", "image/png", photo, photo);
        complete(ownerSubject, id);
        String publicKey = "media/" + id + ".jpg";
        assertThat(storage.size(Bucket.PUBLIC, publicKey)).isPresent();
        UUID owner = as(ownerSubject).getPrincipal().id();
        UUID other = as(otherSubject).getPrincipal().id();

        assertThatThrownBy(() -> media.delete(other, id)).isInstanceOf(ApiException.class).hasMessageContaining("not found");
        assertThat(storage.size(Bucket.PUBLIC, publicKey)).isPresent();
        media.delete(owner, id);
        assertThat(storage.size(Bucket.PUBLIC, publicKey)).isEmpty();
        assertThat(storage.size(Bucket.PRIVATE, privateKey(id))).isEmpty();
        assertThat(call(get("/api/v1/uploads/" + id), as(ownerSubject)).getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT state FROM media_asset WHERE id = ?", String.class, id)).isEqualTo("DELETED");
    }

    @Test
    void database_refuses_public_variants_for_unapproved_or_private_media() throws Exception {
        byte[] doc = pdf("");
        UUID docId = upload(ownerSubject, "COOK_DOCUMENT", "application/pdf", doc, doc);
        complete(ownerSubject, docId);
        assertThatThrownBy(() -> jdbc.update("UPDATE media_asset SET public_key = 'media/leak.pdf' WHERE id = ?", docId))
                .hasMessageContaining("media_public_only_approved_ck");
        byte[] photo = image("jpeg", 640, 480);
        UUID pending = UUID.fromString(body(ticket(ownerSubject, "MEAL_PHOTO", "image/jpeg", photo.length, sha256(photo)))
                .get("uploadId").asString());
        assertThatThrownBy(() -> jdbc.update("UPDATE media_asset SET state = 'APPROVED' WHERE id = ?", pending))
                .hasMessageContaining("media_approved_clean_ck");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
