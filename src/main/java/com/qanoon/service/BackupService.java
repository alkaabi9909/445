package com.qanoon.service;

import com.qanoon.common.AuditService;
import com.qanoon.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** النسخ الاحتياطي: تلقائي يومي، مع إمكانية نسخ فوري يدوي. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final AuditService auditService;

    @Value("${qanoon.backup-path:./data/backups}")
    private String backupPath;

    @Value("${qanoon.storage-path:./data/uploads}")
    private String storagePath;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    public record BackupResult(String path, long sizeBytes, LocalDateTime at, String trigger) {}

    public BackupResult runBackup(String trigger) {
        LocalDateTime now = LocalDateTime.now();
        Path root = Paths.get(backupPath).toAbsolutePath().normalize();
        Path target = root.resolve("backup_" + now.format(STAMP));
        try {
            Files.createDirectories(target);

            // (١) نسخ المرفقات
            Path uploads = Paths.get(storagePath).toAbsolutePath().normalize();
            if (Files.exists(uploads)) {
                copyTree(uploads, target.resolve("uploads"));
            }

            // (٢) قاعدة البيانات
            if (datasourceUrl != null && datasourceUrl.startsWith("jdbc:h2:file:")) {
                copyH2(target);
            } else {
                writeSqlServerInstructions(target, now);
            }

            long size = folderSize(target);
            auditService.log("BACKUP", "Backup", null, target.getFileName().toString(),
                    "نسخة احتياطية (" + trigger + ") — الحجم " + humanSize(size));
            return new BackupResult(target.toString(), size, now, trigger);
        } catch (IOException e) {
            log.error("فشل النسخ الاحتياطي", e);
            throw new BusinessException("تعذّر إنشاء النسخة الاحتياطية: " + e.getMessage());
        }
    }

    public List<BackupResult> list() {
        Path root = Paths.get(backupPath).toAbsolutePath().normalize();
        List<BackupResult> out = new ArrayList<>();
        if (!Files.exists(root)) {
            return out;
        }
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(d -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(d, BasicFileAttributes.class);
                    out.add(new BackupResult(d.toString(), folderSize(d),
                            LocalDateTime.ofInstant(attrs.creationTime().toInstant(),
                                    java.time.ZoneId.systemDefault()),
                            "—"));
                } catch (IOException ignored) {
                    // نسخة تالفة أو قيد الكتابة: تُتجاهل في القائمة
                }
            });
        } catch (IOException e) {
            log.warn("تعذّرت قراءة مجلد النسخ الاحتياطية: {}", e.getMessage());
        }
        out.sort(Comparator.comparing(BackupResult::at).reversed());
        return out;
    }

    // ---------- أدوات ----------

    private void copyH2(Path target) throws IOException {
        // jdbc:h2:file:./data/qanoon-dev;OPTIONS...
        String path = datasourceUrl.substring("jdbc:h2:file:".length());
        int semi = path.indexOf(';');
        if (semi > 0) {
            path = path.substring(0, semi);
        }
        Path db = Paths.get(path + ".mv.db").toAbsolutePath().normalize();
        if (Files.exists(db)) {
            Files.copy(db, target.resolve(db.getFileName().toString()),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * لا نُنفّذ أمر النسخ على SQL Server تلقائياً — نكتب التعليمات
     * ليُشغّلها مسؤول القاعدة بصلاحياته.
     */
    private void writeSqlServerInstructions(Path target, LocalDateTime now) throws IOException {
        String file = target.resolve("QanoonERP_" + now.format(STAMP) + ".bak")
                .toString().replace("\\", "\\\\");
        String script = """
                -- نسخة احتياطية لقاعدة بيانات نظام قانون
                -- شغّل هذا السكربت من SSMS أو sqlcmd بحساب يملك صلاحية النسخ الاحتياطي.
                -- تاريخ الطلب: %s

                BACKUP DATABASE [QanoonERP]
                TO DISK = N'%s'
                WITH FORMAT, INIT, NAME = N'QanoonERP - نسخة كاملة', COMPRESSION, STATS = 10;
                GO
                """.formatted(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), file);
        Files.writeString(target.resolve("استعد-النسخة.sql"), script, StandardCharsets.UTF_8);
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static long folderSize(Path p) {
        try (Stream<Path> walk = Files.walk(p)) {
            return walk.filter(Files::isRegularFile).mapToLong(f -> {
                try {
                    return Files.size(f);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " بايت";
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024.0) + " كيلوبايت";
        return Math.round(bytes / (1024.0 * 1024.0)) + " ميجابايت";
    }
}
