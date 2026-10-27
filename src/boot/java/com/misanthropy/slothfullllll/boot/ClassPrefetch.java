package com.misanthropy.slothfullllll.boot;

import cpw.mods.modlauncher.Launcher;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class ClassPrefetch implements ClassFileTransformer {
    private static final String LOADER = "cpw.mods.modlauncher.TransformingClassLoader";
    private static final String START = "net/minecraft/client/main/Main";
    private static final String SERVER_START = "net/minecraft/server/Main";
    private static final String CLIENT_END = "net/minecraft/client/gui/screens/TitleScreen";
    private static final String SERVER_END = "net/minecraft/server/dedicated/ServerWatchdog";
    private static final String THREAD = "Slothfullllll prefetch";
    private static final String WORKER = "modloading-worker";
    private static final List<String> NEVER = List.of("org/spongepowered/", "com/llamalad7/", "com/bawnorton/", "/mixin/", "/mixins/");

    private final Instrumentation instrumentation;
    private final Path file;
    private final List<String> previous;
    private final ConcurrentLinkedQueue<String> seen = new ConcurrentLinkedQueue<>();
    private final Set<String> underLock = ConcurrentHashMap.newKeySet();
    private Object mixinLock;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean done;

    private ClassPrefetch(Instrumentation instrumentation, Path file, List<String> previous) {
        this.instrumentation = instrumentation;
        this.file = file;
        this.previous = previous;
    }

    static void install(String side) throws Throwable {
        Instrumentation instrumentation = ModuleGraphFix.instrumentation();
        Path file = Config.FILE.resolveSibling("slothfullllll-classes-" + side + ".txt");
        List<String> previous = List.of();
        if (Files.isRegularFile(file)) {
            previous = Files.readAllLines(file, StandardCharsets.UTF_8);
        }
        instrumentation.addTransformer(new ClassPrefetch(instrumentation, file, previous), false);
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        if (done || redefined != null || name == null || loader == null || !LOADER.equals(loader.getClass().getName())) {
            return null;
        }
        if (mixinLock == null && (name.equals(START) || name.equals(SERVER_START))) {
            mixinLock = findMixinLock();
            if (mixinLock == null) {
                SlothfullllllService.LOGGER.info("Class prefetch left off, the Mixin transformer lock was not found");
                done = true;
                instrumentation.removeTransformer(this);
                return null;
            }
        }
        if (mixinLock != null && !started.get() && Thread.currentThread().getName().startsWith(WORKER) && started.compareAndSet(false, true)) {
            prefetch(loader);
        }
        if (mixinLock != null && Thread.holdsLock(mixinLock)) {
            underLock.add(name);
        } else if (name.indexOf("$$") < 0) {
            seen.add(name);
        }
        if (name.equals(CLIENT_END) || name.equals(SERVER_END)) {
            done = true;
            instrumentation.removeTransformer(this);
            save();
        }
        return null;
    }

    private static boolean prefetchable(String name) {
        for (String part : NEVER) {
            if (name.contains(part)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Object findMixinLock() {
        try {
            Object handler = field(Launcher.INSTANCE, "launchPlugins");
            Object mixin = ((Map<String, ?>) field(handler, "plugins")).get("mixin");
            return mixin == null ? null : field(mixin, "processors");
        } catch (Throwable t) {
            SlothfullllllService.LOGGER.warn("Could not find the Mixin transformer lock", t);
            return null;
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                return Unsafes.UNSAFE.getObject(owner, Unsafes.offset(type, name));
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private void prefetch(ClassLoader loader) {
        List<String> names = new ArrayList<>(previous.size());
        for (String name : previous) {
            if (prefetchable(name)) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return;
        }
        Path marker = file.resolveSibling(file.getFileName() + ".running");
        if (Files.exists(marker)) {
            try {
                Files.deleteIfExists(marker);
            } catch (IOException ignored) {
            }
            SlothfullllllService.LOGGER.warn("The last launch did not finish its class prefetch, loading classes on first use this time");
            return;
        }
        try {
            Files.createFile(marker);
        } catch (IOException ignored) {
        }
        int threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));
        AtomicInteger next = new AtomicInteger();
        AtomicInteger loaded = new AtomicInteger();
        AtomicInteger running = new AtomicInteger(threads);
        long start = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            Thread thread = new Thread(() -> {
                int index;
                while (!done && (index = next.getAndIncrement()) < names.size()) {
                    try {
                        Class.forName(names.get(index).replace('/', '.'), false, loader);
                        loaded.incrementAndGet();
                    } catch (Throwable ignored) {
                    }
                }
                if (running.decrementAndGet() == 0) {
                    try {
                        Files.deleteIfExists(marker);
                    } catch (IOException ignored) {
                    }
                    SlothfullllllService.LOGGER.info("Prefetched {} of {} classes in {} ms", loaded.get(), names.size(),
                            (System.nanoTime() - start) / 1_000_000);
                }
            }, THREAD + " " + i);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
        Thread watchdog = new Thread(() -> watch(running), THREAD + " watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void watch(AtomicInteger running) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        while (running.get() > 0) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }
            long[] ids = threads.findDeadlockedThreads();
            if (ids == null) {
                continue;
            }
            ThreadInfo[] infos = threads.getThreadInfo(ids, true, false);
            for (ThreadInfo info : infos) {
                if (info != null && info.getThreadName().startsWith(THREAD)) {
                    Config.disable("class_prefetch");
                    StringBuilder dump = new StringBuilder();
                    for (ThreadInfo each : infos) {
                        if (each != null) {
                            dump.append(each);
                            StackTraceElement[] stack = each.getStackTrace();
                            for (int i = 8; i < Math.min(stack.length, 40); i++) {
                                dump.append("\tat ").append(stack[i]).append('\n');
                            }
                        }
                    }
                    SlothfullllllService.LOGGER.error("Class prefetch deadlocked, turned off for the next launch. Restart the game.\n{}", dump);
                    return;
                }
            }
        }
    }

    private void save() {
        Set<String> names = new LinkedHashSet<>(seen);
        names.removeAll(underLock);
        names.removeIf(name -> !prefetchable(name));
        List<String> out = new ArrayList<>(names);
        Thread thread = new Thread(() -> {
            try {
                Files.write(file, out, StandardCharsets.UTF_8);
            } catch (IOException e) {
                SlothfullllllService.LOGGER.warn("Could not write {}", file, e);
            }
        }, THREAD + " save");
        thread.start();
    }
}
