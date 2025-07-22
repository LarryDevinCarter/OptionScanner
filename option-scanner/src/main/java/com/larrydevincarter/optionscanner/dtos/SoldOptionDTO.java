package com.larrydevincarter.optionscanner.dtos;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SoldOptionDTO {
    @NotNull
    private String underlyingSymbol;
    @NotNull
    private double strikePrice;
    @FutureOrPresent
    private LocalDate expirationDate;
    @NotNull
    private String optionType;
    @PastOrPresent
    private LocalDate soldDate;
    @NotNull
    private double soldPrice;
}