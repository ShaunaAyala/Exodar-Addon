package com.exodar.addon.modules;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ETool extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back")
        .description("Restore the previously held slot when you stop mining.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyWhileAttacking = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-attacking")
        .description("Only switch while the attack key is held. Off = switch as soon as you look at a mineable block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireBetterTool = sgGeneral.add(new BoolSetting.Builder()
        .name("require-better-tool")
        .description("Skip switching when the current item is already the fastest one in the hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swordOnHit = sgGeneral.add(new BoolSetting.Builder()
        .name("sword-on-hit")
        .description("Swap to a sword when attacking an entity. Stays on the sword (no switch-back).")
        .defaultValue(true)
        .build()
    );

    private boolean switched = false;

    public ETool() {
        super(ExodarAddon.CATEGORY, "e-tool", "Switches mainhand to the fastest tool while mining. Slot only — no silent or click swap. Optional switch-back.");
    }

    @Override
    public void onDeactivate() {
        if (switched && switchBack.get()) {
            InvUtils.swapBack();
        }
        switched = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.options == null || mc.level == null || mc.gameMode == null) return;

        boolean attacking = mc.options.keyAttack.isDown();

        if (swordOnHit.get() && attacking && mc.crosshairPickEntity instanceof LivingEntity le && le != mc.player && le.isAlive()) {
            FindItemResult sword = InvUtils.findInHotbar(stack -> stack.getItem().toString().toLowerCase().contains("sword"));
            if (sword != null && sword.found() && sword.isHotbar()) {
                int curr = mc.player.getInventory().getSelectedSlot();
                if (sword.slot() != curr) {
                    InvUtils.swap(sword.slot(), false);
                    switched = false;
                }
            }
            return;
        }

        boolean targetingBlock = mc.hitResult instanceof BlockHitResult && mc.hitResult.getType() == HitResult.Type.BLOCK;
        boolean shouldEvaluate = targetingBlock && (!onlyWhileAttacking.get() || attacking);

        if (!shouldEvaluate) {
            if (switched) {
                if (switchBack.get()) InvUtils.swapBack();
                switched = false;
            }
            return;
        }

        BlockHitResult bhr = (BlockHitResult) mc.hitResult;
        BlockState state = mc.level.getBlockState(bhr.getBlockPos());
        if (state.isAir()) {
            if (switched) {
                if (switchBack.get()) InvUtils.swapBack();
                switched = false;
            }
            return;
        }

        FindItemResult result = InvUtils.findFastestTool(state);
        if (result == null || !result.found() || !result.isHotbar()) {
            if (switched) {
                if (switchBack.get()) InvUtils.swapBack();
                switched = false;
            }
            return;
        }

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        if (result.slot() == currentSlot) return;

        if (requireBetterTool.get()) {
            float currentSpeed = mc.player.getInventory().getItem(currentSlot).getDestroySpeed(state);
            float candidateSpeed = mc.player.getInventory().getItem(result.slot()).getDestroySpeed(state);
            if (candidateSpeed <= currentSpeed) return;
        }

        InvUtils.swap(result.slot(), switchBack.get());
        switched = true;
    }
}
