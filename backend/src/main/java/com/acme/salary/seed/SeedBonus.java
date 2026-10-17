package com.acme.salary.seed;

import com.acme.salary.bonus.BonusType;
import com.acme.salary.common.Money;

import java.time.LocalDate;

public record SeedBonus(BonusType type, Money amount, LocalDate awardedOn, String note) {
}
