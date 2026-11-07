package com.github.olympus.atlas;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Atlas.MOD_ID)
public class Atlas {
    public static final String MOD_ID = "atlas";

    public Atlas() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.register();
        }
    }
}
