package com.larrydevincarter.optionscanner.models.dtos;

import lombok.Data;

import java.util.List;

@Data
public class StockCandidatesRequestDto {
    private int holdStreak = 0;
    private double remainingLiquidity;
    private List<String> excludedTickers = List.of();
}