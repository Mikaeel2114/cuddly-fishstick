package me.bwac;

import java.util.*;

public class PlayerData {
    public static class Pos {
        public final double x, y, z; public final long t;
        public Pos(double x, double y, double z, long t) { this.x = x; this.y = y; this.z = z; this.t = t; }
    }

    public final UUID uuid;
    public final String name;
    public final Map<String, Integer> vl = new HashMap<String, Integer>();
    private final Map<String, Double> buffers = new HashMap<String, Double>();

    public long lastMoveMs, lastSwingMs, lastBlockDamageMs, lastPlaceMs, lastDamageMs, lastVelMs,
            lastExplosionMs, lastTeleportMs, lastRespawnMs, lastIceMs, lastAttackMs;
    public int lastTargetId = -1;

    // movement
    public double lastDy, lastXZ;
    public boolean ground, prevGround, special;
    public int groundTicks, airTicks, noDescTicks;
    public final Deque<Pos> history = new ArrayDeque<Pos>();

    // rotation
    public double yawDelta; public long yawDeltaMs;

    // clicks
    public final Deque<Long> clicks = new ArrayDeque<Long>();
    public final Deque<Long> delays = new ArrayDeque<Long>();
    public long lastClickNs, overCpsSince;

    // velocity
    public double velH, velUx, velUz, velMax; public int velMoves;

    // timer
    public long timerWindow; public int timerCount;

    // world
    public final Deque<Long> places = new ArrayDeque<Long>();
    public final Deque<Long> breaks = new ArrayDeque<Long>();
    public final Map<String, Long> breakStarts = new HashMap<String, Long>();

    public PlayerData(UUID uuid, String name) { this.uuid = uuid; this.name = name; }

    public double addBuf(String k, double a) {
        Double c = buffers.get(k);
        double n = (c == null ? 0 : c) + a;
        buffers.put(k, n);
        return n;
    }
    public void decBuf(String k, double a) {
        Double c = buffers.get(k);
        if (c == null) return;
        buffers.put(k, Math.max(0, c - a));
    }
    public void setBuf(String k, double v) { buffers.put(k, v); }
    public void decayBuffers() { for (Map.Entry<String, Double> e : buffers.entrySet()) e.setValue(e.getValue() * 0.5); }
}
