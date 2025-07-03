package com.larrydevincarter.optionscanner.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface DividendService {
    void processDividends(String symbol, Map<String, Object> response, List<String> errorLog);
    List<String> getSymbolsNeedingUpdate(List<String> symbols);
    List<String> getSymbolsThatHaveDividends(List<String> symbols);
}