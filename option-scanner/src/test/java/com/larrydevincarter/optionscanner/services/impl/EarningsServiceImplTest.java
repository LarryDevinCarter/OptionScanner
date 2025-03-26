//package com.larrydevincarter.optionscanner.services.impl;
//
//import com.larrydevincarter.optionscanner.entities.Earnings;
//import com.larrydevincarter.optionscanner.entities.IncomeStatement;
//import com.larrydevincarter.optionscanner.repositories.EarningsRepository;
//import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//import org.springframework.web.client.ResourceAccessException;
//import org.springframework.web.client.RestTemplate;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.util.*;
//
//import static org.mockito.Mockito.*;
//import static org.junit.jupiter.api.Assertions.*;
//
//class EarningsServiceImplTest {
//
//    @Mock
//    private EarningsRepository earningsRepository;
//
//    @Mock
//    private IncomeStatementRepository incomeStatementRepository;
//
//    @Mock
//    private RestTemplate restTemplate;
//
//    @InjectMocks
//    private EarningsServiceImpl earningsService;
//
//    private List<String> errorLog;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//        earningsService = new EarningsServiceImpl(earningsRepository, incomeStatementRepository, restTemplate);
//        errorLog = new ArrayList<>();
//    }
//
//    @Test
//    void testFetchAndStoreEarnings_Success() {
//        // Arrange
//        when(incomeStatementRepository.findSymbolsUpdatedToday(any(LocalDateTime.class)))
//                .thenReturn(List.of("AAPL"));
//        Map<String, Object> response = new HashMap<>();
//        response.put("annualEarnings", List.of(
//                Map.of("fiscalDateEnding", "2024-12-31", "reportedEPS", "2.5")
//        ));
//        response.put("quarterlyEarnings", List.of(
//                Map.of("fiscalDateEnding", "2024-09-30", "reportedEPS", "1.2", "estimatedEPS", "1.1",
//                        "surprise", "0.1", "surprisePercentage", "9.09", "reportedDate", "2024-10-15")
//        ));
//        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);
//
//        // Act
//        earningsService.fetchAndStoreEarnings(errorLog);
//
//        // Assert
//        verify(earningsRepository, times(1)).deleteBySymbol("AAPL");
//        verify(earningsRepository, times(1)).saveAll(argThat(iterable -> {
//            List<Earnings> earningsList = (List<Earnings>) iterable; // Cast to List
//            return earningsList.size() == 2;
//        }));
//        assertTrue(errorLog.isEmpty());
//    }
//
//    @Test
//    void testFetchAndStoreEarnings_RateLimit() throws InterruptedException {
//        // Arrange
//        List<String> symbols = new ArrayList<>();
//        for (int i = 0; i < 76; i++) {
//            symbols.add("SYM" + i);
//        }
//        when(incomeStatementRepository.findSymbolsUpdatedToday(any(LocalDateTime.class))).thenReturn(symbols);
//        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(createSampleResponse());
//
//        // Act
//        earningsService.fetchAndStoreEarnings(errorLog);
//
//        // Assert
//        verify(restTemplate, times(76)).getForObject(anyString(), eq(Map.class)); // 76 calls made
//        verify(earningsRepository, times(76)).deleteBySymbol(anyString()); // One delete per symbol
//        verify(earningsRepository, times(76)).saveAll(any()); // One save per symbol
//        // No need to check errorLog for rate limit message since it’s not an error
//        assertTrue(errorLog.isEmpty(), "errorLog should be empty since no errors occurred");
//    }
//
//    @Test
//    void testFetchAndStoreEarnings_RetryOnTimeout() {
//        // Arrange
//        when(incomeStatementRepository.findSymbolsUpdatedToday(any(LocalDateTime.class)))
//                .thenReturn(List.of("AAPL"));
//        when(restTemplate.getForObject(anyString(), eq(Map.class)))
//                .thenThrow(new ResourceAccessException("Timeout"))
//                .thenReturn(createSampleResponse());
//
//        // Act
//        earningsService.fetchAndStoreEarnings(errorLog);
//
//        // Assert
//        verify(restTemplate, times(2)).getForObject(anyString(), eq(Map.class));
//        assertTrue(errorLog.stream().anyMatch(log -> log.contains("Attempt 1 failed")));
//    }
//
//    @Test
//    void testFetchAndStoreEarnings_MaxRetriesExceeded() {
//        // Arrange
//        when(incomeStatementRepository.findSymbolsUpdatedToday(any(LocalDateTime.class)))
//                .thenReturn(List.of("AAPL"));
//        when(restTemplate.getForObject(anyString(), eq(Map.class)))
//                .thenThrow(new ResourceAccessException("Timeout"));
//
//        // Act
//        earningsService.fetchAndStoreEarnings(errorLog);
//
//        // Assert
//        verify(restTemplate, times(3)).getForObject(anyString(), eq(Map.class));
//        assertTrue(errorLog.stream().anyMatch(log -> log.contains("Max retries reached")));
//    }
//
//    @Test
//    void testGetSymbolsWithUpdatedIncomeStatements() {
//        // Arrange
//        IncomeStatement stmt = new IncomeStatement();
//        stmt.setSymbol("AAPL");
//        stmt.setLastUpdated(LocalDateTime.now());
//        when(incomeStatementRepository.findSymbolsUpdatedToday(any(LocalDateTime.class)))
//                .thenReturn(List.of("AAPL"));
//
//        // Act
//        List<String> symbols = earningsService.getSymbolsWithUpdatedIncomeStatements();
//
//        // Assert
//        assertEquals(1, symbols.size());
//        assertEquals("AAPL", symbols.get(0));
//    }
//
//    private Map<String, Object> createSampleResponse() {
//        Map<String, Object> response = new HashMap<>();
//        response.put("annualEarnings", List.of(Map.of("fiscalDateEnding", "2024-12-31", "reportedEPS", "2.0")));
//        return response;
//    }
//}