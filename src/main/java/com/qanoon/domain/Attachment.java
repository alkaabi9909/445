package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** مرفق مرتبط بأي كيان (ملف مالي، قضية، تنفيذ، استشارة، أرشيف، دفعة). */
@Getter
@Setter
@Entity
@Table(name = "attachments", indexes = @Index(name = "ix_att_entity", columnList = "entity_type,entity_id"))
public class Attachment extends BaseEntity {
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size")
    private long fileSize;

    /** تصنيف المستند: مطالبة، إيصال، حكم، إنذار، شهادة... */
    @Column(length = 60)
    private String category;

    @Column(length = 300)
    private String description;

    /** المرفق الأصلي الذي نُسخ منه عند التصعيد */
    @Column(name = "copied_from_id")
    private Long copiedFromId;
}
