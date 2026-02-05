package com.larrydevincarter.optionscanner.services;

import com.larrydevincarter.optionscanner.models.dtos.SoldOptionDto;

import java.util.List;

public interface ReportService {

    void generateReport(Double strikeMax);

    void generateSoldOptionsReports(List<SoldOptionDto> dtos);

    void generateCoveredCallsReport(String symbol, Double dollarCostAverage);
}
