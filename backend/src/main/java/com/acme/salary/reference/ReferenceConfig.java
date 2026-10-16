package com.acme.salary.reference;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

@Configuration
public class ReferenceConfig {

    /** FX rates are fixed and dated, so they are loaded once at startup. */
    @Bean
    public CurrencyConverter currencyConverter(FxRateRepository fxRates) {
        return new CurrencyConverter(fxRates.findAll().stream()
                .collect(Collectors.toMap(FxRate::getCurrencyCode, FxRate::getUsdPerUnit)));
    }
}
