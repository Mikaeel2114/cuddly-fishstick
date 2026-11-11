package me.bwac;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Iterator;

/** All checks. Event based (Bukkit API only), so they work without ProtocolLib. */
public final class Checks {
    private Checks() {}

    // ---------------------------------------------------------------- COMBAT

    public static class KillAuraA extends Check {
        public KillAuraA(AntiCheatPlugin pl) { super(pl, "killaura-a", "KillAura A"); }
        @Override public void onAttack(Player p, PlayerData d, Entity t) {
            long now = System.currentTimeMillis();
            Location pl = p.getLocation(), tl = t.getLocation();
            Vector v = new Vector(tl.getX() - pl.getX(), 0, tl.getZ() - pl.getZ());
            if (v.lengthSquared() > 1.44) {
                Vector dir = pl.getDirection().setY(0);
                if (dir.lengthSquared() > 1.0E-4) {
                    double ang = Math.toDegrees(dir.angle(v));
                    if (ang > num("fov-angle", 90)) {
                        if (d.addBuf("ka-a", 1) >= 2) { flag(p, d, "fov angle=" + Util.r(ang)); d.setBuf("ka-a", 0); }
                    } else d.decBuf("ka-a", 0.5);
                }
            }
            if (d.lastTargetId != -1 && d.lastTargetId != t.getEntityId() && now - d.lastAttackMs < 45) {
                if (d.addBuf("ka-a-multi", 1) >= 2) { flag(p, d, "multi target same tick"); d.setBuf("ka-a-multi", 0); }
            } else d.decBuf("ka-a-multi", 0.25);
        }
    }

    /** Attack without a preceding arm swing (vanilla clients always swing first). */
    public static class KillAuraB extends Check {
        public KillAuraB(AntiCheatPlugin pl) { super(pl, "killaura-b", "KillAura B"); }
        @Override public void onAttack(Player p, PlayerData d, Entity t) {
            long now = System.currentTimeMillis();
            if (now - d.lastSwingMs > num("swing-window-ms", 350)) {
                if (d.addBuf("ka-b", 1) >= 2) { flag(p, d, "attack without swing"); d.setBuf("ka-b", 0); }
            } else d.decBuf("ka-b", 0.5);
        }
    }

    public static class KillAuraC extends Check {
        public KillAuraC(AntiCheatPlugin pl) { super(pl, "killaura-c", "KillAura C"); }
        @Override public void onAttack(Player p, PlayerData d, Entity t) {
            long now = System.currentTimeMillis();
            if (now - d.yawDeltaMs <= 100 && d.yawDelta > num("snap-degrees", 100)) {
                if (d.addBuf("ka-c", 1) >= 3) { flag(p, d, "snap " + Util.r(d.yawDelta) + " deg"); d.setBuf("ka-c", 0); }
            } else d.decBuf("ka-c", 0.2);
        }
    }

    public static class Reach extends Check {
        public Reach(AntiCheatPlugin pl) { super(pl, "reach", "Reach"); }
        @Override public void onAttack(Player p, PlayerData d, Entity t) {
            if (!(t instanceof Player)) return;
            PlayerData td = plugin.checks().data((Player) t);
            int ping = Util.ping(p);
            long now = System.currentTimeMillis();
            Location eye = p.getEyeLocation();
            Location tl = t.getLocation();
            double best = Util.boxDist(eye, tl.getX(), tl.getY(), tl.getZ());
            for (PlayerData.Pos pos : td.history) {
                if (pos.t >= now - ping - 200) best = Math.min(best, Util.boxDist(eye, pos.x, pos.y, pos.z));
            }
            double max = ping > 150 ? num("high-ping-reach", 3.2) : num("max-reach", 3.05);
            if (best > max) {
                if (d.addBuf("reach", 1) >= 2) { flag(p, d, "reach=" + Util.r(best) + " max=" + max + " ping=" + ping); d.setBuf("reach", 0); }
            } else d.decBuf("reach", 0.25);
        }
    }

    public static class AutoClickerA extends Check {
        public AutoClickerA(AntiCheatPlugin pl) { super(pl, "autoclicker-a", "AutoClicker A"); }
        @Override public void onSwing(Player p, PlayerData d) {
            long now = System.currentTimeMillis();
            int cps = d.clicks.size();
            if (cps > num("max-cps", 20)) {
                if (d.overCpsSince == 0) d.overCpsSince = now;
                else if (now - d.overCpsSince >= 2000) { flag(p, d, "cps=" + cps); d.overCpsSince = now; }
            } else d.overCpsSince = 0;
        }
    }

