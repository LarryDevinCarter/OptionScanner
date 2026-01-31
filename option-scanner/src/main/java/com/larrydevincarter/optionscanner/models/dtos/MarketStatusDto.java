package com.larrydevincarter.optionscanner.models.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketStatusDto {

    @JsonProperty("isTradingDay")
    private boolean isTradingDay;

    @JsonProperty("todayCloseTime")
    private String todayCloseTime;

}