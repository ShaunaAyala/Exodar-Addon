package com.exodar.addon.mixin;

import com.exodar.addon.modules.LegitAura;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void exodar$movementFix(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Modules modules = Modules.get();
        if (modules == null) return;
        LegitAura aura = modules.get(LegitAura.class);
        if (aura == null || !aura.shouldFixMovement()) return;

        Input keys = this.keyPresses;
        int rawForward = (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0);
        int rawStrafe = (keys.left() ? 1 : 0) - (keys.right() ? 1 : 0);
        if (rawForward == 0 && rawStrafe == 0) return;

        float realYaw = mc.player.getYRot();
        float silentYaw = aura.getSilentYaw();
        float deltaYaw = Mth.wrapDegrees(realYaw - silentYaw);
        if (Math.abs(deltaYaw) < 0.5f) return;

        float deltaRad = (float) Math.toRadians(deltaYaw);
        float cos = Mth.cos(deltaRad);
        float sin = Mth.sin(deltaRad);

        float newStrafeF = rawStrafe * cos - rawForward * sin;
        float newForwardF = rawForward * cos + rawStrafe * sin;

        int newStrafe = Math.round(newStrafeF);
        int newForward = Math.round(newForwardF);

        boolean newForwardKey = newForward > 0;
        boolean newBackwardKey = newForward < 0;
        boolean newLeftKey = newStrafe > 0;
        boolean newRightKey = newStrafe < 0;
        this.keyPresses = new Input(newForwardKey, newBackwardKey, newLeftKey, newRightKey, keys.jump(), keys.shift(), keys.sprint());

        Vec2 newMv = new Vec2(newStrafe, newForward);
        if (newMv.lengthSquared() > 1.0E-7f) {
            this.moveVector = newMv.normalized();
        } else {
            this.moveVector = new Vec2(0f, 0f);
        }
    }
}
