package com.misanthropy.slothfullllll.boot;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

final class LoaderPatches {
    private static final String TEMPLATE_PACKAGE = "com/misanthropy/slothfullllll/boot/";
    private static final String PROVIDER = "cpw/mods/niofs/union/UnionFileSystemProvider";
    private static final String UNION_FS = "cpw/mods/niofs/union/UnionFileSystem";
    private static final String PROCESSOR = "org/spongepowered/asm/mixin/transformer/MixinProcessor";

    private LoaderPatches() {
    }

    static void jarPackages() throws Throwable {
        Class<?> jar = load("cpw.mods.jarhandling.impl.Jar");
        String hook = hook(jar, "PackagesHook");
        install(jar, "Jar package listing", bytes -> prologue(bytes, "getPackages", "()Ljava/util/Set;", hook, "prime"));
    }

    static void unionFsOpen() throws Throwable {
        Class<?> provider = load(PROVIDER.replace('/', '.'));
        String hook = hook(provider, "UnionFsHook");
        install(provider, "Unlocked union file system open", bytes -> provider(bytes, hook));
    }

    static void parallelModScan() throws Throwable {
        Class<?> locator = load("net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator");
        String hook = hook(locator, "ModScanHook");
        install(locator, "Parallel mod scan", bytes -> locator(bytes, hook));
    }

