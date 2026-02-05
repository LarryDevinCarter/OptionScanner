package com.larrydevincarter.optionscanner.models.dtos;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

/**
 * Data Transfer Object representing a sold options contract, capturing key details like strike price,
 * expiration date, and sale information.
 */
@Data
public class SoldOptionDto {
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