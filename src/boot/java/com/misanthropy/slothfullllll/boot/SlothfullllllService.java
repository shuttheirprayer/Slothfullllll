package com.misanthropy.slothfullllll.boot;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;

public final class SlothfullllllService implements ITransformationService {
    static final Logger LOGGER = LogManager.getLogger("Slothfullllll");

    public SlothfullllllService() {
        if (Config.enabled("module_graph")) {
            try {
                ModuleGraphFix.apply();
            } catch (Throwable t) {
                LOGGER.warn("Module graph speedup unavailable, modules will resolve the normal way instead..", t);
            }
        }
    }

    @Override
    public String name() {
        return "slothfullllll";
    }

    @Override
    public void initialize(IEnvironment environment) {
        if (!Config.enabled("mixin_config_lookup")) {
            return;
        }
        try {
            IMixinService current = MixinService.getService();
            if (current instanceof FastResourceMixinService) {
                return;
            }
            Object holder = Unsafes.getStatic(MixinService.class, "instance");
            long offset = Unsafes.offset(MixinService.class, "service");
            FastResourceMixinService fast = new FastResourceMixinService(current);
            if (!Unsafes.UNSAFE.compareAndSwapObject(holder, offset, current, fast)) {
                LOGGER.warn("The mixin service changed while installing the fast resource lookup, left as is");
                return;
            }
            LOGGER.info("Fast mixin resource lookup installed over {}", current.getClass().getName());
        } catch (Throwable t) {
            LOGGER.warn("Could not install the fast mixin resource lookup, mixin configs load the normal way instead..", t);
        }
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<ITransformer> transformers() {
        return List.of();
    }
}
