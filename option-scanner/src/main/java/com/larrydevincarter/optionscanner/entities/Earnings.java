package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "earnings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "fiscal_date_ending", "report_type"}))
@Data
public class Earnings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "fiscal_date_ending", nullable = false)
    private LocalDate fiscalDateEnding;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "reported_eps")
    private Double reportedEPS;

    // The next 4 fields are for quarterly reports only
    @Column(name = "reported_date")
    private LocalDate reportedDate;

    @Column(name = "estimated_eps")
    private Double estimatedEPS;

    @Column(name = "surprise")
    private Double surprise;

    @Column(name = "surprise_percentage")
    private Double surprisePercentage;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", referencedColumnName = "symbol", insertable = false, updatable = false)
    private Asset asset;
}