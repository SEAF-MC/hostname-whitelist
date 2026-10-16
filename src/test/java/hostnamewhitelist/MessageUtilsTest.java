package hostnamewhitelist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageUtilsTest {

    @Test
    void testEmptyAndNull() {
        assertEquals(Component.empty(), MessageUtils.parse(null));
        assertEquals(Component.empty(), MessageUtils.parse(""));
    }

    @Test
    void testPlainText() {
        Component component = MessageUtils.parse("Hello World");
        assertEquals("Hello World", PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void testLegacyAmpersand() {
        Component component = MessageUtils.parse("&cError: Invalid Hostname");
        assertEquals("Error: Invalid Hostname", PlainTextComponentSerializer.plainText().serialize(component));
        assertEquals(NamedTextColor.RED, component.color());
    }

    @Test
    void testLegacySectionSign() {
        Component component = MessageUtils.parse("§aSuccess");
        assertEquals("Success", PlainTextComponentSerializer.plainText().serialize(component));
        assertEquals(NamedTextColor.GREEN, component.color());
    }

    @Test
    void testMiniMessage() {
        Component component = MessageUtils.parse("<red>Unknown Server</red>");
        assertEquals("Unknown Server", PlainTextComponentSerializer.plainText().serialize(component));
        assertEquals(NamedTextColor.RED, component.color());
    }

    @Test
    void testAmpersandInPlainTextWithoutColorCode() {
        Component component = MessageUtils.parse("Tom & Jerry");
        assertEquals("Tom & Jerry", PlainTextComponentSerializer.plainText().serialize(component));
    }
}
