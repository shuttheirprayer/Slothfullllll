package com.misanthropy.slothfullllll.boot;

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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

final class LambdaFormPins {
    private static final String FORM = "java/lang/invoke/LambdaForm";
    private static final String HOOK = "java/lang/invoke/SlothfullllllLambdaFormPins";
    private static final String TEMPLATE = "com/misanthropy/slothfullllll/boot/LambdaFormPinsHook";

    private LambdaFormPins() {
    }

    static void apply() throws Throwable {
        Class<?> form = Class.forName(FORM.replace('/', '.'), false, null);
        Instrumentation instrumentation = ModuleGraphFix.instrumentation();
        if (!instrumentation.isRetransformClassesSupported()) {
            SlothfullllllService.LOGGER.info("Lambda form pins skipped, this JVM cannot retransform classes");
            return;
        }
        try {
            Class.forName(HOOK.replace('/', '.'), false, null);
        } catch (ClassNotFoundException e) {
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(ModuleGraphFix.class.getResourceAsStream("LambdaFormPinsHook.class").readAllBytes())
                    .accept(new ClassRemapper(writer, new SimpleRemapper(TEMPLATE, HOOK)), 0);
            ModuleGraphFix.implLookup().in(form).defineClass(writer.toByteArray());
        }
        boolean[] patched = new boolean[1];
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
                if (redefined != form) {
                    return null;
                }
                try {
                    byte[] out = patch(bytes);
                    patched[0] = out != null;
                    return out;
                } catch (Throwable t) {
                    SlothfullllllService.LOGGER.warn("Could not patch LambdaForm#compileToBytecode", t);
                    return null;
                }
            }
        };
        instrumentation.addTransformer(transformer, true);
        try {
            instrumentation.retransformClasses(form);
        } finally {
            instrumentation.removeTransformer(transformer);
        }
        SlothfullllllService.LOGGER.info(patched[0] ? "Lambda form pins installed" : "Lambda form pins skipped, LambdaForm#compileToBytecode does not have the expected shape");
    }

    static byte[] patch(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!method.name.equals("compileToBytecode") || !method.desc.equals("()V")) {
                continue;
            }
            FieldInsnNode store = null;
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD && field.owner.equals(FORM)
                        && field.name.equals("isCompiled") && field.desc.equals("Z")) {
                    if (store != null) {
                        return null;
                    }
                    store = field;
                }
            }
            if (store == null) {
                return null;
            }
            LabelNode skip = new LabelNode();
            InsnList add = new InsnList();
            add.add(new VarInsnNode(Opcodes.ALOAD, 0));
            add.add(new FieldInsnNode(Opcodes.GETFIELD, FORM, "customized", "Ljava/lang/invoke/MethodHandle;"));
            add.add(new JumpInsnNode(Opcodes.IFNONNULL, skip));
            add.add(new VarInsnNode(Opcodes.ALOAD, 0));
            add.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "pin", "(Ljava/lang/Object;)V", false));
            add.add(skip);
            add.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(store, add);
            method.maxStack = Math.max(method.maxStack, 2);
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }
        return null;
    }
}
