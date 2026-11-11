package me.bwac;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;

import java.util.*;

public class CheckManager implements Listener {
    private final AntiCheatPlugin plugin;
    private final Map<UUID, PlayerData> data = new HashMap<UUID, PlayerData>();
    private final List<Check> checks = new ArrayList<Check>();

    public CheckManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        Collections.addAll(checks,
                new Checks.KillAuraA(plugin), new Checks.KillAuraB(plugin), new Checks.KillAuraC(plugin),
                new Checks.Reach(plugin), new Checks.AutoClickerA(plugin), new Checks.AutoClickerB(plugin),
                new Checks.Velocity(plugin), new Checks.Criticals(plugin), new Checks.NoSlowdown(plugin),
                new Checks.Speed(plugin), new Checks.Fly(plugin), new Checks.Timer(plugin), new Checks.NoFall(plugin),
                new Checks.Step(plugin), new Checks.Jesus(plugin), new Checks.Scaffold(plugin),
                new Checks.FastBreak(plugin), new Checks.BedThroughWall(plugin));
    }

    public PlayerData data(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        if (d == null) { d = new PlayerData(p.getUniqueId(), p.getName()); data.put(p.getUniqueId(), d); }
        return d;
    }
    public Collection<PlayerData> all() { return data.values(); }
    public List<Check> checks() { return checks; }

    // ------------------------------------------------------------------ flag
    public void flag(Check c, Player p, PlayerData d, String info) {
        Integer cur = d.vl.get(c.key);
        int v = (cur == null ? 0 : cur) + 1;
        d.vl.put(c.key, v);
        int max = c.maxVl();
        plugin.alerts().alert(p, c, v, max, info);
        plugin.punishments().log(p.getName(), c.display, v, max, Util.ping(p), plugin.tps(), info);
        if (v >= max && c.punish()) {
            d.vl.put(c.key, 0);
            plugin.punishments().punish(p, c.reason());
        }
    }

    public void decayVl() {
        for (PlayerData d : data.values()) {
            Iterator<Map.Entry<String, Integer>> it = d.vl.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> e = it.next();
                if (e.getValue() <= 1) it.remove(); else e.setValue(e.getValue() - 1);
            }
            d.decayBuffers();
        }
    }

    // ---------------------------------------------------------------- exempt
    private boolean exempt(Player p, PlayerData d, boolean movement) {
        FileConfiguration c = plugin.getConfig();
        long now = System.currentTimeMillis();
        if (!p.isOnline() || p.isDead()) return true;
        if (p.hasPermission(c.getString("general.exempt-permission", "anticheat.bypass"))) return true;
        if (plugin.punishments().isExempt(p.getUniqueId())) return true;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
        if (plugin.tps() < c.getDouble("general.min-tps", 19.0)) return true;
        if (Util.ping(p) > c.getInt("general.max-ping-exempt", 400)) return true;
        if (now - d.lastRespawnMs < c.getInt("bedwars.exempt-respawn-ticks", 40) * 50L) return true;
        if (now - d.lastTeleportMs < 1500) return true;
        if (c.getBoolean("bedwars.exempt-spectators", true) && plugin.isBedwarsSpectator(p)) return true;
        if (movement) {
            if (p.getAllowFlight() || p.isFlying() || p.isInsideVehicle()) return true;
            if (now - d.lastExplosionMs < c.getInt("bedwars.exempt-explosion-knockback-ticks", 30) * 50L) return true;
            if (now - d.lastVelMs < 1500) return true;
        }
        return false;
    }

    private static double wrap(double a) { a %= 360; if (a > 180) a -= 360; if (a < -180) a += 360; return a; }

    // ---------------------------------------------------------------- events
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location from = e.getFrom(), to = e.getTo();
        if (to == null) return;
        PlayerData d = data(p);
        long now = System.currentTimeMillis();
        double yd = Math.abs(wrap(to.getYaw() - from.getYaw()));
        if (yd > 0) { d.yawDelta = yd; d.yawDeltaMs = now; }
        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        if (!moved) return;

        d.prevGround = d.ground;
        d.ground = Util.nearGround(to);
        d.special = Util.special(to);
        if (d.ground || d.special) { d.groundTicks++; d.airTicks = 0; } else { d.groundTicks = 0; d.airTicks++; }
        if (Util.iceOrSlime(to)) d.lastIceMs = now;
        d.history.addLast(new PlayerData.Pos(to.getX(), to.getY(), to.getZ(), now));
        while (d.history.size() > 40) d.history.removeFirst();

        if (d.lastMoveMs > 0) {
            boolean combatExempt = exempt(p, d, false);
            boolean moveExempt = combatExempt || exempt(p, d, true);
            if (moveExempt) d.noDescTicks = 0;
            for (Check c : checks) {
                if (!c.enabled()) continue;
                if (c.isMovement() ? moveExempt : combatExempt) continue;
                c.onMove(p, d, from, to);
            }
        }
        d.lastDy = to.getY() - from.getY();
        d.lastXZ = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        d.lastMoveMs = now;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data(p);
        long now = System.currentTimeMillis();
        d.lastSwingMs = now;
        // mining and block placing also swing every few ticks, so ignore those for click stats
        if (now - d.lastBlockDamageMs < 700 || now - d.lastPlaceMs < 150) return;
        d.clicks.addLast(now);
        while (!d.clicks.isEmpty() && now - d.clicks.peekFirst() > 1000) d.clicks.removeFirst();
        long ns = System.nanoTime();
        if (d.lastClickNs != 0) {
            long delay = (ns - d.lastClickNs) / 1000000L;
            if (delay < 500) { d.delays.addLast(delay); while (d.delays.size() > 60) d.delays.removeFirst(); }
        }
        d.lastClickNs = ns;
        if (exempt(p, d, false)) return;
        for (Check c : checks) if (c.enabled()) c.onSwing(p, d);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) data((Player) e.getEntity()).lastDamageMs = System.currentTimeMillis();
        if (!(e.getDamager() instanceof Player)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        Player p = (Player) e.getDamager();
        PlayerData d = data(p);
        Entity t = e.getEntity();
        if (!exempt(p, d, false)) {
            for (Check c : checks) if (c.enabled()) c.onAttack(p, d, t);
        }
        d.lastAttackMs = System.currentTimeMillis();
        d.lastTargetId = t.getEntityId();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data(p);
        long now = System.currentTimeMillis();
        double h = Math.hypot(e.getVelocity().getX(), e.getVelocity().getZ());
        if (h > 0.1 && now - d.lastDamageMs < 300 && !e.isCancelled()) {
            d.velH = h; d.velUx = e.getVelocity().getX() / h; d.velUz = e.getVelocity().getZ() / h;
            d.velMax = -10; d.velMoves = 0;
        }
        d.lastVelMs = now;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent e) {
        long now = System.currentTimeMillis();
        for (Entity en : e.getEntity().getNearbyEntities(10, 10, 10)) {
            if (en instanceof Player) data((Player) en).lastExplosionMs = now;
        }
    }

    @EventHandler public void onTeleport(PlayerTeleportEvent e) { data(e.getPlayer()).lastTeleportMs = System.currentTimeMillis(); }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) { data(e.getPlayer()).lastRespawnMs = System.currentTimeMillis(); }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        PlayerData d = data(e.getPlayer());
        d.lastRespawnMs = System.currentTimeMillis(); d.lastTeleportMs = d.lastRespawnMs;
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { data.remove(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data(p);
        long now = System.currentTimeMillis();
        d.lastPlaceMs = now;
        d.places.addLast(now);
        while (!d.places.isEmpty() && now - d.places.peekFirst() > 1000) d.places.removeFirst();
        if (exempt(p, d, false)) return;
        for (Check c : checks) if (c.enabled()) c.onPlace(p, d, e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDamage(BlockDamageEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data(p);
        long now = System.currentTimeMillis();
        d.lastBlockDamageMs = now;
        org.bukkit.block.Block b = e.getBlock();
        d.breakStarts.put(b.getWorld().getName() + b.getX() + "," + b.getY() + "," + b.getZ(), now);
        if (d.breakStarts.size() > 100) d.breakStarts.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data(p);
        long now = System.currentTimeMillis();
        d.breaks.addLast(now);
        while (!d.breaks.isEmpty() && now - d.breaks.peekFirst() > 1000) d.breaks.removeFirst();
        if (exempt(p, d, false)) return;
        for (Check c : checks) if (c.enabled()) c.onBreak(p, d, e.getBlock());
    }
}
