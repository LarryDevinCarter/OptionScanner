package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class PutOpportunity {

    @Id
    private String ticker;
    private double strike;
    private double premium;
    private double pop;

    public PutOpportunity() {
    }

    public PutOpportunity(String ticker, double strike, double premium, double pop) {
        this.ticker = ticker;
        this.strike = strike;
        this.premium = premium;
        this.pop = pop;
    }

    @Override
    public String toString() {
        return  String.format("%s: Strike $%.2f, Premium $%.2f, PoP %.2f%%", ticker, strike, premium, pop * 100);
    }
}
