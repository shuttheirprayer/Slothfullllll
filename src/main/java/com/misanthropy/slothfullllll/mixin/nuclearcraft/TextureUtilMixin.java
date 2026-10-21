package com.misanthropy.slothfullllll.mixin.nuclearcraft;

import com.misanthropy.slothfullllll.ModResources;
import java.io.InputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "igentuman.nc.util.TextureUtil", remap = false)
public abstract class TextureUtilMixin {
    @Redirect(method = "getAverageColor", at = @At(value = "INVOKE",
            target = "Ljava/lang/ClassLoader;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"))
    private static InputStream slothfullllll$openFromMod(ClassLoader loader, String name) {
        return ModResources.open("nuclearcraft", name, loader);
    }
}
