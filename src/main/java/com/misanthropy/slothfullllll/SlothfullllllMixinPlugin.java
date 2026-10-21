package com.misanthropy.slothfullllll;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class SlothfullllllMixinPlugin implements IMixinConfigPlugin {
    private static final String ROOT = "com.misanthropy.slothfullllll.mixin.";
    private static final Map<String, String> MOD_IDS = Map.of(
            "kiwi", "kiwi",
            "toomanyplayers", "toomanyplayers",
            "jitl", "jitl",
            "iceandfire", "iceandfire",
            "nuclearcraft", "nuclearcraft",
            "prism", "prism");

    private final Properties config = new Properties();

    @Override
    public void onLoad(String mixinPackage) {
        try (Reader reader = Files.newBufferedReader(Path.of("config", "slothfullllll.properties"))) {
            this.config.load(reader);
        } catch (IOException ignored) {
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(ROOT)) {
            return true;
        }
        String feature = mixinClassName.substring(ROOT.length(), mixinClassName.indexOf('.', ROOT.length()));
        if ("false".equalsIgnoreCase(this.config.getProperty(feature, "true").trim())) {
            return false;
        }
        String modId = MOD_IDS.get(feature);
        return modId == null || LoadingModList.get().getModFileById(modId) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
