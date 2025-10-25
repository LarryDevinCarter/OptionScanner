package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.repositories.base.StatementRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing BalanceSheet entities.
 */
@Repository
public interface BalanceSheetRepository extends StatementRepository<BalanceSheet> {

}