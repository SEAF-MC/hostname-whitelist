package hostnamewhitelist;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class DiscordWebhookTest {

    @Test
    void testWebhookEnabledState() {
        DiscordWebhook disabledPlaceholder = new DiscordWebhook("YOUR_WEBHOOK_URL_HERE", LoggerFactory.getLogger("Test"));
        assertFalse(disabledPlaceholder.isEnabled());

        DiscordWebhook disabledEmpty = new DiscordWebhook("", LoggerFactory.getLogger("Test"));
        assertFalse(disabledEmpty.isEnabled());

        DiscordWebhook disabledNull = new DiscordWebhook(null, LoggerFactory.getLogger("Test"));
        assertFalse(disabledNull.isEnabled());

        DiscordWebhook enabled = new DiscordWebhook("https://discord.com/api/webhooks/123/abc", LoggerFactory.getLogger("Test"));
        assertTrue(enabled.isEnabled());
    }

    @Test
    void testCooldownBehavior() {
        DiscordWebhook webhook = new DiscordWebhook("https://discord.com/api/webhooks/123/abc", LoggerFactory.getLogger("Test"));

        // First attempt should succeed
        assertTrue(webhook.checkAndUpdateCooldown("PlayerOne"));

        // Second attempt with same username (case-insensitive) should be blocked by cooldown
        assertFalse(webhook.checkAndUpdateCooldown("playerone"));
        assertFalse(webhook.checkAndUpdateCooldown("PLAYERONE"));

        // Different user should succeed
        assertTrue(webhook.checkAndUpdateCooldown("PlayerTwo"));

        // Null or blank should not be blocked
        assertTrue(webhook.checkAndUpdateCooldown(null));
        assertTrue(webhook.checkAndUpdateCooldown(""));
    }

    @Test
    void testBuildPayloadValidJson() {
        DiscordWebhook webhook = new DiscordWebhook("https://discord.com/api/webhooks/123/abc", LoggerFactory.getLogger("Test"));

        String payload = webhook.buildPayload("Test Title", "**Player:** `test\"player`\n**IP:** `127.0.0.1`", 0xFF0000);
        assertNotNull(payload);

        JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
        assertTrue(root.has("embeds"));

        JsonArray embeds = root.getAsJsonArray("embeds");
        assertEquals(1, embeds.size());

        JsonObject embed = embeds.get(0).getAsJsonObject();
        assertEquals("Test Title", embed.get("title").getAsString());
        assertEquals(0xFF0000, embed.get("color").getAsInt());
        assertTrue(embed.get("description").getAsString().contains("test\"player"));
    }
}
