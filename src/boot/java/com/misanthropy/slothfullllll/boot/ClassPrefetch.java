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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

final class ClassPrefetch implements ClassFileTransformer {
    private static final String LOADER = "cpw.mods.modlauncher.TransformingClassLoader";
    private static final String START = "net/minecraft/client/main/Main";
    private static final String SERVER_START = "net/minecraft/server/Main";
    private static final String CLIENT_END = "net/minecraft/client/gui/screens/TitleScreen";
    private static final String SERVER_END = "net/minecraft/server/dedicated/ServerWatchdog";
    private static final String THREAD = "Slothfullllll prefetch";
    private static final String WORKER = "modloading-worker";
    private static final List<String> NEVER = List.of("org/spongepowered/", "com/llamalad7/", "com/bawnorton/", "/mixin/", "/mixins/");
    private static final long LATE_WINDOW_MS = 15 * 60 * 1000L;
    private static final int LATE_LIMIT = 60_000;
    private static final int LATE_THREADS = 3;
    private static final long CHECKPOINT_MS = 60_000L;

    private final Instrumentation instrumentation;
    private final Path file;
    private final Path lateFile;
    private final List<String> previous;
    private final List<String> previousLate;
    private final boolean lateEnabled;
    private final ConcurrentLinkedQueue<String> seen = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> lateSeen = new ConcurrentLinkedQueue<>();
    private final Set<String> underLock = ConcurrentHashMap.newKeySet();
    private final AtomicInteger lateCount = new AtomicInteger();
    private final AtomicBoolean lateSaved = new AtomicBoolean();
    private Object mixinLock;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean done;
    private volatile boolean late;
    private volatile boolean finished;

    private ClassPrefetch(Instrumentation instrumentation, Path file, Path lateFile, List<String> previous, List<String> previousLate, boolean lateEnabled) {
        this.instrumentation = instrumentation;
        this.file = file;
        this.lateFile = lateFile;
        this.previous = previous;
        this.previousLate = previousLate;
        this.lateEnabled = lateEnabled;
    }

    static void install(String side) throws Throwable {
        Instrumentation instrumentation = ModuleGraphFix.instrumentation();
        Path file = Config.FILE.resolveSibling("slothfullllll-classes-" + side + ".txt");
        Path lateFile = Config.FILE.resolveSibling("slothfullllll-classes-" + side + "-late.txt");
        boolean lateEnabled = Config.enabled("late_class_prefetch");
        instrumentation.addTransformer(new ClassPrefetch(instrumentation, file, lateFile, read(file), lateEnabled ? read(lateFile) : List.of(), lateEnabled), false);
    }

    private static List<String> read(Path path) throws IOException {
        return Files.isRegularFile(path) ? Files.readAllLines(path, StandardCharsets.UTF_8) : List.of();
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        if (finished || redefined != null || name == null || loader == null || !LOADER.equals(loader.getClass().getName())) {
            return null;
        }
        if (late) {
            recordLate(name);
            return null;
        }
        if (mixinLock == null && (name.equals(START) || name.equals(SERVER_START))) {
            mixinLock = findMixinLock();
            if (mixinLock == null) {
                SlothfullllllService.LOGGER.info("Class prefetch left off, the Mixin transformer lock was not found");
                done = true;
                finished = true;
                instrumentation.removeTransformer(this);
                return null;
            }
        }
        if (mixinLock != null && !started.get() && Thread.currentThread().getName().startsWith(WORKER) && started.compareAndSet(false, true)) {
            prefetch(loader, filter(previous, Set.of()), Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() / 2)), file, () -> !done, "");
        }
        if (mixinLock != null && Thread.holdsLock(mixinLock)) {
            underLock.add(name);
        } else if (name.indexOf("$$") < 0) {
            seen.add(name);
        }
        if (name.equals(CLIENT_END) || name.equals(SERVER_END)) {
            done = true;
            save();
            if (lateEnabled && mixinLock != null) {
                startLate(loader);
            } else {
                finished = true;
                instrumentation.removeTransformer(this);
            }
        }
        return null;
    }

    private void recordLate(String name) {
        if (Thread.holdsLock(mixinLock)) {
            underLock.add(name);
        } else if (name.indexOf("$$") < 0 && prefetchable(name)) {
            lateSeen.add(name);
            if (lateCount.incrementAndGet() >= LATE_LIMIT) {
                finishLate();
            }
        }
    }

    private void startLate(ClassLoader loader) {
        Set<String> early = new HashSet<>(seen);
        late = true;
        List<String> names = filter(previousLate, early);
        if (!names.isEmpty()) {
            prefetch(loader, names, LATE_THREADS, lateFile, () -> !finished, " (after the title screen)");
        }
        Thread timer = new Thread(() -> {
            long end = System.currentTimeMillis() + LATE_WINDOW_MS;
            while (!finished && System.currentTimeMillis() < end) {
                try {
                    Thread.sleep(CHECKPOINT_MS);
                } catch (InterruptedException e) {
                    return;
                }
                if (!finished) {
                    write(lateFile, lateList());
                }
            }
            finishLate();
        }, THREAD + " late timer");
        timer.setDaemon(true);
        timer.start();
    }

    private void finishLate() {
        if (!lateSaved.compareAndSet(false, true)) {
            return;
        }
        finished = true;
        instrumentation.removeTransformer(this);
        write(lateFile, lateList());
    }

    private List<String> lateList() {
        Set<String> names = new LinkedHashSet<>(lateSeen);
        names.addAll(previousLate);
        names.removeAll(underLock);
        names.removeAll(new HashSet<>(seen));
        names.removeIf(name -> !prefetchable(name));
        List<String> out = new ArrayList<>(names);
        return out.size() > LATE_LIMIT ? new ArrayList<>(out.subList(0, LATE_LIMIT)) : out;
    }

    private List<String> filter(List<String> names, Set<String> skip) {
        List<String> out = new ArrayList<>(names.size());
        for (String name : names) {
            if (prefetchable(name) && !skip.contains(name)) {
                out.add(name);
            }
        }
        return out;
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

    private void prefetch(ClassLoader loader, List<String> names, int threads, Path list, BooleanSupplier keepGoing, String label) {
        if (names.isEmpty()) {
            return;
        }
        Path marker = list.resolveSibling(list.getFileName() + ".running");
        if (Files.exists(marker)) {
            try {
                Files.deleteIfExists(marker);
            } catch (IOException ignored) {
            }
            SlothfullllllService.LOGGER.warn("The last launch did not finish its class prefetch{}, loading those classes on first use this time", label);
            return;
        }
        try {
            Files.createFile(marker);
        } catch (IOException ignored) {
        }
        AtomicInteger next = new AtomicInteger();
        AtomicInteger loaded = new AtomicInteger();
        AtomicInteger running = new AtomicInteger(threads);
        long start = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            Thread thread = new Thread(() -> {
                int index;
                while (keepGoing.getAsBoolean() && (index = next.getAndIncrement()) < names.size()) {
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
                    SlothfullllllService.LOGGER.info("Prefetched {} of {} classes{} in {} ms", loaded.get(), names.size(), label,
                            (System.nanoTime() - start) / 1_000_000);
                }
            }, THREAD + (label.isEmpty() ? " " : " late ") + i);
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
        write(file, new ArrayList<>(names));
    }

    private static void write(Path path, List<String> out) {
        Thread thread = new Thread(() -> {
            try {
                Files.write(path, out, StandardCharsets.UTF_8);
            } catch (IOException e) {
                SlothfullllllService.LOGGER.warn("Could not write {}", path, e);
            }
        }, THREAD + " save");
        thread.start();
    }
}
