package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Asset {

    @Id
    private String id;
    private String symbol;
    private String name;
    private String exchange;
    private String status;
    private boolean tradable;
    private LocalDateTime lastUpdated;
}
