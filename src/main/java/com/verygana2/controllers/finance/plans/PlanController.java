package com.verygana2.controllers.finance.plans;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.finance.plans.requests.PlanPaymentRequestDTO;
import com.verygana2.dtos.finance.plans.requests.RechargeRequestDTO;
import com.verygana2.dtos.finance.plans.responses.EffectivePlanStateResponseDTO;
import com.verygana2.dtos.finance.plans.responses.PlanCatalogResponseDTO;
import com.verygana2.dtos.finance.plans.responses.PlanPaymentStatusResponseDTO;
import com.verygana2.dtos.finance.plans.responses.RechargePreviewResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PlanService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/plans")
@RequiredArgsConstructor
public class PlanController {

        private final PlanService planService;
        private final CommercialDetailsService commercialDetailsService;
        private final CommercialContractService commercialContractService;

        /**
         * Genera la URL del checkout de Wompi para pagar un plan.
         */

        @PostMapping("/checkout")
        @PreAuthorize("hasRole('COMMERCIAL')")
        public ResponseEntity<WompiCheckoutResponseDTO> initiatePayment(
                        @AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody PlanPaymentRequestDTO request) {

                Long commercialId = jwt.getClaim("userId");
                CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

                log.info("[PLAN CONTROLLER] Iniciando pago: commercialId={}, plan={}",
                                commercialId, request.getPlanCode());

                WompiCheckoutResponseDTO response = planService.initiatePlanPayment(
                                commercial,
                                request.getPlanCode(),
                                request.getAmountCents());

                log.info("checkoutUrl {}, reference {}, amountInCents {}", response.getCheckoutUrl(),
                                response.getReference(), response.getAmountInCents());

                return ResponseEntity.ok(response);
        }

        /**
         * Consulta el estado de un pago por referencia.
         * Usado por el frontend en polling después de volver del checkout de Wompi.
         */

        @GetMapping("/status/{reference}")
        @PreAuthorize("hasRole('COMMERCIAL')")
        public ResponseEntity<PlanPaymentStatusResponseDTO> getPaymentStatus(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable String reference) {

                Long commercialId = jwt.getClaim("userId");
                CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

                PlanPaymentStatusResponseDTO status = planService.getPaymentStatus(reference, commercial);
                return ResponseEntity.ok(status);
        }

        /**
         * Resumen de solo lectura de una recarga (elegibilidad, mensaje explicativo,
         * rango del plan, saldo estimado tras la reserva de tesorería) — para que el
         * comercial lo revise antes de confirmar y disparar la generación real del otrosí.
         * Los montos vienen en pesos colombianos (campos *Pesos), no en centavos.
         */
        @GetMapping("/recharge/preview")
        @PreAuthorize("hasRole('COMMERCIAL')")
        public ResponseEntity<RechargePreviewResponseDTO> previewRecharge(
                        @AuthenticationPrincipal Jwt jwt,
                        @RequestParam Long amountCents) {

                Long commercialId = jwt.getClaim("userId");
                CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

                return ResponseEntity.ok(planService.previewRecharge(commercial, amountCents));
        }

        /**
         * Solicita una recarga de saldo STANDARD/PREMIUM: genera el contrato específico a
         * ese monto y lo envía a firma electrónica. El pago solo se habilita una vez
         * firmado (ver /plans/recharge/{contractId}/checkout).
         */
        @PostMapping("/recharge/request")
        @PreAuthorize("hasRole('COMMERCIAL')")
        public ResponseEntity<ContractSummaryResponseDTO> requestRecharge(
                        @AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody RechargeRequestDTO request) {

                Long commercialId = jwt.getClaim("userId");
                CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

                ContractSummaryResponseDTO summary = planService.requestRecharge(commercial, request.getAmountCents());
                return ResponseEntity.ok(summary);
        }

        /**
         * Consulta el estado de un contrato de recarga (polling después de volver de la
         * firma electrónica, para saber cuándo llamar a /checkout).
         */
        @GetMapping("/recharge/{contractId}")
        @PreAuthorize("hasRole('COMMERCIAL')")
        public ResponseEntity<ContractSummaryResponseDTO> getRechargeContract(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable Long contractId) {

                Long commercialId = jwt.getClaim("userId");
                return ResponseEntity.ok(commercialContractService.getForCommercial(contractId, commercialId));
        }

        /**
         * Autocancela una recarga en curso (antes de pagarla) — para que el comercial
         * pueda desbloquear una solicitud de cambio de plan sin esperar a que el
         * contrato sea rechazado o quede huérfano.
         */
        @PostMapping("/recharge/{contractId}/cancel")
        @PreAuthorize("hasRole('COMMERCIAL')")
        public ResponseEntity<ContractSummaryResponseDTO> cancelRecharge(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable Long contractId) {

                Long commercialId = jwt.getClaim("userId");
                return ResponseEntity.ok(commercialContractService.cancelForCommercial(contractId, commercialId));
        }

        /**
         * Genera el checkout de Wompi para una recarga ya firmada. El frontend lo llama
         * cuando detecta (por polling del contrato) que el estado pasó a SIGNED.
         */
        @PostMapping("/recharge/{contractId}/checkout")
        @PreAuthorize("hasRole('COMMERCIAL')")
        public ResponseEntity<WompiCheckoutResponseDTO> generateRechargeCheckout(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable Long contractId) {

                Long commercialId = jwt.getClaim("userId");
                CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

                WompiCheckoutResponseDTO response = planService.generateRechargeCheckout(contractId, commercial);
                return ResponseEntity.ok(response);
        }

        /**
         * Retorna el estado efectivo del plan del comercial autenticado.
         * El frontend lo usa para:
         * - Mostrar el plan activo en el dashboard y sidebar
         * - Desbloquear/bloquear funcionalidades según el plan
         * - Mostrar el presupuesto restante (STANDARD/PREMIUM)
         * - Mostrar días restantes de suscripción (BASIC)
         *
         * Se llama al cargar el dashboard y después de completar un pago.
         */
        @GetMapping("/commercial/state")
        @PreAuthorize("hasRole('COMMERCIAL')")
        public ResponseEntity<EffectivePlanStateResponseDTO> getEffectivePlanState(
                        @AuthenticationPrincipal Jwt jwt) {

                Long commercialId = jwt.getClaim("userId");
                CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

                EffectivePlanStateResponseDTO state = planService.getEffectivePlanState(commercial);

                log.debug("[PLAN CONTROLLER] Estado del plan: commercialId={}, plan={}, active={}",
                                commercialId, state.getEffectivePlan(), state.isHasActivePlan());

                return ResponseEntity.ok(state);
        }

        /**
         * Catálogo completo de planes activos (BASIC/STANDARD/PREMIUM) para que el
         * comercial ya onboardeado los compare (vista tarjetas o tabla), marcando cuál
         * es su plan vigente para que el frontend muestre "recargar" en vez de
         * "cambiar de plan" en esa tarjeta/fila.
         */
        @GetMapping("/catalog")
        @PreAuthorize("hasRole('COMMERCIAL')")
        public ResponseEntity<PlanCatalogResponseDTO> getPlanCatalog(@AuthenticationPrincipal Jwt jwt) {

                Long commercialId = jwt.getClaim("userId");
                CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

                return ResponseEntity.ok(planService.getPlanCatalog(commercial));
        }

}