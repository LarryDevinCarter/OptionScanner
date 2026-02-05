package com.larrydevincarter.optionscanner.models.dtos;

import lombok.Data;

import java.util.List;

@Data
public class StockCandidatesRequestDto {
    private String type = "puts";
    private String quality = "high";
    private int holdStreak = 0;
    private int limit = 20;
    private double remainingLiquidity;
    private List<String> excludedTickers = List.of();
}