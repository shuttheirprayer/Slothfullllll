package com.misanthropy.slothfullllll.boot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Stream;

public final class ModScanHook {
    private ModScanHook() {
    }

    public static Stream<Object> map(Stream<Object> source, Function<Object, Object> mapper) {
        List<Object> inputs = source.toList();
        int threads = Math.min(8, Math.min(inputs.size(), Runtime.getRuntime().availableProcessors()));
        if (threads < 2) {
            return inputs.stream().map(mapper);
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        ExecutorService pool = Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task, "Slothfullllll mod scan");
            thread.setDaemon(true);
            thread.setContextClassLoader(context);
            return thread;
        });
        try {
            List<Future<Object>> futures = new ArrayList<>(inputs.size());
            for (Object input : inputs) {
                futures.add(pool.submit(() -> mapper.apply(input)));
            }
            List<Object> out = new ArrayList<>(inputs.size());
            for (Future<Object> future : futures) {
                try {
                    out.add(future.get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    throw new RuntimeException(cause);
                }
            }
            return out.stream();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            pool.shutdownNow();
        }
    }
}
