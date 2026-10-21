package com.misanthropy.slothfullllll.mixin.jitl;

import com.misanthropy.slothfullllll.ModResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.jitl.client.ClientLoginChecker", remap = false)
public abstract class ClientLoginCheckerMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final String INTERNET = "net.jitl.core.helper.InternetHandler";

    @Redirect(method = "onPlayerLogin", at = @At(value = "INVOKE", target = "Lnet/jitl/core/helper/InternetHandler;isOnline()Z"))
    private static boolean slothfullllll$isOnline() {
        return Boolean.TRUE.equals(ModResources.call(INTERNET, "isOnline", false));
    }

    @Redirect(method = "onPlayerLogin", at = @At(value = "INVOKE", target = "Lnet/jitl/core/helper/InternetHandler;isUpdateAvailable()Z"))
    private static boolean slothfullllll$isUpdateAvailable() {
        return Boolean.TRUE.equals(ModResources.call(INTERNET, "isUpdateAvailable", false));
    }

    @Redirect(method = "onPlayerLogin", at = @At(value = "INVOKE", target = "Lnet/jitl/core/helper/InternetHandler;getUpdateVersion()Ljava/lang/String;"))
    private static String slothfullllll$getUpdateVersion() {
        return String.valueOf(ModResources.call(INTERNET, "getUpdateVersion", ""));
    }
}
