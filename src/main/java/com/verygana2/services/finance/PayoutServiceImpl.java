package com.verygana2.services.finance;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.services.interfaces.finance.PayoutService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService{

    private final PayoutRepository payoutRepository;

    @Override
    public BigDecimal getCommercialEarningsForPeriod(Long commercialId, Integer year, Integer month) {
        ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota"));
        ZonedDateTime endDate = startDate.plusMonths(1);
        return payoutRepository.sumTotalByCommercialIdAndPeriod(commercialId, startDate, endDate);
    }
}
