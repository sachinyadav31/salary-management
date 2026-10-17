package com.acme.salary.bonus.api;

import com.acme.salary.bonus.BonusService;
import com.acme.salary.bonus.BonusService.AwardBonus;
import com.acme.salary.bonus.BonusService.BonusView;
import com.acme.salary.bonus.BonusType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/bonuses")
@Tag(name = "Bonuses", description = "One-off awards: performance payout, spot, sign-on, retention")
public class BonusController {

    /** Amount only: the currency is always the employee's country currency. */
    public record AwardBonusRequest(@NotNull BonusType type, @NotNull @Positive BigDecimal amount,
                                    @NotNull LocalDate awardedOn, @Size(max = 500) String note) {
    }

    private final BonusService bonuses;

    public BonusController(BonusService bonuses) {
        this.bonuses = bonuses;
    }

    @GetMapping
    @Operation(summary = "Bonus awards, newest first")
    public List<BonusView> list(@PathVariable long employeeId) {
        return bonuses.bonuses(employeeId);
    }

    @PostMapping
    @Operation(summary = "Award a one-off bonus (doesn't change base salary)")
    public ResponseEntity<List<BonusView>> award(@PathVariable long employeeId, @Valid @RequestBody AwardBonusRequest request) {
        bonuses.award(employeeId, new AwardBonus(request.type(), request.amount(), request.awardedOn(), request.note()));
        return ResponseEntity.created(URI.create("/api/employees/" + employeeId + "/bonuses")).body(bonuses.bonuses(employeeId));
    }
}
