package com.acme.salary.payband.api;

import com.acme.salary.payband.PayBandQueryRepository;
import com.acme.salary.payband.PayBandSummary;
import org.springframework.web.bind.annotation.GetMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only in v1: bands are reference data managed by migrations. */
@RestController
@Tag(name = "Pay bands", description = "Bands per country and level")
public class PayBandController {

    private final PayBandQueryRepository payBands;

    public PayBandController(PayBandQueryRepository payBands) {
        this.payBands = payBands;
    }

    @Operation(summary = "All pay bands with headcount below / within / above")
    @GetMapping("/api/pay-bands")
    public List<PayBandSummary> list() {
        return payBands.findAllWithDistribution();
    }
}
