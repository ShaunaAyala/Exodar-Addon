package com.exodar.addon.modules;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.Set;

public class AimAssist extends Module {
    public enum AimAxes { Yaw, Pitch, Both }

    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgAim = settings.createGroup("Aim");

    private final Setting<Set<EntityType<?>>> entities = sgFilter.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entity types to aim at.")
        .defaultValue(EntityType.PLAYER)
        .onlyAttackable()
        .build()
    );

    private final Setting<Double> range = sgFilter.add(new DoubleSetting.Builder()
        .name("range")
        .description("Max distance to a valid target.")
        .defaultValue(8.0)
        .range(0.0, 256.0)
        .sliderRange(0.0, 64.0)
        .build()
    );

    private final Setting<KillAura.EntityAge> mobAgeFilter = sgFilter.add(new EnumSetting.Builder<KillAura.EntityAge>()
        .name("mob-age-filter")
        .description("Filter mobs by age.")
        .defaultValue(KillAura.EntityAge.Both)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Skip entities with a custom name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-passive")
        .description("Skip passive mobs (animals, ambient, water animals).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-tamed")
        .description("Skip tamed animals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreCreative = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-creative")
        .description("Skip players in creative or spectator mode.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Skip players in the Meteor friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useFovRange = sgFilter.add(new BoolSetting.Builder()
        .name("use-fov-range")
        .description("Restrict targets to a horizontal FOV cone.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> fovRange = sgFilter.add(new DoubleSetting.Builder()
        .name("fov-range")
        .description("FOV cone size in degrees.")
        .defaultValue(60.0)
        .range(0.0, 180.0)
        .sliderRange(0.0, 180.0)
        .visible(useFovRange::get)
        .build()
    );

    private final Setting<Boolean> ignoreWalls = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-walls")
        .description("Aim through walls.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SortPriority> priority = sgFilter.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to pick a target when multiple are valid.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<AimAxes> aimAxes = sgAim.add(new EnumSetting.Builder<AimAxes>()
        .name("aim-axes")
        .description("Which axes are adjusted.")
        .defaultValue(AimAxes.Both)
        .build()
    );

    private final Setting<Target> bodyTarget = sgAim.add(new EnumSetting.Builder<Target>()
        .name("aim-target")
        .description("Body part to aim at.")
        .defaultValue(Target.Head)
        .build()
    );

    private final Setting<Double> speedYaw = sgAim.add(new DoubleSetting.Builder()
        .name("speed-yaw")
        .description("Speed at which to adjust horizontal (yaw) aim. 0 = don't aim yaw.")
        .defaultValue(1.0)
        .min(0.0)
        .sliderRange(0.0, 10.0)
        .build()
    );

    private final Setting<Double> speedPitch = sgAim.add(new DoubleSetting.Builder()
        .name("speed-pitch")
        .description("Speed at which to adjust vertical (pitch) aim. 0 = don't aim pitch.")
        .defaultValue(1.0)
        .min(0.0)
        .sliderRange(0.0, 10.0)
        .build()
    );

    public AimAssist() {
        super(ExodarAddon.CATEGORY, "aim-assist", "Smooth client-side aim toward filtered targets. Speed-clamped, no instant snap.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        Entity target = TargetUtils.get(this::isValidTarget, priority.get());
        if (target == null) return;

        double targetYaw = Rotations.getYaw(target);
        double targetPitch = Rotations.getPitch(target, bodyTarget.get());

        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();

        float dYaw = wrapDegrees((float) (targetYaw - curYaw));
        float dPitch = wrapDegrees((float) (targetPitch - curPitch));

        float factorYaw = speedYaw.get().floatValue() / 10f;
        float factorPitch = speedPitch.get().floatValue() / 10f;

        float newYaw = curYaw + dYaw * factorYaw;
        float newPitch = curPitch + dPitch * factorPitch;
        newPitch = Math.max(-90f, Math.min(90f, newPitch));

        AimAxes axis = aimAxes.get();
        if (axis == AimAxes.Yaw || axis == AimAxes.Both) mc.player.setYRot(newYaw);
        if (axis == AimAxes.Pitch || axis == AimAxes.Both) mc.player.setXRot(newPitch);
    }

    private boolean isValidTarget(Entity e) {
        if (e == mc.player) return false;
        if (!e.isAlive()) return false;
        if (!entities.get().contains(e.getType())) return false;
        if (mc.player.distanceTo(e) > range.get()) return false;
        if (!ignoreWalls.get() && !mc.player.hasLineOfSight(e)) return false;

        if (useFovRange.get()) {
            double tYaw = Rotations.getYaw(e);
            double diff = Math.abs(wrapDegrees((float) (tYaw - mc.player.getYRot())));
            if (diff > fovRange.get() / 2.0) return false;
        }

        if (ignoreNamed.get() && e.hasCustomName()) return false;

        if (e instanceof Player p) {
            if (p.isSpectator()) return false;
            if (ignoreCreative.get()) {
                GameType gm = EntityUtils.getGameMode(p);
                if (gm == GameType.CREATIVE || gm == GameType.SPECTATOR) return false;
            }
            if (ignoreFriends.get() && Friends.get().isFriend(p)) return false;
        }

        if (e instanceof TamableAnimal ta && ignoreTamed.get() && ta.isTame()) return false;

        if (e instanceof AgeableMob am) {
            KillAura.EntityAge age = mobAgeFilter.get();
            if (age == KillAura.EntityAge.Adult && am.isBaby()) return false;
            if (age == KillAura.EntityAge.Baby && !am.isBaby()) return false;
        }

        if (ignorePassive.get() && (e instanceof Animal || e instanceof AmbientCreature || e instanceof WaterAnimal)) return false;

        return true;
    }

    private static float wrapDegrees(float deg) {
        deg %= 360f;
        if (deg >= 180f) deg -= 360f;
        else if (deg < -180f) deg += 360f;
        return deg;
    }
}
