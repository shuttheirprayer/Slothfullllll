package com.misanthropy.slothfullllll.boot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;

public final class NestedModLocator extends AbstractJarFileModLocator {
    private static final String NESTED = "META-INF/jars/slothfullllll-mod.jar";

    @Override
    public Stream<Path> scanCandidates() {
        try {
            Path self = Path.of(NestedModLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            byte[] bytes = Files.readAllBytes(self.resolve(NESTED));
            CRC32 crc = new CRC32();
            crc.update(bytes);
            Path dir = FMLPaths.GAMEDIR.get().resolve(".slothfullllll");
            Path out = dir.resolve("slothfullllll-mod-" + Long.toHexString(crc.getValue()) + ".jar");
            if (!Files.isRegularFile(out) || Files.size(out) != bytes.length) {
                Files.createDirectories(dir);
                Files.write(out, bytes);
            }
            try (Stream<Path> old = Files.list(dir)) {
                old.filter(path -> !path.equals(out)).forEach(path -> path.toFile().delete());
            }
            return Stream.of(out);
        } catch (Exception e) {
            SlothfullllllService.LOGGER.error("Could not unpack the Slothfullllll mod part, its mixins stay off", e);
            return Stream.empty();
        }
    }

    @Override
    public String name() {
        return "slothfullllll";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }
}
