package com.devoxx.led.ai;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Minimal client for the OpenAI image generation endpoint
 * ({@code POST /v1/images/generations}), using only the JDK {@link HttpClient}
 * and no external JSON library.
 *
 * <p>Given a text prompt, it requests a single 1024x1024 image, decodes the
 * returned base64 PNG into a {@link BufferedImage}, and hands it back. The
 * caller is expected to downscale the result to 64x64 (e.g. via
 * {@code ImageAdapter}) for the LED matrix.</p>
 *
 * <p>The API key is read from the {@code CHATGPT_API_KEY} entry resolved by
 * {@link DotEnv} (OS environment variable or {@code .env}). The key is never
 * logged.</p>
 */
public final class OpenAiImageClient {

    private static final String ENDPOINT = "https://api.openai.com/v1/images/generations";

    private final String apiKey;
    private final String model;
    private final HttpClient http;

    public OpenAiImageClient(DotEnv env) {
        this.apiKey = env.get("CHATGPT_API_KEY");
        this.model = env.getOrDefault("OPENAI_IMAGE_MODEL", "gpt-image-1");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** @return {@code true} if an API key is configured. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Generate one image from {@code prompt} and return it decoded.
     *
     * @param prompt the natural-language description
     * @return the generated image (1024x1024)
     * @throws IOException on network failure, a non-2xx API response, or a
     *                     response that cannot be parsed/decoded
     */
    public BufferedImage generate(String prompt) throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IOException("No CHATGPT_API_KEY configured (set it in .env or the environment).");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IOException("Prompt is empty.");
        }

        String body = "{"
                + "\"model\":\"" + jsonEscape(model) + "\","
                + "\"prompt\":\"" + jsonEscape(prompt) + "\","
                + "\"n\":1,"
                + "\"size\":\"1024x1024\""
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        String payload = response.body();

        if (status < 200 || status >= 300) {
            String msg = extractJsonString(payload, "message");
            throw new IOException("OpenAI image API returned HTTP " + status
                    + (msg != null ? ": " + msg : ""));
        }

        String b64 = extractJsonString(payload, "b64_json");
        if (b64 != null) {
            byte[] png = Base64.getDecoder().decode(b64);
            return decode(png);
        }

        // Some responses may return a URL instead of inline base64.
        String url = extractJsonString(payload, "url");
        if (url != null) {
            HttpRequest imgReq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(1))
                    .GET()
                    .build();
            HttpResponse<byte[]> imgResp =
                    http.send(imgReq, HttpResponse.BodyHandlers.ofByteArray());
            if (imgResp.statusCode() / 100 != 2) {
                throw new IOException("Failed to download generated image: HTTP "
                        + imgResp.statusCode());
            }
            return decode(imgResp.body());
        }

        throw new IOException("Could not find image data in the API response.");
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) {
            throw new IOException("Returned image bytes could not be decoded.");
        }
        return img;
    }

    // ---- tiny JSON helpers (sufficient for the fields we read) ---------------

    /** JSON-escape a string value for embedding in a request body. */
    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Extract the first string value for {@code "key": "..."} from a JSON
     * document, decoding standard JSON escape sequences. Returns {@code null}
     * if the key is not present. This is deliberately simple: it is only used
     * for the handful of flat string fields this client reads.
     */
    static String extractJsonString(String json, String key) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        while (k >= 0) {
            int colon = json.indexOf(':', k + needle.length());
            if (colon < 0) {
                return null;
            }
            int i = colon + 1;
            // Skip whitespace.
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i < json.length() && json.charAt(i) == '"') {
                // Parse a JSON string literal starting at i.
                StringBuilder sb = new StringBuilder();
                i++; // skip opening quote
                while (i < json.length()) {
                    char c = json.charAt(i);
                    if (c == '\\' && i + 1 < json.length()) {
                        char n = json.charAt(i + 1);
                        switch (n) {
                            case '"' -> sb.append('"');
                            case '\\' -> sb.append('\\');
                            case '/' -> sb.append('/');
                            case 'n' -> sb.append('\n');
                            case 'r' -> sb.append('\r');
                            case 't' -> sb.append('\t');
                            case 'b' -> sb.append('\b');
                            case 'f' -> sb.append('\f');
                            case 'u' -> {
                                if (i + 5 < json.length()) {
                                    String hex = json.substring(i + 2, i + 6);
                                    try {
                                        sb.append((char) Integer.parseInt(hex, 16));
                                    } catch (NumberFormatException ignored) {
                                        // leave as-is
                                    }
                                    i += 4;
                                }
                            }
                            default -> sb.append(n);
                        }
                        i += 2;
                    } else if (c == '"') {
                        return sb.toString();
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                return sb.toString();
            }
            // Value for this occurrence was not a string; look for the next one.
            k = json.indexOf(needle, k + needle.length());
        }
        return null;
    }
}
