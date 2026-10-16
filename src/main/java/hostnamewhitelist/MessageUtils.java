package hostnamewhitelist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

/**
 * Utility class for parsing Minecraft chat components.
 * Supports both MiniMessage formatting and legacy color codes (& and §).
 */
public final class MessageUtils {

    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("(?i)[&§][0-9a-fk-or#]");

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageUtils() {
        // Utility class
    }

    /**
     * Parses a string message into an Adventure {@link Component}.
     * Automatically detects whether to use Legacy serialization or MiniMessage.
     *
     * @param message the formatted string message
     * @return the parsed Component
     */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        if (LEGACY_CODE_PATTERN.matcher(message).find()) {
            String normalized = message.replace('§', '&');
            return LEGACY_SERIALIZER.deserialize(normalized);
        }

        return MINI_MESSAGE.deserialize(message);
    }
}
