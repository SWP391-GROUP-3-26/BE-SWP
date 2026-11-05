package com.swp391.beswp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Voucher", schema = "dbo")
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Voucher_ID")
    private Integer id;

    @Column(name = "Code", nullable = false, length = 50)
    private String code;

    @Column(name = "Discount_Type", nullable = false, length = 20)
    private String discountType;

    @Column(name = "Discount_Value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "Start_Date", nullable = false)
    private LocalDate startDate;

    @Column(name = "End_Date", nullable = false)
    private LocalDate endDate;
}
