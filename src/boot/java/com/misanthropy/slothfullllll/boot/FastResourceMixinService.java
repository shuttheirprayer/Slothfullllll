package com.misanthropy.slothfullllll.boot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.ReEntranceLock;

final class FastResourceMixinService implements IMixinService {
    private static final Object MISSING = new Object();

    private final IMixinService delegate;
    private final Map<ClassLoader, Object> indexes = new WeakHashMap<>();

    FastResourceMixinService(IMixinService delegate) {
        this.delegate = delegate;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if (name.indexOf('/') < 0 && name.endsWith(".json")) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            RootResourceIndex index = loader == null ? null : this.index(loader);
            if (index != null) {
                URL url = index.find(name);
                if (url == null) {
                    return null;
                }
                try {
                    return url.openStream();
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return this.delegate.getResourceAsStream(name);
    }

    private synchronized RootResourceIndex index(ClassLoader loader) {
        Object index = this.indexes.get(loader);
        if (index == null) {
            index = RootResourceIndex.build(loader);
            this.indexes.put(loader, index == null ? MISSING : index);
        }
        return index instanceof RootResourceIndex root ? root : null;
    }

    @Override
    public String getName() {
        return this.delegate.getName();
    }

    @Override
    public boolean isValid() {
        return this.delegate.isValid();
    }

    @Override
    public void prepare() {
        this.delegate.prepare();
    }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        return this.delegate.getInitialPhase();
    }

    @Override
    public void offer(IMixinInternal internal) {
        this.delegate.offer(internal);
    }

    @Override
    public void init() {
        this.delegate.init();
    }

    @Override
    public void beginPhase() {
        this.delegate.beginPhase();
    }

    @Override
    public void checkEnv(Object bootSource) {
        this.delegate.checkEnv(bootSource);
    }

    @Override
    public ReEntranceLock getReEntranceLock() {
        return this.delegate.getReEntranceLock();
    }

    @Override
    public IClassProvider getClassProvider() {
        return this.delegate.getClassProvider();
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this.delegate.getBytecodeProvider();
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this.delegate.getTransformerProvider();
    }

    @Override
    public IClassTracker getClassTracker() {
        return this.delegate.getClassTracker();
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return this.delegate.getAuditTrail();
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return this.delegate.getPlatformAgents();
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return this.delegate.getPrimaryContainer();
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return this.delegate.getMixinContainers();
    }

    @Override
    public String getSideName() {
        return this.delegate.getSideName();
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
        return this.delegate.getMinCompatibilityLevel();
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return this.delegate.getMaxCompatibilityLevel();
    }

    @Override
    public ILogger getLogger(String name) {
        return this.delegate.getLogger(name);
    }
}
