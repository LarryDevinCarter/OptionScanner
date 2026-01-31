package com.larrydevincarter.optionscanner.services;

import com.larrydevincarter.optionscanner.models.dtos.MarketStatusDto;

public interface MarketService {
    MarketStatusDto getMarketStatus();
}