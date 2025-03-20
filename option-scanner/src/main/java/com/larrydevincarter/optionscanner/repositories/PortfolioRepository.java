package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, String> {
}
