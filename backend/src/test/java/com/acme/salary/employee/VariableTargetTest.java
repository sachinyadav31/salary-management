package com.acme.salary.employee;

import com.acme.salary.common.Money;
import com.acme.salary.reference.JobLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableTargetTest {

    @Test
    void convertsBetweenPercentAndBasisPoints() {
        assertThat(VariableTarget.ofPercent(new BigDecimal("12.5"))).isEqualTo(new VariableTarget(1250));
        assertThat(new VariableTarget(1250).percent()).isEqualByComparingTo("12.5");
        assertThat(new VariableTarget(2000).label()).isEqualTo("20%");
    }

    @Test
    void rejectsImpossibleTargets() {
        assertThatThrownBy(() -> VariableTarget.ofPercent(new BigDecimal("10.555"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VariableTarget(10_001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VariableTarget(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void computesTheTargetAmountRoundedToTheMinorUnit() {
        assertThat(new VariableTarget(1000).of(Money.ofMinor(129_500_000, "INR"))).isEqualTo(Money.ofMinor(12_950_000, "INR"));
        assertThat(new VariableTarget(1250).of(Money.ofMinor(4_501, "USD"))).isEqualTo(Money.ofMinor(563, "USD")); // 562.625 -> 563
        assertThat(new VariableTarget(1500).of(Money.ofMinor(6_620_005, "JPY"))).isEqualTo(Money.ofMinor(993_001, "JPY")); // whole yen
        assertThat(VariableTarget.NONE.of(Money.ofMinor(100, "GBP"))).isEqualTo(Money.ofMinor(0, "GBP"));
    }

    @Test
    void defaultsDependOnRole() {
        assertThat(VariableTarget.defaultFor("Sales", JobLevel.L1)).isEqualTo(new VariableTarget(2000));
        assertThat(VariableTarget.defaultFor("Engineering", JobLevel.L3)).isEqualTo(new VariableTarget(1000));
        assertThat(VariableTarget.defaultFor("Engineering", JobLevel.L5)).isEqualTo(new VariableTarget(1500));
        assertThat(VariableTarget.defaultFor("People", JobLevel.L6)).isEqualTo(new VariableTarget(2000));
    }
}
