package com.misanthropy.slothfullllll.boot;

import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.nio.file.Files;
import java.nio.file.Path;
import sun.misc.Unsafe;

final class VerifierSkip {
    private static final String FLAG = "BytecodeVerificationRemote";

    private VerifierSkip() {
    }

    static void apply() {
        Path dll = Path.of(System.getProperty("java.home"), "bin", "server", "jvm.dll");
        if (!Files.isRegularFile(dll)) {
            dll = Path.of(System.getProperty("java.home"), "lib", "server", "libjvm.so");
        }
        if (!Files.isRegularFile(dll)) {
            SlothfullllllService.LOGGER.info("Class verification skip left off, no HotSpot server VM found");
            return;
        }
        NativeLibrary jvm = NativeLibrary.getInstance(dll.toString());
        Unsafe u = Unsafes.UNSAFE;
        long structs = u.getAddress(symbol(jvm, "gHotSpotVMStructs"));
        long typeName = u.getLong(symbol(jvm, "gHotSpotVMStructEntryTypeNameOffset"));
        long fieldName = u.getLong(symbol(jvm, "gHotSpotVMStructEntryFieldNameOffset"));
        long offset = u.getLong(symbol(jvm, "gHotSpotVMStructEntryOffsetOffset"));
        long address = u.getLong(symbol(jvm, "gHotSpotVMStructEntryAddressOffset"));
        long stride = u.getLong(symbol(jvm, "gHotSpotVMStructEntryArrayStride"));
        long types = u.getAddress(symbol(jvm, "gHotSpotVMTypes"));
        long typeEntryName = u.getLong(symbol(jvm, "gHotSpotVMTypeEntryTypeNameOffset"));
        long typeEntrySize = u.getLong(symbol(jvm, "gHotSpotVMTypeEntrySizeOffset"));
        long typeStride = u.getLong(symbol(jvm, "gHotSpotVMTypeEntryArrayStride"));

        long flags = 0, count = 0, nameOffset = -1, valueOffset = -1, size = 0;
        for (long e = structs; ; e += stride) {
            String type = string(u, u.getAddress(e + typeName));
            if (type == null) {
                break;
            }
            if (!type.equals("JVMFlag")) {
                continue;
            }
            switch (String.valueOf(string(u, u.getAddress(e + fieldName)))) {
                case "flags" -> flags = u.getAddress(e + address);
                case "numFlags" -> count = u.getAddress(e + address);
                case "_name" -> nameOffset = u.getLong(e + offset);
                case "_addr" -> valueOffset = u.getLong(e + offset);
                default -> {
                }
            }
        }
        for (long e = types; ; e += typeStride) {
            String type = string(u, u.getAddress(e + typeEntryName));
            if (type == null) {
                break;
            }
            if (type.equals("JVMFlag")) {
                size = u.getLong(e + typeEntrySize);
                break;
            }
        }
        if (flags == 0 || count == 0 || nameOffset < 0 || valueOffset < 0 || size <= 0) {
            SlothfullllllService.LOGGER.info("Class verification skip left off, this JVM does not describe its flags");
            return;
        }
        long base = u.getAddress(flags);
        long total = u.getLong(count);
        for (long i = 0; i < total; i++) {
            long flag = base + i * size;
            if (FLAG.equals(string(u, u.getAddress(flag + nameOffset)))) {
                long value = u.getAddress(flag + valueOffset);
                if (u.getByte(value) != 0) {
                    u.putByte(value, (byte) 0);
                    SlothfullllllService.LOGGER.info("Mod classes now load without bytecode verification");
                }
                return;
            }
        }
        SlothfullllllService.LOGGER.info("Class verification skip left off, {} not found", FLAG);
    }

    private static long symbol(NativeLibrary library, String name) {
        return Pointer.nativeValue(library.getGlobalVariableAddress(name));
    }

    private static String string(Unsafe u, long address) {
        if (address == 0) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (long i = address; ; i++) {
            byte b = u.getByte(i);
            if (b == 0) {
                return out.toString();
            }
            out.append((char) b);
        }
    }
}
