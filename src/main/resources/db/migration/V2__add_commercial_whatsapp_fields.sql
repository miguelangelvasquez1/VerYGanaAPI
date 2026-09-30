-- Agrega disponibilidad de WhatsApp y número de contacto al perfil comercial.
-- Ver CommercialDetails#whatsappAvailable / #whatsappNumber.
ALTER TABLE commercial_details
    ADD COLUMN whatsapp_available TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN whatsapp_number VARCHAR(20) NULL;
