package com.matbakhy.app.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.security.AccountAuthentication;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Test helper: an APPROVED meal photo through the real ticket, direct PUT and completion flow. */
public final class TestUploads {

    private TestUploads() {
    }

    public static UUID approvedMealPhoto(MockMvc mvc, JsonMapper json, AccountAuthentication owner) throws Exception {
        BufferedImage img = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x15803D));
        g.fillRect(0, 0, 640, 480);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        byte[] bytes = out.toByteArray();
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        JsonNode ticket = json.readTree(mvc.perform(post("/api/v1/uploads").with(authentication(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"MEAL_PHOTO\",\"filename\":\"meal.jpg\",\"contentType\":\"image/jpeg\","
                        + "\"bytes\":" + bytes.length + ",\"sha256\":\"" + sha + "\"}")).andReturn().getResponse().getContentAsString());
        HttpResponse<String> put = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(ticket.get("uploadUrl").asString()))
                .header("Content-Type", "image/jpeg").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofString());
        if (put.statusCode() != 200) {
            throw new IllegalStateException("upload failed: " + put.statusCode());
        }
        UUID id = UUID.fromString(ticket.get("uploadId").asString());
        JsonNode done = json.readTree(mvc.perform(post("/api/v1/uploads/" + id + "/complete").with(authentication(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getContentAsString());
        if (!"APPROVED".equals(done.get("state").asString())) {
            throw new IllegalStateException("photo not approved: " + done);
        }
        return id;
    }
}
