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
        if (Config.enabled("skip_bytecode_verification")) {
            try {
                VerifierSkip.apply();
            } catch (Throwable t) {
                LOGGER.warn("Class verification skip unavailable, mod classes are verified the normal way instead..", t);
            }
        }
        if (Config.enabled("nested_jar_cache")) {
            try {
                NestedJarFix.apply();
            } catch (Throwable t) {
                LOGGER.warn("Nested jar cache unavailable, nested jars are read into memory the normal way instead..", t);
            }
        }
        if (Config.enabled("module_graph")) {
            try {
                ModuleGraphFix.apply();
            } catch (Throwable t) {
                LOGGER.warn("Module graph speedup unavailable, modules will resolve the normal way instead..", t);
            }
        }
        if (Config.enabled("jar_packages")) {
            try {
                LoaderPatches.jarPackages();
            } catch (Throwable t) {
                LOGGER.warn("Jar package speedup unavailable, packages are listed the normal way instead..", t);
            }
        }
        if (Config.enabled("union_fs_open")) {
            try {
                LoaderPatches.unionFsOpen();
            } catch (Throwable t) {
                LOGGER.warn("Unlocked union file system open unavailable, jars open the normal way instead..", t);
            }
        }
        if (Config.enabled("parallel_mod_scan")) {
            try {
                LoaderPatches.parallelModScan();
            } catch (Throwable t) {
                LOGGER.warn("Parallel mod scan unavailable, mods are scanned one by one instead..", t);
            }
        }
    }

    @Override
    public String name() {
        return "slothfullllll";
    }

    @Override
    public void initialize(IEnvironment environment) {
        if (Config.enabled("class_prefetch")) {
            try {
                String target = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("");
                ClassPrefetch.install(target.contains("server") ? "server" : "client");
            } catch (Throwable t) {
                LOGGER.warn("Class prefetch unavailable, classes load on first use instead..", t);
            }
        }
        if (Config.enabled("mixin_opcode_names")) {
            try {
                MixinOpcodeNamesFix.apply();
            } catch (Throwable t) {
                LOGGER.warn("Could not cache Mixin opcode names, they are looked up the normal way instead..", t);
            }
        }
        if (Config.enabled("mixin_target_index")) {
            try {
                LoaderPatches.mixinTargetIndex();
            } catch (Throwable t) {
                LOGGER.warn("Mixin target index unavailable, every mixin config is checked per class instead..", t);
            }
        }
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
