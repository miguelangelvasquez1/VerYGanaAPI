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
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.finance.plans.requests.PlanPaymentRequestDTO;
import com.verygana2.dtos.finance.plans.responses.EffectivePlanStateResponseDTO;
import com.verygana2.dtos.finance.plans.responses.PlanPaymentStatusResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.models.userDetails.CommercialDetails;
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

        /**
         * Genera la URL del checkout de Wompi para pagar un plan.
         */

        @PostMapping("/checkout")
        @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
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
        @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
        public ResponseEntity<PlanPaymentStatusResponseDTO> getPaymentStatus(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable String reference) {

                Long commercialId = jwt.getClaim("userId");
                CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

                PlanPaymentStatusResponseDTO status = planService.getPaymentStatus(reference, commercial);
                return ResponseEntity.ok(status);
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
        @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
        public ResponseEntity<EffectivePlanStateResponseDTO> getEffectivePlanState(
                        @AuthenticationPrincipal Jwt jwt) {

                Long commercialId = jwt.getClaim("userId");
                CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

                EffectivePlanStateResponseDTO state = planService.getEffectivePlanState(commercial);

                log.debug("[PLAN CONTROLLER] Estado del plan: commercialId={}, plan={}, active={}",
                                commercialId, state.getEffectivePlan(), state.isHasActivePlan());

                return ResponseEntity.ok(state);
        }

}