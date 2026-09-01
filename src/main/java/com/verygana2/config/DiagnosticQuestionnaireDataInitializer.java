package com.verygana2.config;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticQuestionnaireResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticQuestionnaireResponseDTO.Option;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticQuestionnaireResponseDTO.Question;
import com.verygana2.dtos.user.commercial.onboarding.DiagnosticQuestionnaireResponseDTO.Section;
import com.verygana2.models.commercial.diagnostic.DiagnosticQuestion;
import com.verygana2.models.commercial.diagnostic.DiagnosticQuestionOption;
import com.verygana2.models.commercial.diagnostic.DiagnosticQuestionnaire;
import com.verygana2.models.commercial.diagnostic.DiagnosticSection;
import com.verygana2.repositories.commercial.DiagnosticQuestionnaireRepository;

import lombok.RequiredArgsConstructor;

/**
 * Siembra el catálogo del cuestionario de diagnóstico comercial desde
 * {@code db/seed/diagnostic-questionnaire-v1.json} al arrancar la app. Es
 * IDEMPOTENTE por versión: si esa versión ya existe no hace nada, lo que permite
 * agregar una v2 más adelante sin borrar la v1 (trazabilidad §18).
 *
 * @Order(3) para correr después de PlanDataInitializer (@Order(2)).
 */
@Component
@Order(3)
@RequiredArgsConstructor
public class DiagnosticQuestionnaireDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticQuestionnaireDataInitializer.class);
    private static final String RESOURCE = "db/seed/diagnostic-questionnaire-v1.json";

    private final DiagnosticQuestionnaireRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        DiagnosticQuestionnaireResponseDTO spec;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            spec = objectMapper.readValue(in, DiagnosticQuestionnaireResponseDTO.class);
        }

        if (repository.existsByVersion(spec.version())) {
            log.info("=== DiagnosticQuestionnaireDataInitializer: cuestionario v{} ya existe — omitiendo ===",
                    spec.version());
            return;
        }

        repository.save(toEntity(spec));
        log.info("=== DiagnosticQuestionnaireDataInitializer: cuestionario v{} sembrado ({} secciones) ===",
                spec.version(), spec.sections().size());
    }

    private DiagnosticQuestionnaire toEntity(DiagnosticQuestionnaireResponseDTO spec) {
        DiagnosticQuestionnaire questionnaire = new DiagnosticQuestionnaire();
        questionnaire.setVersion(spec.version());
        questionnaire.setActive(true);
        questionnaire.setOpeningMessage(spec.openingMessage());
        questionnaire.getOpeningActions().addAll(spec.openingActions());

        int sectionOrder = 0;
        for (Section sSpec : spec.sections()) {
            DiagnosticSection section = new DiagnosticSection();
            section.setCode(sSpec.code());
            section.setTitle(sSpec.title());
            section.setSubtitle(sSpec.subtitle());
            section.setDisplayOrder(sectionOrder++);

            int questionOrder = 0;
            for (Question qSpec : sSpec.questions()) {
                section.addQuestion(toQuestionEntity(qSpec, questionOrder++));
            }
            questionnaire.addSection(section);
        }
        return questionnaire;
    }

    private DiagnosticQuestion toQuestionEntity(Question qSpec, int order) {
        DiagnosticQuestion question = new DiagnosticQuestion();
        question.setCode(qSpec.code());
        question.setFieldName(qSpec.fieldName());
        question.setText(qSpec.text());
        question.setHelpText(qSpec.helpText());
        question.setType(qSpec.type());
        question.setRequired(qSpec.required());
        question.setMaxSelections(qSpec.maxSelections());
        question.setOrdered(qSpec.ordered());
        question.setDisplayOrder(order);
        if (qSpec.dependsOn() != null) {
            question.setDependsOnQuestionCode(qSpec.dependsOn().questionCode());
            question.setDependsOnValues(String.join(",", qSpec.dependsOn().values()));
        }

        int optionOrder = 0;
        for (Option oSpec : qSpec.options()) {
            DiagnosticQuestionOption option = new DiagnosticQuestionOption();
            option.setValue(oSpec.value());
            option.setLabel(oSpec.label());
            option.setExclusive(oSpec.exclusive());
            option.setDisplayOrder(optionOrder++);
            question.addOption(option);
        }
        return question;
    }
}
