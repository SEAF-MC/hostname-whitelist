package hostnamewhitelist;

import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Handles configuration loading for config.yml and hostnames.txt.
 */
public class PluginConfig {

    public static final String DEFAULT_KICK_MESSAGE = "<red>Unknown Server: {hostname}\n\n<gray>Please make sure you are using the correct IP";
    public static final String DEFAULT_INVALID_MOTD = "<red>Invalid Hostname. Please use the correct IP address.";

    private final Path dataDirectory;
    private final Logger logger;
    private final Path hostnamesFile;
    private final Path configFile;

    private volatile List<String> hostnames = List.of();
    private volatile List<Pattern> patterns = List.of();
    private volatile String kickMessage = DEFAULT_KICK_MESSAGE;
    private volatile String invalidMotd = DEFAULT_INVALID_MOTD;
    private volatile String discordWebhookUrl = "";
    private volatile boolean logAllowedConnections = false;
    private volatile boolean logDeniedConnections = false;

    public PluginConfig(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.configFile = dataDirectory.resolve("config.yml");
        this.hostnamesFile = dataDirectory.resolve("hostnames.txt");
    }

    /**
     * Loads or reloads both configuration files.
     */
    public void load() {
        try {
            Files.createDirectories(dataDirectory);
            loadMainConfig();
            loadHostnames();
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
        }
    }

    private void loadMainConfig() throws IOException {
        if (!Files.exists(configFile)) {
            String defaultConfig = """
                    # Hostname Whitelist Configuration

                    # Message shown when player connects with invalid hostname
                    # Supports MiniMessage (<red>, <bold>) and legacy color codes (&c, &l)
                    # Placeholders: {hostname}
                    kick-message: "<red>Unknown Server: {hostname}\\n\\n<gray>Please make sure you are using the correct IP"

                    # MOTD shown for invalid hostname pings
                    invalid-motd: "<red>Invalid Hostname. Please use the correct IP address."

                    # Discord Webhook URL for logging connections
                    # Leave empty or set to "YOUR_WEBHOOK_URL_HERE" to disable
                    discord-webhook: "YOUR_WEBHOOK_URL_HERE"

                    # Whether to log allowed connections (true/false)
                    log-allowed-connections: false

                    # Whether to log denied connections (true/false)
                    log-denied-connections: false
                    """;
            Files.writeString(configFile, defaultConfig, StandardCharsets.UTF_8);
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configFile)
                .build();

        try {
            CommentedConfigurationNode root = loader.load();

            String rawKick = root.node("kick-message").getString(DEFAULT_KICK_MESSAGE);
            this.kickMessage = rawKick != null ? rawKick.replace("\\n", "\n") : DEFAULT_KICK_MESSAGE;

            this.invalidMotd = root.node("invalid-motd").getString(DEFAULT_INVALID_MOTD);

            String webhook = root.node("discord-webhook").getString("");
            this.discordWebhookUrl = webhook != null ? webhook.trim() : "";

            this.logAllowedConnections = root.node("log-allowed-connections").getBoolean(false);
            this.logDeniedConnections = root.node("log-denied-connections").getBoolean(false);
        } catch (Exception e) {
            logger.error("Failed to parse config.yml, retaining current configuration", e);
        }
    }

    private void loadHostnames() throws IOException {
        if (!Files.exists(hostnamesFile)) {
            String defaultHostnames = """
                    # Hostname & IP Whitelist
                    # Add one hostname or IP per line
                    # Use * as wildcard for subdomains or IP ranges
                    #
                    # Examples:
                    # *.uwu.org.ua     - Allows all subdomains of uwu.org.ua
                    # uwu.org.ua       - Allows exact hostname match
                    # 192.168.1.1      - Allows exact IP address
                    # 192.168.1.*      - Allows IP range 192.168.1.0-255
                    # 10.*.*.*         - Allows all 10.x.x.x addresses
                    #
                    # Lines starting with # or inline # are comments

                    *.uwu.org.ua
                    uwu.org.ua
                    felixhost.xyz
                    """;
            Files.writeString(hostnamesFile, defaultHostnames, StandardCharsets.UTF_8);
        }

        List<String> loadedHostnames = new ArrayList<>();
        List<Pattern> loadedPatterns = new ArrayList<>();

        List<String> lines = Files.readAllLines(hostnamesFile, StandardCharsets.UTF_8);
        for (String rawLine : lines) {
            String line = rawLine;

            // Strip comments
            int commentIdx = line.indexOf('#');
            if (commentIdx != -1) {
                line = line.substring(0, commentIdx);
            }

            line = line.trim().toLowerCase(Locale.ROOT);
            if (line.isEmpty()) {
                continue;
            }

            // Strip port if present in whitelist entry
            int colonIdx = line.indexOf(':');
            if (colonIdx != -1) {
                line = line.substring(0, colonIdx).trim();
            }

            // Strip trailing dot if present
            if (line.endsWith(".")) {
                line = line.substring(0, line.length() - 1);
            }

            if (line.isEmpty()) {
                continue;
            }

            try {
                Pattern pattern = createPattern(line);
                loadedHostnames.add(line);
                loadedPatterns.add(pattern);
                logger.info("Loaded hostname pattern: {}", line);
            } catch (PatternSyntaxException e) {
                logger.error("Failed to compile pattern for hostname: {}", line, e);
            }
        }

        this.hostnames = List.copyOf(loadedHostnames);
        this.patterns = List.copyOf(loadedPatterns);
    }

    /**
     * Converts a wildcard hostname pattern (e.g. *.example.com) into a compiled regex Pattern.
     * All characters except '*' are escaped safely.
     *
     * @param patternStr the input wildcard string
     * @return the compiled regex Pattern
     */
    private static Pattern createPattern(String patternStr) {
        StringBuilder regex = new StringBuilder("^");
        String[] tokens = patternStr.split("\\*", -1);
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            if (!tokens[i].isEmpty()) {
                regex.append(Pattern.quote(tokens[i]));
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Checks if the given hostname is permitted by the whitelist.
     *
     * @param hostname the hostname from the incoming connection
     * @return true if allowed, false otherwise
     */
    public boolean isHostnameAllowed(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return false;
        }

        String normalized = hostname.trim().toLowerCase(Locale.ROOT);

        // Strip port if present
        int colonIdx = normalized.indexOf(':');
        if (colonIdx != -1) {
            normalized = normalized.substring(0, colonIdx).trim();
        }

        // Strip trailing DNS root dot if present
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.isEmpty()) {
            return false;
        }

        for (Pattern pattern : patterns) {
            if (pattern.matcher(normalized).matches()) {
                return true;
            }
        }

        return false;
    }

    public List<String> getHostnames() {
        return hostnames;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public String getInvalidMotd() {
        return invalidMotd;
    }

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl;
    }

    public boolean isLogAllowedConnections() {
        return logAllowedConnections;
    }

    public boolean isLogDeniedConnections() {
        return logDeniedConnections;
    }
}
