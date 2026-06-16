package com.verygana2.services.surveys;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.survey.SurveyAnalyticsDTO;
import com.verygana2.dtos.survey.SurveyResponseDetailDTO;
import com.verygana2.models.surveys.QuestionOption;
import com.verygana2.models.surveys.Survey;
import com.verygana2.models.surveys.SurveyAnswer;
import com.verygana2.models.surveys.SurveyQuestion;
import com.verygana2.models.surveys.SurveySession;
import com.verygana2.repositories.surveys.SurveyRepository;
import com.verygana2.repositories.surveys.SurveySessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SurveyResponseService {

    private final SurveySessionRepository sessionRepository;
    private final SurveyRepository surveyRepository;

    @Transactional(readOnly = true)
    public PagedResponse<SurveyResponseDetailDTO> getPagedResponses(Long surveyId, Pageable pageable) {
        return PagedResponse.from(
                sessionRepository.findBySurveyId(surveyId, pageable).map(this::toDetailDTO));
    }

    @Transactional(readOnly = true)
    public SurveyAnalyticsDTO getAnalytics(Long surveyId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + surveyId));

        List<SurveySession> allSessions = sessionRepository.findBySurveyIdWithAnswers(surveyId);

        int total     = allSessions.size();
        int completed = (int) allSessions.stream()
                .filter(s -> s.getStatus() == SurveySession.SessionStatus.COMPLETED)
                .count();

        double completionRate = total == 0 ? 0.0
                : Math.round((completed * 1000.0 / total)) / 10.0;

        Double avgMinutes = allSessions.stream()
                .filter(s -> s.getCompletedAt() != null && s.getStartedAt() != null)
                .mapToLong(s -> Duration.between(s.getStartedAt(), s.getCompletedAt()).toMinutes())
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);

        List<SurveyAnalyticsDTO.QuestionStatDTO> questionStats = survey.getQuestions()
                .stream()
                .sorted(Comparator.comparingInt(SurveyQuestion::getOrderIndex))
                .map(q -> buildQuestionStat(q, allSessions))
                .collect(Collectors.toList());

        return SurveyAnalyticsDTO.builder()
                .surveyId(surveyId)
                .surveyTitle(survey.getTitle())
                .totalResponses(total)
                .completedResponses(completed)
                .completionRate(completionRate)
                .averageCompletionMinutes(avgMinutes)
                .questionStats(questionStats)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] exportAsCsv(Long surveyId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + surveyId));

        List<SurveySession> sessions = sessionRepository.findBySurveyIdWithAnswers(surveyId);
        List<SurveyQuestion> questions = survey.getQuestions().stream()
                .sorted(Comparator.comparingInt(SurveyQuestion::getOrderIndex))
                .collect(Collectors.toList());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

            List<String> headers = new ArrayList<>(
                    List.of("ID Sesión", "ID Consumidor", "Nombre", "Estado",
                            "Iniciada", "Completada"));
            questions.forEach(q -> headers.add(q.getText()));
            writer.println(String.join(",", headers.stream()
                    .map(h -> "\"" + h.replace("\"", "\"\"") + "\"")
                    .collect(Collectors.toList())));

            for (SurveySession session : sessions) {
                Map<Long, SurveyAnswer> answerMap = session.getAnswers().stream()
                        .collect(Collectors.toMap(
                                a -> a.getQuestion().getId(),
                                a -> a));

                List<String> row = new ArrayList<>();
                row.add(String.valueOf(session.getId()));
                row.add(String.valueOf(session.getConsumer().getId()));
                row.add(csvCell(null));
                row.add(session.getStatus().name());
                row.add(session.getStartedAt() != null ? session.getStartedAt().toString() : "");
                row.add(session.getCompletedAt() != null ? session.getCompletedAt().toString() : "");

                for (SurveyQuestion q : questions) {
                    SurveyAnswer ans = answerMap.get(q.getId());
                    row.add(csvCell(ans != null ? answerText(ans) : ""));
                }
                writer.println(String.join(",", row));
            }
        }
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] exportAsXlsx(Long surveyId) {
        try {
            @SuppressWarnings("unused")
            Class<?> wbClass = Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");

            Survey survey = surveyRepository.findById(surveyId)
                    .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + surveyId));

            List<SurveySession> sessions = sessionRepository.findBySurveyIdWithAnswers(surveyId);
            List<SurveyQuestion> questions = survey.getQuestions().stream()
                    .sorted(Comparator.comparingInt(SurveyQuestion::getOrderIndex))
                    .collect(Collectors.toList());

            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                    new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Respuestas");

            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            List<String> headers = new ArrayList<>(
                    List.of("ID Sesión", "ID Consumidor", "Nombre", "Estado",
                            "Iniciada", "Completada"));
            questions.forEach(q -> headers.add(q.getText()));

            for (int i = 0; i < headers.size(); i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.autoSizeColumn(i);
            }

            int rowNum = 1;
            for (SurveySession session : sessions) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                Map<Long, SurveyAnswer> answerMap = session.getAnswers().stream()
                        .collect(Collectors.toMap(
                                a -> a.getQuestion().getId(), a -> a));

                row.createCell(0).setCellValue(session.getId());
                row.createCell(1).setCellValue(session.getConsumer().getId());
                row.createCell(2).setCellValue("");
                row.createCell(3).setCellValue(session.getStatus().name());
                row.createCell(4).setCellValue(
                        session.getStartedAt() != null ? session.getStartedAt().toString() : "");
                row.createCell(5).setCellValue(
                        session.getCompletedAt() != null ? session.getCompletedAt().toString() : "");

                for (int i = 0; i < questions.size(); i++) {
                    SurveyAnswer ans = answerMap.get(questions.get(i).getId());
                    row.createCell(6 + i).setCellValue(ans != null ? answerText(ans) : "");
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();
            return out.toByteArray();

        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException(
                    "Apache POI is not on the classpath. Add poi-ooxml to pom.xml to enable XLSX export.", e);
        } catch (Exception e) {
            throw new RuntimeException("Error generating XLSX file", e);
        }
    }

    private SurveyResponseDetailDTO toDetailDTO(SurveySession session) {
        List<SurveyResponseDetailDTO.AnswerDetailDTO> answers = session.getAnswers()
                .stream()
                .map(this::toAnswerDTO)
                .collect(Collectors.toList());

        return SurveyResponseDetailDTO.builder()
                .id(session.getId())
                .consumerId(session.getConsumer().getId())
                .consumerName(null)
                .status(session.getStatus().name())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .answers(answers)
                .build();
    }

    private SurveyResponseDetailDTO.AnswerDetailDTO toAnswerDTO(SurveyAnswer answer) {
        SurveyQuestion q = answer.getQuestion();

        List<String> multiTexts = answer.getSelectedOptions() != null
                ? answer.getSelectedOptions().stream()
                        .map(QuestionOption::getText)
                        .collect(Collectors.toList())
                : List.of();

        return SurveyResponseDetailDTO.AnswerDetailDTO.builder()
                .questionId(q.getId())
                .questionText(q.getText())
                .questionType(q.getType().name())
                .textAnswer(answer.getTextAnswer())
                .selectedOptionText(
                        answer.getSelectedOption() != null
                                ? answer.getSelectedOption().getText()
                                : null)
                .selectedOptionTexts(multiTexts)
                .build();
    }

    private SurveyAnalyticsDTO.QuestionStatDTO buildQuestionStat(
            SurveyQuestion question, List<SurveySession> sessions) {

        List<SurveyAnswer> answers = sessions.stream()
                .flatMap(s -> s.getAnswers().stream())
                .filter(a -> a.getQuestion().getId().equals(question.getId()))
                .collect(Collectors.toList());

        int totalAnswers = answers.size();

        return switch (question.getType()) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE, YES_NO ->
                buildChoiceStat(question, answers, totalAnswers);
            case RATING ->
                buildRatingStat(question, answers, totalAnswers);
            case TEXT ->
                buildTextStat(question, answers, totalAnswers);
        };
    }

    private SurveyAnalyticsDTO.QuestionStatDTO buildChoiceStat(
            SurveyQuestion question, List<SurveyAnswer> answers, int total) {

        Map<String, Integer> counts = new LinkedHashMap<>();
        if (!question.getOptions().isEmpty()) {
            question.getOptions().forEach(o -> counts.put(o.getText(), 0));
        } else if (question.getType() == SurveyQuestion.QuestionType.YES_NO) {
            counts.put("SI", 0);
            counts.put("NO", 0);
        }

        for (SurveyAnswer a : answers) {
            if (a.getSelectedOption() != null) {
                counts.merge(a.getSelectedOption().getText(), 1, Integer::sum);
            } else if (a.getSelectedOptions() != null && !a.getSelectedOptions().isEmpty()) {
                a.getSelectedOptions().forEach(o -> counts.merge(o.getText(), 1, Integer::sum));
            } else if (a.getTextAnswer() != null && !a.getTextAnswer().isBlank()) {
                String key = normalizeYesNo(a.getTextAnswer().trim());
                counts.merge(key, 1, Integer::sum);
            }
        }

        List<SurveyAnalyticsDTO.OptionStatDTO> optionStats = counts.entrySet().stream()
                .map(e -> SurveyAnalyticsDTO.OptionStatDTO.builder()
                        .optionText(e.getKey())
                        .count(e.getValue())
                        .percentage(total == 0 ? 0.0
                                : Math.round(e.getValue() * 1000.0 / total) / 10.0)
                        .build())
                .collect(Collectors.toList());

        return SurveyAnalyticsDTO.QuestionStatDTO.builder()
                .questionId(question.getId())
                .questionText(question.getText())
                .questionType(question.getType().name())
                .totalAnswers(total)
                .optionStats(optionStats)
                .textAnswers(List.of())
                .build();
    }

    private String normalizeYesNo(String raw) {
        return switch (raw.toUpperCase()) {
            case "YES", "SI", "SÍ", "TRUE", "1" -> "SI";
            case "NO", "FALSE", "0"              -> "NO";
            default                               -> raw.toUpperCase();
        };
    }

    private SurveyAnalyticsDTO.QuestionStatDTO buildRatingStat(
            SurveyQuestion question, List<SurveyAnswer> answers, int total) {

        Map<String, Integer> ratingCounts = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) ratingCounts.put(String.valueOf(i), 0);

        for (SurveyAnswer a : answers) {
            if (a.getTextAnswer() != null) {
                ratingCounts.merge(a.getTextAnswer().trim(), 1, Integer::sum);
            }
        }

        List<SurveyAnalyticsDTO.OptionStatDTO> optionStats = ratingCounts.entrySet().stream()
                .map(e -> SurveyAnalyticsDTO.OptionStatDTO.builder()
                        .optionText(e.getKey())
                        .count(e.getValue())
                        .percentage(total == 0 ? 0.0
                                : Math.round(e.getValue() * 1000.0 / total) / 10.0)
                        .build())
                .collect(Collectors.toList());

        Double avg = answers.stream()
                .filter(a -> a.getTextAnswer() != null)
                .mapToInt(a -> {
                    try { return Integer.parseInt(a.getTextAnswer().trim()); }
                    catch (NumberFormatException ex) { return 0; }
                })
                .filter(v -> v > 0)
                .average()
                .stream().boxed().findFirst().orElse(null);

        return SurveyAnalyticsDTO.QuestionStatDTO.builder()
                .questionId(question.getId())
                .questionText(question.getText())
                .questionType(question.getType().name())
                .totalAnswers(total)
                .optionStats(optionStats)
                .averageRating(avg != null ? Math.round(avg * 100.0) / 100.0 : null)
                .textAnswers(List.of())
                .build();
    }

    private SurveyAnalyticsDTO.QuestionStatDTO buildTextStat(
            SurveyQuestion question, List<SurveyAnswer> answers, int total) {

        List<String> samples = answers.stream()
                .map(SurveyAnswer::getTextAnswer)
                .filter(t -> t != null && !t.isBlank())
                .limit(50)
                .collect(Collectors.toList());

        return SurveyAnalyticsDTO.QuestionStatDTO.builder()
                .questionId(question.getId())
                .questionText(question.getText())
                .questionType(question.getType().name())
                .totalAnswers(total)
                .optionStats(List.of())
                .textAnswers(samples)
                .build();
    }

    private String answerText(SurveyAnswer ans) {
        if (ans.getTextAnswer() != null) return ans.getTextAnswer();
        if (ans.getSelectedOption() != null) return ans.getSelectedOption().getText();
        if (ans.getSelectedOptions() != null && !ans.getSelectedOptions().isEmpty()) {
            return ans.getSelectedOptions().stream()
                    .map(QuestionOption::getText)
                    .collect(Collectors.joining(" | "));
        }
        return "";
    }

    private String csvCell(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
