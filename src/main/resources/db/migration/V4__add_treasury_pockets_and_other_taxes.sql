-- MP-04: bolsillos de tesoreria nuevos (conectividad, infraestructura y nomina/
-- remuneracion VerYGana). Existen como cuentas de tesoreria pero NO se alimentan
-- del reparto de depositos STANDARD/PREMIUM (TreasuryServiceImpl#distributeDeposit
-- sigue siendo 60/10/30) — se nutriran de otras operaciones, fuera de alcance por
-- ahora. Ver TreasuryAccountCode, TreasuryDataInitializer.

-- Campo inerte por ahora (ICA/coljuegos futuros) — ver Investment.otherTaxesCents.
ALTER TABLE investments ADD COLUMN other_taxes_cents BIGINT NOT NULL DEFAULT 0;

ALTER TABLE treasury_accounts MODIFY COLUMN code
    ENUM('CONNECTIVITY','EXTERNAL_INCOME','FORTIFICATION','INFRASTRUCTURE','KEYS_RESERVE',
         'OPERATIONS','PAYOUTS_PENDING','PAYROLL','TAX_RESERVE') NOT NULL;

ALTER TABLE commercial_onboarding RENAME COLUMN can_have_pets_override to can_use_pets_override;
