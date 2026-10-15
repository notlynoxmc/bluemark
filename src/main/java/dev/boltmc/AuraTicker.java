package dev.boltmc;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Spawns the colored aura particles around you, a few per second. */
public final class AuraTicker {
    private AuraTicker() {}

    public static void start() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BoltMc-aura");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(() -> {
            try { Minecraft.getInstance().execute(AuraTicker::spawn); } catch (Throwable ignored) { }
        }, 1000, 90, TimeUnit.MILLISECONDS);
    }

    private static void spawn() {
        BoltMcConfig cfg = BoltMcConfig.get();
        if (!cfg.on(Cosmetic.AURA)) return;

        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null || player.isInvisible()) return;

        long now = System.currentTimeMillis();
        double angle = (now / 300.0) % (Math.PI * 2);
        double radius = 0.55;
        double px = player.getX() + Math.cos(angle) * radius;
        double pz = player.getZ() + Math.sin(angle) * radius;
        double py = player.getY() + 0.9 + Math.sin(now / 250.0) * 0.3;

        level.addParticle(new DustParticleOptions(cfg.rgb(Cosmetic.AURA), 1.0f), px, py, pz, 0.0, 0.01, 0.0);
    }
}
