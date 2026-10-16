package com.acme.salary.payband;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conversions between the API's decimal compa-ratio (0.97) and the database's basis points (9700). */
public final class CompaRatio {

    private CompaRatio() {
    }

    public static BigDecimal fromBasisPoints(long basisPoints) {
        return BigDecimal.valueOf(basisPoints, 4).setScale(2, RoundingMode.HALF_UP);
    }

    public static long toBasisPoints(BigDecimal ratio) {
        if (ratio.signum() < 0) {
            throw new IllegalArgumentException("Compa-ratio cannot be negative");
        }
        return ratio.movePointRight(4).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
