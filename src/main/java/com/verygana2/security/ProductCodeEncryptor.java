package com.verygana2.security;

/**
 * CodeEncryptor dedicado a códigos/licencias de producto (ver CodeEncryptionConfig).
 * Un tipo propio permite que Spring resuelva la inyección por tipo, sin
 * depender de que Lombok copie un @Qualifier al constructor generado.
 */
public class ProductCodeEncryptor extends CodeEncryptor {

    public ProductCodeEncryptor(String aesHexKey, String hmacHexKey) {
        super(aesHexKey, hmacHexKey);
    }
}
