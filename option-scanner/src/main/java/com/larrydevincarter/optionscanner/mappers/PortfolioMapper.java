package com.larrydevincarter.optionscanner.mappers;

import com.larrydevincarter.optionscanner.dtos.PortfolioInputDTO;
import com.larrydevincarter.optionscanner.entities.Portfolio;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    @Mapping(target = "acquisitionDate", source = "acquisitionDate", qualifiedByName = "parseDateOrNow")
    Portfolio toEntity(PortfolioInputDTO dto);

    @Named("parseDateOrNow")
    default LocalDateTime parseDateOrNow(String dateStr) {

        if (dateStr == null || dateStr.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.parse(dateStr + "T00:00:00"); //sets time to midnight if only date in field (will likely be most of the time)
        }
    }
}
