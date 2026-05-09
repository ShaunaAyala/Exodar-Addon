package com.exodar.addon.mixin;

import com.exodar.addon.modules.LegitAura;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class EntityMovementMixin {
    @Redirect(
        method = "moveRelative(FLnet/minecraft/world/phys/Vec3;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F")
    )
    private float exodar$silentYawForMovement(Entity self) {
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
