package com.qanoon.common;

import com.qanoon.domain.Attachment;
import com.qanoon.repo.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** تخزين المرفقات على قرص الخادم مع حماية من المسارات الخطرة والامتدادات التنفيذية. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final long MAX_SIZE = 25L * 1024 * 1024;

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "js", "sh", "msi", "ps1", "com", "scr", "vbs", "jar", "dll");

    private final AttachmentRepository attachmentRepository;

    @Value("${qanoon.storage-path:./data/uploads}")
    private String storagePath;

    private Path root() {
        Path p = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new BusinessException("تعذّر تجهيز مجلد المرفقات: " + e.getMessage());
        }
        return p;
    }

    @Transactional
    public Attachment store(MultipartFile f, String entityType, Long entityId, String category, String description) {
        if (f == null || f.isEmpty()) {
            throw new BusinessException("لم يتم اختيار ملف للرفع");
        }
        if (f.getSize() > MAX_SIZE) {
            throw new BusinessException("حجم الملف يتجاوز الحد المسموح (٢٥ ميجابايت)");
        }
        String original = sanitizeName(f.getOriginalFilename());
        String ext = extensionOf(original);
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("نوع الملف غير مسموح به لأسباب أمنية: ." + ext);
        }

        String stored = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
        Path target = root().resolve(stored).normalize();
        if (!target.startsWith(root())) {
            throw new BusinessException("مسار الملف غير صالح");
        }
        try (var in = f.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("تعذّر حفظ الملف: " + e.getMessage());
        }

        Attachment a = new Attachment();
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setFileName(original);
        a.setStoredName(stored);
        a.setContentType(f.getContentType());
        a.setFileSize(f.getSize());
        a.setCategory(category);
        a.setDescription(description);
        return attachmentRepository.save(a);
    }

    @Transactional(readOnly = true)
    public List<Attachment> list(String entityType, Long entityId) {
        return attachmentRepository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public Attachment get(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("المرفق غير موجود"));
    }

    @Transactional(readOnly = true)
    public Resource load(Long attachmentId) {
        Attachment a = get(attachmentId);
        Path p = root().resolve(a.getStoredName()).normalize();
        if (!p.startsWith(root()) || !Files.exists(p)) {
            throw new NotFoundException("ملف المرفق غير موجود على الخادم");
        }
        return new PathResource(p);
    }

    @Transactional
    public void delete(Long attachmentId) {
        Attachment a = get(attachmentId);
        try {
            Files.deleteIfExists(root().resolve(a.getStoredName()).normalize());
        } catch (IOException e) {
            log.warn("تعذّر حذف ملف المرفق {}: {}", a.getStoredName(), e.getMessage());
        }
        attachmentRepository.delete(a);
    }

    /**
     * ينسخ كل مرفقات كيان إلى كيان آخر — يُستخدم عند تصعيد الملف المالي لقضية
     * وعند تحويل القضية لملف تنفيذ، حتى لا يُعاد إدخال أي مستند.
     */
    @Transactional
    public List<Attachment> copyAll(String fromType, Long fromId, String toType, Long toId) {
        List<Attachment> copies = new ArrayList<>();
        for (Attachment src : attachmentRepository.findByEntityTypeAndEntityId(fromType, fromId)) {
            String ext = extensionOf(src.getStoredName());
            String stored = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
            Path from = root().resolve(src.getStoredName()).normalize();
            Path to = root().resolve(stored).normalize();
            try {
                if (Files.exists(from)) {
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                log.warn("تعذّر نسخ المرفق {}: {}", src.getStoredName(), e.getMessage());
                continue;
            }
            Attachment c = new Attachment();
            c.setEntityType(toType);
            c.setEntityId(toId);
            c.setFileName(src.getFileName());
            c.setStoredName(stored);
            c.setContentType(src.getContentType());
            c.setFileSize(src.getFileSize());
            c.setCategory(src.getCategory());
            c.setDescription(src.getDescription());
            c.setCopiedFromId(src.getId());
            copies.add(attachmentRepository.save(c));
        }
        return copies;
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "مستند";
        }
        String base = Paths.get(name).getFileName().toString();
        return base.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
