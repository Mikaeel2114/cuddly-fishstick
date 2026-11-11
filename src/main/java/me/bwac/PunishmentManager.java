package me.bwac;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

public class PunishmentManager implements Listener {
    private final AntiCheatPlugin plugin;
    private final File dataFile, logFile;
    private YamlConfiguration data;

    public PunishmentManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.logFile = new File(plugin.getDataFolder(), "logs.txt");
        plugin.getDataFolder().mkdirs();
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private synchronized void save() {
        try { data.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Could not save data.yml: " + e.getMessage()); }
    }

    // ------------------------------------------------------------- exempt list
    public synchronized boolean isExempt(UUID u) { return data.getStringList("exempt").contains(u.toString()); }
    public synchronized boolean toggleExempt(UUID u) {
        List<String> l = new ArrayList<String>(data.getStringList("exempt"));
        boolean added;
        if (l.remove(u.toString())) added = false; else { l.add(u.toString()); added = true; }
        data.set("exempt", l); save();
        return added;
    }

    // ------------------------------------------------------------------- logs
    public synchronized void log(String player, String check, int vl, int max, int ping, double tps, String info) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "|" + player + "|" + check
                + "|VL " + vl + "/" + max + "|ping " + ping + "|tps " + Util.r(tps) + "|" + info;
        PrintWriter w = null;
        try { w = new PrintWriter(new FileWriter(logFile, true)); w.println(line); }
        catch (IOException ignored) {} finally { if (w != null) w.close(); }
    }

    public synchronized List<String> getLogs(String player, int limit) {
        List<String> out = new ArrayList<String>();
        if (!logFile.exists()) return out;
        try {
            List<String> all = Files.readAllLines(logFile.toPath(), java.nio.charset.Charset.forName("UTF-8"));
            for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
                String[] parts = all.get(i).split("\\|");
                if (parts.length > 1 && parts[1].equalsIgnoreCase(player)) out.add(all.get(i));
            }
        } catch (IOException ignored) {}
        Collections.reverse(out);
        return out;
    }

    public synchronized int getOffenses(UUID u) { return data.getInt("offenses." + u + ".count", 0); }

    // ------------------------------------------------------------------- bans
    public static class Ban {
        public String id, name, reason; public long expires;
        public long remaining() { return expires < 0 ? -1 : Math.max(0, expires - System.currentTimeMillis()); }
    }

    public synchronized Ban getBan(UUID u) {
        ConfigurationSection s = data.getConfigurationSection("bans." + u);
        if (s == null) return null;
        Ban b = new Ban();
        b.id = s.getString("id"); b.name = s.getString("name"); b.reason = s.getString("reason"); b.expires = s.getLong("expires", -1);
        if (b.expires >= 0 && b.expires <= System.currentTimeMillis()) { data.set("bans." + u, null); save(); return null; }
        return b;
    }

    public synchronized String ban(UUID u, String name, String reason, long durationMs) {
        String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        data.set("bans." + u + ".id", id);
        data.set("bans." + u + ".name", name);
        data.set("bans." + u + ".reason", reason);
        data.set("bans." + u + ".expires", durationMs < 0 ? -1L : System.currentTimeMillis() + durationMs);
        data.set("bans." + u + ".issued", System.currentTimeMillis());
        save();
        return id;
    }

    public synchronized boolean unban(String name) {
        ConfigurationSection bans = data.getConfigurationSection("bans");
        if (bans == null) return false;
        for (String key : bans.getKeys(false)) {
            if (name.equalsIgnoreCase(bans.getString(key + ".name"))) {
                data.set("bans." + key, null);
                data.set("offenses." + key, null);
                save();
                return true;
            }
        }
        return false;
    }

    public String screen(String reason, long durationMs, String id) {
        List<String> lines = plugin.getConfig().getStringList("punishments.screen-message");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(Util.color(lines.get(i).replace("%reason%", reason)
                    .replace("%duration%", Util.formatDuration(durationMs)).replace("%id%", id)));
        }
        return sb.toString();
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        Ban b = getBan(e.getUniqueId());
        if (b == null) return;
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, screen(b.reason, b.remaining(), b.id));
    }

    // ------------------------------------------------------------- punishing
    /** Runs the escalating ladder for this player. Call from the main thread. */
    public void punish(Player p, String reason) {
        FileConfiguration c = plugin.getConfig();
        UUID u = p.getUniqueId();
        int offense;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long reset = Util.parseDuration(c.getString("punishments.reset-offenses-after", "90d"));
            long last = data.getLong("offenses." + u + ".last", 0);
            int count = data.getInt("offenses." + u + ".count", 0);
            if (reset > 0 && last > 0 && now - last > reset) count = 0;
            offense = ++count;
            data.set("offenses." + u + ".count", count);
            data.set("offenses." + u + ".last", now);
            save();
        }
        int maxStep = 1;
        while (c.isConfigurationSection("punishments.ladder.offense-" + (maxStep + 1))) maxStep++;
        int step = Math.min(offense, maxStep);
        String base = "punishments.ladder.offense-" + step;
        String type = c.getString(base + ".type", "KICK").toUpperCase();
        long dur = Util.parseDuration(c.getString(base + ".duration", "0"));
        if (type.equals("BAN")) dur = -1;

        String name = p.getName();
        String id = "-";
        boolean useCmds = c.getBoolean("punishments.use-commands", false);
        if (type.equals("KICK")) {
            List<String> kick = c.getStringList("punishments.kick-message");
            if (kick.isEmpty()) kick = Arrays.asList("&c&lREMOVED FOR CHEATING", "&7Reason: &f%reason%");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < kick.size(); i++) { if (i > 0) sb.append("\n"); sb.append(Util.color(kick.get(i).replace("%reason%", reason))); }
            if (useCmds) dispatch(c.getString("punishments.ban-commands.kick", "kick %player% %reason%"), name, reason, dur);
            else p.kickPlayer(sb.toString());
        } else {
            if (useCmds) {
                String key = type.equals("TEMPBAN") ? "tempban" : "ban";
                dispatch(c.getString("punishments.ban-commands." + key, "ban %player% %reason%"), name, reason, dur);
            } else {
                id = ban(u, name, reason, dur);
                p.kickPlayer(screen(reason, dur, id));
            }
        }
        log(name, "PUNISH", offense, maxStep, 0, plugin.tps(), type + " " + Util.formatDuration(dur) + " id=" + id + " reason=" + reason);
        if (c.getBoolean("punishments.broadcast.enabled", true)) {
            Bukkit.broadcastMessage(Util.color(c.getString("punishments.broadcast.message", "&c%player% was removed for cheating.")
                    .replace("%player%", name).replace("%reason%", reason)));
        }
    }

    private void dispatch(String cmd, String player, String reason, long dur) {
        String d = dur < 0 ? "permanent" : (dur / 1000) + "s";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player).replace("%reason%", reason).replace("%duration%", d));
    }
}
