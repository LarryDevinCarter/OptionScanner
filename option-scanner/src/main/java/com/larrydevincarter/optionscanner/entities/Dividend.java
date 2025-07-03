package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividends",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "ex_dividend_date"}))
@Data
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "ex_dividend_date", nullable = false)
    private LocalDate exDividendDate;

    @Column(name = "declaration_date")
    private LocalDate declarationDate;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", referencedColumnName = "symbol", insertable = false, updatable = false)
    private Asset asset;
}