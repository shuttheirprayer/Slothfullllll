package com.misanthropy.slothfullllll.boot;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

final class Unsafes {
    static final Unsafe UNSAFE;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Unsafes() {
    }

    static long offset(Class<?> owner, String name) throws NoSuchFieldException {
        return UNSAFE.objectFieldOffset(owner.getDeclaredField(name));
    }

    static Object getStatic(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        return UNSAFE.getObject(UNSAFE.staticFieldBase(field), UNSAFE.staticFieldOffset(field));
    }
}
