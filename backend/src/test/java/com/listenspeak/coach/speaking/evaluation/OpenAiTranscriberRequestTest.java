package com.listenspeak.coach.speaking.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.listenspeak.coach.platform.config.AppProperties;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Inspects the request the transcriber builds, without calling anything.
 *
 * <p>The point is to prove that no unsupported parameter is ever sent, rather
 * than to prove that a fallback recovers when one is.
 */
class OpenAiTranscriberRequestTest {

    @TempDir
    Path scratch;

    /** The SDK reads the file while building the multipart body, so it must exist. */
    private Path recording;

    @BeforeEach
    void createRecording() throws Exception {
        recording = scratch.resolve("answer.webm");
        Files.write(recording, new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
    }

    private OpenAiTranscriber transcriberFor(String model) {
        AppProperties properties = new AppProperties(
                AppProperties.ContentMode.LIVE,
                new AppProperties.Auth(AppProperties.AuthMode.LOCAL_STUB, null, null, null),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Limits(20, 30, 5),
                new AppProperties.Speaking(15_728_640, List.of("audio/webm"), false),
                new AppProperties.Storage(
                        AppProperties.StorageMode.LOCAL,
                        null,
                        null,
                        "./target/test-data",
                        Duration.ofMinutes(15),
                        Duration.ofDays(30)),
                new AppProperties.OpenAi(
                        "test-key",
                        "https://api.openai.com/v1",
                        "gen",
                        "score",
                        "tts",
                        model,
                        "whisper-1",
                        Duration.ofSeconds(60),
                        2));

        return new OpenAiTranscriber(null, properties);
    }

    /**
     * The built request. An unset parameter is an empty Optional and is simply
     * not transmitted, so assertions check values rather than key names - the
     * SDK's own toString lists every known key regardless.
     */
    private TranscriptionCreateParams paramsFor(String model) {
        return transcriberFor(model).buildParams(model, recording);
    }

    @Test
    void neverSendsTimestampGranularitiesToGptTranscribe() {
        assertThat(paramsFor("gpt-transcribe").timestampGranularities()).isEmpty();
    }

    @Test
    void neverAsksGptTranscribeForLogprobs() {
        // `include` is how logprobs would be requested; it stays unset.
        assertThat(paramsFor("gpt-transcribe").include()).isEmpty();
    }

    @Test
    void neverAsksGptTranscribeForVerboseJson() {
        assertThat(paramsFor("gpt-transcribe").responseFormat()).isEmpty();
    }

    @Test
    void sendsTheVerbatimPromptSoDisfluenciesSurvive() {
        assertThat(paramsFor("gpt-transcribe").prompt())
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("word for word")
                .contains("false starts");
    }

    @Test
    void sendsFillerKeywordsToAModelThatSupportsThem() {
        assertThat(paramsFor("gpt-transcribe").keywords())
                .get(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .contains("um", "uh", "you know");
    }

    @Test
    void sendsPluralLanguagesToGptTranscribeAndNeverTheSingularForm() {
        TranscriptionCreateParams params = paramsFor("gpt-transcribe");

        assertThat(params.languages())
                .get(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly("en");
        assertThat(params.language()).isEmpty();
    }

    @Test
    void sendsSingularLanguageToWhisperAndNeverKeywords() {
        TranscriptionCreateParams params = paramsFor("whisper-1");

        assertThat(params.language()).contains("en");
        assertThat(params.languages()).isEmpty();
        assertThat(params.keywords()).isEmpty();
    }

    @Test
    void sendsOnlyAPromptToAnUnknownModel() {
        TranscriptionCreateParams params = paramsFor("some-future-model");

        assertThat(params.prompt()).isPresent();
        assertThat(params.keywords()).isEmpty();
        assertThat(params.languages()).isEmpty();
        assertThat(params.language()).isEmpty();
        assertThat(params.timestampGranularities()).isEmpty();
        assertThat(params.responseFormat()).isEmpty();
    }
}
