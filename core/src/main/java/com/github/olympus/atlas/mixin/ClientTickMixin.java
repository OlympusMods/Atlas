package com.github.olympus.atlas.mixin;

import com.github.olympus.atlas.client.Atlas;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void atlas$tick(CallbackInfo callback) {
        Atlas.tick((Minecraft) (Object) this);
    }
}