    /** Click delay consistency. Statistical, so keep it flag-only. */
    public static class AutoClickerB extends Check {
        public AutoClickerB(AntiCheatPlugin pl) { super(pl, "autoclicker-b", "AutoClicker B"); }
        @Override public void onSwing(Player p, PlayerData d) {
            if (d.delays.size() < 40) return;
            double sum = 0;
            for (long v : d.delays) sum += v;
            double mean = sum / d.delays.size();
            double var = 0;
            for (long v : d.delays) var += (v - mean) * (v - mean);
            double std = Math.sqrt(var / d.delays.size());
            if (mean < 125 && std < num("min-stddev-ms", 8)) flag(p, d, "std=" + Util.r(std) + "ms mean=" + Util.r(mean) + "ms");
            d.delays.clear();
        }
    }

    public static class Velocity extends Check {
        public Velocity(AntiCheatPlugin pl) { super(pl, "velocity", "Velocity"); }
        @Override public void onMove(Player p, PlayerData d, Location from, Location to) {
            if (d.velH <= 0) return;
            long now = System.currentTimeMillis();
            double proj = (to.getX() - from.getX()) * d.velUx + (to.getZ() - from.getZ()) * d.velUz;
            d.velMax = Math.max(d.velMax, proj);
            d.velMoves++;
            if (now - d.lastVelMs < Util.ping(p) + 400) return;
            double h = d.velH; int moves = d.velMoves; double max = d.velMax;
            d.velH = 0;
            if (moves < 3 || blocked(to)) return;
            double pct = max / h;
            if (pct < num("min-percent", 0.85)) {
                if (d.addBuf("vel", 1) >= 3) { flag(p, d, "took " + Util.r(pct * 100) + "% of knockback"); d.setBuf("vel", 0); }
            } else d.decBuf("vel", 1);
        }
        private boolean blocked(Location l) {
            double[][] o = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (double[] a : o) {
                if (l.clone().add(a[0], 0, a[1]).getBlock().getType().isSolid()) return true;
                if (l.clone().add(a[0], 1, a[1]).getBlock().getType().isSolid()) return true;
            }
            return l.clone().add(0, 2, 0).getBlock().getType().isSolid() || Util.special(l);
        }
    }

    public static class Criticals extends Check {
        public Criticals(AntiCheatPlugin pl) { super(pl, "criticals", "Criticals"); }
        @Override public void onAttack(Player p, PlayerData d, Entity t) {
            if (!p.isOnGround() && d.groundTicks >= 3 && Math.abs(d.lastDy) < 0.001 && !d.special) {
                if (d.addBuf("crit", 1) >= 3) { flag(p, d, "crit while standing on ground"); d.setBuf("crit", 0); }
            } else d.decBuf("crit", 0.25);
        }
    }

    public static class NoSlowdown extends Check {
        public NoSlowdown(AntiCheatPlugin pl) { super(pl, "noslowdown", "NoSlowdown"); movement = true; }
        @Override public void onMove(Player p, PlayerData d, Location from, Location to) {
            double xz = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
            long now = System.currentTimeMillis();
            if (p.isBlocking() && d.groundTicks >= 3 && now - d.lastIceMs > 1500 && xz > num("max-blocking-speed", 0.15)) {
                if (d.addBuf("noslow", 1) >= 5) { flag(p, d, "speed while blocking=" + Util.r(xz)); d.setBuf("noslow", 0); }
            } else d.decBuf("noslow", 0.5);
        }
    }

    // -------------------------------------------------------------- MOVEMENT

