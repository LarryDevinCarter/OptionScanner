package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class StockOverview {

    @Id
    private String ticker;
    private double peRatio;
    private double eps;
    private double dividendYield;
    private double marketCap;
    private double bookValue;
    private LocalDateTime lastUpdated;

//    public StockOverview() {}
//
//    public String getTicker() {
//        return ticker;
//    }
//
//    public double getPeRatio() {
//        return peRatio;
//    }
//
//    public double getEps() {
//        return eps;
//    }
//
//    public double getDividendYield() {
//        return dividendYield;
//    }
//
//    public void setTicker(String ticker) {
//        this.ticker = ticker;
//    }
//
//    public void setPeRatio(double peRatio) {
//        this.peRatio = peRatio;
//    }
//
//    public void setEps(double eps) {
//        this.eps = eps;
//    }
//
//    public void setDividendYield(double dividendYield) {
//        this.dividendYield = dividendYield;
//    }
//
//    public void setMarketCap(double marketCap) {
//        this.marketCap = marketCap;
//    }
//
//    public void setBookValue(double bookValue) {
//        this.bookValue = bookValue;
//    }
//
//    public void setLastUpdated(LocalDateTime lastUpdated) {
//        this.lastUpdated = lastUpdated;
//    }
}
