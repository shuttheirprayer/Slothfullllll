package com.misanthropy.slothfullllll.boot;

public final class LambdaFormPinsHook {
    private static final int CAP = 50_000;
    private static final Object[] PINS = new Object[CAP];
    private static int next;

    private LambdaFormPinsHook() {
    }

    public static void pin(Object form) {
        synchronized (PINS) {
            if (next < CAP) {
                PINS[next++] = form;
            }
        }
    }
}