    public static class Speed extends Check {
        public Speed(AntiCheatPlugin pl) { super(pl, "speed", "Speed"); movement = true; }
        @Override public void onMove(Player p, PlayerData d, Location from, Location to) {
            long now = System.currentTimeMillis();
            double ticks = Math.max(1.0, (now - d.lastMoveMs) / 50.0);
            double per = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()) / ticks;
            if (now - d.lastIceMs < 1500 || d.special) return;
            double max = d.groundTicks >= 2 ? num("ground-max", 0.30) : num("air-max", 0.42);
            if (to.clone().add(0, 2.0, 0).getBlock().getType().isSolid()) max += 0.25;
            for (PotionEffect e : p.getActivePotionEffects()) {
                if (e.getType().equals(PotionEffectType.SPEED)) max *= 1 + 0.2 * (e.getAmplifier() + 1);
            }
            if (p.getWalkSpeed() > 0.2f) max *= p.getWalkSpeed() / 0.2f;
            if (per > max) {
                if (d.addBuf("speed", 1) >= 4) { flag(p, d, "speed=" + Util.r(per) + " max=" + Util.r(max)); d.setBuf("speed", 0); }
            } else d.decBuf("speed", 0.25);
        }
    }

    public static class Fly extends Check {
        public Fly(AntiCheatPlugin pl) { super(pl, "fly", "Fly"); movement = true; }
        @Override public void onMove(Player p, PlayerData d, Location from, Location to) {
            double dy = to.getY() - from.getY();
            if (d.ground || d.special || p.hasPotionEffect(PotionEffectType.JUMP)) { d.noDescTicks = 0; return; }
            if (dy >= -0.005) d.noDescTicks++; else d.noDescTicks = 0;
            if (d.noDescTicks > intg("max-air-ticks", 10)) {
                flag(p, d, "airtime without descent=" + d.noDescTicks); d.noDescTicks = 0; return;
            }
            if (d.airTicks > 14 && dy > -0.15 && dy < 0) {
                if (d.addBuf("fly-glide", 1) >= 4) { flag(p, d, "glide dy=" + Util.r(dy)); d.setBuf("fly-glide", 0); }
            } else d.decBuf("fly-glide", 0.5);
        }
    }

    public static class Timer extends Check {
        public Timer(AntiCheatPlugin pl) { super(pl, "timer", "Timer"); }
        @Override public void onMove(Player p, PlayerData d, Location from, Location to) {
            if (plugin.tps() < num("min-tps", 19.5)) { d.timerCount = 0; d.timerWindow = 0; return; }
            long now = System.currentTimeMillis();
            if (d.timerWindow == 0) d.timerWindow = now;
            d.timerCount++;
            if (now - d.timerWindow >= 1000) {
                if (d.timerCount > num("max-moves-per-second", 26)) {
                    if (d.addBuf("timer", 1) >= 2) { flag(p, d, "moves/sec=" + d.timerCount); d.setBuf("timer", 0); }
                } else d.decBuf("timer", 0.5);
                d.timerWindow = now; d.timerCount = 0;
            }
        }
    }

    public static class NoFall extends Check {
        public NoFall(AntiCheatPlugin pl) { super(pl, "nofall", "NoFall"); movement = true; }
        @Override public void onMove(Player p, PlayerData d, Location from, Location to) {
            double dy = to.getY() - from.getY();
            if (p.isOnGround() && !d.ground && !d.special && dy < -0.2) {
                if (d.addBuf("nofall", 1) >= 3) { flag(p, d, "spoofed onGround while falling"); d.setBuf("nofall", 0); }
            } else d.decBuf("nofall", 0.25);
        }
    }

    public static class Step extends Check {
        public Step(AntiCheatPlugin pl) { super(pl, "step", "Step"); movement = true; }
        @Override public void onMove(Player p, PlayerData d, Location from, Location to) {
            double dy = to.getY() - from.getY();
            if (dy > num("max-step", 0.65) && d.prevGround && !d.special && !p.hasPotionEffect(PotionEffectType.JUMP)) {
                if (d.addBuf("step", 1) >= 2) { flag(p, d, "step dy=" + Util.r(dy)); d.setBuf("step", 0); }
            } else d.decBuf("step", 0.25);
        }
    }

    public static class Jesus extends Check {
        public Jesus(AntiCheatPlugin pl) { super(pl, "jesus", "Jesus"); movement = true; }
        @Override public void onMove(Player p, PlayerData d, Location from, Location to) {
            boolean liquidBelow = Util.liquid(to.clone().add(0, -0.1, 0).getBlock().getType());
            boolean liquidFeet = Util.liquid(to.getBlock().getType());
            double dy = Math.abs(to.getY() - from.getY());
            double xz = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
            if (liquidBelow && !liquidFeet && !d.ground && dy < 0.01 && xz > 0.1) {
                if (d.addBuf("jesus", 1) >= 6) { flag(p, d, "walking on liquid"); d.setBuf("jesus", 0); }
            } else d.decBuf("jesus", 0.5);
        }
    }

    // ----------------------------------------------------------------- WORLD

    public static class Scaffold extends Check {
        public Scaffold(AntiCheatPlugin pl) { super(pl, "scaffold", "Scaffold"); }
        @Override public void onPlace(Player p, PlayerData d, Block b) {
            Location l = p.getLocation();
            boolean under = b.getY() == l.getBlockY() - 1
                    && Math.abs(b.getX() + 0.5 - l.getX()) < 1.3 && Math.abs(b.getZ() + 0.5 - l.getZ()) < 1.3;
            if (!under) return;
            if (l.getPitch() < num("min-pitch", 30)) {
                if (d.addBuf("scaf-pitch", 1) >= 2) { flag(p, d, "bridging while looking up, pitch=" + Util.r(l.getPitch())); d.setBuf("scaf-pitch", 0); }
            } else d.decBuf("scaf-pitch", 0.25);
            if (d.places.size() > num("max-blocks-per-second", 12)) {
                if (d.addBuf("scaf-rate", 1) >= 2) { flag(p, d, "place rate=" + d.places.size() + "/s"); d.setBuf("scaf-rate", 0); }
            } else d.decBuf("scaf-rate", 0.25);
        }
    }

    public static class FastBreak extends Check {
        public FastBreak(AntiCheatPlugin pl) { super(pl, "fastbreak", "FastBreak"); }
        @Override public void onBreak(Player p, PlayerData d, Block b) {
            long now = System.currentTimeMillis();
            String key = b.getWorld().getName() + b.getX() + "," + b.getY() + "," + b.getZ();
            Long start = d.breakStarts.remove(key);
            float hardness = b.getType().getHardness();
            if (hardness > 0) {
                if (start == null || now - start > 15000) {
                    if (d.addBuf("fb-nostart", 1) >= 3) { flag(p, d, "broke block without starting dig"); d.setBuf("fb-nostart", 0); }
                } else {
                    long min = (long) (hardness * 1500.0 / num("best-tool-multiplier", 60) * num("tolerance", 0.6));
                    if (now - start < min) {
                        if (d.addBuf("fb-speed", 1) >= 3) { flag(p, d, "break took " + (now - start) + "ms, min " + min + "ms"); d.setBuf("fb-speed", 0); }
                    }
                }
            }
            if (d.breaks.size() > num("max-breaks-per-second", 8)) {
                if (d.addBuf("fb-rate", 1) >= 2) { flag(p, d, "nuker rate=" + d.breaks.size() + "/s"); d.setBuf("fb-rate", 0); }
            }
        }
    }

    public static class BedThroughWall extends Check {
        public BedThroughWall(AntiCheatPlugin pl) { super(pl, "bed-through-wall", "Bed Nuker"); }
        @Override public void onBreak(Player p, PlayerData d, Block b) {
            if (b.getType() != Material.BED_BLOCK) return;
            Location eye = p.getEyeLocation();
            double[][] pts = {{0.5, 0.5, 0.5}, {0.1, 0.5, 0.1}, {0.9, 0.5, 0.1}, {0.1, 0.5, 0.9}, {0.9, 0.5, 0.9}};
            for (double[] pt : pts) {
                Vector target = new Vector(b.getX() + pt[0], b.getY() + pt[1], b.getZ() + pt[2]);
                if (clear(eye, target, b)) return;
            }
            flag(p, d, "bed broken with no line of sight");
        }
        private boolean clear(Location eye, Vector target, Block bed) {
            Vector from = eye.toVector();
            Vector dir = target.clone().subtract(from);
            double len = dir.length();
            if (len < 0.5) return true;
            dir.normalize().multiply(0.2);
            Vector cur = from.clone();
            for (double t = 0; t < len; t += 0.2) {
                Block bl = eye.getWorld().getBlockAt(cur.getBlockX(), cur.getBlockY(), cur.getBlockZ());
                if (!bl.equals(bed) && bl.getType().isOccluding()) return false;
                cur.add(dir);
            }
            return true;
        }
    }
}
