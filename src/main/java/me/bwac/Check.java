package me.bwac;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public abstract class Check {
    protected final AntiCheatPlugin plugin;
    public final String key, display;
    /** movement checks are also skipped after knockback, explosions and while flying */
    protected boolean movement = false;

    protected Check(AntiCheatPlugin plugin, String key, String display) {
        this.plugin = plugin; this.key = key; this.display = display;
    }

    private String path(String p) { return "checks." + key + "." + p; }
    public boolean isMovement() { return movement; }
    public boolean enabled() { return plugin.getConfig().getBoolean(path("enabled"), true); }
    public int maxVl() { return plugin.getConfig().getInt(path("max-vl"), 10); }
    public boolean punish() { return plugin.getConfig().getBoolean(path("punish"), false); }
    public String reason() { return plugin.getConfig().getString(path("reason"), "Unfair Advantage (" + display + ")"); }
    protected double num(String n, double def) { return plugin.getConfig().getDouble(path(n), def); }
    protected int intg(String n, int def) { return plugin.getConfig().getInt(path(n), def); }
    protected void flag(Player p, PlayerData d, String info) { plugin.checks().flag(this, p, d, info); }

    public void onMove(Player p, PlayerData d, Location from, Location to) {}
    public void onAttack(Player p, PlayerData d, Entity target) {}
    public void onSwing(Player p, PlayerData d) {}
    public void onPlace(Player p, PlayerData d, Block b) {}
    public void onBlockDamage(Player p, PlayerData d, Block b) {}
    public void onBreak(Player p, PlayerData d, Block b) {}
}
