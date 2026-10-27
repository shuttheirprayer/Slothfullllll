package com.misanthropy.slothfullllll.boot;

import java.nio.file.Path;
import java.util.Map;

public final class UnionFsHook {
    private UnionFsHook() {
    }

    public static Path[] normalize(Path[] paths) {
        Path[] out = new Path[paths.length];
        for (int i = 0; i < paths.length; i++) {
            out[i] = paths[i].toAbsolutePath().normalize();
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(Map map, String key, Object fileSystem) {
        synchronized (map) {
            map.put(key, fileSystem);
        }
    }
}
