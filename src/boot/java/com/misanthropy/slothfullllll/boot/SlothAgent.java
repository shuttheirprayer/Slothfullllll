package com.misanthropy.slothfullllll.boot;

import java.lang.instrument.Instrumentation;

public final class SlothAgent {
    public static volatile Instrumentation instrumentation;

    private SlothAgent() {
    }

    public static void agentmain(String args, Instrumentation inst) {
        instrumentation = inst;
    }
}
