package com.larrydevincarter.optionscanner.controllers;

import com.larrydevincarter.optionscanner.dtos.PortfolioInputDTO;
import com.larrydevincarter.optionscanner.entities.Portfolio;
import com.larrydevincarter.optionscanner.mappers.PortfolioMapper;
import com.larrydevincarter.optionscanner.repositories.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolio")
public class PortfolioController {

    public static final Logger logger = LoggerFactory.getLogger(PortfolioController.class);

    @Autowired
    private PortfolioMapper mapper;
    @Autowired
    private PortfolioRepository portfolioRepository;

    @PostMapping("/add")
    public ResponseEntity<Portfolio> addPortfolioEntry(@Validated @RequestBody PortfolioInputDTO dto) {
        Portfolio portfolio = mapper.toEntity(dto);
        portfolioRepository.save(portfolio);
        logger.info("Added {}: {} shares at ${}, acquired on {}", dto.getTicker(), dto.getShares(), dto.getCostBasis(), portfolio.getAcquisitionDate().toString());
        return  new ResponseEntity<>(portfolio, HttpStatus.CREATED);
    }
}
