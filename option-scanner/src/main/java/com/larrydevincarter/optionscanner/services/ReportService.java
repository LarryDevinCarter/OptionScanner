package com.larrydevincarter.optionscanner.services;

import com.larrydevincarter.optionscanner.dtos.SoldOptionDTO;

import java.io.IOException;
import java.util.List;

public interface ReportService {

    void generateReport(Double strikeMax);

    void generateSoldOptionsReports(List<SoldOptionDTO> dtos);
}
