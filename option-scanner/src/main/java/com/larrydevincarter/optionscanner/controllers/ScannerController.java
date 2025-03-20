package com.larrydevincarter.optionscanner.controllers;

import com.larrydevincarter.optionscanner.dtos.PortfolioInputDTO;
import com.larrydevincarter.optionscanner.entities.Portfolio;
import com.larrydevincarter.optionscanner.mappers.PortfolioMapper;
import com.larrydevincarter.optionscanner.repositories.CallOpportunityRepository;
import com.larrydevincarter.optionscanner.repositories.PortfolioRepository;
import com.larrydevincarter.optionscanner.repositories.PutOpportunityRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import javax.sound.sampled.Port;

@Controller
public class ScannerController {

    private static final Logger logger = LoggerFactory.getLogger(ScannerController.class);

    @Autowired
    private PutOpportunityRepository putRepository;
    @Autowired
    private CallOpportunityRepository callRepository;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private PortfolioController portfolioController;

    @GetMapping("/puts")
    private String getPuts(Model model) {
        logger.info("Accessed /puts");
        model.addAttribute("puts", putRepository.findTop3ByOrderByPremiumDesc());
        return "puts";
    }

    @GetMapping("/calls")
    public String getCalls(Model model) {
        logger.info("Accessed /calls");
        model.addAttribute("calls", callRepository.findTop3ByOrderByPremiumDesc());
        return "calls";
    }

    @GetMapping("/portfolio")
    public String getPortfolio(Model model) {
        logger.info("Accessed /portfolio");
        model.addAttribute("portfolio", portfolioRepository.findAll());
        model.addAttribute("portfolioInput", new PortfolioInputDTO());
        return "portfolio";
    }

    @PostMapping("/portfolio")
    public String addPortfolioEntry (@Valid @ModelAttribute("portfolioInput") PortfolioInputDTO dto, BindingResult result, Model model) {

        logger.info("Submitted portfolio entry: {}, {} shares", dto.getTicker(), dto.getShares());

        if (result.hasErrors()) {
            model.addAttribute("portfolio", portfolioRepository.findAll());
            return "portfolio";
        }
        portfolioController.addPortfolioEntry(dto);
        return "redirect:/portfolio";
    }
}
