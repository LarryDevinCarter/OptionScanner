package com.larrydevincarter.optionscanner.models.dtos;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class OwnedAssetDTO {

    @NotBlank(message = "Symbol is required")
    @Size(min = 1, max = 10)
    private String symbol;

    @NotNull(message = "Dollar cost average is required")
    @Positive(message = "Dollar cost average must be positive")
    private Double dollarCostAverage;
}