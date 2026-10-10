package am.pulsar.tax;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Ежедневные копии папки данных: {dir}/backups/ГГГГ-ММ-ДД/, хранятся последние KEEP дней. */
final class Backups {

    static final int KEEP = 14;

    private Backups() {
    }

    /** Делает копию за сегодня, если её ещё нет, и удаляет старые. Возвращает папку копии. */
    static Path run(Path dir, LocalDate today) throws IOException {
        Path root = dir.resolve("backups");
        Path target = root.resolve(today.toString());
        if (!Files.exists(target)) {
            Path tmp = root.resolve(today + ".tmp");
            deleteTree(tmp);
            copy(dir.resolve("users.json"), tmp.resolve("users.json"));
            Path users = dir.resolve("users");
            if (Files.isDirectory(users)) {
                try (Stream<Path> files = Files.list(users)) {
                    for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                        copy(f, tmp.resolve("users").resolve(f.getFileName()));
                    }
                }
            }
            if (Files.exists(tmp)) Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        }
        prune(root);
        return target;
    }

    private static void copy(Path from, Path to) throws IOException {
        if (!Files.exists(from)) return;
        Files.createDirectories(to.getParent());
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void prune(Path root) throws IOException {
        if (!Files.isDirectory(root)) return;
        List<Path> days;
        try (Stream<Path> s = Files.list(root)) {
            days = s.filter(p -> p.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}"))
                    .sorted(Comparator.reverseOrder()).toList();
        }
        for (Path old : days.subList(Math.min(KEEP, days.size()), days.size())) deleteTree(old);
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.delete(f);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
