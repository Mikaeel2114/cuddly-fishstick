package me.bwac;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class AcCommand implements CommandExecutor {
    private final AntiCheatPlugin plugin;
    private static final String P = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "AC" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;

    public AcCommand(AntiCheatPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        boolean admin = s.hasPermission("anticheat.admin") || s.isOp();
        boolean staff = admin || s.hasPermission(plugin.getConfig().getString("alerts.permission", "anticheat.alerts"));
        if (!staff) { s.sendMessage(P + "No permission."); return true; }
        if (a.length == 0) {
            s.sendMessage(P + "/ac alerts | tp <p> | info <p> | logs <p>" + (admin ? " | ban <p> <reason> | unban <p> | exempt <p> | reload" : ""));
            return true;
        }
        String sub = a[0].toLowerCase();
        if (sub.equals("alerts")) {
            if (!(s instanceof Player)) { s.sendMessage(P + "Players only."); return true; }
            boolean on = plugin.alerts().toggle((Player) s);
            s.sendMessage(P + "Alerts " + (on ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            return true;
        }
        if (sub.equals("tp")) {
            if (!(s instanceof Player) || a.length < 2) return true;
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t == null) { s.sendMessage(P + "Player not online."); return true; }
            ((Player) s).teleport(t);
            s.sendMessage(P + "Teleported to " + t.getName());
            return true;
        }
        if (sub.equals("info") && a.length >= 2) {
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t == null) { s.sendMessage(P + "Player not online."); return true; }
            PlayerData d = plugin.checks().data(t);
            s.sendMessage(P + t.getName() + " | ping " + Util.ping(t) + " | offenses " + plugin.punishments().getOffenses(t.getUniqueId()));
            if (d.vl.isEmpty()) s.sendMessage(ChatColor.GREEN + "No active violations.");
            for (Map.Entry<String, Integer> e : d.vl.entrySet()) s.sendMessage(ChatColor.GRAY + " - " + e.getKey() + ": " + ChatColor.WHITE + e.getValue());
            return true;
        }
        if (sub.equals("logs") && a.length >= 2) {
            List<String> logs = plugin.punishments().getLogs(a[1], 15);
            if (logs.isEmpty()) s.sendMessage(P + "No logs for " + a[1]);
            for (String l : logs) s.sendMessage(ChatColor.GRAY + l.replace("|", " | "));
            return true;
        }
        if (!admin) { s.sendMessage(P + "No permission."); return true; }
        if (sub.equals("reload")) {
            plugin.reloadConfig();
            s.sendMessage(P + "Config reloaded.");
            return true;
        }
        if (sub.equals("ban") && a.length >= 3) {
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t == null) { s.sendMessage(P + "Player must be online."); return true; }
            StringBuilder r = new StringBuilder();
            for (int i = 2; i < a.length; i++) r.append(i > 2 ? " " : "").append(a[i]);
            String id = plugin.punishments().ban(t.getUniqueId(), t.getName(), r.toString(), -1);
            t.kickPlayer(plugin.punishments().screen(r.toString(), -1, id));
            s.sendMessage(P + "Banned " + t.getName() + " (ID " + id + ")");
            return true;
        }
        if (sub.equals("unban") && a.length >= 2) {
            s.sendMessage(P + (plugin.punishments().unban(a[1]) ? "Unbanned " + a[1] : "No ban found for " + a[1]));
            return true;
        }
        if (sub.equals("exempt") && a.length >= 2) {
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t == null) { s.sendMessage(P + "Player must be online."); return true; }
            boolean on = plugin.punishments().toggleExempt(t.getUniqueId());
            s.sendMessage(P + t.getName() + (on ? " is now exempt." : " is no longer exempt."));
            return true;
        }
        s.sendMessage(P + "Unknown subcommand.");
        return true;
    }
}
