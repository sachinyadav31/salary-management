package com.acme.salary.payband.api;

import com.acme.salary.payband.PayBandQueryRepository;
import com.acme.salary.payband.PayBandSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only in v1: bands are reference data managed by migrations. */
@RestController
public class PayBandController {

    private final PayBandQueryRepository payBands;

    public PayBandController(PayBandQueryRepository payBands) {
        this.payBands = payBands;
    }

    @GetMapping("/api/pay-bands")
    public List<PayBandSummary> list() {
        return payBands.findAllWithDistribution();
    }
}
