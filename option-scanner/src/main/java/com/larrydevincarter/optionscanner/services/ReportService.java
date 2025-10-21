package com.larrydevincarter.optionscanner.services;

import com.larrydevincarter.optionscanner.models.dtos.SoldOptionDTO;

import java.util.List;

public interface ReportService {

    void generateReport(Double strikeMax);

    void generateSoldOptionsReports(List<SoldOptionDTO> dtos);
}
