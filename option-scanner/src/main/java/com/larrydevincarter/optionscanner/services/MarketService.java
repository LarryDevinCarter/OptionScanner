package com.larrydevincarter.optionscanner.services;

import com.larrydevincarter.optionscanner.models.dtos.MarketStatusDto;

import java.time.LocalDate;
import java.util.Set;

public interface MarketService {
    MarketStatusDto getMarketStatus();

    Set<LocalDate> getTradingDays();
}