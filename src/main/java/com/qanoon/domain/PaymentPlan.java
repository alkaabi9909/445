package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** خطة التقسيط الموثقة بالتعهد. */
@Getter
@Setter
@Entity
@Table(name = "payment_plans")
public class PaymentPlan extends BaseEntity {
    @Column(name = "financial_file_id", nullable = false)
    private Long financialFileId;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "installments_count", nullable = false)
    private int installmentsCount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** الفاصل بين الأقساط بالأشهر */
    @Column(name = "interval_months", nullable = false)
    private int intervalMonths = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private Enums.PaymentMethod paymentMethod;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<Installment> installments = new ArrayList<>();
}
