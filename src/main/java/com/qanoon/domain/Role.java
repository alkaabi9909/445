package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/** الدور الوظيفي مع مجموعة صلاحياته الدقيقة. */
@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name_ar", nullable = false, length = 100)
    private String nameAr;

    @Column(length = 300)
    private String description;

    /** الأدوار الأربعة الأساسية لا يمكن حذفها */
    @Column(name = "is_system", nullable = false)
    private boolean system;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", length = 50)
    private Set<String> permissions = new LinkedHashSet<>();

    public boolean has(String permission) { return permissions.contains(permission); }
}
