package com.larrydevincarter.optionscanner.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface OptionService {
    void processOptionsForSymbol(String symbol, List<String> errorLog, Set<LocalDate> tradingDays, LocalDate previousTradingDay);
}