package com.misanthropy.slothfullllll.boot;

import java.lang.module.ResolvedModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ResolverHook {
    private ResolverHook() {
    }

    public static Collection<Set<ResolvedModule>> nonAutomaticReads(Map<ResolvedModule, Set<ResolvedModule>> graph) {
        List<Set<ResolvedModule>> reads = new ArrayList<>(graph.size());
        for (Map.Entry<ResolvedModule, Set<ResolvedModule>> entry : graph.entrySet()) {
            if (!entry.getKey().reference().descriptor().isAutomatic()) {
                reads.add(entry.getValue());
            }
        }
        return reads;
    }
}
