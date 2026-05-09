package com.exodar.addon.modules;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.Set;

public class LegitAura extends Module {
    public enum RotationMode { Legit, Instant }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgMovementFix = settings.createGroup("Movement Fix");

    private final Setting<RotationMode> rotation = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotation")
        .description("Legit = smooth silent rotation. Instant = near-snap silent.")
        .defaultValue(RotationMode.Legit)
        .build()
    );

    private final Setting<Integer> legitSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("legit-speed")
        .description("Legit rotation speed. 10 = ~90% per tick. 1 = slowest.")
        .defaultValue(6)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(() -> rotation.get() == RotationMode.Legit)
        .build()
    );

    private final Setting<Target> bodyTarget = sgGeneral.add(new EnumSetting.Builder<Target>()
        .name("aim-target")
        .description("Body part to aim at.")
        .defaultValue(Target.Body)
        .build()
    );

    private final Setting<Boolean> ignorePitch = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-pitch")
        .description("Only adjust horizontal aim. Pitch stays where you point.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").description("Attack range. Vanilla default is 3.0.").defaultValue(3.0).range(0.0, 6.0).sliderRange(0.0, 6.0).build()
    );


    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click").description("Attack only while you hold the attack key.").defaultValue(false).build()
    );

    private final Setting<Boolean> onlyOnLook = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-look").description("Attack only when looking at the target (within FOV).").defaultValue(false).build()
    );

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov-degrees").description("FOV cone for only-on-look.").defaultValue(60.0).range(0.0, 180.0).sliderRange(0.0, 180.0).visible(onlyOnLook::get).build()
    );

    private final Setting<Boolean> requireCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("require-weapon-cooldown").description("Only attack when weapon is fully charged (perfect swing).").defaultValue(true).build()
    );


    private final Setting<Set<EntityType<?>>> entities = sgFilter.add(new EntityTypeListSetting.Builder()
        .name("entities").description("Entity types to attack.").defaultValue(EntityType.PLAYER).onlyAttackable().build()
    );

    private final Setting<SortPriority> priority = sgFilter.add(new EnumSetting.Builder<SortPriority>()
        .name("priority").description("Target selection priority.").defaultValue(SortPriority.LowestDistance).build()
    );

    private final Setting<KillAura.EntityAge> mobAgeFilter = sgFilter.add(new EnumSetting.Builder<KillAura.EntityAge>()
        .name("mob-age-filter").description("Filter mobs by age.").defaultValue(KillAura.EntityAge.Both).build()
    );

    private final Setting<Boolean> ignoreNamed = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-named").description("Skip name-tagged entities.").defaultValue(false).build()
    );

    private final Setting<Boolean> ignorePassive = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-passive").description("Skip passive mobs.").defaultValue(false).build()
    );

    private final Setting<Boolean> ignoreTamed = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-tamed").description("Skip tamed animals.").defaultValue(true).build()
    );

    private final Setting<Boolean> ignoreCreative = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-creative").description("Skip creative or spectator players.").defaultValue(true).build()
    );

    private final Setting<Boolean> ignoreFriends = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-friends").description("Skip players in the Meteor friends list.").defaultValue(true).build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("hit-delay-ticks").description("Min ticks between attacks.").defaultValue(0).range(0, 40).sliderRange(0, 40).build()
    );

    private final Setting<Boolean> pauseOnUse = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-use").description("Pause attacks while using an item (eating, drawing bow, blocking).").defaultValue(true).build()
    );


    private static final int MAX_RETURN_TICKS = 6;
    private static final float RETURN_FACTOR = 0.5f;
    private static final float CONVERGE_THRESHOLD = 1.5f;
    private static final float MAX_DELTA_PER_TICK = 120f;

    private int delayLeft = 0;
    private Entity currentTarget = null;
    private boolean lastTickRotated = false;
    private boolean returning = false;
    private boolean pendingDisable = false;
    private int returnTicks = 0;
    private float lastSentYaw = 0f;
    private float lastSentPitch = 0f;
    private float savedYaw = 0f;
    private float savedPitch = 0f;
    private boolean swapped = false;

    public LegitAura() {
        super(ExodarAddon.CATEGORY, "legit-aura", "Silently aims and attacks nearby enemies for you.");
    }

    @Override
    public void onActivate() {
        delayLeft = 0;
        currentTarget = null;
        lastTickRotated = false;
        returning = false;
        returnTicks = 0;
        if (mc.player != null) {
            lastSentYaw = mc.player.getYRot();
            lastSentPitch = mc.player.getXRot();
        }
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
        lastTickRotated = false;
        returning = false;
        returnTicks = 0;
        pendingDisable = false;
    }

    @Override
    public void toggle() {
        if (isActive() && lastTickRotated && !pendingDisable) {
            pendingDisable = true;
            currentTarget = null;
            returning = true;
            returnTicks = 0;
            return;
        }
        super.toggle();
    }

    @Override
    public void disable() {
        if (isActive() && lastTickRotated && !pendingDisable) {
            pendingDisable = true;
            currentTarget = null;
            returning = true;
            returnTicks = 0;
            return;
        }
        super.disable();
    }

    public boolean shouldFixMovement() {
        return isActive() && (currentTarget != null || returning);
    }

    public float getSilentYaw() { return lastSentYaw; }
    public float getSilentPitch() { return lastSentPitch; }

    public Entity getCurrentTarget() {
        return currentTarget;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;

        if (delayLeft > 0) delayLeft--;

        boolean usingItem = mc.player.isUsingItem() && !meteordevelopment.meteorclient.systems.modules.Modules.get().isActive(meteordevelopment.meteorclient.systems.modules.player.Multitask.class);
        boolean paused = (pauseOnUse.get() && usingItem)
            || (onlyOnClick.get() && (mc.options == null || !mc.options.keyAttack.isDown()));

        Entity target = paused ? null : TargetUtils.get(this::isValidTarget, priority.get());

        if (target != null) {
            currentTarget = target;
            returning = false;
            returnTicks = 0;

            float targetYaw = (float) Rotations.getYaw(target);
            float targetPitch = (float) Rotations.getPitch(target, bodyTarget.get());

            float factor = rotation.get() == RotationMode.Legit ? legitSpeed.get() * 0.09f : 1.0f;

            float dYaw = wrapDegrees(targetYaw - lastSentYaw);
            float dPitch = ignorePitch.get() ? 0f : (targetPitch - lastSentPitch);
            float deltaY = clampDelta(dYaw * factor);
            float deltaP = clampDelta(dPitch * factor);
            float newYaw = lastSentYaw + deltaY;
            float newPitch = ignorePitch.get() ? mc.player.getXRot() : (lastSentPitch + deltaP);
            newPitch = Math.max(-90f, Math.min(90f, newPitch));

            newYaw = snapToGcd(newYaw);
            newPitch = snapToGcd(newPitch);

            lastSentYaw = newYaw;
            lastSentPitch = newPitch;

            boolean aimAcquired = Math.abs(wrapDegrees(targetYaw - newYaw)) < 2f
                && (ignorePitch.get() || Math.abs(targetPitch - newPitch) < 2f);

            boolean raycastHits = raycastHitsTarget(target, newYaw, newPitch);

            boolean canAttack = lastTickRotated
                && aimAcquired
                && raycastHits
                && delayLeft <= 0
                && !(onlyOnLook.get() && !isLookingAt(target))
                && (!requireCooldown.get() || mc.player.getAttackStrengthScale(0) >= 1.0f);

            if (canAttack) {
                performAttack(target);
                delayLeft = hitDelay.get();
            }

            lastTickRotated = true;
            return;
        }

        currentTarget = null;

        if (lastTickRotated && !returning) {
            returning = true;
            returnTicks = 0;
        }

        if (returning) {
            returnTicks++;
            float realYaw = mc.player.getYRot();
            float realPitch = mc.player.getXRot();
            float dYaw = wrapDegrees(realYaw - lastSentYaw);
            float dPitch = realPitch - lastSentPitch;

            if ((Math.abs(dYaw) < CONVERGE_THRESHOLD && Math.abs(dPitch) < CONVERGE_THRESHOLD)
                || returnTicks >= MAX_RETURN_TICKS) {
                returning = false;
                lastTickRotated = false;
                lastSentYaw = realYaw;
                lastSentPitch = realPitch;
                if (pendingDisable) {
                    pendingDisable = false;
                    super.disable();
                }
                return;
            }

            float deltaY = clampDelta(dYaw * RETURN_FACTOR);
            float deltaP = clampDelta(dPitch * RETURN_FACTOR);
            float newYaw = lastSentYaw + deltaY;
            float newPitch = lastSentPitch + deltaP;
            newPitch = Math.max(-90f, Math.min(90f, newPitch));

            newYaw = snapToGcd(newYaw);
            newPitch = snapToGcd(newPitch);

            lastSentYaw = newYaw;
            lastSentPitch = newPitch;
        }
    }

    @EventHandler
    private void onPreSendMovement(SendMovementPacketsEvent.Pre event) {
        if (mc.player == null) return;
        if (!shouldFixMovement()) return;
        savedYaw = mc.player.getYRot();
        savedPitch = mc.player.getXRot();
        mc.player.setYRot(lastSentYaw);
        mc.player.setXRot(Math.max(-90f, Math.min(90f, lastSentPitch)));
        mc.player.yBodyRot = lastSentYaw;
        mc.player.yBodyRotO = lastSentYaw;
        mc.player.yHeadRot = lastSentYaw;
        mc.player.yHeadRotO = lastSentYaw;
        swapped = true;
    }

    @EventHandler
    private void onPostSendMovement(SendMovementPacketsEvent.Post event) {
        if (mc.player == null) return;
        if (!swapped) return;
        mc.player.setYRot(savedYaw);
        mc.player.setXRot(savedPitch);
        swapped = false;
    }

    public void applyOverride() {
        if (mc.player == null || mc.level == null) return;
        if (currentTarget == null || !currentTarget.isAlive()) return;

        net.minecraft.world.phys.Vec3 eyePos = mc.player.getEyePosition(1.0f);
        float yawRad = (float) Math.toRadians(lastSentYaw);
        float pitchRad = (float) Math.toRadians(lastSentPitch);
        double cosPitch = Math.cos(pitchRad);
        net.minecraft.world.phys.Vec3 lookVec = new net.minecraft.world.phys.Vec3(
            -Math.sin(yawRad) * cosPitch,
            -Math.sin(pitchRad),
            Math.cos(yawRad) * cosPitch
        );
        double reach = range.get();
        net.minecraft.world.phys.Vec3 endPos = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
        float border = currentTarget.getPickRadius();
        net.minecraft.world.phys.AABB box = currentTarget.getBoundingBox().inflate(border);
        java.util.Optional<net.minecraft.world.phys.Vec3> hit = box.clip(eyePos, endPos);
        if (hit.isEmpty()) return;

        double hitDist = eyePos.distanceTo(hit.get());
        double maxOverride = Math.min(range.get(), 2.95);
        if (hitDist > maxOverride) return;

        mc.hitResult = new net.minecraft.world.phys.EntityHitResult(currentTarget, hit.get());
        mc.crosshairPickEntity = currentTarget;
    }

    private boolean raycastHitsTarget(Entity target, float yaw, float pitch) {
        if (mc.player == null) return false;
        net.minecraft.world.phys.Vec3 eyePos = mc.player.getEyePosition(1.0f);
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        net.minecraft.world.phys.Vec3 lookVec = new net.minecraft.world.phys.Vec3(
            -Math.sin(yawRad) * cosPitch,
            -Math.sin(pitchRad),
            Math.cos(yawRad) * cosPitch
        );
        double reach = range.get();
        net.minecraft.world.phys.Vec3 endPos = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
        float border = target.getPickRadius();
        net.minecraft.world.phys.AABB box = target.getBoundingBox().inflate(border);
        return box.clip(eyePos, endPos).isPresent();
    }

    private static float clampDelta(float delta) {
        if (delta > MAX_DELTA_PER_TICK) return MAX_DELTA_PER_TICK;
        if (delta < -MAX_DELTA_PER_TICK) return -MAX_DELTA_PER_TICK;
        return delta;
    }

    private float snapToGcd(float angle) {
        if (mc.options == null) return angle;
        double sens = mc.options.sensitivity().get() * 0.6 + 0.2;
        float gcd = (float) (sens * sens * sens * 8.0 * 0.15);
        if (gcd <= 0f) return angle;
        return Math.round(angle / gcd) * gcd;
    }

    private void performAttack(Entity target) {
        if (mc.player == null || mc.gameMode == null) return;
        if (target == null || !target.isAlive()) return;

        boolean visible = mc.player.hasLineOfSight(target);
        Penetration pen = meteordevelopment.meteorclient.systems.modules.Modules.get().get(Penetration.class);
        boolean penActive = pen != null && pen.isActive();

        if (mc.player.isUsingItem() && meteordevelopment.meteorclient.systems.modules.Modules.get().isActive(meteordevelopment.meteorclient.systems.modules.player.Multitask.class)) {
            mc.gameMode.releaseUsingItem(mc.player);
        }

        if (visible || penActive) {
            mc.gameMode.attack(mc.player, target);
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean isLookingAt(Entity target) {
        float fovDeg = fov.get().floatValue();
        double targetYaw = Rotations.getYaw(target);
        float yawDiff = Math.abs(wrapDegrees((float) (targetYaw - mc.player.getYRot())));
        return yawDiff <= fovDeg / 2.0f;
    }

    private boolean isValidTarget(Entity e) {
        if (e == mc.player) return false;
        if (!e.isAlive()) return false;
        if (!entities.get().contains(e.getType())) return false;

        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.AABB ebox = e.getBoundingBox();
        net.minecraft.world.phys.Vec3 closest = new net.minecraft.world.phys.Vec3(
            net.minecraft.util.Mth.clamp(eye.x, ebox.minX, ebox.maxX),
            net.minecraft.util.Mth.clamp(eye.y, ebox.minY, ebox.maxY),
            net.minecraft.util.Mth.clamp(eye.z, ebox.minZ, ebox.maxZ)
        );
        double eyeToHitbox = eye.distanceTo(closest);
        double trackingRange = range.get() + 1.5;
        if (eyeToHitbox > trackingRange) return false;

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

        return e instanceof LivingEntity;
    }

    private static float wrapDegrees(float deg) {
        deg %= 360f;
        if (deg >= 180f) deg -= 360f;
        else if (deg < -180f) deg += 360f;
        return deg;
    }
}
