package hostnamewhitelist;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Handles sending notifications to a Discord channel via Webhook.
 */
public class DiscordWebhook {

    private static final Gson GSON = new Gson();
    private static final int COLOR_DENIED = 0xE74C3C; // Red
    private static final int COLOR_ALLOWED = 0x2ECC71; // Green
    private static final String DEFAULT_URL_PLACEHOLDER = "YOUR_WEBHOOK_URL_HERE";

    private final String webhookUrl;
    private final Logger logger;
    private final boolean enabled;
    private final HttpClient httpClient;

    // Cooldown cache: username (lowercase) -> Boolean with 10-minute expiry and 5000 max size
    private final Cache<String, Boolean> cooldownCache;

    public DiscordWebhook(String webhookUrl, Logger logger) {
        this.webhookUrl = webhookUrl != null ? webhookUrl.trim() : "";
        this.logger = logger;
        this.enabled = isValidWebhookUrl(this.webhookUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.cooldownCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(5000)
                .build();
    }

    private static boolean isValidWebhookUrl(String url) {
        if (url == null || url.isBlank() || url.equalsIgnoreCase(DEFAULT_URL_PLACEHOLDER)) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the user is on cooldown. If not, places them on cooldown and returns true.
     */
    private boolean checkAndUpdateCooldown(String username) {
        if (username == null || username.isBlank()) {
            return true;
        }
        String key = username.toLowerCase(Locale.ROOT);
        return cooldownCache.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }

    public void sendDeniedConnection(String username, String hostname, String ip) {
        if (!enabled || !checkAndUpdateCooldown(username)) {
            return;
        }

        String description = String.format("**Player:** `%s`\n**Hostname:** `%s`\n**IP:** `%s`",
                username,
                (hostname == null || hostname.isEmpty()) ? "none" : hostname,
                ip);

        sendAsync(buildPayload("🚫 Connection Denied", description, COLOR_DENIED));
    }

    public void sendAllowedConnection(String username, String hostname, String ip) {
        if (!enabled || !checkAndUpdateCooldown(username)) {
            return;
        }

        String description = String.format("**Player:** `%s`\n**Hostname:** `%s`\n**IP:** `%s`",
                username,
                (hostname == null || hostname.isEmpty()) ? "none" : hostname,
                ip);

        sendAsync(buildPayload("✅ Connection Allowed", description, COLOR_ALLOWED));
    }

    /**
     * Builds Discord webhook embed payload using Gson.
     */
    private String buildPayload(String title, String description, int color) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", description);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Hostname Whitelist");
        embed.add("footer", footer);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonObject root = new JsonObject();
        root.add("embeds", embeds);

        return GSON.toJson(root);
    }

    private void sendAsync(String jsonPayload) {
        if (!enabled) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "HostnameWhitelist-Velocity")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        int status = response.statusCode();
                        if (status != 200 && status != 204) {
                            logger.warn("Discord webhook returned status {}: {}", status, response.body());
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.warn("Failed to send Discord webhook: {}", throwable.getMessage());
                        return null;
                    });
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid Discord webhook URI: {}", e.getMessage());
        }
    }

    public void shutdown() {
        cooldownCache.invalidateAll();
        httpClient.close();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
