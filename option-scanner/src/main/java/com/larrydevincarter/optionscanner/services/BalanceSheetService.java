package com.larrydevincarter.optionscanner.services;

import java.util.List;
import java.util.Map;

public interface BalanceSheetService {

    List<String> getSymbolsNeedingUpdate();

    void processBalanceSheets(String symbol, Map<String, Object> responseBody, List<String> errorLog);
}
