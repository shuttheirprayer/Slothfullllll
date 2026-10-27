package com.misanthropy.slothfullllll.boot;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.Opcodes;

public final class OpcodeNamesHook {
    private static final Map<String, Map<Integer, String>> TABLES = new ConcurrentHashMap<>();

    private OpcodeNamesHook() {
    }

    public static String name(int opcode, String start, int min) {
        if (opcode >= min) {
            String name = TABLES.computeIfAbsent(start, OpcodeNamesHook::table).get(opcode);
            if (name != null) {
                return name;
            }
        }
        return opcode >= 0 ? String.valueOf(opcode) : "UNKNOWN";
    }

    private static Map<Integer, String> table(String start) {
        Map<Integer, String> names = new HashMap<>();
        boolean found = false;
        try {
            for (Field field : Opcodes.class.getDeclaredFields()) {
                if (found || field.getName().equals(start)) {
                    found = true;
                    if (field.getType() == int.class) {
                        names.putIfAbsent(field.getInt(null), field.getName());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return names;
    }
}
