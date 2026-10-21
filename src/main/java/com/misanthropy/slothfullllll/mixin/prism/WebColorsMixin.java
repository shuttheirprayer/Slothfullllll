package com.misanthropy.slothfullllll.mixin.prism;

import com.misanthropy.slothfullllll.ModResources;
import java.io.InputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.anthonyhilyard.prism.util.WebColors", remap = false)
public abstract class WebColorsMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Ljava/lang/ClassLoader;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"))
    private static InputStream slothfullllll$openFromMod(ClassLoader loader, String name) {
        return ModResources.open("prism", name, loader);
    }
}
