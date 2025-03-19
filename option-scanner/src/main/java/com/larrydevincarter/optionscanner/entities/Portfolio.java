package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class Portfolio {

    @Id
    private String ticker;
    private int shares;
    private double costBasis;
    private LocalDateTime acquisitionDate;

}
