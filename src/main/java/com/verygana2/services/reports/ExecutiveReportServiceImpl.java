package com.verygana2.services.reports;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.commercial.ContractPdfRenderer;
import com.verygana2.services.interfaces.reports.ExecutiveReportService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Arma el reporte ejecutivo PDF combinando las secciones que cada dominio aporta
 * vía {@link ReportMetricProvider} (ver esa interfaz para cómo se integran nuevos
 * dominios) y lo renderiza reutilizando {@link ContractPdfRenderer}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutiveReportServiceImpl implements ExecutiveReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Spring inyecta aquí automáticamente todos los @Component que implementan la interfaz.
    private final List<ReportMetricProvider> providers;
    private final ReportTemplateLoader templateLoader;
    private final ContractPdfRenderer pdfRenderer;
    private final CommercialDetailsRepository commercialDetailsRepository;

    @Override
    @RequirePlanCapability(value = RequirePlanCapability.Capability.CAN_EXPORT_REPORT, requiresBudget = true)
    public byte[] generateExecutiveReportPdf(Long commercialId, List<ReportMetricType> metricTypes,
            ZonedDateTime startDate, ZonedDateTime endDate) {

        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found: " + commercialId));

        List<ReportMetricProvider> selectedProviders = (metricTypes == null || metricTypes.isEmpty())
                ? providers
                : providers.stream().filter(p -> metricTypes.contains(p.getType())).toList();

        StringBuilder sectionsHtml = new StringBuilder();
        for (ReportMetricProvider provider : selectedProviders) {
            try {
                ReportMetricSection section = provider.buildSection(commercialId, startDate, endDate);
                sectionsHtml.append(renderSection(section));
            } catch (Exception e) {
                // Un dominio sin implementar o con error no debe tumbar el resto del reporte.
                log.warn("No se pudo generar la sección {} del reporte ejecutivo para commercial {}: {}",
                        provider.getType(), commercialId, e.getMessage());
            }
        }

        if (sectionsHtml.isEmpty()) {
            sectionsHtml.append("<p class=\"empty-section\">No hay métricas disponibles para el período seleccionado.</p>");
        }

        Map<String, String> vars = Map.of(
                "companyName", nullSafe(commercial.getCompanyName()),
                "planCode", commercial.getCurrentPlan() != null ? commercial.getCurrentPlan().getCode().name() : "N/A",
                "periodLabel", startDate.format(DATE_FMT) + " – " + endDate.minusDays(1).format(DATE_FMT),
                "generatedAt", ZonedDateTime.now(startDate.getZone()).format(DATE_FMT),
                "sectionsHtml", sectionsHtml.toString());

        String html = templateLoader.render("reporte-ejecutivo.html", vars);
        return pdfRenderer.renderToPdf(html);
    }

    private String renderSection(ReportMetricSection section) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(escape(section.title())).append("</h2>");

        if (section.rows().isEmpty() && section.tables().isEmpty()) {
            sb.append("<p class=\"empty-section\">Sin datos para este período.</p>");
            return sb.toString();
        }

        if (!section.rows().isEmpty()) {
            sb.append("<table class=\"rows-table\">");
            for (ReportMetricRow row : section.rows()) {
                sb.append("<tr><th>").append(escape(row.label())).append("</th><td>")
                        .append(escape(row.value())).append("</td></tr>");
            }
            sb.append("</table>");
        }

        for (ReportMetricTable table : section.tables()) {
            if (table.title() != null && !table.title().isBlank()) {
                sb.append("<p><strong>").append(escape(table.title())).append("</strong></p>");
            }
            sb.append("<table><thead><tr>");
            for (String header : table.headers()) {
                sb.append("<th>").append(escape(header)).append("</th>");
            }
            sb.append("</tr></thead><tbody>");
            for (List<String> row : table.rows()) {
                sb.append("<tr>");
                for (String cell : row) {
                    sb.append("<td>").append(escape(cell)).append("</td>");
                }
                sb.append("</tr>");
            }
            sb.append("</tbody></table>");
        }

        return sb.toString();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
