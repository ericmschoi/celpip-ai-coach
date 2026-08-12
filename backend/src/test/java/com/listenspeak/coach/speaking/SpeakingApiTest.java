package com.listenspeak.coach.speaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.listenspeak.coach.platform.media.Ffmpeg;
import com.listenspeak.coach.platform.security.LocalStubAuthenticationFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SpeakingApiTest {

    private static final String OWNER = "speaking-owner";
    private static final String INTRUDER = "speaking-intruder";

    private static Ffmpeg ffmpeg;

    @TempDir
    static Path scratch;

    private MockMvc mockMvc;

    @Autowired
    void setUp(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @BeforeAll
    static void checkFfmpeg() {
        ffmpeg = new Ffmpeg("ffmpeg", "ffprobe");
        org.assertj.core.api.Assumptions.assumeThat(ffmpeg.isAvailable()).isTrue();
    }

    /** A real, decodable recording of a given length, so FFmpeg has something to measure. */
    private static byte[] recording(double seconds) {
        Path file = scratch.resolve("recording-%s.webm".formatted(seconds));
        Ffmpeg.Result result = ffmpeg.ffmpeg(List.of(
                "-loglevel", "error",
                "-f", "lavfi",
                "-i", "sine=frequency=200:sample_rate=48000:duration=%.2f".formatted(seconds),
                "-af", "volume=-14dB",
                "-c:a", "libopus",
                "-b:a", "32k",
                file.toString()));
        assertThat(result.succeeded()).as(result.output()).isTrue();
        try {
            return Files.readAllBytes(file);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String createPrompt(String user, int taskNumber) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/speaking/tasks/" + taskNumber + "/prompts")
                        .header(LocalStubAuthenticationFilter.HEADER, user))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString().replaceFirst("(?s).*\"id\"\\s*:\\s*\"([0-9a-f-]+)\".*", "$1");
    }

    private MockMultipartFile audioPart(byte[] content, String contentType) {
        // The client filename is deliberately hostile: the server must ignore it.
        return new MockMultipartFile("recording", "../../etc/passwd", contentType, content);
    }

    // --- tasks and prompts -------------------------------------------------

    @Test
    void listsAllEightTasksWithTheirTimings() throws Exception {
        mockMvc.perform(get("/api/v1/speaking/tasks").header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(8)))
                .andExpect(jsonPath("$[0].taskNumber").value(1))
                .andExpect(jsonPath("$[0].preparationSeconds").value(30))
                .andExpect(jsonPath("$[0].answerSeconds").value(90));
    }

    @Test
    void createsAPromptCarryingTheTasksOwnTimings() throws Exception {
        mockMvc.perform(post("/api/v1/speaking/tasks/5/prompts").header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskNumber").value(5))
                .andExpect(jsonPath("$.preparationSeconds").value(60))
                .andExpect(jsonPath("$.answerSeconds").value(60))
                .andExpect(jsonPath("$.situation").isNotEmpty())
                .andExpect(jsonPath("$.instruction").isNotEmpty());
    }

    @Test
    void rejectsATaskNumberOutsideOneToEight() throws Exception {
        mockMvc.perform(post("/api/v1/speaking/tasks/9/prompts").header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isBadRequest());
    }

    // --- upload validation -------------------------------------------------

    @Test
    void rejectsAnUnsupportedAudioFormat() throws Exception {
        String promptId = createPrompt(OWNER, 1);

        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart("not audio".getBytes(), "application/pdf"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void acceptsWebmWithACodecParameterBecauseThatIsWhatBrowsersSend() throws Exception {
        String promptId = createPrompt(OWNER, 1);

        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(30), "audio/webm;codecs=opus"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnEmptyUpload() throws Exception {
        String promptId = createPrompt(OWNER, 1);

        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(new byte[0], "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAFileThatIsNotDecodableAudio() throws Exception {
        String promptId = createPrompt(OWNER, 1);

        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart("PK not really audio".repeat(200).getBytes(), "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsARecordingLongerThanTheTaskAllows() throws Exception {
        // Task 2 allows 60 seconds; 90 is past the grace window.
        String promptId = createPrompt(OWNER, 2);

        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(90), "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // --- evaluation --------------------------------------------------------

    @Test
    void returnsAllFourDimensionsWithEvidenceAndAClampedLevel() throws Exception {
        String promptId = createPrompt(OWNER, 1);

        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(60), "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dimensions", Matchers.hasSize(4)))
                .andExpect(jsonPath("$.dimensions[*].dimension")
                        .value(Matchers.containsInAnyOrder(
                                "CONTENT_COHERENCE", "VOCABULARY", "LISTENABILITY", "TASK_FULFILLMENT")))
                .andExpect(jsonPath("$.dimensions[*].evidence").value(Matchers.everyItem(Matchers.not(Matchers.emptyString()))))
                .andExpect(jsonPath("$.estimatedLevel").value(Matchers.allOf(
                        Matchers.greaterThanOrEqualTo(1), Matchers.lessThanOrEqualTo(12))))
                .andExpect(jsonPath("$.confidence").value(Matchers.oneOf("LOW", "MEDIUM", "HIGH")))
                .andExpect(jsonPath("$.strengths", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.improvements", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.sampleAnswer").isNotEmpty())
                .andExpect(jsonPath("$.nextDrill").isNotEmpty());
    }

    @Test
    void alwaysStatesThatTheEstimateIsNotAnOfficialScore() throws Exception {
        String promptId = createPrompt(OWNER, 1);

        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(45), "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disclaimer")
                        .value("This is an AI estimate for practice only, not an official CELPIP score."));
    }

    @Test
    void reportsDeliveryMetricsMeasuredFromTheActualRecording() throws Exception {
        String promptId = createPrompt(OWNER, 1);

        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(40), "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.allowedSeconds").value(90))
                .andExpect(jsonPath("$.metrics.durationSeconds")
                        .value(Matchers.allOf(Matchers.greaterThan(35.0), Matchers.lessThan(45.0))))
                .andExpect(jsonPath("$.metrics.wordCount").value(Matchers.greaterThan(0)));
    }

    @Test
    void anEvaluationCanBeFetchedAgainByItsOwner() throws Exception {
        String promptId = createPrompt(OWNER, 1);

        String body = mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(30), "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String evaluationId = body.replaceFirst("(?s).*\"id\"\\s*:\\s*\"([0-9a-f-]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/speaking/evaluations/" + evaluationId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(evaluationId));
    }

    // --- ownership ---------------------------------------------------------

    @Test
    void anotherUserCannotSubmitAgainstYourPrompt() throws Exception {
        String promptId = createPrompt(OWNER, 1);

        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(20), "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, INTRUDER))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUserCannotReadYourEvaluation() throws Exception {
        String promptId = createPrompt(OWNER, 1);
        String body = mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(20), "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String evaluationId = body.replaceFirst("(?s).*\"id\"\\s*:\\s*\"([0-9a-f-]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/speaking/evaluations/" + evaluationId)
                        .header(LocalStubAuthenticationFilter.HEADER, INTRUDER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void anUnknownPromptIdIsANotFound() throws Exception {
        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(20), "audio/webm"))
                        .param("promptId", UUID.randomUUID().toString())
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isNotFound());
    }

    // --- cleanup -----------------------------------------------------------

    @Test
    void deletesTheUploadedRecordingAfterEvaluating() throws Exception {
        Path tempRoot = Path.of(System.getProperty("java.io.tmpdir"));
        long before = countRecordings(tempRoot);

        String promptId = createPrompt(OWNER, 1);
        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(25), "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isOk());

        assertThat(countRecordings(tempRoot)).isEqualTo(before);
    }

    @Test
    void deletesTheUploadedRecordingEvenWhenValidationRejectsIt() throws Exception {
        Path tempRoot = Path.of(System.getProperty("java.io.tmpdir"));
        long before = countRecordings(tempRoot);

        String promptId = createPrompt(OWNER, 2);
        mockMvc.perform(multipart("/api/v1/speaking/evaluations")
                        .file(audioPart(recording(90), "audio/webm"))
                        .param("promptId", promptId)
                        .header(LocalStubAuthenticationFilter.HEADER, OWNER))
                .andExpect(status().isBadRequest());

        assertThat(countRecordings(tempRoot)).isEqualTo(before);
    }

    private static long countRecordings(Path tempRoot) {
        try (var paths = Files.list(tempRoot)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("listenspeak-speaking-"))
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    @Test
    void speakingRoutesLiveUnderTheAuthenticatedApiPrefix() throws Exception {
        mockMvc.perform(get("/api/v1/speaking/tasks").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
