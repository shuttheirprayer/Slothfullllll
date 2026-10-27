package com.misanthropy.slothfullllll.boot;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class ModuleGraphFix {
    private static final String HOOK = "java/lang/module/SlothfullllllResolverHook";
    private static final String TEMPLATE = "com/misanthropy/slothfullllll/boot/ResolverHook";
    private static final String AGENT = "com.misanthropy.slothfullllll.boot.SlothAgent";

    private ModuleGraphFix() {
    }

    static void apply() throws Throwable {
        long start = System.nanoTime();
        Class<?> resolver = Class.forName("java.lang.module.Resolver", false, null);
        Instrumentation instrumentation = instrumentation();
        if (!instrumentation.isRetransformClassesSupported()) {
            SlothfullllllService.LOGGER.info("Module graph speedup skipped, this JVM cannot retransform classes");
            return;
        }
        try {
            Class.forName(HOOK.replace('/', '.'), false, null);
        } catch (ClassNotFoundException e) {
            implLookup().in(resolver).defineClass(hookBytes());
        }
        boolean[] patched = new boolean[1];
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
                if (redefined != resolver) {
                    return null;
                }
                try {
                    byte[] out = patch(bytes);
                    patched[0] = out != null;
                    return out;
                } catch (Throwable t) {
                    SlothfullllllService.LOGGER.warn("Could not patch Resolver#makeGraph", t);
                    return null;
                }
            }
        };
        instrumentation.addTransformer(transformer, true);
        try {
            instrumentation.retransformClasses(resolver);
        } finally {
            instrumentation.removeTransformer(transformer);
        }
        if (patched[0]) {
            SlothfullllllService.LOGGER.info("Module graph speedup installed in {} ms", (System.nanoTime() - start) / 1_000_000);
        } else {
            SlothfullllllService.LOGGER.info("Module graph speedup skipped, Resolver#makeGraph does not have the expected shape");
        }
    }

    static byte[] patch(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!method.name.equals("makeGraph")) {
                continue;
            }
            List<MethodInsnNode> calls = new ArrayList<>();
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals("java/util/Map") && call.name.equals("values")) {
                    calls.add(call);
                }
            }
            if (calls.size() != 3) {
                return null;
            }
            MethodInsnNode closure = calls.get(2);
            if (!(closure.getNext() instanceof MethodInsnNode next) || !next.name.equals("iterator")) {
                return null;
            }
            method.instructions.set(closure, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "nonAutomaticReads", "(Ljava/util/Map;)Ljava/util/Collection;", false));
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }
        return null;
    }

    private static byte[] hookBytes() throws IOException {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(read("ResolverHook.class")).accept(new ClassRemapper(writer, new SimpleRemapper(TEMPLATE, HOOK)), 0);
        return writer.toByteArray();
    }

    static Instrumentation instrumentation() throws Throwable {
        ClassLoader system = ClassLoader.getSystemClassLoader();
        Class<?> agent;
        try {
            agent = Class.forName(AGENT, true, system);
        } catch (ClassNotFoundException e) {
            byte[] bytes = read("SlothAgent.class");
            MethodHandle define = implLookup().findVirtual(ClassLoader.class, "defineClass",
                    MethodType.methodType(Class.class, String.class, byte[].class, int.class, int.class));
            agent = (Class<?>) define.invoke(system, AGENT, bytes, 0, bytes.length);
        }
        Field field = agent.getField("instrumentation");
        if (field.get(null) == null) {
            Path jar = Files.createTempFile("slothfullllll-agent", ".jar");
            try {
                Manifest manifest = new Manifest();
                Attributes attributes = manifest.getMainAttributes();
                attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
                attributes.putValue("Launcher-Agent-Class", AGENT);
                attributes.putValue("Can-Redefine-Classes", "true");
                attributes.putValue("Can-Retransform-Classes", "true");
                new JarOutputStream(Files.newOutputStream(jar), manifest).close();
                MethodHandle load = implLookup().findStatic(Class.forName("sun.instrument.InstrumentationImpl", false, null), "loadAgent",
                        MethodType.methodType(void.class, String.class));
                load.invoke(jar.toString());
            } finally {
                try {
                    Files.deleteIfExists(jar);
                } catch (IOException e) {
                    jar.toFile().deleteOnExit();
                }
            }
        }
        Instrumentation instrumentation = (Instrumentation) field.get(null);
        if (instrumentation == null) {
            throw new IllegalStateException("The agent did not receive an Instrumentation instance");
        }
        return instrumentation;
    }

    static MethodHandles.Lookup implLookup() throws NoSuchFieldException {
        return (MethodHandles.Lookup) Unsafes.getStatic(MethodHandles.Lookup.class, "IMPL_LOOKUP");
    }

    private static byte[] read(String name) throws IOException {
        try (InputStream in = ModuleGraphFix.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("Missing " + name);
            }
            return in.readAllBytes();
        }
    }
}
