package com.verygana2.services.plans;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.finance.plans.RequirePlanCapability.Capability;
import com.verygana2.services.plans.PlanFeatureGuard.PlanCapabilityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PlanGuardAspect}: extracción del parámetro commercialId por
 * nombre (default y custom), validación de TODAS las capacidades en orden, y la
 * decisión de invocar assertBudgetAvailable/proceed() solo cuando corresponde.
 * No levanta Spring/AspectJ — invoca el método @Around directamente con mocks puros
 * de ProceedingJoinPoint/MethodSignature.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanGuardAspect")
class PlanGuardAspectTest {

    @Mock private PlanFeatureGuard planFeatureGuard;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature methodSignature;

    private PlanGuardAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new PlanGuardAspect(planFeatureGuard);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
    }

    /** Clase auxiliar solo para obtener instancias reales de @RequirePlanCapability vía reflexión. */
    private static class AnnotatedMethods {
        @RequirePlanCapability(value = { Capability.CAN_ADVERTISE })
        void defaultParamName(Long commercialId) { /* no-op */ }

        @RequirePlanCapability(value = { Capability.CAN_ADVERTISE }, commercialIdParam = "bizId")
        void customParamName(Long bizId) { /* no-op */ }

        @RequirePlanCapability(value = { Capability.CAN_ADVERTISE, Capability.CAN_USE_GAMES, Capability.CAN_USE_SURVEYS })
        void multipleCapabilities(Long commercialId) { /* no-op */ }

        @RequirePlanCapability(value = { Capability.CAN_ADVERTISE }, requiresBudget = true)
        void withBudget(Long commercialId) { /* no-op */ }

        @RequirePlanCapability(value = { Capability.CAN_ADVERTISE }, requiresBudget = false)
        void withoutBudget(Long commercialId) { /* no-op */ }
    }

    private RequirePlanCapability annotationOf(String methodName) throws NoSuchMethodException {
        return AnnotatedMethods.class.getDeclaredMethod(methodName, Long.class).getAnnotation(RequirePlanCapability.class);
    }

    // ─── Extracción de commercialId ─────────────────────────────────────────

    @Nested
    @DisplayName("extracción de commercialId")
    class ExtractCommercialId {

        @Test
        @DisplayName("parámetro llamado 'commercialId' (default): lo extrae correctamente")
        void defaultParamName_extractsCorrectly() throws Throwable {
            when(methodSignature.getParameterNames()).thenReturn(new String[] { "commercialId" });
            when(joinPoint.getArgs()).thenReturn(new Object[] { 42L });
            when(joinPoint.proceed()).thenReturn("ok");

            Object result = aspect.checkPlanCapabilities(joinPoint, annotationOf("defaultParamName"));

            assertThat(result).isEqualTo("ok");
            verify(planFeatureGuard).assertCapability(42L, Capability.CAN_ADVERTISE);
        }

        @Test
        @DisplayName("commercialIdParam() apunta a otro nombre de parámetro: lo extrae correctamente")
        void customParamName_extractsCorrectly() throws Throwable {
            when(methodSignature.getParameterNames()).thenReturn(new String[] { "bizId" });
            when(joinPoint.getArgs()).thenReturn(new Object[] { 77L });
            when(joinPoint.proceed()).thenReturn("ok");

            Object result = aspect.checkPlanCapabilities(joinPoint, annotationOf("customParamName"));

            assertThat(result).isEqualTo("ok");
            verify(planFeatureGuard).assertCapability(77L, Capability.CAN_ADVERTISE);
        }

        @Test
        @DisplayName("ningún parámetro coincide con el nombre esperado: lanza IllegalArgumentException")
        void noMatchingParam_throwsIllegalArgumentException() throws Throwable {
            when(methodSignature.getParameterNames()).thenReturn(new String[] { "otherParam" });
            when(joinPoint.getArgs()).thenReturn(new Object[] { 42L });

            assertThatThrownBy(() -> aspect.checkPlanCapabilities(joinPoint, annotationOf("defaultParamName")))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(joinPoint, never()).proceed();
        }
    }

    // ─── Validación de capacidades ──────────────────────────────────────────

    @Nested
    @DisplayName("validación de capacidades")
    class CapabilityValidation {

        @Test
        @DisplayName("valida TODAS las capacidades de value() en orden cuando todas pasan")
        void validatesAllCapabilitiesInOrder_whenAllPass() throws Throwable {
            when(methodSignature.getParameterNames()).thenReturn(new String[] { "commercialId" });
            when(joinPoint.getArgs()).thenReturn(new Object[] { 1L });
            when(joinPoint.proceed()).thenReturn("ok");

            aspect.checkPlanCapabilities(joinPoint, annotationOf("multipleCapabilities"));

            var order = inOrder(planFeatureGuard);
            order.verify(planFeatureGuard).assertCapability(1L, Capability.CAN_ADVERTISE);
            order.verify(planFeatureGuard).assertCapability(1L, Capability.CAN_USE_GAMES);
            order.verify(planFeatureGuard).assertCapability(1L, Capability.CAN_USE_SURVEYS);
        }

        @Test
        @DisplayName("si la primera capacidad falla, no sigue verificando las demás ni el budget, y propaga sin llamar proceed()")
        void firstCapabilityFails_stopsAndPropagates() throws Throwable {
            when(methodSignature.getParameterNames()).thenReturn(new String[] { "commercialId" });
            when(joinPoint.getArgs()).thenReturn(new Object[] { 1L });
            doThrow(new PlanCapabilityException("no permitido"))
                    .when(planFeatureGuard).assertCapability(1L, Capability.CAN_ADVERTISE);

            assertThatThrownBy(() -> aspect.checkPlanCapabilities(joinPoint, annotationOf("multipleCapabilities")))
                    .isInstanceOf(PlanCapabilityException.class);

            verify(planFeatureGuard, never()).assertCapability(eq(1L), eq(Capability.CAN_USE_GAMES));
            verify(planFeatureGuard, never()).assertCapability(eq(1L), eq(Capability.CAN_USE_SURVEYS));
            verify(planFeatureGuard, never()).assertBudgetAvailable(anyLong());
            verify(joinPoint, never()).proceed();
        }
    }

    // ─── Budget ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assertBudgetAvailable")
    class Budget {

        @Test
        @DisplayName("todas las capacidades pasan y requiresBudget()==true: llama assertBudgetAvailable")
        void allCapabilitiesPassAndRequiresBudget_callsAssertBudgetAvailable() throws Throwable {
            when(methodSignature.getParameterNames()).thenReturn(new String[] { "commercialId" });
            when(joinPoint.getArgs()).thenReturn(new Object[] { 1L });
            when(joinPoint.proceed()).thenReturn("ok");

            aspect.checkPlanCapabilities(joinPoint, annotationOf("withBudget"));

            verify(planFeatureGuard).assertBudgetAvailable(1L);
        }

        @Test
        @DisplayName("requiresBudget()==false: nunca llama assertBudgetAvailable aunque las capacidades pasen")
        void requiresBudgetFalse_neverCallsAssertBudgetAvailable() throws Throwable {
            when(methodSignature.getParameterNames()).thenReturn(new String[] { "commercialId" });
            when(joinPoint.getArgs()).thenReturn(new Object[] { 1L });
            when(joinPoint.proceed()).thenReturn("ok");

            aspect.checkPlanCapabilities(joinPoint, annotationOf("withoutBudget"));

            verify(planFeatureGuard, never()).assertBudgetAvailable(any());
        }

        @Test
        @DisplayName("assertBudgetAvailable lanza: propaga la excepción sin llamar proceed()")
        void assertBudgetAvailableThrows_propagatesWithoutProceed() throws Throwable {
            when(methodSignature.getParameterNames()).thenReturn(new String[] { "commercialId" });
            when(joinPoint.getArgs()).thenReturn(new Object[] { 1L });
            doThrow(new PlanFeatureGuard.BudgetSuspendedException("sin saldo"))
                    .when(planFeatureGuard).assertBudgetAvailable(1L);

            assertThatThrownBy(() -> aspect.checkPlanCapabilities(joinPoint, annotationOf("withBudget")))
                    .isInstanceOf(PlanFeatureGuard.BudgetSuspendedException.class);

            verify(joinPoint, never()).proceed();
        }
    }

    // ─── proceed() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("proceed() se invoca y su resultado se retorna solo cuando todas las validaciones pasan")
    void proceedInvokedAndResultReturned_onlyWhenAllValidationsPass() throws Throwable {
        when(methodSignature.getParameterNames()).thenReturn(new String[] { "commercialId" });
        when(joinPoint.getArgs()).thenReturn(new Object[] { 1L });
        when(joinPoint.proceed()).thenReturn("proceed-result");

        Object result = aspect.checkPlanCapabilities(joinPoint, annotationOf("defaultParamName"));

        assertThat(result).isEqualTo("proceed-result");
        verify(joinPoint).proceed();
    }
}
