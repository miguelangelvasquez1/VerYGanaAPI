-- Renombra la feature CAN_HAVE_PETS -> CAN_USE_PETS para que coincida con el código
-- (PlanDataInitializer, CommercialOnboardingMapper, EffectivePlanResolver, etc).
--
-- PlanDataInitializer es idempotente (solo siembra si la tabla plans está vacía), así
-- que el rename hecho en Java no se propaga a ningún entorno que ya tuviera el catálogo
-- sembrado con el código viejo — sin este UPDATE, plan.getBoolFeature("CAN_USE_PETS", false)
-- nunca encuentra la fila (sigue con code='CAN_HAVE_PETS') y cae siempre al default false.
UPDATE features SET code = 'CAN_USE_PETS', name = 'Puede usar mascotas' WHERE code = 'CAN_HAVE_PETS';
