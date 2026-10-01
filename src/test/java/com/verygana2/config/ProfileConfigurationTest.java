package com.verygana2.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import jakarta.validation.Validation;

/**
 * Regla #1 de CLAUDE.md: una clave que un perfil no resuelve tumba el arranque de ese
 * perfil, y no se nota hasta el despliegue. Este test lo adelanta al build.
 *
 * <p>Recorre todas las clases de {@code com.verygana2} y, por cada perfil que se
 * despliega, comprueba que resuelva:
 * <ul>
 *   <li>cada {@code @Value("${clave}")} sin default;</li>
 *   <li>cada placeholder sin default de {@code @Scheduled} (cron, fixedDelayString…);</li>
 *   <li>cada {@code @ConfigurationProperties @Validated}: que enlace y pase la validación.</li>
 * </ul>
 *
 * <p>Solo mira los yml del repo: se quitan las variables de entorno y las propiedades
 * de sistema para que el shell de quien corre el test no tape una clave faltante. Que
 * una clave apunte a {@code ${VARIABLE}} está bien: esa la pone Infisical.
 */
@DisplayName("Configuración por perfil: cada clave requerida existe en cada perfil desplegable")
class ProfileConfigurationTest {

    private static final String BASE_PACKAGE = "com.verygana2";

    /** Combinaciones de perfiles que se despliegan. El último gana, como en Spring. */
    static Stream<List<String>> deployableProfiles() {
        return Stream.of(List.of("dev"), List.of("prod"), List.of("prod", "beta"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deployableProfiles")
    @DisplayName("cada @Value y @Scheduled sin default resuelve")
    void everyRequiredPlaceholderResolves(List<String> profiles) {
        StandardEnvironment env = environmentFor(profiles);

        Set<String> missing = new TreeSet<>();
        for (Class<?> type : applicationClasses()) {
            for (Usage usage : placeholderUsages(type)) {
                for (String key : requiredKeys(usage.expression())) {
                    if (!env.containsProperty(key)) {
                        missing.add(key + "  <- " + usage.where());
                    }
                }
            }
        }

        assertThat(missing)
                .as("Claves sin default que el perfil %s no resuelve (la app no arranca)", profiles)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deployableProfiles")
    @DisplayName("cada @ConfigurationProperties @Validated enlaza y pasa la validación")
    void everyValidatedConfigurationBinds(List<String> profiles) {
        StandardEnvironment env = environmentFor(profiles);
        Binder binder = new Binder(ConfigurationPropertySources.get(env), new PropertySourcesPlaceholdersResolver(env));
        var validator = new SpringValidatorAdapter(Validation.buildDefaultValidatorFactory().getValidator());

        List<String> failures = new ArrayList<>();
        for (Class<?> type : applicationClasses()) {
            ConfigurationProperties props = type.getAnnotation(ConfigurationProperties.class);
            if (props == null || !type.isAnnotationPresent(Validated.class)) {
                continue;
            }
            String prefix = props.prefix().isEmpty() ? props.value() : props.prefix();
            try {
                binder.bindOrCreate(prefix, Bindable.of(type), new ValidationBindHandler(validator));
            } catch (BindException e) {
                failures.add(prefix + " (" + type.getSimpleName() + "): " + rootMessage(e));
            }
        }

        assertThat(failures)
                .as("Configuración validada que el perfil %s rechaza al arrancar", profiles)
                .isEmpty();
    }

    @Nested
    @DisplayName("beta (prod,beta) no toca dinero ni firmas reales")
    class BetaIsSandboxed {

        private final StandardEnvironment env = environmentFor(List.of("prod", "beta"));

        @Test
        @DisplayName("Wompi pagos y payouts apuntan a sandbox, con las llaves de sandbox")
        void wompiIsSandbox() {
            assertThat(env.getProperty("wompi.api-base-url")).contains("sandbox");
            assertThat(env.getProperty("wompi.payout.api-base-url")).contains("sandbox");
            // Sin resolver: interesa el nombre de la variable, no su valor.
            assertThat(rawValue(env, "wompi.private-key")).contains("WOMPI_SANDBOX_");
            assertThat(rawValue(env, "wompi.payout.api-key")).contains("WOMPI_SANDBOX_");
        }

        @Test
        @DisplayName("ZapSign en sandbox: los contratos del piloto no tienen validez legal")
        void zapsignIsSandbox() {
            assertThat(env.getProperty("zapsign.sandbox", Boolean.class)).isTrue();
        }

        @Test
        @DisplayName("Swagger apagado en prod y beta: la documentación de la API no queda pública")
        void swaggerIsOff() {
            for (StandardEnvironment deployed : List.of(env, environmentFor(List.of("prod")))) {
                assertThat(deployed.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
                assertThat(deployed.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
            }
        }

        @Test
        @DisplayName("prod sigue en producción: beta no se filtra a prod")
        void prodIsUntouched() {
            StandardEnvironment prod = environmentFor(List.of("prod"));
            assertThat(prod.getProperty("wompi.api-base-url")).doesNotContain("sandbox");
            assertThat(prod.getProperty("zapsign.sandbox", Boolean.class)).isFalse();
        }
    }

    // ─── Entorno ─────────────────────────────────────────────────────────────

    private static StandardEnvironment environmentFor(List<String> profiles) {
        StandardEnvironment env = new StandardEnvironment();
        var sources = env.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

        load("application.yml").forEach(sources::addLast);
        for (String profile : profiles) {
            // addFirst: el perfil que se agrega después tiene precedencia, como en Spring.
            load("application-" + profile + ".yml").forEach(sources::addFirst);
        }
        ConfigurationPropertySources.attach(env);
        return env;
    }

    private static List<PropertySource<?>> load(String file) {
        try {
            return new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + file, e);
        }
    }

    /** El valor tal como está en el yml, sin resolver placeholders. */
    private static String rawValue(StandardEnvironment env, String key) {
        for (PropertySource<?> source : env.getPropertySources()) {
            Object value = source.getProperty(key);
            if (value != null && !(source.getName().startsWith("configurationProperties"))) {
                return value.toString();
            }
        }
        return null;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    // ─── Escaneo del código ──────────────────────────────────────────────────

    private record Usage(String expression, String where) {}

    private static List<Class<?>> applicationClasses() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true);
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                classes.add(Class.forName(candidate.getBeanClassName(), false,
                        ProfileConfigurationTest.class.getClassLoader()));
            } catch (ClassNotFoundException | LinkageError e) {
                throw new IllegalStateException("No se pudo cargar " + candidate.getBeanClassName(), e);
            }
        }
        return classes;
    }

    private static List<Usage> placeholderUsages(Class<?> type) {
        List<Usage> usages = new ArrayList<>();
        String name = type.getSimpleName();

        for (var field : type.getDeclaredFields()) {
            addValue(usages, field, name + "." + field.getName());
        }
        for (Method method : type.getDeclaredMethods()) {
            addValue(usages, method, name + "." + method.getName() + "()");
            addParameters(usages, method, name);
            Scheduled scheduled = method.getAnnotation(Scheduled.class);
            if (scheduled != null) {
                String where = name + "." + method.getName() + "() @Scheduled";
                for (String expr : List.of(scheduled.cron(), scheduled.fixedDelayString(),
                        scheduled.fixedRateString(), scheduled.initialDelayString(), scheduled.zone())) {
                    if (!expr.isEmpty()) {
                        usages.add(new Usage(expr, where));
                    }
                }
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            addParameters(usages, constructor, name);
        }
        return usages;
    }

    private static void addParameters(List<Usage> usages, Executable executable, String owner) {
        for (Parameter parameter : executable.getParameters()) {
            addValue(usages, parameter, owner + "(" + parameter.getName() + ")");
        }
    }

    private static void addValue(List<Usage> usages, AnnotatedElement element, String where) {
        Value value = element.getAnnotation(Value.class);
        if (value != null) {
            usages.add(new Usage(value.value(), where));
        }
    }

    /**
     * Las claves de {@code ${...}} que no traen default. {@code ${a:${b}}} cuenta como
     * con default: si falta {@code a} se usa {@code b}, y ese caso lo cubre otro uso.
     */
    static List<String> requiredKeys(String expression) {
        List<String> keys = new ArrayList<>();
        int i = 0;
        while ((i = expression.indexOf("${", i)) >= 0) {
            int depth = 0;
            int end = i;
            while (end < expression.length()) {
                if (expression.startsWith("${", end)) {
                    depth++;
                    end += 2;
                    continue;
                }
                if (expression.charAt(end) == '}' && --depth == 0) {
                    break;
                }
                end++;
            }
            String inner = expression.substring(i + 2, Math.min(end, expression.length()));
            int colon = topLevelColon(inner);
            if (colon < 0) {
                keys.add(inner.trim());
            }
            i = end + 1;
        }
        return keys;
    }

    private static int topLevelColon(String inner) {
        int depth = 0;
        for (int k = 0; k < inner.length(); k++) {
            if (inner.startsWith("${", k)) {
                depth++;
                k++;
            } else if (inner.charAt(k) == '}') {
                depth--;
            } else if (inner.charAt(k) == ':' && depth == 0) {
                return k;
            }
        }
        return -1;
    }
}
