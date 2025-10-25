package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.Earnings;
import com.larrydevincarter.optionscanner.repositories.base.StatementRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing Earnings entities.
 */
@Repository
public interface EarningsRepository extends StatementRepository<Earnings> {

}