package com.finpilot.transaction.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FingerprintService is deliberately framework-free (no Spring annotations)
 * so its core algorithm can be compiled and unit tested in complete
 * isolation — see FingerprintServiceTest, which does exactly that without
 * any Spring context. This class is the only place that wires it into the
 * application as a bean.
 */
@Configuration
public class TransactionServiceConfig {

    @Bean
    public FingerprintService fingerprintService() {
        return new FingerprintService();
    }
}
