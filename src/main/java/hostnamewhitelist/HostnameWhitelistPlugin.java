package hostnamewhitelist;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "hostname-whitelist",
        name = "Hostname Whitelist",
        version = "1.0.0",
        authors = {"uwu.org.ua"},
        description = "Whitelist hostnames for connecting to the server"
)
public class HostnameWhitelistPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final PluginContainer container;

    private PluginConfig config;
    private KnownPlayersManager knownPlayersManager;
    private DiscordWebhook discordWebhook;

    @Inject
    public HostnameWhitelistPlugin(
            ProxyServer server,
            Logger logger,
            @DataDirectory Path dataDirectory,
            PluginContainer container
    ) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.container = container;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();
        initDiscordWebhook();

        // Register connection listener
        server.getEventManager().register(this, new ConnectionListener(this));

        // Register /hostnamewhitelist command
        CommandManager commandManager = server.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("hostnamewhitelist")
                .plugin(this)
                .build();
        commandManager.register(commandMeta, new HostnameWhitelistCommand(this));

        logger.info("Hostname Whitelist v{} enabled!", getVersion());
        logger.info("Loaded {} hostname pattern(s)", config.getHostnames().size());
        logger.info("Loaded {} known player(s)", knownPlayersManager.getKnownPlayerCount());
        if (discordWebhook.isEnabled()) {
            logger.info("Discord webhook logging is active");
        }
    }

    @Subscribe
    public void onProxyReload(ProxyReloadEvent event) {
        reload();
        logger.info("Configuration reloaded via proxy reload event.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        shutdownDiscordWebhook();
        if (knownPlayersManager != null) {
            knownPlayersManager.shutdown();
        }
    }

    /**
     * Reloads configuration, known players, and webhook client.
     */
    public synchronized void reload() {
        loadConfig();
        initDiscordWebhook();
        logger.info("Hostname Whitelist reloaded! Loaded {} pattern(s) and {} known player(s)",
                config.getHostnames().size(), knownPlayersManager.getKnownPlayerCount());
    }

    private void loadConfig() {
        this.config = new PluginConfig(dataDirectory, logger);
        this.config.load();

        if (this.knownPlayersManager == null) {
            this.knownPlayersManager = new KnownPlayersManager(dataDirectory, logger);
        }
        this.knownPlayersManager.load();
    }

    private void initDiscordWebhook() {
        shutdownDiscordWebhook();
        this.discordWebhook = new DiscordWebhook(config.getDiscordWebhookUrl(), logger);
    }

    private void shutdownDiscordWebhook() {
        if (discordWebhook != null) {
            discordWebhook.shutdown();
        }
    }

    public String getVersion() {
        return container.getDescription().getVersion().orElse("1.0.0");
    }

    public PluginConfig getConfig() {
        return config;
    }

    public KnownPlayersManager getKnownPlayersManager() {
        return knownPlayersManager;
    }

    public Logger getLogger() {
        return logger;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }
}
