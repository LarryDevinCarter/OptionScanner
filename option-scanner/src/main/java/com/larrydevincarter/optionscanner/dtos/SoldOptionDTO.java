package com.larrydevincarter.optionscanner.dtos;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SoldOptionDTO {
    private String underlyingSymbol;
    private double strikePrice;
    private LocalDate expirationDate;
    private String optionType; // "put" or "call"
    private LocalDate soldDate;
    private double soldPrice;
}