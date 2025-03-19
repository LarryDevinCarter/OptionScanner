package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallOpportunity {

    @Id
    private String ticker;
    private double strike;
    private double premium;
    private double pop;

    @Override
    public String toString() {
        return String.format("%s: Strike $%.2f, Premium $%.2f, PoP %.2f%%", ticker, strike, premium, pop * 100);
    }
}
