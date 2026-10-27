package com.misanthropy.slothfullllll.boot;

import java.lang.reflect.Field;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackagesHook {
    private static volatile Field[] fields;
    private static boolean broken;

    private PackagesHook() {
    }

    public static void prime(Object jar) {
        if (broken) {
            return;
        }
        try {
            Field[] f = fields(jar);
            if (f[1].get(jar) != null) {
                return;
            }
            Set<String> packages = compute(f[0].get(jar), f);
            if (packages != null) {
                f[1].set(jar, packages);
            }
        } catch (Throwable t) {
            broken = true;
        }
    }

    private static Field[] fields(Object jar) throws Exception {
        Field[] f = fields;
        if (f == null) {
            Class<?> jarClass = jar.getClass();
            Field filesystem = jarClass.getDeclaredField("filesystem");
            Field packages = jarClass.getDeclaredField("packages");
            Class<?> fsClass = filesystem.getType();
            Field basepaths = fsClass.getDeclaredField("basepaths");
            Field filter = fsClass.getDeclaredField("pathFilter");
            for (Field x : new Field[]{filesystem, packages, basepaths, filter}) {
                x.setAccessible(true);
            }
            f = new Field[]{filesystem, packages, basepaths, filter};
            fields = f;
        }
        return f;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> compute(Object filesystem, Field[] f) throws Exception {
        if (filesystem == null) {
            return null;
        }
        List<Path> bases = (List<Path>) f[2].get(filesystem);
        if (bases == null || bases.size() != 1) {
            return null;
        }
        Path base = bases.get(0);
        if (base.getFileSystem() != FileSystems.getDefault() || !Files.isRegularFile(base)) {
            return null;
        }
        BiPredicate<String, String> filter = (BiPredicate<String, String>) f[3].get(filesystem);
        String sBase = base.toString().replace('\\', '/');
        if (sBase.length() > 1 && sBase.startsWith("/")) {
            sBase = sBase.substring(1);
        }
        if (filter != null && !filter.test("/", sBase)) {
            return null;
        }
        Set<String> out = new HashSet<>();
        Map<String, Boolean> dirs = filter == null ? null : new HashMap<>();
        try (ZipFile zip = new ZipFile(base.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!plain(name)) {
                    return null;
                }
                if (name.endsWith("/") || !name.endsWith(".class") || name.startsWith("META-INF/")) {
                    continue;
                }
                int slash = name.lastIndexOf('/');
                if (slash <= 0) {
                    continue;
                }
                if (filter != null && !visible(name, slash, sBase, filter, dirs)) {
                    continue;
                }
                out.add(name.substring(0, slash).replace('/', '.'));
            }
        }
        return out;
    }

    private static boolean plain(String name) {
        return !name.isEmpty()
                && name.indexOf('\\') < 0
                && name.charAt(0) != '/'
                && !name.contains("//")
                && !name.equals(".") && !name.startsWith("./") && !name.contains("/./") && !name.endsWith("/.")
                && !name.equals("..") && !name.startsWith("../") && !name.contains("/../") && !name.endsWith("/..");
    }

    private static boolean visible(String name, int slash, String sBase, BiPredicate<String, String> filter, Map<String, Boolean> dirs) {
        int from = 0;
        while (true) {
            int next = name.indexOf('/', from);
            if (next < 0 || next > slash) {
                break;
            }
            String dir = name.substring(0, next + 1);
            Boolean ok = dirs.get(dir);
            if (ok == null) {
                ok = filter.test(dir, sBase);
                dirs.put(dir, ok);
            }
            if (!ok) {
                return false;
            }
            from = next + 1;
        }
        return filter.test(name, sBase);
    }
}
