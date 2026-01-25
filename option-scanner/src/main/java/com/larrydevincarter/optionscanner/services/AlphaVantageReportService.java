package com.larrydevincarter.optionscanner.services;

import java.util.List;
import java.util.Map;

public interface AlphaVantageReportService {
    List<String> getSymbolsNeedingUpdate(List<String> symbols);
    void processReport(String symbol, Map<String, Object> responseBody, List<String> errorLog);
    List<String> getSymbolsThatHaveData(List<String> symbols);
    String getFunctionName();
    String getReportDisplayName();
}