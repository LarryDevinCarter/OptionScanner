package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.base.StatementRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomeStatementRepository extends StatementRepository<IncomeStatement> {

}
