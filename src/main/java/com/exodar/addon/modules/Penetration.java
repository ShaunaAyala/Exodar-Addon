package com.exodar.addon.modules;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class Penetration extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Reach distance to find entities through walls.")
        .defaultValue(3.0)
        .range(1.0, 6.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    public Penetration() {
        super(ExodarAddon.CATEGORY, "penetration", "Allows hitting entities through walls within reach.");
    }

    public void applyOverride() {
        if (mc.player == null || mc.level == null) return;

        if (mc.hitResult instanceof EntityHitResult) return;

        double reach = range.get();
        Entity camera = mc.getCameraEntity();
        if (camera == null) return;

        Vec3 eyePos = camera.getEyePosition(1.0f);
        Vec3 lookVec = camera.getViewVector(1.0f);
        Vec3 reachVec = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);

        AABB scanArea = camera.getBoundingBox()
            .expandTowards(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach)
            .inflate(1.0, 1.0, 1.0);

        List<Entity> entities = mc.level.getEntities(camera, scanArea,
            e -> !e.isSpectator() && e.isPickable() && (e instanceof LivingEntity || e instanceof ItemFrame));

        Entity foundEntity = null;
        Vec3 hitVec = null;
        double closestDist = reach;

        for (Entity e : entities) {
            float border = e.getPickRadius();
            AABB box = e.getBoundingBox().inflate(border);
            Optional<Vec3> intercept = box.clip(eyePos, reachVec);

            if (box.contains(eyePos)) {
                if (closestDist >= 0.0) {
                    foundEntity = e;
                    hitVec = intercept.orElse(eyePos);
                    closestDist = 0.0;
                }
            } else if (intercept.isPresent()) {
                double d = eyePos.distanceTo(intercept.get());
                if (d < closestDist) {
                    foundEntity = e;
                    hitVec = intercept.get();
                    closestDist = d;
                }
            }
        }

        if (foundEntity != null && hitVec != null && closestDist <= reach) {
            mc.hitResult = new EntityHitResult(foundEntity, hitVec);
            mc.crosshairPickEntity = foundEntity;
        }
    }
}
