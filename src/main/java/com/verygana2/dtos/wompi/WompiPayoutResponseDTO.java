package com.verygana2.dtos.wompi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Response de POST /payouts, según el spec público de SwaggerHub
 * (https://app.swaggerhub.com/apis-docs/wompi/Payouts/1.0.0):
 *
 * {
 *   "status": 201,
 *   "meta": { "trace_id": "..." },
 *   "code": "OK",
 *   "message": "Solicitud ejecutada correctamente.",
 *   "data": { "payoutId": "...", "transactions": 1, "success": 1, "failed": 0 }
 * }
 *
 * Esta respuesta solo confirma que el lote fue *aceptado* para procesar —
 * no es el resultado final del pago. El resultado real (APPROVED/DECLINED/
 * FAILED por transacción) llega después vía webhook "transaction.updated".
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WompiPayoutResponseDTO {

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private PayoutData data;

    /** Aceptado por Wompi si el código HTTP es 2xx y ninguna transacción del lote fue rechazada en validación. */
    public boolean isAccepted() {
        boolean httpOk = status != null && status >= 200 && status < 300;
        boolean noValidationFailures = data == null || data.getFailed() == null || data.getFailed() == 0;
        return httpOk && noValidationFailures;
    }

    public String getPayoutId() {
        return data != null ? data.getPayoutId() : null;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PayoutData {

        @JsonProperty("payoutId")
        private String payoutId;

        @JsonProperty("transactions")
        private Integer transactions;

        @JsonProperty("success")
        private Integer success;

        @JsonProperty("failed")
        private Integer failed;
    }
}
