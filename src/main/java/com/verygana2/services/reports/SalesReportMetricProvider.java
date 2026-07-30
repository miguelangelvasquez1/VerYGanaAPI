package com.verygana2.services.reports;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.dtos.user.commercial.responses.SalesReportResponseDTO;
import com.verygana2.services.interfaces.details.CommercialDetailsService;

import lombok.RequiredArgsConstructor;

/**
 * Aporta la sección de ventas al reporte ejecutivo, reutilizando
 * {@link CommercialDetailsService#getSalesReport}.
 */
@Component
@RequiredArgsConstructor
public class SalesReportMetricProvider implements ReportMetricProvider {

    private final CommercialDetailsService commercialDetailsService;

    @Override
    public ReportMetricType getType() {
        return ReportMetricType.SALES;
    }

    @Override
    public ReportMetricSection buildSection(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        SalesReportResponseDTO report = commercialDetailsService.getSalesReport(commercialId, startDate, endDate);

        List<ReportMetricRow> rows = List.of(
                new ReportMetricRow("Ventas totales", formatCop(report.getTotalSalesAmount())),
                new ReportMetricRow("Cantidad de ventas", String.valueOf(nullToZero(report.getTotalSalesCount()))),
                new ReportMetricRow("Comisiones pagadas a la plataforma", formatCop(report.getTotalPlatformCommissionsAmount()))
        );

        List<FeaturedProductResponseDTO> topProducts = report.getTopSellingProducts();
        List<ReportMetricTable> tables = (topProducts == null || topProducts.isEmpty())
                ? List.of()
                : List.of(new ReportMetricTable(
                        "Top productos vendidos",
                        List.of("Producto", "Precio", "Unidades vendidas"),
                        topProducts.stream()
                                .map(p -> List.of(
                                        p.getName(),
                                        formatCop(p.getPrice()),
                                        String.valueOf(p.getTotalSales())))
                                .toList()));

        return new ReportMetricSection("Ventas", rows, tables);
    }

    private String formatCop(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return "$" + String.format("%,.0f", amount).replace(",", ".");
    }

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }
}
