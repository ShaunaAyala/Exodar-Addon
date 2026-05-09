package com.exodar.addon.modules;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.Multitask;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class AutoClicker extends Module {
    public enum ClickButton { Left, Right, Both }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ClickButton> button = sgGeneral.add(new EnumSetting.Builder<ClickButton>()
        .name("button")
        .description("Which mouse button to auto-click.")
        .defaultValue(ClickButton.Left)
        .build()
    );

    private final Setting<Boolean> requireMouseHeld = sgGeneral.add(new BoolSetting.Builder()
        .name("require-mouse-held")
        .description("Only click while you are holding the same mouse button.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("require-weapon-cooldown")
        .description("Only attack when the weapon is fully charged (perfect swing). Applies to left clicks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> noMiss = sgGeneral.add(new BoolSetting.Builder()
        .name("no-miss")
        .description("Skip left-click when no entity is targeted under the crosshair.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swingOnAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-arm")
        .description("Animate the arm swing on left-click attack.")
        .defaultValue(true)
        .build()
    );

    public AutoClicker() {
        super(ExodarAddon.CATEGORY, "e-auto-clicker", "Auto-clicks while the mouse is held. Optional perfect-swing weapon cooldown.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || mc.options == null) return;

        ClickButton btn = button.get();
        boolean wantLeft = btn == ClickButton.Left || btn == ClickButton.Both;
        boolean wantRight = btn == ClickButton.Right || btn == ClickButton.Both;

        if (wantLeft) leftClick();
        if (wantRight) rightClick();
    }

    private void leftClick() {
        if (requireMouseHeld.get() && !mc.options.keyAttack.isDown()) return;
        if (requireCooldown.get() && mc.player.getAttackStrengthScale(0) < 1.0f) return;

        boolean usingItem = mc.player.isUsingItem();
        if (usingItem && !Modules.get().isActive(Multitask.class)) return;

        Entity target = mc.crosshairPickEntity;
        boolean entityHit = target instanceof LivingEntity && target != mc.player && target.isAlive();

        if (noMiss.get() && !entityHit) return;

        if (usingItem) {
            mc.gameMode.releaseUsingItem(mc.player);
        }

        if (entityHit) {
            mc.gameMode.attack(mc.player, target);
        } else {
            mc.player.resetAttackStrengthTicker();
        }
        if (swingOnAttack.get()) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void rightClick() {
        if (requireMouseHeld.get() && !mc.options.keyUse.isDown()) return;
        mc.options.keyUse.setDown(true);
    }
}
