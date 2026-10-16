package hostnamewhitelist;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
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

    // GeoIP location cache: ip -> resolved location string with 1-hour expiry
    private final Cache<String, String> locationCache;

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
        this.locationCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(2000)
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

    private boolean checkAndUpdateCooldown(String username) {
        if (username == null || username.isBlank()) {
            return true;
        }
        String key = username.toLowerCase(Locale.ROOT);
        return cooldownCache.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }

    /**
     * Sends a denied connection notification.
     * If the player has connected before, IP and Location are omitted to prevent accidental doxxing.
     *
     * @param username the connecting player username
     * @param hostname the virtual host used
     * @param ip the remote IP address
     * @param isKnownPlayer whether the player has previously connected with a valid hostname
     */
    public void sendDeniedConnection(String username, String hostname, String ip, boolean isKnownPlayer) {
        if (!enabled || !checkAndUpdateCooldown(username)) {
            return;
        }

        String safeHostname = (hostname == null || hostname.isEmpty()) ? "none" : hostname;

        if (isKnownPlayer) {
            String description = String.format(
                    "**Player:** `%s`\n**Hostname:** `%s`",
                    username,
                    safeHostname
            );
            sendAsync(buildPayload("🚫 Connection Denied", description, COLOR_DENIED));
            return;
        }

        resolveLocationAsync(ip).thenAccept(location -> {
            String description = String.format(
                    "**Player:** `%s`\n**IP:** `%s`\n**Location:** `%s`\n**Hostname:** `%s`",
                    username,
                    ip,
                    location,
                    safeHostname
            );
            sendAsync(buildPayload("🚫 Connection Denied", description, COLOR_DENIED));
        });
    }

    /**
     * Sends an allowed connection notification with player, IP, location, and hostname.
     *
     * @param username the connecting player username
     * @param hostname the virtual host used
     * @param ip the remote IP address
     */
    public void sendAllowedConnection(String username, String hostname, String ip) {
        if (!enabled || !checkAndUpdateCooldown(username)) {
            return;
        }

        String safeHostname = (hostname == null || hostname.isEmpty()) ? "none" : hostname;

        resolveLocationAsync(ip).thenAccept(location -> {
            String description = String.format(
                    "**Player:** `%s`\n**IP:** `%s`\n**Location:** `%s`\n**Hostname:** `%s`",
                    username,
                    ip,
                    location,
                    safeHostname
            );
            sendAsync(buildPayload("✅ Connection Allowed", description, COLOR_ALLOWED));
        });
    }

    /**
     * Resolves the geographical location for an IP address asynchronously using db-ip.com free API.
     */
    private CompletableFuture<String> resolveLocationAsync(String ip) {
        if (ip == null || ip.isBlank() || ip.equals("unknown")) {
            return CompletableFuture.completedFuture("Unknown");
        }

        String cached = locationCache.getIfPresent(ip);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        if (isPrivateOrLocal(ip)) {
            String local = "Local / Private Network";
            locationCache.put(ip, local);
            return CompletableFuture.completedFuture(local);
        }

        try {
            String url = "https://api.db-ip.com/v2/free/" + URLEncoder.encode(ip, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "HostnameWhitelist-Velocity")
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            String loc = parseLocation(response.body());
                            locationCache.put(ip, loc);
                            return loc;
                        }
                        return "Unknown";
                    })
                    .exceptionally(t -> "Unknown");
        } catch (Exception e) {
            return CompletableFuture.completedFuture("Unknown");
        }
    }

    private String parseLocation(String jsonBody) {
        try {
            JsonObject obj = JsonParser.parseString(jsonBody).getAsJsonObject();
            String stateProv = obj.has("stateProv") && !obj.get("stateProv").isJsonNull() ? obj.get("stateProv").getAsString() : null;
            String countryName = obj.has("countryName") && !obj.get("countryName").isJsonNull() ? obj.get("countryName").getAsString() : null;
            String countryCode = obj.has("countryCode") && !obj.get("countryCode").isJsonNull() ? obj.get("countryCode").getAsString() : null;

            if ("ZZ".equalsIgnoreCase(countryCode)) {
                return "Local / Private Network";
            }

            // For the USA: format as "State, United States"
            if ("US".equalsIgnoreCase(countryCode) || "United States".equalsIgnoreCase(countryName)) {
                if (stateProv != null && !stateProv.isBlank()) {
                    return stateProv + ", United States";
                }
                return "United States";
            }

            // For other countries: format as just the country
            if (countryName != null && !countryName.isBlank()) {
                return countryName;
            } else if (countryCode != null && !countryCode.isBlank()) {
                return countryCode;
            }

            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static boolean isPrivateOrLocal(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

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
        locationCache.invalidateAll();
        httpClient.close();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
