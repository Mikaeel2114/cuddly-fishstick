package me.bwac;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AlertManager {
    private final AntiCheatPlugin plugin;
    private final Set<UUID> muted = new HashSet<UUID>();
    private final Map<String, Long> cooldowns = new HashMap<String, Long>();

    public AlertManager(AntiCheatPlugin plugin) { this.plugin = plugin; }

    /** returns true if alerts are now ON for the staff member */
    public boolean toggle(Player p) {
        if (muted.remove(p.getUniqueId())) return true;
        muted.add(p.getUniqueId());
        return false;
    }

    public void alert(Player cheater, Check c, int vl, int max, String info) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("alerts.enabled", true)) return;
        long now = System.currentTimeMillis();
        String ck = cheater.getUniqueId() + c.key;
        Long last = cooldowns.get(ck);
        if (last != null && now - last < cfg.getInt("alerts.cooldown-seconds", 3) * 1000L) return;
        cooldowns.put(ck, now);

        String msg = Util.color(cfg.getString("alerts.message", "[AC] %player% failed %check% (%vl%/%max%)")
                .replace("%player%", cheater.getName()).replace("%check%", c.display)
                .replace("%vl%", String.valueOf(vl)).replace("%max%", String.valueOf(max))
                .replace("%ping%", String.valueOf(Util.ping(cheater))).replace("%tps%", String.valueOf(Util.r(plugin.tps()))));

        String perm = cfg.getString("alerts.permission", "anticheat.alerts");
        boolean ops = cfg.getBoolean("alerts.notify-ops", true);
        boolean click = cfg.getBoolean("alerts.click-teleport", true);
        String soundName = cfg.getString("alerts.sound", "NOTE_PLING");
        Sound sound = null;
        try { if (soundName != null && !soundName.isEmpty()) sound = Sound.valueOf(soundName.toUpperCase()); } catch (IllegalArgumentException ignored) {}

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (muted.contains(staff.getUniqueId())) continue;
            if (!(staff.hasPermission(perm) || (ops && staff.isOp()))) continue;
            if (click) {
                TextComponent tc = new TextComponent(TextComponent.fromLegacyText(msg));
                tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ac tp " + cheater.getName()));
                tc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        TextComponent.fromLegacyText(ChatColor.GRAY + "Click to teleport\n" + ChatColor.WHITE + info)));
                staff.spigot().sendMessage(tc);
            } else staff.sendMessage(msg);
            if (sound != null) staff.playSound(staff.getLocation(), sound, 0.6f, 1.2f);
        }
        if (cfg.getBoolean("alerts.notify-console", true)) plugin.getLogger().info(ChatColor.stripColor(msg) + " [" + info + "]");
        webhook(ChatColor.stripColor(msg) + " | " + info);
    }

    private void webhook(final String text) {
        final String url = plugin.getConfig().getString("alerts.discord-webhook", "");
        if (url == null || url.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("User-Agent", "BedwarsAC");
                    c.setDoOutput(true);
                    String json = "{\"content\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
                    OutputStream os = c.getOutputStream();
                    os.write(json.getBytes("UTF-8"));
                    os.close();
                    c.getResponseCode();
                    c.disconnect();
                } catch (Exception ignored) {}
            }
        });
    }
}
