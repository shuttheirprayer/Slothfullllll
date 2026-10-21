package com.misanthropy.slothfullllll.boot;

import cpw.mods.jarhandling.SecureJar;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class RootResourceIndex {
    private static final String LOADER_CLASS = "cpw.mods.cl.ModuleClassLoader";

    private static long rootsOffset = -1L;
    private static long fallbackOffset = -1L;
    private static long jarOffset = -1L;

    private final Map<String, List<SecureJar.ModuleDataProvider>> owners;
    private final ClassLoader fallback;

    private RootResourceIndex(Map<String, List<SecureJar.ModuleDataProvider>> owners, ClassLoader fallback) {
        this.owners = owners;
        this.fallback = fallback;
    }

    static RootResourceIndex build(ClassLoader loader) {
        try {
            Class<?> type = loader.getClass();
            while (type != null && !LOADER_CLASS.equals(type.getName())) {
                type = type.getSuperclass();
            }
            if (type == null) {
                return null;
            }
            if (rootsOffset < 0L) {
                rootsOffset = Unsafes.offset(type, "resolvedRoots");
                fallbackOffset = Unsafes.offset(type, "fallbackClassLoader");
            }
            Map<?, ?> roots = (Map<?, ?>) Unsafes.UNSAFE.getObject(loader, rootsOffset);
            ClassLoader fallback = (ClassLoader) Unsafes.UNSAFE.getObject(loader, fallbackOffset);
            Map<String, List<SecureJar.ModuleDataProvider>> owners = new HashMap<>();
            for (Object reference : (Collection<?>) roots.values()) {
                if (jarOffset < 0L) {
                    jarOffset = Unsafes.offset(reference.getClass(), "jar");
                }
                SecureJar.ModuleDataProvider jar = (SecureJar.ModuleDataProvider) Unsafes.UNSAFE.getObject(reference, jarOffset);
                Path root = Paths.get(jar.uri());
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
                    for (Path entry : entries) {
                        Path fileName = entry.getFileName();
                        if (fileName == null) {
                            continue;
                        }
                        String name = fileName.toString();
                        if (name.endsWith(".json")) {
                            owners.computeIfAbsent(name, k -> new ArrayList<>(1)).add(jar);
                        }
                    }
                }
            }
            return new RootResourceIndex(owners, fallback);
        } catch (Throwable t) {
            SlothfullllllService.LOGGER.warn("Slothfullllll could not index {}, using the normal lookup", loader, t);
            return null;
        }
    }

    URL find(String name) {
        List<SecureJar.ModuleDataProvider> jars = this.owners.get(name);
        if (jars != null) {
            for (SecureJar.ModuleDataProvider jar : jars) {
                Optional<URI> uri = jar.findFile(name);
                if (uri.isPresent()) {
                    try {
                        return uri.get().toURL();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return this.fallback.getResource(name);
    }
}
