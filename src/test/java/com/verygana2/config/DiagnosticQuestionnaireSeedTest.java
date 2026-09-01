package com.verygana2.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.user.commercial.onboarding.CommercialDiagnosticRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticQuestionnaireResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticQuestionnaireResponseDTO.Option;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticQuestionnaireResponseDTO.Question;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticQuestionnaireResponseDTO.Section;
import com.verygana2.models.enums.commercial.diagnostic.DiagnosticQuestionType;

/**
 * El seed {@code db/seed/diagnostic-questionnaire-v1.json} debe estar alineado con
 * {@code CommercialDiagnosticRequestDTO} y sus enums — este test cae si alguien
 * introduce un fieldName o un value que el backend no sabe recibir.
 */
@DisplayName("Seed del cuestionario de diagnóstico — coherencia con el DTO de respuestas")
class DiagnosticQuestionnaireSeedTest {

    /** Debe reflejar exactamente los checks de CommercialOnboardingServiceImpl.validateDiagnostic(). */
    private static final Set<String> SERVER_REQUIRED_FIELDS = Set.of(
            "mainActivity", "marketReachStructure", "sellsDirectlyAndConcentrated",
            "directSaleToConsumer", "desiredActiveOffers", "metricsNeeded",
            "independentEntrepreneursHelp", "typeAMonthlyFeeViable",
            "typeBInvestmentCapacity", "acceptsPremiumBrandFocus");

    private static DiagnosticQuestionnaireResponseDTO spec;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = new ClassPathResource("db/seed/diagnostic-questionnaire-v1.json").getInputStream()) {
            spec = new ObjectMapper().readValue(in, DiagnosticQuestionnaireResponseDTO.class);
        }
    }

    private static List<Question> questions() {
        return spec.sections().stream().flatMap(s -> s.questions().stream()).toList();
    }

    @Test
    @DisplayName("cada fieldName existe como campo de CommercialDiagnosticRequestDTO")
    void fieldNamesExistOnRequestDto() {
        for (Question q : questions()) {
            assertThatCode(() -> CommercialDiagnosticRequestDTO.class.getDeclaredField(q.fieldName()))
                    .as("%s -> campo '%s'", q.code(), q.fieldName())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("cada value de opción es una constante válida del enum del campo (o true/false para BOOLEAN)")
    void optionValuesMatchFieldType() throws Exception {
        for (Question q : questions()) {
            Field field = CommercialDiagnosticRequestDTO.class.getDeclaredField(q.fieldName());

            if (q.type() == DiagnosticQuestionType.BOOLEAN) {
                assertThat(field.getType()).as("%s debe ser Boolean", q.code()).isEqualTo(Boolean.class);
                assertThat(q.options()).extracting(Option::value)
                        .as("%s opciones", q.code()).containsExactlyInAnyOrder("true", "false");
                continue;
            }

            Class<?> enumType = enumTypeOf(field);
            assertThat(enumType.isEnum()).as("%s -> %s no resuelve a un enum", q.code(), q.fieldName()).isTrue();
            Set<String> valid = Arrays.stream(enumType.getEnumConstants())
                    .map(e -> ((Enum<?>) e).name()).collect(Collectors.toSet());
            assertThat(q.options()).extracting(Option::value)
                    .as("%s opciones fuera del enum %s", q.code(), enumType.getSimpleName())
                    .allMatch(valid::contains);

            if (q.type() == DiagnosticQuestionType.MULTI_CHOICE) {
                assertThat(Collection.class.isAssignableFrom(field.getType()))
                        .as("%s multi-choice debe mapear a una colección", q.code()).isTrue();
                if (q.ordered()) {
                    assertThat(q.maxSelections())
                            .as("%s multi-choice ordenada sin maxSelections", q.code()).isNotNull();
                }
            } else {
                assertThat(field.getType()).as("%s", q.code()).isEqualTo(enumType);
            }
        }
    }

    @Test
    @DisplayName("cada dependsOn apunta a una pregunta anterior existente")
    void dependenciesPointToEarlierQuestions() {
        List<String> order = questions().stream().map(Question::code).toList();
        for (Question q : questions()) {
            if (q.dependsOn() == null) {
                continue;
            }
            String dep = q.dependsOn().questionCode();
            assertThat(order).as("%s depende de %s inexistente", q.code(), dep).contains(dep);
            assertThat(order.indexOf(dep))
                    .as("%s depende de %s que aparece después en el flujo", q.code(), dep)
                    .isLessThan(order.indexOf(q.code()));
            assertThat(q.dependsOn().values()).as("%s dependsOn sin valores", q.code()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("las preguntas required del seed son exactamente las que valida el servidor")
    void requiredMatchesServerValidation() {
        Set<String> requiredInSeed = questions().stream()
                .filter(Question::required).map(Question::fieldName).collect(Collectors.toSet());
        assertThat(requiredInSeed).containsExactlyInAnyOrderElementsOf(SERVER_REQUIRED_FIELDS);
    }

    @Test
    @DisplayName("ninguna pregunta required es adaptativa (dependsOn == null)")
    void requiredQuestionsAreNeverConditional() {
        assertThat(questions()).filteredOn(Question::required)
                .allSatisfy(q -> assertThat(q.dependsOn()).as("%s es required y condicional", q.code()).isNull());
    }

    @Test
    @DisplayName("no hay codes ni fieldNames duplicados")
    void noDuplicates() {
        assertThat(questions().stream().map(Question::code).toList()).doesNotHaveDuplicates();
        assertThat(questions().stream().map(Question::fieldName).toList()).doesNotHaveDuplicates();
        assertThat(spec.sections().stream().map(Section::code).toList()).doesNotHaveDuplicates();
    }

    private static Class<?> enumTypeOf(Field field) {
        if (Collection.class.isAssignableFrom(field.getType())) {
            ParameterizedType pt = (ParameterizedType) field.getGenericType();
            return (Class<?>) pt.getActualTypeArguments()[0];
        }
        return field.getType();
    }
}
