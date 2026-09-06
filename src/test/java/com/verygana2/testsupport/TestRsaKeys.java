package com.verygana2.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Par de llaves RSA desechable para los tests que levantan {@code SecurityConfig}.
 *
 * <p>El perfil por defecto de {@code application.yml} es {@code dev}, y
 * {@code application-dev.yml} apunta {@code rsa.private-key} a
 * {@code classpath:certs/private.pem}. Ese archivo está en .gitignore —como debe
 * ser, es una llave privada real— así que en CI no existe y el contexto de Spring
 * no arranca.
 *
 * <p>En vez de versionar una llave, se genera una al vuelo por corrida y se
 * escribe en un directorio temporal que se borra al salir de la JVM. Los tests
 * solo tienen que registrar las rutas:
 *
 * <pre>{@code
 * @DynamicPropertySource
 * static void rsaKeys(DynamicPropertyRegistry registry) {
 *     TestRsaKeys.register(registry);
 * }
 * }</pre>
 */
public final class TestRsaKeys {

    private static final Path PRIVATE_PEM;
    private static final Path PUBLIC_PEM;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            Path dir = Files.createTempDirectory("verygana-test-certs");
            dir.toFile().deleteOnExit();

            PRIVATE_PEM = write(dir.resolve("private.pem"), "PRIVATE KEY",
                    keyPair.getPrivate().getEncoded());   // PKCS#8
            PUBLIC_PEM = write(dir.resolve("public.pem"), "PUBLIC KEY",
                    keyPair.getPublic().getEncoded());    // X.509 SubjectPublicKeyInfo
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("No se pudo generar el par de llaves RSA de prueba", e);
        }
    }

    private TestRsaKeys() {
    }

    private static Path write(Path file, String label, byte[] der) throws IOException {
        String pem = "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(der)
                + "\n-----END " + label + "-----\n";
        Files.writeString(file, pem);
        file.toFile().deleteOnExit();
        return file;
    }

    /** Apunta {@code rsa.private-key} y {@code rsa.public-key} a las llaves generadas. */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("rsa.private-key", () -> "file:" + PRIVATE_PEM.toAbsolutePath());
        registry.add("rsa.public-key", () -> "file:" + PUBLIC_PEM.toAbsolutePath());
    }
}
