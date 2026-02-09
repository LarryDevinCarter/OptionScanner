package com.larrydevincarter.optionscanner.models.dtos;

import lombok.Data;

@Data
public class OptionBatchRequestDto {
    private String ticker;
    private Double currentPrice;
    private Double costBasis;
}