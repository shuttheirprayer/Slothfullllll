package com.misanthropy.slothfullllll.mixin.toomanyplayers;

import com.misanthropy.slothfullllll.ModResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "ru.feytox.toomanyplayers.TooManyPlayers", remap = false)
public abstract class TooManyPlayersMixin {
    @Redirect(method = "onInitialize", at = @At(value = "INVOKE",
            target = "Lru/feytox/toomanyplayers/OnlineWhitelist;reloadWhitelist()Z"))
    private boolean slothfullllll$reloadOffThread() {
        ModResources.callOffThread("ru.feytox.toomanyplayers.OnlineWhitelist", "reloadWhitelist");
        return false;
    }
}
