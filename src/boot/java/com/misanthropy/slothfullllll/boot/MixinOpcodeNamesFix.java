package com.misanthropy.slothfullllll.boot;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

final class MixinOpcodeNamesFix {
    private static final String TARGET = "org.spongepowered.asm.util.Bytecode";
    private static final String HOOK = "org/spongepowered/asm/util/SlothfullllllOpcodeNames";
    private static final String TEMPLATE = "com/misanthropy/slothfullllll/boot/OpcodeNamesHook";
    private static final String DESC = "(ILjava/lang/String;I)Ljava/lang/String;";

    private MixinOpcodeNamesFix() {
    }

    static void apply() throws Throwable {
        Class<?> bytecode = Class.forName(TARGET, false, SlothfullllllService.class.getClassLoader());
        Instrumentation instrumentation = ModuleGraphFix.instrumentation();
        if (!instrumentation.isRetransformClassesSupported()) {
            return;
        }
        try {
            Class.forName(HOOK.replace('/', '.'), false, bytecode.getClassLoader());
        } catch (ClassNotFoundException e) {
            MethodHandles.Lookup lookup = ModuleGraphFix.implLookup().in(bytecode);
            lookup.defineClass(hookBytes());
        }
        boolean[] patched = new boolean[1];
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
                if (redefined != bytecode) {
                    return null;
                }
                try {
                    byte[] out = patch(bytes);
                    patched[0] = out != null;
                    return out;
                } catch (Throwable t) {
                    SlothfullllllService.LOGGER.warn("Could not patch Bytecode#getOpcodeName", t);
                    return null;
                }
            }
        };
        instrumentation.addTransformer(transformer, true);
        try {
            instrumentation.retransformClasses(bytecode);
        } finally {
            instrumentation.removeTransformer(transformer);
        }
        SlothfullllllService.LOGGER.info(patched[0] ? "Mixin opcode name lookup cached" : "Mixin opcode name lookup left as is, Bytecode#getOpcodeName does not have the expected shape");
    }

    static byte[] patch(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!method.name.equals("getOpcodeName") || !method.desc.equals(DESC) || (method.access & Opcodes.ACC_STATIC) == 0) {
                continue;
            }
            InsnList body = new InsnList();
            body.add(new VarInsnNode(Opcodes.ILOAD, 0));
            body.add(new VarInsnNode(Opcodes.ALOAD, 1));
            body.add(new VarInsnNode(Opcodes.ILOAD, 2));
            body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "name", DESC, false));
            body.add(new InsnNode(Opcodes.ARETURN));
            method.instructions = body;
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            method.maxStack = 3;
            method.maxLocals = 3;
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }
        return null;
    }

    private static byte[] hookBytes() throws IOException {
        ClassWriter writer = new ClassWriter(0);
        try (InputStream in = MixinOpcodeNamesFix.class.getResourceAsStream("OpcodeNamesHook.class")) {
            if (in == null) {
                throw new IOException("Missing OpcodeNamesHook.class");
            }
            new ClassReader(in.readAllBytes()).accept(new ClassRemapper(writer, new SimpleRemapper(TEMPLATE, HOOK)), 0);
        }
        return writer.toByteArray();
    }
}
