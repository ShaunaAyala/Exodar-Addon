package com.exodar.addon.modules;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.Set;

public class TriggerBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Max distance to attack the crosshair-targeted entity.")
        .defaultValue(4.5)
        .range(0.0, 32.0)
        .sliderRange(0.0, 8.0)
        .build()
    );

    private final Setting<Boolean> requireCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("require-weapon-cooldown")
        .description("Only attack when the weapon is fully charged (perfect swing).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Min ticks between attacks (cooldown bypass when 0).")
        .defaultValue(0)
        .range(0, 40)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> swingOnAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-arm")
        .description("Animate the arm swing on attack.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgFilter.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entity types this triggerbot will attack.")
        .defaultValue(EntityType.PLAYER)
        .onlyAttackable()
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Skip players in the Meteor friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreCreative = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-creative")
        .description("Skip creative or spectator players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Skip entities with a custom name.")
        .defaultValue(false)
        .build()
    );

    private int delayLeft = 0;

    public TriggerBot() {
        super(ExodarAddon.CATEGORY, "trigger-bot", "Attacks the crosshair-targeted entity automatically. Respects weapon cooldown.");
    }

    @Override
    public void onActivate() {
        delayLeft = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null) return;
        if (delayLeft > 0) {
            delayLeft--;
            return;
        }

        Entity target = mc.crosshairPickEntity;
        if (!(target instanceof LivingEntity le)) return;
        if (le == mc.player) return;
        if (!le.isAlive()) return;
        if (mc.player.distanceTo(le) > range.get()) return;
        if (!entities.get().contains(le.getType())) return;
        if (ignoreNamed.get() && le.hasCustomName()) return;

        if (le instanceof Player p) {
            if (ignoreCreative.get()) {
                GameType gm = EntityUtils.getGameMode(p);
                if (gm == GameType.CREATIVE || gm == GameType.SPECTATOR) return;
            }
            if (ignoreFriends.get() && Friends.get().isFriend(p)) return;
        }

        if (requireCooldown.get() && mc.player.getAttackStrengthScale(0) < 1.0f) return;

        mc.gameMode.attack(mc.player, le);
        if (swingOnAttack.get()) mc.player.swing(InteractionHand.MAIN_HAND);
        delayLeft = tickDelay.get();
    }
}
