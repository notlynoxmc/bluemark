package dev.boltmc.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.boltmc.BoltMcConfig;
import dev.boltmc.Cosmetic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * Draws Bolt Mc's cosmetics on top of the player model: wings, a cape and a hat, all simple
 * flat/boxy shapes tinted with the color picked in the settings screen (same trick vanilla
 * uses for capes: a plain white texture, colored per-vertex).
 *
 * NOTE: this is the one file in the mod that touches 3D entity rendering, which is the part
 * most likely to need small adjustments for the exact class/method names of this Minecraft
 * build (PoseStack / VertexConsumer / RenderType APIs do shift between versions). Everything
 * else in the mod (settings screen, config, chat) does not depend on this file.
 */
public class CosmeticLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation PLAIN =
            ResourceLocation.fromNamespaceAndPath("boltmc", "textures/cosmetic/plain.png");

    private long lastParticleMs = 0;

    public CosmeticLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                        AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                        float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        BoltMcConfig cfg = BoltMcConfig.get();
        if (!cfg.enabled || player.isInvisible()) return;

        poseStack.pushPose();
        // player model origin is at the feet; move up to the shoulders / back.
        poseStack.translate(0.0, -1.5, 0.0);

        if (cfg.on(Cosmetic.CAPE)) drawCape(poseStack, buffer, packedLight, cfg, ageInTicks);
        if (cfg.on(Cosmetic.WINGS)) drawWings(poseStack, buffer, packedLight, cfg, ageInTicks);
        if (cfg.on(Cosmetic.HAT)) drawHat(poseStack, buffer, packedLight, cfg);

        poseStack.popPose();

        if (cfg.on(Cosmetic.AURA)) spawnAuraParticles(player, cfg);
    }

    // ------------------------------------------------------------------ cape

    private void drawCape(PoseStack poseStack, MultiBufferSource buffer, int light, BoltMcConfig cfg, float ageInTicks) {
        int color = 0xFF000000 | cfg.rgb(Cosmetic.CAPE);
        float sway = (float) Math.sin(ageInTicks / 10f) * 2.5f;

        poseStack.pushPose();
        poseStack.translate(0.0, 0.0, 0.18);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(5f + sway));

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(PLAIN));
        PoseStack.Pose pose = poseStack.last();
        // a single flat panel, 10px wide, 16px tall (in 1/16ths of a block), hanging from the shoulders
        quad(vc, pose,
                -0.3125f, 0f, 0f,
                0.3125f, 0f, 0f,
                0.3125f, 0.75f, 0f,
                -0.3125f, 0.75f, 0f,
                color, light);
        poseStack.popPose();
    }

    // ------------------------------------------------------------------ wings

    private void drawWings(PoseStack poseStack, MultiBufferSource buffer, int light, BoltMcConfig cfg, float ageInTicks) {
        int color = 0xFF000000 | cfg.rgb(Cosmetic.WINGS);
        float flap = (float) Math.sin(ageInTicks / 6f) * 12f;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(PLAIN));

        for (int side = -1; side <= 1; side += 2) {
            poseStack.pushPose();
            poseStack.translate(side * 0.15, 0.55, 0.12);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * (20f + flap)));

            PoseStack.Pose pose = poseStack.last();
            float w = side * 0.45f;
            quad(vc, pose,
                    0f, 0f, 0f,
                    w, 0f, 0f,
                    w, 0.35f, -0.05f,
                    0f, 0.4f, 0f,
                    color, light);
            poseStack.popPose();
        }
    }

    // ------------------------------------------------------------------ hat (a small box above the head)

    private void drawHat(PoseStack poseStack, MultiBufferSource buffer, int light, BoltMcConfig cfg) {
        int color = 0xFF000000 | cfg.rgb(Cosmetic.HAT);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(PLAIN));

        poseStack.pushPose();
        poseStack.translate(0.0, 1.72, 0.0);
        PoseStack.Pose pose = poseStack.last();

        float h = 0.22f, s = 0.22f; // half-size and height, in blocks
        // top
        quad(vc, pose, -s, h, -s,  s, h, -s,  s, h, s,  -s, h, s, color, light);
        // four sides
        quad(vc, pose, -s, 0, -s,  s, 0, -s,  s, h, -s,  -s, h, -s, color, light);
        quad(vc, pose,  s, 0,  s, -s, 0,  s, -s, h,  s,   s, h,  s, color, light);
        quad(vc, pose, -s, 0,  s, -s, 0, -s, -s, h, -s,  -s, h,  s, color, light);
        quad(vc, pose,  s, 0, -s,  s, 0,  s,  s, h,  s,   s, h, -s, color, light);
        poseStack.popPose();
    }

    // ------------------------------------------------------------------ aura particles

    private void spawnAuraParticles(AbstractClientPlayer player, BoltMcConfig cfg) {
        long now = System.currentTimeMillis();
        if (now - lastParticleMs < 90) return; // a few per second, not a storm
        lastParticleMs = now;

        int rgb = cfg.rgb(Cosmetic.AURA);
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;

        var level = Minecraft.getInstance().level;
        if (level == null) return;

        double angle = (now / 300.0) % (Math.PI * 2);
        double radius = 0.55;
        double px = player.getX() + Math.cos(angle) * radius;
        double pz = player.getZ() + Math.sin(angle) * radius;
        double py = player.getY() + 0.9 + Math.sin(now / 250.0) * 0.3;

        level.addParticle(new DustParticleOptions(new Vector3f(r, g, b), 1.0f), px, py, pz, 0.0, 0.01, 0.0);
    }

    // ------------------------------------------------------------------ small geometry helper

    /** Emits one flat, double-nothing (cutout) quad in the given 4 local-space corners, tinted by color. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3,
                              float x4, float y4, float z4,
                              int argb, int light) {
        float a = ((argb >> 24) & 255) / 255f;
        float r = ((argb >> 16) & 255) / 255f;
        float g = ((argb >> 8) & 255) / 255f;
        float b = (argb & 255) / 255f;

        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(0f, 0f).setLight(light).setNormal(pose, 0, 1, 0);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(1f, 0f).setLight(light).setNormal(pose, 0, 1, 0);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(1f, 1f).setLight(light).setNormal(pose, 0, 1, 0);
        vc.addVertex(pose, x4, y4, z4).setColor(r, g, b, a).setUv(0f, 1f).setLight(light).setNormal(pose, 0, 1, 0);
    }
}
