package hostnamewhitelist;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;

import java.net.InetSocketAddress;

/**
 * Listens for incoming connections and ping events to enforce the hostname whitelist.
 */
public class ConnectionListener {

    private final HostnameWhitelistPlugin plugin;

    public ConnectionListener(HostnameWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe(priority = Short.MAX_VALUE)
    public void onLogin(LoginEvent event) {
        String hostname = event.getPlayer().getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .orElse("");

        InetSocketAddress remoteAddress = event.getPlayer().getRemoteAddress();
        String playerIp = resolveIpAddress(remoteAddress);
        String username = event.getPlayer().getUsername();

        if (!plugin.getConfig().isHostnameAllowed(hostname)) {
            String rawKickMessage = plugin.getConfig().getKickMessage()
                    .replace("{hostname}", hostname.isEmpty() ? "none" : hostname);

            Component message = MessageUtils.parse(rawKickMessage);
            event.setResult(LoginEvent.ComponentResult.denied(message));

            plugin.getLogger().info("Denied connection from {} ({}) using hostname: '{}'",
                    username, playerIp, hostname.isEmpty() ? "<none>" : hostname);

            if (plugin.getConfig().isLogDeniedConnections()) {
                boolean isKnown = plugin.getKnownPlayersManager().isKnown(username);
                plugin.getDiscordWebhook().sendDeniedConnection(username, hostname, playerIp, isKnown);
            }
        } else {
            // Track known player so they aren't doxxed if denied in the future
            plugin.getKnownPlayersManager().addPlayer(username);

            if (plugin.getConfig().isLogAllowedConnections()) {
                plugin.getDiscordWebhook().sendAllowedConnection(username, hostname, playerIp);
            }
        }
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onProxyPing(ProxyPingEvent event) {
        String hostname = event.getConnection().getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .orElse("");

        if (!plugin.getConfig().isHostnameAllowed(hostname)) {
            Component motd = MessageUtils.parse(plugin.getConfig().getInvalidMotd());

            ServerPing originalPing = event.getPing();
            ServerPing updatedPing = originalPing.asBuilder()
                    .description(motd)
                    .clearSamplePlayers()
                    .onlinePlayers(0)
                    .maximumPlayers(0)
                    .version(new ServerPing.Version(0, ""))
                    .clearFavicon()
                    .build();

            event.setPing(updatedPing);
        }
    }

    private static String resolveIpAddress(InetSocketAddress address) {
        if (address == null) {
            return "unknown";
        }
        if (address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }
        return address.getHostString();
    }
}
