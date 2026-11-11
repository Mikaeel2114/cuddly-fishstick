package me.bwac;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public class AntiCheatPlugin extends JavaPlugin {
    private CheckManager checkManager;
    private AlertManager alertManager;
    private PunishmentManager punishmentManager;
    private volatile double tps = 20.0;
    private long lastTpsNs = System.nanoTime();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        alertManager = new AlertManager(this);
        punishmentManager = new PunishmentManager(this);
        checkManager = new CheckManager(this);
        Bukkit.getPluginManager().registerEvents(checkManager, this);
        Bukkit.getPluginManager().registerEvents(punishmentManager, this);
        getCommand("ac").setExecutor(new AcCommand(this));

        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int t = 0;
            @Override public void run() {
                if (++t >= 20) {
                    t = 0;
                    long now = System.nanoTime();
                    double secs = (now - lastTpsNs) / 1.0E9;
                    lastTpsNs = now;
                    tps = Math.min(20.0, 20.0 / secs);
                }
            }
        }, 1L, 1L);

        long decay = Math.max(5, getConfig().getInt("general.vl-decay-seconds", 60)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() { checkManager.decayVl(); }
        }, decay, decay);
        getLogger().info("BedwarsAC enabled with " + checkManager.checks().size() + " checks.");
    }

    public CheckManager checks() { return checkManager; }
    public AlertManager alerts() { return alertManager; }
    public PunishmentManager punishments() { return punishmentManager; }
    public double tps() { return tps; }

    /** Soft hook for BedWars1058 / BedWarsProxy. Returns false if the plugin or API is missing. */
    public boolean isBedwarsSpectator(Player p) {
        if (!Bukkit.getPluginManager().isPluginEnabled("BedWars1058") && !Bukkit.getPluginManager().isPluginEnabled("BedWarsProxy")) return false;
        try {
            Class<?> api = Class.forName("com.andrei1058.bedwars.api.BedWars");
            Object bw = Bukkit.getServicesManager().getRegistration(api).getProvider();
            Object util = api.getMethod("getArenaUtil").invoke(bw);
            Object arena = util.getClass().getMethod("getArenaByPlayer", Player.class).invoke(util, p);
            if (arena == null) return false;
            Method m = arena.getClass().getMethod("isSpectator", Player.class);
            return (Boolean) m.invoke(arena, p);
        } catch (Throwable t) { return false; }
    }
}
