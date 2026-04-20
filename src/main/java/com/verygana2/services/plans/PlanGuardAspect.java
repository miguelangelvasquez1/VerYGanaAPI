package com.verygana2.services.plans;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import com.verygana2.models.plans.RequirePlanCapability;

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
        for (RequirePlanCapability.Capability cap : requireCapability.value()) {
            guard.assertCapability(commercialId, cap);
        }

        // Si todo es correcto, continuar con la ejecución del método
        return joinPoint.proceed();
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