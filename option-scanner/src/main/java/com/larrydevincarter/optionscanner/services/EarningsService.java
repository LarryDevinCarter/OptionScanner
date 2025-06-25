package com.larrydevincarter.optionscanner.services;

import java.util.List;
import java.util.Map;

public interface EarningsService {

    List<String> getSymbolsNeedingUpdate(List<String> symbols);

    void processEarnings(String symbol, Map<String, Object> response, List<String> errorLog);

    List<String> getSymbolsThatHaveStatements(List<String> symbols);
}
