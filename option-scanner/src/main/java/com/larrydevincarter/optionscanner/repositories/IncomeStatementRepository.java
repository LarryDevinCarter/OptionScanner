package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.base.StatementRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomeStatementRepository extends StatementRepository<IncomeStatement> {

}
