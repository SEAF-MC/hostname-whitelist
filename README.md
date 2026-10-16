## Fork Notes

Basically a complete AI rewrite of hostname-whitelist. This, like all my other AI projects, is intended for private use but I will do my best to provide support if needed.

It's been so long since I forked this that I kinda forgot all of what was changed/added. But here is a list of my best guesses:
 - Everything has been translated to English.
 - The DC embed was altered to be: Username, IP, Location, Hostname.
 - A 10 minute per player cooldown on denied notis.
 - Previous correctly connected player names are stored to prevent accidental doxxing.

 Note: While this might work with the original config I would recommend deleting it before switching just to be sure.

# Hostname Whitelist

A simple yet powerful Velocity proxy plugin that restricts server access to
specific hostnames or IP addresses. This is useful for preventing direct IP
connections or enforcing the use of specific domains (e.g., `play.example.com`).

## Why is this important?

Malicious actors can create their own BungeeCord or Velocity proxy and forward
players to your server without their knowledge. This allows them to perform a
**Man-in-the-Middle (MitM) attack**, enabling them to:

- Log all commands (including `/login` and `/register` passwords on non-premium
  servers).
- Hijack player sessions (playing as the user).
- Execute commands on behalf of the victim.

By enforcing a hostname whitelist, you ensure that players can ONLY connect via
your official domain (e.g., `uwu.org.ua`), effectively blocking these malicious
redirects.

Similar security systems are used by major servers like **DonutSMP**, **2b2t**,
and **Hypixel** to protect their players.

## Features

- **Hostname Whitelisting**: Only allow connections from specified domains or
  IPs.
- **Wildcard Support**: Easily whitelist subdomains (e.g., `*.uwu.org.ua`) or IP
  ranges (e.g., `192.168.1.*`).
- **Customizable Messages**: Configure the kick message shown to blocked
  players.
- **Custom MOTD**: Display a specific MOTD for players pinging with an invalid
  hostname.
- **Discord Webhook Logging**: Optionally log allowed and blocked connections to
  a Discord channel.

## Installation

1. Download the latest release of the plugin.
2. Place the `.jar` file into your Velocity server's `plugins` folder.
3. Restart your proxy server.

## Configuration

The plugin creates a configuration folder at `plugins/hostname-whitelist/`
containing `config.yml`, `hostnames.txt`, and `known-players.txt`.

### config.yml

This file handles the general plugin settings. Both **MiniMessage** (e.g. `<red>`, `<bold>`) and **Legacy color codes** (e.g. `&c`, `§c`) are supported.

```yaml
# Hostname Whitelist Configuration

# Message shown when player connects with invalid hostname
# Placeholders: {hostname}
kick-message: "<red>Unknown Server: {hostname}\n\n<gray>Please make sure you are using the correct IP"

# MOTD shown for invalid hostname pings
invalid-motd: "<red>Invalid Hostname. Please use the correct IP address."

# Discord Webhook URL for logging connections
# Leave empty or set to "YOUR_WEBHOOK_URL_HERE" to disable
discord-webhook: "YOUR_WEBHOOK_URL_HERE"

# Whether to log allowed connections (true/false)
log-allowed-connections: false

# Whether to log denied connections (true/false)
log-denied-connections: false
```

## Commands & Permissions

- `/hostnamewhitelist reload` - Reloads `config.yml` and `hostnames.txt` without restarting the proxy.
  - **Permission**: `hostnamewhitelist.admin`

### hostnames.txt

This file is where you define which hostnames or IPs are allowed. You can add
one entry per line.

**Examples:**

```text
# Allow all subdomains of uwu.org.ua
*.uwu.org.ua

# Allow exact hostname match
uwu.org.ua

# Allow specific IP address
192.168.1.1

# Allow IP range 192.168.1.0-255
192.168.1.*
```

### known-players.txt

This file automatically records usernames of players who have successfully connected using an allowed hostname.

**Privacy Protection (Anti-Doxxing):**
If a player listed in `known-players.txt` accidentally connects with an invalid hostname or wrong IP, the Discord webhook alert will **only** include their username and the attempted hostname — redacting their IP address and location to prevent accidental doxxing.

## Discord Webhook

To use the Discord logging feature:

1. Create a Webhook in your Discord server (Server Settings -> Integrations -> Webhooks).
2. Copy the Webhook URL.
3. Paste it into the `discord-webhook` field in `config.yml`.
4. Enable `log-allowed-connections` or `log-denied-connections` as desired.

Discord alerts display **Player**, **IP**, **Location** (country, or `State, United States` via db-ip.com GeoIP API), and **Hostname**. For players listed in `known-players.txt`, denied alerts omit IP and Location data.

# License

This project is licensed under the Mozilla Public License 2.0
