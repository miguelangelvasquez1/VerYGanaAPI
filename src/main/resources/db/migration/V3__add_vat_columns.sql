-- MP-02 Frente 2: IVA en depósitos/suscripción (adicional) y en comisión de venta (extraído).
-- Ver Plan.servicesCommissionPct, Investment/Subscription.vatAmountCents,
-- PurchaseItem/Purchase.commissionVatCents, TreasuryAccountCode.TAX_RESERVE.

-- Comisión de venta para comerciales STANDARD con vocación SERVICES.
ALTER TABLE plans ADD COLUMN services_commission_pct INT NOT NULL DEFAULT 0;
UPDATE plans SET services_commission_pct = 15 WHERE code = 'STANDARD';

-- IVA cobrado adicional sobre el depósito de inversión / la suscripción BASIC.
-- 0 es el valor correcto para filas históricas: antes de este cambio no se cobraba IVA.
ALTER TABLE investments ADD COLUMN vat_amount_cents BIGINT NOT NULL DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN vat_amount_cents BIGINT NOT NULL DEFAULT 0;

-- Snapshot de la Vocación Empresarial del comercial (commercial_details.commercial_activity_type)
-- al momento de la compra, y porción de IVA dentro de la comisión ya retenida.
ALTER TABLE purchase_items ADD COLUMN commercial_activity_type_at_purchase ENUM('PRODUCTS','SERVICES') NULL;
ALTER TABLE purchase_items ADD COLUMN commission_vat_cents BIGINT NOT NULL DEFAULT 0;
ALTER TABLE purchases ADD COLUMN commission_vat_cents BIGINT NOT NULL DEFAULT 0;

-- Nueva cuenta de tesorería para IVA pendiente de declarar/pagar a la DIAN (ver
-- TreasuryAccountCode.TAX_RESERVE) y los movimientos que la alimentan.
ALTER TABLE treasury_accounts MODIFY COLUMN code
    ENUM('EXTERNAL_INCOME','FORTIFICATION','KEYS_RESERVE','OPERATIONS','PAYOUTS_PENDING','TAX_RESERVE') NOT NULL;

ALTER TABLE treasury_movements MODIFY COLUMN concept
    ENUM('BASIC_PLAN_SUBSCRIPTION','BASIC_PLAN_SUBSCRIPTION_VAT','BUSINESS_DEPOSIT_FORTIFICATION',
         'BUSINESS_DEPOSIT_KEYS','BUSINESS_DEPOSIT_OPERATIONS','BUSINESS_DEPOSIT_VAT',
         'COMMISSION_RETENTION','COMMISSION_REVERSAL','COMMISSION_VAT_RETENTION','COMMISSION_VAT_REVERSAL',
         'COPAYMENT_KEYS_CONVERSION','EXPIRED_KEYS_TO_FORTIFICATION','FORTIFICATION_PURCHASE',
         'PAYOUT_TO_BUSINESS','REFUND_CASH_TO_OPERATIONS','REFUND_KEYS_TO_RESERVE','REFUND_TO_BUYER',
         'SALE_TO_PAYOUT_PENDING') NOT NULL;

-- IVA sobre el monto que el empresario aceptó en el paso de plan/resumen económico
-- (snapshot congelado para el contrato). Nullable igual que investment_amount_cents_snapshot
-- (null para BASIC) y monthly_fee_cents_snapshot (null para STANDARD/PREMIUM): cada
-- empresario solo llena uno de los dos pares.
ALTER TABLE commercial_onboarding ADD COLUMN investment_vat_cents_snapshot BIGINT NULL;
ALTER TABLE commercial_onboarding ADD COLUMN monthly_fee_vat_cents_snapshot BIGINT NULL;

ALTER TABLE raffles DROP COLUMN requires_pet;
