package hostnamewhitelist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigTest {

    @Test
    void testPatternMatchingExact() {
        Pattern pattern = PluginConfig.createPattern("uwu.org.ua");
        assertTrue(pattern.matcher("uwu.org.ua").matches());
        assertFalse(pattern.matcher("sub.uwu.org.ua").matches());
        assertFalse(pattern.matcher("evil-uwu.org.ua").matches());
        assertFalse(pattern.matcher("uwu.org.ua.com").matches());
    }

    @Test
    void testPatternMatchingSubdomainWildcard() {
        Pattern pattern = PluginConfig.createPattern("*.uwu.org.ua");
        assertTrue(pattern.matcher("play.uwu.org.ua").matches());
        assertTrue(pattern.matcher("sub.play.uwu.org.ua").matches());
        assertFalse(pattern.matcher("evil-uwu.org.ua").matches());
        assertFalse(pattern.matcher("uwu.org.ua").matches());
    }

    @Test
    void testPatternMatchingIpWildcard() {
        Pattern pattern = PluginConfig.createPattern("192.168.1.*");
        assertTrue(pattern.matcher("192.168.1.1").matches());
        assertTrue(pattern.matcher("192.168.1.254").matches());
        assertFalse(pattern.matcher("192.168.2.1").matches());
    }

    @Test
    void testPatternMatchingMultipleWildcards() {
        Pattern pattern = PluginConfig.createPattern("10.*.*.*");
        assertTrue(pattern.matcher("10.0.0.1").matches());
        assertTrue(pattern.matcher("10.123.45.67").matches());
        assertFalse(pattern.matcher("11.0.0.1").matches());
    }

    @Test
    void testIsHostnameAllowedWithNormalizations(@TempDir Path tempDir) throws IOException {
        Path hostnamesFile = tempDir.resolve("hostnames.txt");
        Files.writeString(hostnamesFile, """
                # Comments and empty lines
                *.uwu.org.ua
                uwu.org.ua
                192.168.1.* # inline comment
                """);

        PluginConfig config = new PluginConfig(tempDir, LoggerFactory.getLogger("TestLogger"));
        config.load();

        // Exact match
        assertTrue(config.isHostnameAllowed("uwu.org.ua"));

        // Case insensitivity
        assertTrue(config.isHostnameAllowed("UWU.ORG.UA"));
        assertTrue(config.isHostnameAllowed("Play.Uwu.Org.Ua"));

        // Port stripping
        assertTrue(config.isHostnameAllowed("play.uwu.org.ua:25565"));
        assertTrue(config.isHostnameAllowed("uwu.org.ua:12345"));

        // Trailing dot (FQDN)
        assertTrue(config.isHostnameAllowed("play.uwu.org.ua."));
        assertTrue(config.isHostnameAllowed("uwu.org.ua."));

        // Wildcard IP with inline comment
        assertTrue(config.isHostnameAllowed("192.168.1.100"));

        // Disallowed hostnames
        assertFalse(config.isHostnameAllowed("evil-uwu.org.ua"));
        assertFalse(config.isHostnameAllowed("google.com"));
        assertFalse(config.isHostnameAllowed("192.168.2.1"));
        assertFalse(config.isHostnameAllowed(""));
        assertFalse(config.isHostnameAllowed("   "));
        assertFalse(config.isHostnameAllowed(null));
    }

    @Test
    void testDefaultConfigCreation(@TempDir Path tempDir) {
        PluginConfig config = new PluginConfig(tempDir, LoggerFactory.getLogger("TestLogger"));
        config.load();

        assertTrue(Files.exists(tempDir.resolve("config.yml")));
        assertTrue(Files.exists(tempDir.resolve("hostnames.txt")));
        assertNotNull(config.getKickMessage());
        assertNotNull(config.getInvalidMotd());
        assertFalse(config.isLogAllowedConnections());
        assertFalse(config.isLogDeniedConnections());
        assertFalse(config.getHostnames().isEmpty());
    }
}
