package com.misanthropy.slothfullllll.boot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

public final class NestedJarHook {
    private static final long STALE_MILLIS = TimeUnit.DAYS.toMillis(14);
    private static final long TOUCH_MILLIS = TimeUnit.DAYS.toMillis(1);
    private static final Path DIR = prepare();

    private NestedJarHook() {
    }

    public static SeekableByteChannel channel(Path path, OpenOption[] options) throws IOException {
        if (DIR == null || path.getFileSystem() == FileSystems.getDefault() || !readOnly(options)) {
            return Files.newByteChannel(path, options);
        }
        try {
            long expected = Files.size(path);
            Path fileName = path.getFileName();
            String name = fileName == null ? "jar" : fileName.toString().replaceAll("[^A-Za-z0-9._-]", "_");
            Path cached = DIR.resolve(key(path, expected) + "-" + name);
            if (!Files.isRegularFile(cached) || Files.size(cached) != expected) {
                Path temp = Files.createTempFile(DIR, "nested", ".tmp");
                try {
                    Files.copy(path, temp, StandardCopyOption.REPLACE_EXISTING);
                    if (Files.size(temp) != expected) {
                        return Files.newByteChannel(path, options);
                    }
                    try {
                        Files.move(temp, cached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        if (!Files.isRegularFile(cached) || Files.size(cached) != expected) {
                            throw e;
                        }
                    }
                } finally {
                    Files.deleteIfExists(temp);
                }
            } else {
                long now = System.currentTimeMillis();
                if (now - Files.getLastModifiedTime(cached).toMillis() > TOUCH_MILLIS) {
                    Files.setLastModifiedTime(cached, FileTime.fromMillis(now));
                }
            }
            return FileChannel.open(cached, StandardOpenOption.READ);
        } catch (IOException | RuntimeException | NoSuchAlgorithmException e) {
            return Files.newByteChannel(path, options);
        }
    }

    private static String key(Path path, long size) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        Path outer = outerFile(path);
        if (outer != null) {
            String identity = path.toUri().toString().replaceAll("(%23|#)[0-9]+!", "!") + "|" + size + "|" + Files.getLastModifiedTime(path).toMillis()
                    + "|" + outer.toAbsolutePath() + "|" + Files.size(outer) + "|" + Files.getLastModifiedTime(outer).toMillis();
            digest.update(identity.getBytes(StandardCharsets.UTF_8));
            return "m" + HexFormat.of().formatHex(digest.digest());
        }
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path outerFile(Path path) {
        try {
            String location = path.toUri().getSchemeSpecificPart();
            int end = location.length();
            int hash = location.indexOf('#');
            int bang = location.indexOf('!');
            if (hash >= 0) {
                end = Math.min(end, hash);
            }
            if (bang >= 0) {
                end = Math.min(end, bang);
            }
            String file = location.substring(0, end);
            if (file.startsWith("file:")) {
                file = file.substring(5);
            }
            if (file.length() > 2 && file.charAt(0) == '/' && file.charAt(2) == ':') {
                file = file.substring(1);
            }
            Path outer = Path.of(file);
            if (outer.isAbsolute() && Files.isRegularFile(outer)) {
                return outer;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static boolean readOnly(OpenOption[] options) {
        for (OpenOption option : options) {
            if (option != StandardOpenOption.READ) {
                return false;
            }
        }
        return true;
    }

    private static Path prepare() {
        try {
            Path dir = Path.of(".slothfullllll", "nested").toAbsolutePath();
            Files.createDirectories(dir);
            long now = System.currentTimeMillis();
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
                for (Path file : files) {
                    try {
                        if (now - Files.getLastModifiedTime(file).toMillis() > STALE_MILLIS) {
                            Files.deleteIfExists(file);
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
            return dir;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
