package com.verygana2.services.plans;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import com.verygana2.models.finance.plans.RequirePlanCapability;

import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class PlanGuardAspect {

    private final PlanFeatureGuard guard;

    @Around("@annotation(requireCapability) && within(com.verygana2.services..*)")
    public Object checkPlanCapabilities(ProceedingJoinPoint joinPoint,
                                        RequirePlanCapability requireCapability) throws Throwable {

        String paramName = requireCapability.commercialIdParam();
        Long commercialId = extractCommercialId(joinPoint, paramName);

        if (commercialId == null) {
            throw new IllegalArgumentException("No se encontró el parámetro '" + paramName + "' en el método.");
        }

        // Validar todas las capacidades requeridas
        boolean touchesAssetLimit = false;
        for (RequirePlanCapability.Capability cap : requireCapability.value()) {
            guard.assertCapability(commercialId, cap);
            touchesAssetLimit |= isAssetLimit(cap);
        }

        // Cualquier método que verifique un límite MAX_* está creando o reactivando un
        // activo. Si hay un cambio de plan en curso se bloquea: el plan destino podría no
        // admitirlo y dejaría al comercial por encima del límite al aplicarse el cambio.
        if (touchesAssetLimit) {
            guard.assertNoOpenPlanChangeRequest(commercialId);
        }

        // Los errores de "tu plan no incluye esto" priman sobre los de "saldo agotado" —
        // son dos problemas distintos (subir de plan vs. recargar).
        if (requireCapability.requiresBudget()) {
            guard.assertBudgetAvailable(commercialId);
        }

        // Si todo es correcto, continuar con la ejecución del método
        return joinPoint.proceed();
    }

    private boolean isAssetLimit(RequirePlanCapability.Capability cap) {
        return switch (cap) {
            case MAX_PRODUCTS, MAX_ADS, MAX_BRANDED_GAMES, MAX_SURVEYS -> true;
            default -> false;
        };
    }

    private Long extractCommercialId(ProceedingJoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            if (paramName.equals(paramNames[i]) && args[i] != null) {
                return (Long) args[i];
            }
        }
        return null;
    }
}