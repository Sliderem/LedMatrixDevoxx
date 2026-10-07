package com.devoxx.led.device;

import com.devoxx.led.core.Frame;
import com.devoxx.led.core.Rgb;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Minimal client for a Divoom Pixoo 64 on the local network, using the device's
 * local HTTP API (no cloud, no auth). Commands are JSON documents POSTed to
 * {@code http://<host>/post}; the device replies with a JSON body containing an
 * {@code error_code} (0 = success).
 *
 * <p>This pushes a 64x64 image as an "HTTP GIF" frame via {@code Draw/SendHttpGif}.
 * The pixel payload is the raw RGB bytes (64*64*3 = 12288 bytes), row-major,
 * three bytes per pixel, base64-encoded. For a single still we reset the GIF id
 * first and send one frame; a short animation sends successive frames with an
 * incrementing offset.</p>
 *
 * <p>Uses only the JDK {@link HttpClient}; zero external dependencies.</p>
 */
public final class PixooClient {

    private final String baseUrl;
    private final HttpClient http;

    /**
     * @param host device IP or hostname, e.g. {@code 192.168.1.100} (an optional
     *             {@code http://} prefix and trailing slash are tolerated)
     */
    public PixooClient(String host) {
        String h = host.trim();
        if (h.startsWith("http://")) {
            h = h.substring("http://".length());
        } else if (h.startsWith("https://")) {
            h = h.substring("https://".length());
        }
        if (h.endsWith("/")) {
            h = h.substring(0, h.length() - 1);
        }
        this.baseUrl = "http://" + h + "/post";
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Reset the device's HTTP GIF buffer; call before sending a fresh frame set. */
    public void resetHttpGif() throws IOException, InterruptedException {
        post("{\"Command\":\"Draw/ResetHttpGifId\"}");
    }

    /**
     * Push a single still {@link Frame} (expected 64x64) to the device.
     *
     * @throws IOException if the device is unreachable or returns an error
     */
    public void sendStill(Frame frame) throws IOException, InterruptedException {
        resetHttpGif();
        sendFrame(frame, 1, 0, 0, 1000);
    }

    /**
     * Push a short animation. Each frame is sent with an incrementing offset;
     * {@code picId} ties them into one animation; {@code speedMs} is the per-frame
     * duration the device uses.
     */
    public void sendAnimation(List<Frame> frames, int speedMs)
            throws IOException, InterruptedException {
        resetHttpGif();
        int picId = 1;
        int total = frames.size();
        for (int i = 0; i < total; i++) {
            sendFrame(frames.get(i), total, i, picId, speedMs);
        }
    }

    private void sendFrame(Frame frame, int picNum, int picOffset, int picId, int speedMs)
            throws IOException, InterruptedException {
        String data = encodeRgb(frame);
        String body = "{"
                + "\"Command\":\"Draw/SendHttpGif\","
                + "\"PicNum\":" + picNum + ","
                + "\"PicWidth\":64,"
                + "\"PicOffset\":" + picOffset + ","
                + "\"PicID\":" + picId + ","
                + "\"PicSpeed\":" + speedMs + ","
                + "\"PicData\":\"" + data + "\""
                + "}";
        post(body);
    }

    /** Encode a frame as base64 of raw RGB bytes (row-major, 3 bytes/pixel). */
    static String encodeRgb(Frame frame) {
        int[] px = frame.pixels();
        byte[] rgb = new byte[px.length * 3];
        int j = 0;
        for (int argb : px) {
            rgb[j++] = (byte) Rgb.r(argb);
            rgb[j++] = (byte) Rgb.g(argb);
            rgb[j++] = (byte) Rgb.b(argb);
        }
        return Base64.getEncoder().encodeToString(rgb);
    }

    private void post(String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code / 100 != 2) {
            throw new IOException("Pixoo returned HTTP " + code);
        }
        // Device replies e.g. {"error_code":0}; a non-zero code signals a problem.
        String b = resp.body();
        int idx = b.indexOf("error_code");
        if (idx >= 0) {
            String tail = b.substring(idx + "error_code".length());
            String digits = tail.replaceAll("[^0-9-].*$", "").replaceAll("[^0-9-]", "");
            if (!digits.isEmpty() && !digits.equals("0")) {
                throw new IOException("Pixoo error_code " + digits + " for command");
            }
        }
    }
}
