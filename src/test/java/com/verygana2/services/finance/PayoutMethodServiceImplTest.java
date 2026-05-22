package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.repositories.finance.PayoutMethodRepository;

import java.util.List;

/**
 * BUG DETECTED (fixed): PayoutMethodServiceImpl had
 *   private PayoutMethodRepository payoutMethodRepository;  ← missing `final`
 * @RequiredArgsConstructor only injects `final` fields. Without `final`, the
 * field is never injected → NullPointerException at runtime on first call.
 *
 * Fix applied: added `final` keyword to the field declaration.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutMethodServiceImpl")
class PayoutMethodServiceImplTest {

    @Mock PayoutMethodRepository payoutMethodRepository;

    @InjectMocks PayoutMethodServiceImpl service;

    @Test
    @DisplayName("getByCommercialId returns paged results from repository")
    void getByCommercialId_returnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);

        PayoutMethod method = new PayoutMethod();
        Page<PayoutMethod> page = new PageImpl<>(List.of(method));

        when(payoutMethodRepository.findByCommercialId(5L, pageable)).thenReturn(page);

        PagedResponse<PayoutMethod> result = service.getByCommercialId(5L, pageable);

        assertThat(result.getData()).containsExactly(method);
        assertThat(result.getMeta().getTotalElements()).isEqualTo(1);
        verify(payoutMethodRepository).findByCommercialId(5L, pageable);
    }

    @Test
    @DisplayName("getByCommercialId returns empty paged response when no methods registered")
    void getByCommercialId_returnsEmptyWhenNone() {
        Pageable pageable = PageRequest.of(0, 10);
        when(payoutMethodRepository.findByCommercialId(99L, pageable)).thenReturn(Page.empty());

        PagedResponse<PayoutMethod> result = service.getByCommercialId(99L, pageable);

        assertThat(result.getData()).isEmpty();
        assertThat(result.getMeta().getTotalElements()).isZero();
    }

    @Test
    @DisplayName("getByCommercialId passes pageable through to repository (page/size respected)")
    void getByCommercialId_respectsPagination() {
        Pageable pageable = PageRequest.of(2, 5);
        when(payoutMethodRepository.findByCommercialId(any(), any())).thenReturn(Page.empty());

        service.getByCommercialId(1L, pageable);

        verify(payoutMethodRepository).findByCommercialId(1L, pageable);
    }
}
