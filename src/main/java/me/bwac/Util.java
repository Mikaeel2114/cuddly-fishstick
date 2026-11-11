package me.bwac;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Util {
    private Util() {}

    private static final Set<Material> LIQUID_CLIMB = EnumSet.of(Material.WATER, Material.STATIONARY_WATER,
            Material.LAVA, Material.STATIONARY_LAVA, Material.LADDER, Material.VINE, Material.WEB);
    private static final Set<Material> ICE_SLIME = EnumSet.of(Material.ICE, Material.PACKED_ICE, Material.SLIME_BLOCK);
    private static final Pattern DUR = Pattern.compile("(\\d+)\\s*([smhdw])");

    public static String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }

    public static double r(double v) { return Math.round(v * 100.0) / 100.0; }

    public static int ping(Player p) {
        try {
            Object h = p.getClass().getMethod("getHandle").invoke(p);
            return h.getClass().getField("ping").getInt(h);
        } catch (Throwable t) { return 0; }
    }

    /** returns millis, -1 = permanent, 0 = none */
    public static long parseDuration(String s) {
        if (s == null) return 0;
        s = s.trim().toLowerCase();
        if (s.equals("permanent") || s.equals("perm") || s.equals("-1")) return -1;
        long total = 0;
        Matcher m = DUR.matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            switch (m.group(2).charAt(0)) {
                case 's': total += n * 1000L; break;
                case 'm': total += n * 60000L; break;
                case 'h': total += n * 3600000L; break;
                case 'd': total += n * 86400000L; break;
                case 'w': total += n * 604800000L; break;
                default: break;
            }
        }
        return total;
    }

    public static String formatDuration(long ms) {
        if (ms < 0) return "Permanent";
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 && d == 0) sb.append(s).append("s");
        String out = sb.toString().trim();
        return out.isEmpty() ? "0s" : out;
    }

    public static boolean isSolidGround(Material m) {
        return m.isSolid() || m == Material.WATER_LILY || m == Material.CARPET || m == Material.SNOW;
    }

    /** true if something solid is just below the player's feet (3x3 sample) */
    public static boolean nearGround(Location l) {
        double[] o = {-0.3, 0, 0.3};
        for (double dx : o) for (double dz : o) {
            if (isSolidGround(l.clone().add(dx, -0.2, dz).getBlock().getType())) return true;
            if (isSolidGround(l.clone().add(dx, 0.0, dz).getBlock().getType())
                    && l.getY() - Math.floor(l.getY()) > 0.001 && l.getY() - Math.floor(l.getY()) < 0.6) return true;
        }
        return false;
    }

    /** liquids, ladders, vines, webs around the player */
    public static boolean special(Location l) {
        if (LIQUID_CLIMB.contains(l.getBlock().getType())) return true;
        if (LIQUID_CLIMB.contains(l.clone().add(0, 1, 0).getBlock().getType())) return true;
        if (LIQUID_CLIMB.contains(l.clone().add(0, -0.2, 0).getBlock().getType())) return true;
        return false;
    }

    public static boolean iceOrSlime(Location l) {
        return ICE_SLIME.contains(l.clone().add(0, -0.3, 0).getBlock().getType())
                || ICE_SLIME.contains(l.clone().add(0, -1.3, 0).getBlock().getType());
    }

    public static boolean liquid(Material m) {
        return m == Material.WATER || m == Material.STATIONARY_WATER || m == Material.LAVA || m == Material.STATIONARY_LAVA;
    }

    /** distance from a point to a player-sized hitbox (0.6 x 1.8) at the given feet position */
    public static double boxDist(Location eye, double x, double y, double z) {
        double dx = Math.max(0, Math.abs(eye.getX() - x) - 0.3);
        double dz = Math.max(0, Math.abs(eye.getZ() - z) - 0.3);
        double dy = 0;
        if (eye.getY() < y) dy = y - eye.getY();
        else if (eye.getY() > y + 1.8) dy = eye.getY() - (y + 1.8);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
