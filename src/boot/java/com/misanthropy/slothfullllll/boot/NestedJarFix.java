package com.misanthropy.slothfullllll.boot;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class NestedJarFix {
    private static final String TARGET = "jdk.nio.zipfs.ZipFileSystem";
    private static final String HOOK = "jdk/nio/zipfs/SlothfullllllNestedJars";
    private static final String TEMPLATE = "com/misanthropy/slothfullllll/boot/NestedJarHook";
    private static final String DESC = "(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/nio/channels/SeekableByteChannel;";

    private NestedJarFix() {
    }

    static void apply() throws Throwable {
        Class<?> zipfs = Class.forName(TARGET, false, ClassLoader.getPlatformClassLoader());
        Instrumentation instrumentation = ModuleGraphFix.instrumentation();
        if (!instrumentation.isRetransformClassesSupported()) {
            return;
        }
        try {
            Class.forName(HOOK.replace('/', '.'), false, zipfs.getClassLoader());
        } catch (ClassNotFoundException e) {
            ModuleGraphFix.implLookup().in(zipfs).defineClass(hookBytes());
        }
        boolean[] patched = new boolean[1];
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(Module module, ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
                if (redefined != zipfs) {
                    return null;
                }
                try {
                    byte[] out = patch(bytes);
                    patched[0] = out != null;
                    return out;
                } catch (Throwable t) {
                    SlothfullllllService.LOGGER.warn("Could not patch the nested jar reader", t);
                    return null;
                }
            }
        };
        instrumentation.addTransformer(transformer, true);
        try {
            instrumentation.retransformClasses(zipfs);
        } finally {
            instrumentation.removeTransformer(transformer);
        }
        SlothfullllllService.LOGGER.info(patched[0]
                ? "Nested jars are read from disk instead of being held in memory"
                : "Nested jar reader left as is, ZipFileSystem does not have the expected shape");
    }

    static byte[] patch(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int replaced = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("<init>")) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals("java/nio/file/Files") && call.name.equals("newByteChannel") && call.desc.equals(DESC)) {
                    call.owner = HOOK;
                    call.name = "channel";
                    replaced++;
                }
            }
        }
        if (replaced != 1) {
            return null;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] hookBytes() throws IOException {
        ClassWriter writer = new ClassWriter(0);
        try (InputStream in = NestedJarFix.class.getResourceAsStream("NestedJarHook.class")) {
            if (in == null) {
                throw new IOException("Missing NestedJarHook.class");
            }
            new ClassReader(in.readAllBytes()).accept(new ClassRemapper(writer, new SimpleRemapper(TEMPLATE, HOOK)), 0);
        }
        return writer.toByteArray();
    }
}
