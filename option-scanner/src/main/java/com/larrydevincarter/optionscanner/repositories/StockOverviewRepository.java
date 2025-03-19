package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.StockOverview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockOverviewRepository extends JpaRepository<StockOverview, String> {
}
