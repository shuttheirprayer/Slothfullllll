package com.misanthropy.slothfullllll.boot;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

final class Config {
    static final Path FILE = Path.of("config", "slothfullllll.properties");
    private static final List<String> KEYS = List.of(
            "module_graph", "mixin_config_lookup", "mixin_opcode_names", "nested_jar_cache", "forge_pack_listing",
            "jar_packages", "union_fs_open", "parallel_mod_scan", "mixin_target_index", "skip_bytecode_verification", "class_prefetch",
            "kiwi", "toomanyplayers", "jitl", "iceandfire", "nuclearcraft", "prism");
    private static final Properties VALUES = load();

    private Config() {
    }

    static boolean enabled(String key) {
        return !"false".equalsIgnoreCase(VALUES.getProperty(key, "true").trim());
    }

    static synchronized void disable(String key) {
        VALUES.setProperty(key, "false");
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            VALUES.store(writer, "Slothfullllll features. Set a key to false to turn that feature off.");
        } catch (IOException e) {
            SlothfullllllService.LOGGER.warn("Could not write {}", FILE, e);
        }
    }

    private static Properties load() {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            values.load(reader);
        } catch (IOException ignored) {
        }
        boolean missing = false;
        for (String key : KEYS) {
            if (!values.containsKey(key)) {
                values.setProperty(key, "true");
                missing = true;
            }
        }
        if (missing) {
            try {
                Files.createDirectories(FILE.getParent());
                try (Writer writer = Files.newBufferedWriter(FILE)) {
                    values.store(writer, "Slothfullllll features. Set a key to false to turn that feature off.");
                }
            } catch (IOException e) {
                SlothfullllllService.LOGGER.warn("Could not write {}", FILE, e);
            }
        }
        return values;
    }
}
