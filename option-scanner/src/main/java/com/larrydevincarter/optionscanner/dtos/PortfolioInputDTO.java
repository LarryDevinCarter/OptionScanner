package com.larrydevincarter.optionscanner.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PortfolioInputDTO {

    @NotBlank(message = "Ticker cannot be empty")
    private String ticker;
    @Min(value = 1, message = "Shares must be great than 0")
    private int shares;
    @Min(value = 0, message = "Cost basis must be non-negative")
    private double costBasis;

    private String acquisitionDate; //YEAR-MN-DY Format
}
