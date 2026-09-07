package com.larrydevincarter.optionscanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OptionScanner — background data engine for stock/option fundamentals and chains.
 * Scheduled refresh lives in {@code AssetServiceImpl}; this class only boots the app.
 */
@SpringBootApplication
@EnableScheduling
@Slf4j
public class OptionScannerApplication {

	public static void main(String[] args) {
		SpringApplication.run(OptionScannerApplication.class, args);
	}
}
