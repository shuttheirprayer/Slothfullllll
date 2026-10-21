package com.misanthropy.slothfullllll.mixin.kiwi;

import com.misanthropy.slothfullllll.ModResources;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "snownee.kiwi.loader.AnnotatedTypeLoader", remap = false)
public abstract class AnnotatedTypeLoaderMixin {
    @Redirect(method = "get()Lsnownee/kiwi/loader/KiwiConfiguration;", at = @At(value = "INVOKE", ordinal = 0,
            target = "Ljava/util/Optional;map(Ljava/util/function/Function;)Ljava/util/Optional;"))
    private Optional<Object> slothfullllll$rememberMod(Optional<Object> optional, Function<Object, Object> mapper) {
        Optional<Object> mapped = optional.map(mapper);
        ModResources.rememberKiwiMod(mapped.orElse(null));
        return mapped;
    }

    @Redirect(method = "get()Lsnownee/kiwi/loader/KiwiConfiguration;", at = @At(value = "INVOKE",
            target = "Ljava/lang/ClassLoader;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"))
    private InputStream slothfullllll$openFromMod(ClassLoader loader, String name) {
        return ModResources.openKiwi(loader, name);
    }
}
