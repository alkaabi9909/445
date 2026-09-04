package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** إعداد عام قابل للضبط من شاشة الإعدادات. */
@Getter
@Setter
@Entity
@Table(name = "system_settings")
public class SystemSetting extends BaseEntity {
    @Column(name = "setting_key", nullable = false, unique = true, length = 80)
    private String settingKey;

    @Column(name = "setting_value", length = 500)
    private String settingValue;

    @Column(name = "name_ar", nullable = false, length = 200)
    private String nameAr;

    /** TEXT, NUMBER, BOOLEAN */
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType = "TEXT";

    @Column(length = 500)
    private String description;

    @Column(name = "setting_group", length = 60)
    private String settingGroup;
}
