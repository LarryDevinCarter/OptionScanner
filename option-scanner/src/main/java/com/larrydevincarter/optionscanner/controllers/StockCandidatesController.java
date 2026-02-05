package com.larrydevincarter.optionscanner.controllers;

import com.larrydevincarter.optionscanner.models.dtos.StockCandidatesRequestDto;
import com.larrydevincarter.optionscanner.services.FilterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StockCandidatesController {

    private final FilterService filterService;

    @PostMapping("/stock-candidates")
    public List<String> getStockCandidates(@RequestBody StockCandidatesRequestDto request) {
        return filterService.getStockCandidates(request);
    }
}