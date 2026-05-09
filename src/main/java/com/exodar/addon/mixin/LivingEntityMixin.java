package com.exodar.addon.mixin;

import com.exodar.addon.modules.LegitAura;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Redirect(
        method = "jumpFromGround()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")
    )
    private float exodar$silentYawForJump(LivingEntity self) {
        if (self == Minecraft.getInstance().player) {
            Modules modules = Modules.get();
            if (modules != null) {
                LegitAura aura = modules.get(LegitAura.class);
                if (aura != null && aura.shouldFixMovement()) {
                    return aura.getSilentYaw();
                }
            }
        }
        return self.getYRot();
    }
}
