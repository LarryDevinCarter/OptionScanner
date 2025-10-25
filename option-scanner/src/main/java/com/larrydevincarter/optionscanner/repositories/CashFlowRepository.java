package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.CashFlow;
import com.larrydevincarter.optionscanner.repositories.base.StatementRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing CashFlow entities.
 */
@Repository
public interface CashFlowRepository extends StatementRepository<CashFlow> {

}