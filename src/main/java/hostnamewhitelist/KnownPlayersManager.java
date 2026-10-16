package hostnamewhitelist;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages tracking of players who have successfully connected using a valid hostname.
 * Prevents accidental IP/location exposure in Discord webhook alerts for known players.
 */
public class KnownPlayersManager {

    private final Path knownPlayersFile;
    private final Logger logger;
    private final Set<String> knownPlayers = ConcurrentHashMap.newKeySet();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "HostnameWhitelist-KnownPlayersIO");
        thread.setDaemon(true);
        return thread;
    });

    public KnownPlayersManager(Path dataDirectory, Logger logger) {
        this.knownPlayersFile = dataDirectory.resolve("known-players.txt");
        this.logger = logger;
    }

    /**
     * Loads known players from known-players.txt.
     */
    public void load() {
        try {
            if (!Files.exists(knownPlayersFile)) {
                String header = """
                        # Known Players List
                        # Players who have successfully connected with a valid whitelisted hostname.
                        # If a player on this list is denied later, their IP and location are withheld
                        # from Discord notifications to protect their privacy.
                        # One username per line (case-insensitive).
                        """;
                Files.writeString(knownPlayersFile, header, StandardCharsets.UTF_8);
            }

            knownPlayers.clear();
            List<String> lines = Files.readAllLines(knownPlayersFile, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine;
                int commentIdx = line.indexOf('#');
                if (commentIdx != -1) {
                    line = line.substring(0, commentIdx);
                }
                line = line.trim().toLowerCase(Locale.ROOT);
                if (!line.isEmpty()) {
                    knownPlayers.add(line);
                }
            }

            logger.info("Loaded {} known player(s)", knownPlayers.size());
        } catch (IOException e) {
            logger.error("Failed to load known-players.txt", e);
        }
    }

    /**
     * Checks if a player has successfully connected with a valid hostname before.
     *
     * @param username the player's username
     * @return true if player is known, false otherwise
     */
    public boolean isKnown(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return knownPlayers.contains(username.toLowerCase(Locale.ROOT));
    }

    /**
     * Records a player as known if not already registered, persisting to file.
     *
     * @param username the player's username
     */
    public void addPlayer(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        String key = username.toLowerCase(Locale.ROOT);
        if (knownPlayers.add(key)) {
            ioExecutor.submit(() -> {
                try {
                    Files.writeString(
                            knownPlayersFile,
                            username + System.lineSeparator(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    );
                } catch (IOException e) {
                    logger.error("Failed to append {} to known-players.txt", username, e);
                }
            });
        }
    }

    /**
     * Returns the count of known players.
     */
    public int getKnownPlayerCount() {
        return knownPlayers.size();
    }

    public void shutdown() {
        ioExecutor.shutdown();
    }
}
