package com.exodar.addon.modules;

import com.exodar.addon.ExodarAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

public class JumpReset extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> chance = sgGeneral.add(new IntSetting.Builder()
        .name("chance")
        .description("Percent chance to jump on each detected knockback packet.")
        .defaultValue(85)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private static final long EXPLOSION_IGNORE_MS = 500L;
    private static final int MIN_DELAY_TICKS = 2;
    private static final int MAX_DELAY_TICKS = 6;

    private final Random rng = new Random();
    private volatile boolean queued = false;
    private volatile long lastExplosionMs = 0L;

    private int delayLeft = 0;
    private boolean restoreJumpKey = false;
    private boolean userHadJumpDown = false;

    public JumpReset() {
        super(ExodarAddon.CATEGORY, "jump-reset", "Jumps when receiving knockback from a player. Skips explosions, water/lava, non-solid ground.");
    }

    @Override
    public void onActivate() {
        queued = false;
        lastExplosionMs = 0L;
        delayLeft = 0;
        restoreJumpKey = false;
        userHadJumpDown = false;
    }

    @Override
    public void onDeactivate() {
        if (restoreJumpKey && mc.options != null) {
            mc.options.keyJump.setDown(userHadJumpDown);
            restoreJumpKey = false;
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        if (event.packet instanceof ClientboundExplodePacket) {
            lastExplosionMs = System.currentTimeMillis();
            return;
        }

        if (!(event.packet instanceof ClientboundSetEntityMotionPacket vel)) return;
        if (vel.id() != mc.player.getId()) return;
        if (vel.movement().y <= 0.0) return;
        if (System.currentTimeMillis() - lastExplosionMs < EXPLOSION_IGNORE_MS) return;
        if (!mc.player.onGround()) return;
        if (mc.player.isInWater() || mc.player.isUnderWater() || mc.player.isInLava()) return;

        BlockPos below = mc.player.blockPosition().below();
        BlockState belowState = mc.level.getBlockState(below);
        if (!belowState.blocksMotion()) return;

        if (rng.nextInt(100) >= chance.get()) return;

        queued = true;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.options == null) return;

        if (queued) {
            queued = false;
            delayLeft = MIN_DELAY_TICKS + rng.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
        }

        if (delayLeft > 0) {
            delayLeft--;
            if (delayLeft == 0 && mc.player.onGround() && !mc.player.isInWater() && !mc.player.isInLava()) {
                userHadJumpDown = mc.options.keyJump.isDown();
                mc.options.keyJump.setDown(true);
                restoreJumpKey = true;
            }
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.options == null) return;
        if (restoreJumpKey) {
            mc.options.keyJump.setDown(userHadJumpDown);
            restoreJumpKey = false;
        }
    }
}
