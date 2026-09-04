package com.qanoon.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** عدّاد أرقام المستندات لكل نوع وسنة: مالي، قضية، تنفيذ، استشارة، إيصال، شهادة، أرشيف. */
@Getter
@Setter
@Entity
@Table(name = "number_sequences", uniqueConstraints =
        @UniqueConstraint(name = "uq_seq", columnNames = {"seq_key", "seq_year"}))
public class NumberSequence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seq_key", nullable = false, length = 30)
    private String seqKey;

    @Column(name = "seq_year", nullable = false)
    private int seqYear;

    @Column(name = "last_value", nullable = false)
    private long lastValue;

    @Column(nullable = false, length = 10)
    private String prefix;
}
