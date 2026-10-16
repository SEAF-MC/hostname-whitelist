package hostnamewhitelist;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

import java.util.List;

/**
 * Command handler for /hostnamewhitelist.
 */
public class HostnameWhitelistCommand implements SimpleCommand {

    public static final String PERMISSION_ADMIN = "hostnamewhitelist.admin";
    private final HostnameWhitelistPlugin plugin;

    public HostnameWhitelistCommand(HostnameWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!source.hasPermission(PERMISSION_ADMIN)) {
                source.sendMessage(MessageUtils.parse("<red>You do not have permission to execute this command.</red>"));
                return;
            }

            plugin.reload();
            int count = plugin.getConfig().getHostnames().size();
            source.sendMessage(MessageUtils.parse(
                    "<green>Hostname Whitelist reloaded successfully! Loaded <gold>" + count + "</gold> hostname patterns.</green>"
            ));
            return;
        }

        source.sendMessage(MessageUtils.parse("<gold><bold>Hostname Whitelist</bold> <gray>v" + plugin.getVersion() + "</gray></gold>"));
        source.sendMessage(MessageUtils.parse("<yellow>Usage: <white>/hostnamewhitelist reload</white> - Reload configuration and hostnames</yellow>"));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1 && invocation.source().hasPermission(PERMISSION_ADMIN)) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            if ("reload".startsWith(prefix)) {
                return List.of("reload");
            }
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION_ADMIN);
    }
}
