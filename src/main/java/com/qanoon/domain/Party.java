package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * طرف في الملفات: موكل (client) أو مدين/خصم (debtor).
 * جدول واحد مع حقل نوع الطرف لتسهيل البحث والربط.
 */
@Getter
@Setter
@Entity
@Table(name = "parties")
public class Party extends BaseEntity {
    public enum Kind { CLIENT, DEBTOR }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 20)
    private Enums.PartyType partyType = Enums.PartyType.INDIVIDUAL;

    @Column(name = "id_number", length = 50)
    private String idNumber;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 300)
    private String address;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;
}
