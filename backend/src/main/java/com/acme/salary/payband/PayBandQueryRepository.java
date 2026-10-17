package com.acme.salary.payband;

import com.acme.salary.common.Money;
import com.acme.salary.common.web.MoneyView;
import com.acme.salary.reference.JobLevel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PayBandQueryRepository {

    private final JdbcTemplate jdbc;

    public PayBandQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All bands with active headcount split by band position, ordered by country name then level. */
    public List<PayBandSummary> findAllWithDistribution() {
        return jdbc.query("""
                select b.country_code, c.name as country_name, b.level, b.currency_code,
                       b.min_minor, b.mid_minor, b.max_minor,
                       count(p.employee_id)                                  as headcount,
                       coalesce(sum(p.band_position = 'BELOW'), 0)          as below,
                       coalesce(sum(p.band_position = 'WITHIN'), 0)         as within,
                       coalesce(sum(p.band_position = 'ABOVE'), 0)          as above
                from pay_band b
                join country c on c.code = b.country_code
                left join employee e on e.country_code = b.country_code and e.level = b.level and e.status = 'ACTIVE'
                left join employee_pay p on p.employee_id = e.id
                group by b.country_code, c.name, b.level, b.currency_code, b.min_minor, b.mid_minor, b.max_minor
                order by c.name, b.level""",
                (rs, i) -> {
                    String currency = rs.getString("currency_code");
                    return new PayBandSummary(
                            rs.getString("country_code"),
                            rs.getString("country_name"),
                            JobLevel.valueOf(rs.getString("level")),
                            new PayBandView(
                                    MoneyView.from(Money.ofMinor(rs.getLong("min_minor"), currency)),
                                    MoneyView.from(Money.ofMinor(rs.getLong("mid_minor"), currency)),
                                    MoneyView.from(Money.ofMinor(rs.getLong("max_minor"), currency))),
                            rs.getLong("headcount"),
                            rs.getLong("below"),
                            rs.getLong("within"),
                            rs.getLong("above"));
                });
    }
}
