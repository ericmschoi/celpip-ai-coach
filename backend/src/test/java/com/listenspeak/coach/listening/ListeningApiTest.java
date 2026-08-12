package com.listenspeak.coach.listening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.listenspeak.coach.listening.domain.ListeningExercise;
import com.listenspeak.coach.listening.domain.Question;
import com.listenspeak.coach.platform.security.LocalStubAuthenticationFilter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class ListeningApiTest {

    private static final String OWNER = "owner-1";
    private static final String INTRUDER = "owner-2";

    private MockMvc mockMvc;

    @Autowired
    private ListeningExerciseRepository repository;

    @Autowired
    void setUp(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private String createExercise(String user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/listening/exercises")
                        .header(LocalStubAuthenticationFilter.HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"part\":5,\"difficulty\":\"COMPETENT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private static String idOf(String body) {
        return body.replaceFirst("(?s).*\"id\"\\s*:\\s*\"([0-9a-f-]+)\".*", "$1");
    }

    // --- secrecy -----------------------------------------------------------

    @Test
    void preSubmissionResponseCarriesNoTranscriptAndNoAnswerKey() throws Exception {
        String body = createExercise(OWNER);

        assertThat(body)
                .doesNotContain("speakerTurns")
                .doesNotContain("correctOptionId")
                .doesNotContain("explanation")
                .doesNotContain("evidence")
                .doesNotContain("transcript");
    }

    @Test
    void preSubmissionResponseNeverContainsTheActualAnswerText() throws Exception {
        String body = createExercise(OWNER);
        String exerciseId = idOf(body);

        ListeningExercise exercise = repository
                .findByOwnerAndId(OWNER, UUID.fromString(exerciseId))
                .orElseThrow();

        // The explanations and the spoken lines must not appear anywhere in the
        // payload the browser receives before submitting.
        for (Question question : exercise.questions()) {
            assertThat(body).doesNotContain(question.explanation());
            assertThat(body).doesNotContain(question.evidence());
        }
        assertThat(body).doesNotContain(exercise.speakerTurns().get(0).text());
    }

    @Test
    void fetchingAnExerciseAgainStillHidesTheAnswers() throws Exception {
        String exerciseId = idOf(createExercise(OWNER));

        mockMvc.perform(get("/api/v1/listening/exercises/" + exerciseId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions", org.hamcrest.Matchers.hasSize(6)))
                .andExpect(jsonPath("$.questions[0].options", org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$.correctOptionId").doesNotExist())
                .andExpect(jsonPath("$.speakerTurns").doesNotExist());
    }

    @Test
    void discloseThatVoicesAreAiGenerated() throws Exception {
        assertThat(createExercise(OWNER)).contains("This exercise uses AI-generated voices.");
    }

    // --- ownership ---------------------------------------------------------

    @Test
    void anotherUserCannotReadTheExercise() throws Exception {
        String exerciseId = idOf(createExercise(OWNER));

        mockMvc.perform(get("/api/v1/listening/exercises/" + exerciseId)
                        .header(LocalStubAuthenticationFilter.HEADER, INTRUDER))
                // 404, not 403: probing must not confirm the resource exists.
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void anotherUserCannotSubmitAgainstTheExercise() throws Exception {
        String exerciseId = idOf(createExercise(OWNER));

        mockMvc.perform(post("/api/v1/listening/exercises/" + exerciseId + "/submissions")
                        .header(LocalStubAuthenticationFilter.HEADER, INTRUDER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersJson(allSame("A"))))
                .andExpect(status().isNotFound());
    }

    // --- scoring -----------------------------------------------------------

    @Test
    void scoresEveryCorrectAnswerDeterministically() throws Exception {
        String exerciseId = idOf(createExercise(OWNER));
        ListeningExercise exercise = repository
                .findByOwnerAndId(OWNER, UUID.fromString(exerciseId))
                .orElseThrow();

        String answers = exercise.questions().stream()
                .map(question -> """
                        {"questionId":"%s","selectedOptionId":"%s"}"""
                        .formatted(question.id(), question.correctOptionId()))
                .collect(Collectors.joining(",", "{\"answers\":[", "]}"));

        mockMvc.perform(post("/api/v1/listening/exercises/" + exerciseId + "/submissions")
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctCount").value(6))
                .andExpect(jsonPath("$.totalQuestions").value(6))
                .andExpect(jsonPath("$.scorePercent").value(100))
                .andExpect(jsonPath("$.weakestSkill").doesNotExist());
    }

    @Test
    void submissionRevealsTheTranscriptRationaleAndEvidence() throws Exception {
        String exerciseId = idOf(createExercise(OWNER));

        MvcResult result = mockMvc.perform(post("/api/v1/listening/exercises/" + exerciseId + "/submissions")
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersJson(allSame("A"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript", org.hamcrest.Matchers.hasSize(16)))
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(6)))
                .andExpect(jsonPath("$.results[0].explanation").isNotEmpty())
                .andExpect(jsonPath("$.results[0].evidence").isNotEmpty())
                .andExpect(jsonPath("$.results[0].correctOptionId").isNotEmpty())
                .andExpect(jsonPath("$.tip").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Priya");
    }

    @Test
    void rejectsASecondSubmissionForTheSameExercise() throws Exception {
        String exerciseId = idOf(createExercise(OWNER));

        mockMvc.perform(post("/api/v1/listening/exercises/" + exerciseId + "/submissions")
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersJson(allSame("A"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/listening/exercises/" + exerciseId + "/submissions")
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answersJson(allSame("B"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
    }

    @Test
    void rejectsAPartialSubmission() throws Exception {
        String exerciseId = idOf(createExercise(OWNER));

        mockMvc.perform(post("/api/v1/listening/exercises/" + exerciseId + "/submissions")
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":\"q1\",\"selectedOptionId\":\"A\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsAnOptionIdThatIsNotAToD() throws Exception {
        String exerciseId = idOf(createExercise(OWNER));

        mockMvc.perform(post("/api/v1/listening/exercises/" + exerciseId + "/submissions")
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":\"q1\",\"selectedOptionId\":\"Z\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // --- request validation and idempotency --------------------------------

    @Test
    void rejectsAPartOutsideOneToSix() throws Exception {
        mockMvc.perform(post("/api/v1/listening/exercises")
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"part\":9,\"difficulty\":\"COMPETENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("part"));
    }

    @Test
    void repeatingACreateWithTheSameIdempotencyKeyReturnsTheOriginalExercise() throws Exception {
        String first = mockMvc.perform(post("/api/v1/listening/exercises")
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER)
                        .header("Idempotency-Key", "retry-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"part\":5,\"difficulty\":\"COMPETENT\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(post("/api/v1/listening/exercises")
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER)
                        .header("Idempotency-Key", "retry-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"part\":5,\"difficulty\":\"COMPETENT\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(idOf(second)).isEqualTo(idOf(first));
    }

    @Test
    void anIdempotencyKeyIsScopedToItsOwner() throws Exception {
        String mine = mockMvc.perform(post("/api/v1/listening/exercises")
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER)
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"part\":5,\"difficulty\":\"COMPETENT\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String theirs = mockMvc.perform(post("/api/v1/listening/exercises")
                        .header(LocalStubAuthenticationFilter.HEADER, INTRUDER)
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"part\":5,\"difficulty\":\"COMPETENT\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(idOf(theirs)).isNotEqualTo(idOf(mine));
    }

    @Test
    void requiresAuthenticationInDeployedMode() throws Exception {
        // LOCAL_STUB accepts any identity, so this asserts the route is mapped
        // under /api/v1 where CognitoSecurityTest proves it is protected.
        mockMvc.perform(get("/api/v1/listening/exercises/" + UUID.randomUUID())
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isNotFound());
    }

    private static String answersJson(List<String> selections) {
        StringBuilder json = new StringBuilder("{\"answers\":[");
        for (int i = 0; i < selections.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"questionId\":\"q%d\",\"selectedOptionId\":\"%s\"}".formatted(i + 1, selections.get(i)));
        }
        return json.append("]}").toString();
    }

    private static List<String> allSame(String optionId) {
        return List.of(optionId, optionId, optionId, optionId, optionId, optionId);
    }
}
