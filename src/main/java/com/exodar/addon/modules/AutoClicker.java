package com.exodar.addon.modules;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class AutoClicker extends Module {
    public enum ClickButton { Left, Right, Both }
    public enum TargetMode { EntitiesOnly, BlocksOnly, Anything }

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

    private final Setting<TargetMode> targetMode = sgGeneral.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("What targets count as valid for left-click attacks.")
        .defaultValue(TargetMode.Anything)
        .build()
    );

    private final Setting<Boolean> swingOnAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-arm")
        .description("Animate the arm swing on left-click attack.")
        .defaultValue(true)
        .build()
    );

    public AutoClicker() {
        super(ExodarAddon.CATEGORY, "auto-clicker", "Auto-clicks while the mouse is held. Optional perfect-swing weapon cooldown.");
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

        Entity target = mc.crosshairPickEntity;
        boolean entityHit = target instanceof LivingEntity && target != mc.player && target.isAlive();
        boolean blockHit = mc.hitResult instanceof BlockHitResult && mc.hitResult.getType() == HitResult.Type.BLOCK;

        TargetMode mode = targetMode.get();
        boolean allowEntity = mode == TargetMode.EntitiesOnly || mode == TargetMode.Anything;
        boolean allowBlock = mode == TargetMode.BlocksOnly || mode == TargetMode.Anything;

        if (entityHit && allowEntity) {
            mc.gameMode.attack(mc.player, target);
            if (swingOnAttack.get()) mc.player.swing(InteractionHand.MAIN_HAND);
        } else if (blockHit && allowBlock) {
            if (swingOnAttack.get()) mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void rightClick() {
        if (requireMouseHeld.get() && !mc.options.keyUse.isDown()) return;
        mc.options.keyUse.setDown(true);
    }
}