    static void mixinTargetIndex() throws Throwable {
        Class<?> processor = load(PROCESSOR.replace('/', '.'));
        Class<?> config = load("org.spongepowered.asm.mixin.transformer.MixinConfig");
        ModuleGraphFix.instrumentation().redefineModule(Object.class.getModule(), Set.of(), Map.of(),
                Map.of("java.util", Set.of(processor.getModule())), Set.of(), Map.of());
        String hook = hook(processor, "MixinIndexHook");
        if (install(config, "Mixin config change tracking", bytes -> mappingVersion(bytes, hook))) {
            install(processor, "Mixin target index", bytes -> configCandidates(bytes, hook));
        }
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, SlothfullllllService.class.getClassLoader());
    }

    private static String hook(Class<?> target, String template) throws Throwable {
        String name = target.getPackageName().replace('.', '/') + "/Slothfullllll" + template;
        try {
            Class.forName(name.replace('/', '.'), false, target.getClassLoader());
        } catch (ClassNotFoundException e) {
            ClassWriter writer = new ClassWriter(0);
            try (InputStream in = LoaderPatches.class.getResourceAsStream(template + ".class")) {
                if (in == null) {
                    throw new IOException("Missing " + template + ".class");
                }
                new ClassReader(in.readAllBytes()).accept(new ClassRemapper(writer, new SimpleRemapper(TEMPLATE_PACKAGE + template, name)), 0);
            }
            ModuleGraphFix.implLookup().in(target).defineClass(writer.toByteArray());
        }
        return name;
    }

    private static boolean install(Class<?> target, String label, UnaryOperator<byte[]> patch) throws Throwable {
        Instrumentation instrumentation = ModuleGraphFix.instrumentation();
        if (!instrumentation.isRetransformClassesSupported()) {
            return false;
        }
        boolean[] patched = new boolean[1];
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(Module module, ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
                if (redefined != target) {
                    return null;
                }
                try {
                    byte[] out = patch.apply(bytes);
                    patched[0] = out != null;
                    return out;
                } catch (Throwable t) {
                    SlothfullllllService.LOGGER.warn("Could not patch {}", target.getName(), t);
                    return null;
                }
            }
        };
        instrumentation.addTransformer(transformer, true);
        try {
            instrumentation.retransformClasses(target);
        } finally {
            instrumentation.removeTransformer(transformer);
        }
        SlothfullllllService.LOGGER.info(patched[0] ? "{} installed" : "{} left as is, {} does not have the expected shape", label, target.getName());
        return patched[0];
    }

    static byte[] prologue(byte[] original, String name, String desc, String hook, String call) {
        return rewrite(original, name, desc, mv -> new MethodVisitor(Opcodes.ASM9, mv) {
            @Override
            public void visitCode() {
                super.visitCode();
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, call, "(Ljava/lang/Object;)V", false);
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                super.visitMaxs(Math.max(maxStack, 1), maxLocals);
            }
        });
    }

    static byte[] configCandidates(byte[] original, String hook) {
        int[] count = new int[1];
        byte[] out = rewrite(original, "applyMixins",
                "(Lorg/spongepowered/asm/mixin/MixinEnvironment;Ljava/lang/String;Lorg/objectweb/asm/tree/ClassNode;)Z",
                mv -> new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        super.visitFieldInsn(opcode, owner, name, descriptor);
                        if (opcode == Opcodes.GETFIELD && owner.equals(PROCESSOR) && name.equals("configs")) {
                            count[0]++;
                            super.visitVarInsn(Opcodes.ALOAD, 2);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "candidates",
                                    "(Ljava/util/List;Ljava/lang/String;)Ljava/util/List;", false);
                        }
                    }

                    @Override
                    public void visitMaxs(int maxStack, int maxLocals) {
                        super.visitMaxs(maxStack + 1, maxLocals);
                    }
                });
        return count[0] == 2 ? out : null;
    }

    static byte[] mappingVersion(byte[] original, String hook) {
        int[] count = new int[1];
        byte[] out = rewrite(original, "mixinsFor", "(Ljava/lang/String;)Ljava/util/List;",
                mv -> new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (name.equals("put") && owner.equals("java/util/Map")) {
                            count[0]++;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "bump", "()V", false);
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                });
        return count[0] == 1 ? out : null;
    }

    static byte[] locator(byte[] original, String hook) {
        int[] count = new int[1];
        byte[] out = rewrite(original, "scanMods", "()Ljava/util/List;", mv -> new MethodVisitor(Opcodes.ASM9, mv) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == Opcodes.INVOKEINTERFACE && owner.equals("java/util/stream/Stream") && name.equals("map")
                        && descriptor.equals("(Ljava/util/function/Function;)Ljava/util/stream/Stream;")) {
                    count[0]++;
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "map",
                            "(Ljava/util/stream/Stream;Ljava/util/function/Function;)Ljava/util/stream/Stream;", false);
                    return;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        });
        return count[0] == 1 ? out : null;
    }

    static byte[] provider(byte[] original, String hook) {
        String desc = "(Ljava/lang/String;Ljava/util/function/BiPredicate;[Ljava/nio/file/Path;)L" + UNION_FS + ";";
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        boolean[] found = new boolean[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("newFileSystemInternal") || !descriptor.equals(desc)) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                found[0] = true;
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 3);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "normalize", "([Ljava/nio/file/Path;)[Ljava/nio/file/Path;", false);
                mv.visitVarInsn(Opcodes.ASTORE, 4);
                mv.visitTypeInsn(Opcodes.NEW, UNION_FS);
                mv.visitInsn(Opcodes.DUP);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitVarInsn(Opcodes.ALOAD, 4);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, UNION_FS, "<init>",
                        "(L" + PROVIDER + ";Ljava/util/function/BiPredicate;Ljava/lang/String;[Ljava/nio/file/Path;)V", false);
                mv.visitVarInsn(Opcodes.ASTORE, 5);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitFieldInsn(Opcodes.GETFIELD, PROVIDER, "fileSystems", "Ljava/util/Map;");
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitVarInsn(Opcodes.ALOAD, 5);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, hook, "register", "(Ljava/util/Map;Ljava/lang/String;Ljava/lang/Object;)V", false);
                mv.visitVarInsn(Opcodes.ALOAD, 5);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(6, 6);
                mv.visitEnd();
                return null;
            }
        }, 0);
        return found[0] ? writer.toByteArray() : null;
    }

    static byte[] rewrite(byte[] original, String name, String desc, UnaryOperator<MethodVisitor> wrap) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        boolean[] found = new boolean[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String n, String d, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, n, d, signature, exceptions);
                if (!n.equals(name) || !d.equals(desc)) {
                    return mv;
                }
                found[0] = true;
                return wrap.apply(mv);
            }
        }, 0);
        return found[0] ? writer.toByteArray() : null;
    }
}
