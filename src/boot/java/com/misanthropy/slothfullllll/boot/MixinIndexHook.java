package com.misanthropy.slothfullllll.boot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MixinIndexHook {
    private static volatile int version;
    private static boolean broken;

    private static Field modCountField;
    private static Method packageMethod;
    private static Field mappingField;

    private static List<?> cachedList;
    private static int cachedModCount = -1;
    private static int cachedVersion = -1;
    private static Object[] configs;
    private static Map<String, int[]> targets;
    private static Map<String, int[]> packageBuckets;
    private static int[] shortPackages;

    private MixinIndexHook() {
    }

    public static void bump() {
        version++;
    }

    public static synchronized List<?> candidates(List<?> list, String name) {
        if (broken || name == null || list.getClass() != ArrayList.class) {
            return list;
        }
        try {
            int modCount = modCountField == null ? initAndRead(list) : modCountField.getInt(list);
            if (list != cachedList || modCount != cachedModCount || version != cachedVersion) {
                rebuild(list, modCount);
            }
            int[] byTarget = targets.get(name);
            int[] byBucket = null;
            int second = secondDot(name);
            if (second > 0) {
                byBucket = packageBuckets.get(name.substring(0, second + 1));
            }
            int[] merged = merge(byTarget, merge(byBucket, shortPackages));
            if (merged.length == 0) {
                return List.of();
            }
            List<Object> out = new ArrayList<>(merged.length);
            for (int index : merged) {
                out.add(configs[index]);
            }
            return out;
        } catch (Throwable t) {
            broken = true;
            System.out.println("[Slothfullllll] mixin target index disabled: " + t);
            return list;
        }
    }

    private static int initAndRead(List<?> list) throws ReflectiveOperationException {
        Field f = AbstractList.class.getDeclaredField("modCount");
        f.setAccessible(true);
        modCountField = f;
        return f.getInt(list);
    }

    private static void rebuild(List<?> list, int modCount) throws ReflectiveOperationException {
        Object[] array = list.toArray();
        Map<String, List<Integer>> byTarget = new HashMap<>();
        Map<String, List<Integer>> buckets = new HashMap<>();
        List<Integer> shortOnes = new ArrayList<>();
        for (int i = 0; i < array.length; i++) {
            Object config = array[i];
            if (packageMethod == null || packageMethod.getDeclaringClass() != config.getClass()) {
                packageMethod = config.getClass().getMethod("getMixinPackage");
                packageMethod.setAccessible(true);
                mappingField = config.getClass().getDeclaredField("mixinMapping");
                mappingField.setAccessible(true);
            }
            String pkg = (String) packageMethod.invoke(config);
            if (pkg != null && !pkg.isEmpty()) {
                int second = secondDot(pkg);
                if (second > 0) {
                    buckets.computeIfAbsent(pkg.substring(0, second + 1), k -> new ArrayList<>()).add(i);
                } else {
                    shortOnes.add(i);
                }
            }
            for (Object key : ((Map<?, ?>) mappingField.get(config)).keySet()) {
                if (key instanceof String s) {
                    byTarget.computeIfAbsent(s, k -> new ArrayList<>()).add(i);
                }
            }
        }
        configs = array;
        targets = toArrays(byTarget);
        packageBuckets = toArrays(buckets);
        shortPackages = shortOnes.stream().mapToInt(Integer::intValue).toArray();
        cachedList = list;
        cachedModCount = modCount;
        cachedVersion = version;
    }

    private static Map<String, int[]> toArrays(Map<String, List<Integer>> in) {
        Map<String, int[]> out = new HashMap<>(in.size() * 2);
        for (Map.Entry<String, List<Integer>> e : in.entrySet()) {
            out.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        }
        return out;
    }

    private static int secondDot(String s) {
        int first = s.indexOf('.');
        return first < 0 ? -1 : s.indexOf('.', first + 1);
    }

    private static int[] merge(int[] a, int[] b) {
        if (a == null || a.length == 0) {
            return b == null ? new int[0] : b;
        }
        if (b == null || b.length == 0) {
            return a;
        }
        int[] out = new int[a.length + b.length];
        int i = 0, j = 0, k = 0;
        while (i < a.length || j < b.length) {
            int next;
            if (j >= b.length || (i < a.length && a[i] < b[j])) {
                next = a[i++];
            } else if (i >= a.length || b[j] < a[i]) {
                next = b[j++];
            } else {
                next = a[i++];
                j++;
            }
            out[k++] = next;
        }
        return Arrays.copyOf(out, k);
    }
}
