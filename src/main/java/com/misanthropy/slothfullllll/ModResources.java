package com.misanthropy.slothfullllll;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.slf4j.Logger;

public final class ModResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<IModInfo> KIWI_MOD = new ThreadLocal<>();

    private ModResources() {
    }

    public static InputStream open(String modId, String name, ClassLoader fallback) {
        ModFileInfo file = LoadingModList.get().getModFileById(modId);
        return file == null ? fallback.getResourceAsStream(name) : open(file.getFile(), name);
    }

    public static void rememberKiwiMod(Object mod) {
        if (mod instanceof IModInfo info) {
            KIWI_MOD.set(info);
        } else {
            KIWI_MOD.remove();
        }
    }

    public static InputStream openKiwi(ClassLoader fallback, String name) {
        IModInfo mod = KIWI_MOD.get();
        KIWI_MOD.remove();
        return mod == null ? fallback.getResourceAsStream(name) : open(mod.getOwningFile().getFile(), name);
    }

    public static Object call(String owner, String method, Object fallback) {
        try {
            return Class.forName(owner, true, ModResources.class.getClassLoader()).getMethod(method).invoke(null);
        } catch (Throwable t) {
            LOGGER.debug("{}#{} failed, using {}", owner, method, fallback, t);
            return fallback;
        }
    }

    public static void callOffThread(String owner, String method) {
        CompletableFuture.runAsync(() -> call(owner, method, null));
    }

    private static InputStream open(IModFile file, String name) {
        try {
            return Files.newInputStream(file.findResource(name.startsWith("/") ? name.substring(1) : name));
        } catch (Exception e) {
            return null;
        }
    }
}
