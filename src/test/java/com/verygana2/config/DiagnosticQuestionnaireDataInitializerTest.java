package com.verygana2.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.models.commercial.diagnostic.DiagnosticQuestion;
import com.verygana2.models.commercial.diagnostic.DiagnosticQuestionnaire;
import com.verygana2.repositories.commercial.DiagnosticQuestionnaireRepository;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:diagnostic-questionnaire-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("DiagnosticQuestionnaireDataInitializer — siembra desde el JSON")
class DiagnosticQuestionnaireDataInitializerTest {

    @Autowired
    DiagnosticQuestionnaireRepository repository;

    @Test
    @DisplayName("carga el cuestionario v1 completo y es idempotente por versión")
    void seedsFromJsonAndIsIdempotent() throws Exception {
        DiagnosticQuestionnaireDataInitializer initializer =
                new DiagnosticQuestionnaireDataInitializer(repository, new ObjectMapper());

        initializer.run(null);
        initializer.run(null); // segunda corrida: no debe duplicar

        assertThat(repository.findAll()).hasSize(1);

        DiagnosticQuestionnaire q = repository.findFirstByActiveTrueOrderByVersionDesc().orElseThrow();
        assertThat(q.getVersion()).isEqualTo(1);
        assertThat(q.isActive()).isTrue();
        assertThat(q.getOpeningActions()).containsExactly(
                "Comenzar", "Conocer primero las modalidades", "Guardar y continuar después", "Cancelar");
        assertThat(q.getSections()).hasSize(10);

        long questionCount = q.getSections().stream().mapToLong(s -> s.getQuestions().size()).sum();
        assertThat(questionCount).isEqualTo(43);

        DiagnosticQuestion d3 = q.getSections().stream()
                .flatMap(s -> s.getQuestions().stream())
                .filter(question -> question.getCode().equals("D-3"))
                .findFirst().orElseThrow();
        assertThat(d3.getDependsOnQuestionCode()).isEqualTo("D-2");
        assertThat(d3.getDependsOnValues()).isEqualTo("SI,CASOS_AISLADOS");
        assertThat(d3.getOptions()).anySatisfy(o -> {
            assertThat(o.getValue()).isEqualTo("NINGUNO");
            assertThat(o.isExclusive()).isTrue();
        });
    }
}
