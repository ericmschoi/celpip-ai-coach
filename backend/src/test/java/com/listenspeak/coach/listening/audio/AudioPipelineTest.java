package com.listenspeak.coach.listening.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.listenspeak.coach.listening.domain.ExerciseFixtures;
import com.listenspeak.coach.listening.domain.GeneratedExercise;
import com.listenspeak.coach.platform.media.Ffmpeg;
import com.listenspeak.coach.platform.media.MediaProcessingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the real FFmpeg pipeline. These are the tests that catch hum,
 * clipping, scrambled turn order, and leaked temp files - none of which a
 * mock-based test would notice.
 */
class AudioPipelineTest {

    private Ffmpeg ffmpeg;
    private AudioQualityGate qualityGate;
    private AudioAssembler assembler;

    @TempDir
    Path scratch;

    /** Isolated so leftover-directory counts cannot see another process's work. */
    @TempDir
    Path workRoot;

    @BeforeAll
    static void checkFfmpeg() {
        assumeThat(new Ffmpeg("ffmpeg", "ffprobe").isAvailable())
                .as("ffmpeg must be installed to run the audio pipeline tests")
                .isTrue();
    }

    @BeforeEach
    void setUp() {
        ffmpeg = new Ffmpeg("ffmpeg", "ffprobe", workRoot.toString());
        qualityGate = new AudioQualityGate(ffmpeg);
        assembler = new AudioAssembler(ffmpeg, qualityGate);
    }

    /** A tone at a realistic speech level, which passes the quality gate. */
    private byte[] tone(double seconds, int frequency) {
        Path file = scratch.resolve("tone-%d-%s.wav".formatted(frequency, seconds));
        Ffmpeg.Result result = ffmpeg.ffmpeg(List.of(
                "-loglevel", "error",
                "-f", "lavfi",
                "-i", "sine=frequency=%d:sample_rate=24000:duration=%.2f".formatted(frequency, seconds),
                "-af", "volume=-12dB",
                "-ac", "1",
                "-c:a", "pcm_s16le",
                file.toString()));
        assertThat(result.succeeded()).as(result.output()).isTrue();
        return read(file);
    }

    private byte[] silence(double seconds) {
        Path file = scratch.resolve("silence-%s.wav".formatted(seconds));
        ffmpeg.ffmpeg(List.of(
                "-loglevel", "error",
                "-f", "lavfi",
                "-i", "anullsrc=r=24000:cl=mono",
                "-t", String.valueOf(seconds),
                "-c:a", "pcm_s16le",
                file.toString()));
        return read(file);
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    // --- assembly ----------------------------------------------------------

    @Test
    void assemblesSegmentsIntoOneDecodableRecording() {
        var segments = List.of(
                new AudioAssembler.Segment(tone(12, 300), 350),
                new AudioAssembler.Segment(tone(12, 420), 350),
                new AudioAssembler.Segment(tone(12, 520), 0));

        AudioAssembler.Assembled assembled = assembler.assemble(segments);

        // 36s of speech plus 0.7s of gaps, allowing for encoder padding.
        assertThat(assembled.durationSeconds()).isBetween(35.0, 38.0);
        assertThat(assembled.mp3()).hasSizeGreaterThan(10_000);
        assertThat(assembled.mp3()[0]).isIn((byte) 0xFF, (byte) 'I'); // MPEG frame or ID3
    }

    @Test
    void keepsTurnsInDialogueOrder() {
        // Ascending pitch per turn: if assembly reordered them, the pitch at the
        // start would not be the lowest.
        var segments = List.of(
                new AudioAssembler.Segment(tone(11, 200), 0),
                new AudioAssembler.Segment(tone(11, 800), 0),
                new AudioAssembler.Segment(tone(11, 2000), 0));

        AudioAssembler.Assembled assembled = assembler.assemble(segments);
        Path output = scratch.resolve("ordered.mp3");
        write(output, assembled.mp3());

        assertThat(dominantFrequency(output, 0, 8)).isLessThan(dominantFrequency(output, 24, 8));
    }

    @Test
    void normalisesLoudnessWithoutClipping() {
        var segments = List.of(new AudioAssembler.Segment(tone(30, 440), 0));

        AudioAssembler.Assembled assembled = assembler.assemble(segments);
        Path output = scratch.resolve("normalised.mp3");
        write(output, assembled.mp3());

        AudioQualityGate.Report report = qualityGate.inspect(output);
        assertThat(report.passed()).as(report.summary()).isTrue();
        assertThat(report.peakDbfs()).isLessThan(-0.5);
        assertThat(report.rmsDbfs()).isBetween(-26.0, -10.0);
    }

    @Test
    void refusesToAssembleNothing() {
        assertThatThrownBy(() -> assembler.assemble(List.of()))
                .isInstanceOf(MediaProcessingException.class)
                .hasMessageContaining("zero segments");
    }

    @Test
    void leavesNoTemporaryFilesBehindWhenAssemblyFails() {
        long before = countWorkDirectories(workRoot);

        // Bytes that are not decodable audio at all.
        var broken = List.of(new AudioAssembler.Segment("not audio".getBytes(), 0));

        assertThatThrownBy(() -> assembler.assemble(broken)).isInstanceOf(MediaProcessingException.class);

        assertThat(countWorkDirectories(workRoot)).isEqualTo(before);
    }

    @Test
    void leavesNoTemporaryFilesBehindOnSuccessEither() {
        long before = countWorkDirectories(workRoot);

        assembler.assemble(List.of(new AudioAssembler.Segment(tone(25, 440), 0)));

        assertThat(countWorkDirectories(workRoot)).isEqualTo(before);
    }

    // --- quality gate ------------------------------------------------------

    @Test
    void acceptsTheCommittedSeedFixture() {
        Path seed = Path.of("src/main/resources/seed/audio/part-5-community-centre.mp3");
        assumeThat(Files.exists(seed)).isTrue();

        AudioQualityGate.Report report = qualityGate.inspect(seed);

        assertThat(report.passed()).as(report.summary()).isTrue();
        assertThat(report.durationSeconds()).isGreaterThan(120);
        assertThat(report.peakDbfs()).as("no clipping").isLessThan(-0.5);
        assertThat(report.longestSilenceSeconds()).as("no dead air").isLessThan(4.0);
    }

    @Test
    void rejectsAFileThatIsEffectivelySilent() {
        Path file = scratch.resolve("quiet.mp3");
        ffmpeg.ffmpeg(List.of(
                "-loglevel", "error",
                "-f", "lavfi",
                "-i", "anullsrc=r=24000:cl=mono",
                "-t", "40",
                "-c:a", "libmp3lame",
                file.toString()));

        AudioQualityGate.Report report = qualityGate.inspect(file);

        assertThat(report.passed()).isFalse();
        assertThat(report.summary()).containsAnyOf("silent", "silence");
    }

    @Test
    void rejectsAClippedFile() {
        Path file = scratch.resolve("clipped.mp3");
        ffmpeg.ffmpeg(List.of(
                "-loglevel", "error",
                "-f", "lavfi",
                "-i", "sine=frequency=440:sample_rate=24000:duration=40",
                "-af", "volume=20dB",
                "-c:a", "libmp3lame",
                file.toString()));

        AudioQualityGate.Report report = qualityGate.inspect(file);

        assertThat(report.passed()).isFalse();
        assertThat(report.summary()).contains("clips");
    }

    @Test
    void rejectsAFileTooShortToBeAnExercise() {
        Path file = scratch.resolve("short.mp3");
        ffmpeg.ffmpeg(List.of(
                "-loglevel", "error",
                "-f", "lavfi",
                "-i", "sine=frequency=440:sample_rate=24000:duration=3",
                "-c:a", "libmp3lame",
                file.toString()));

        assertThat(qualityGate.inspect(file).summary()).contains("of audio");
    }

    @Test
    void rejectsSomethingThatIsNotAudioAtAll() {
        Path file = scratch.resolve("nonsense.mp3");
        write(file, "this is not an audio file".repeat(1000).getBytes());

        AudioQualityGate.Report report = qualityGate.inspect(file);

        assertThat(report.passed()).isFalse();
        assertThat(report.summary()).contains("not decodable");
    }

    // --- renderer ----------------------------------------------------------

    @Test
    void rendersEveryTurnWithItsSpeakersVoiceAndKeepsTheOrder() {
        Map<String, String> voiceByText = new ConcurrentHashMap<>();
        List<String> requestedTexts = Collections.synchronizedList(new ArrayList<>());

        TextToSpeech fake = (text, voice) -> {
            voiceByText.put(text, voice);
            requestedTexts.add(text);
            // Vary pitch a little so the assembled file is not one flat tone.
            return tone(2, 300 + (Math.abs(text.hashCode()) % 400));
        };

        GeneratedExercise exercise = ExerciseFixtures.validPart5();
        AudioAssembler.Assembled assembled = new DialogueRenderer(fake, assembler).render(exercise);

        assertThat(requestedTexts).hasSize(exercise.speakerTurns().size());
        assertThat(assembled.durationSeconds()).isGreaterThan(20);

        // Every turn by the same speaker used the same voice, and different
        // speakers used different voices.
        Map<String, String> voiceBySpeaker = new java.util.HashMap<>();
        for (var turn : exercise.speakerTurns()) {
            String voice = voiceByText.get(turn.text());
            assertThat(voice).isNotNull();
            String previous = voiceBySpeaker.putIfAbsent(turn.speakerId(), voice);
            if (previous != null) {
                assertThat(voice).isEqualTo(previous);
            }
        }
        assertThat(voiceBySpeaker.values()).doesNotHaveDuplicates();
    }

    @Test
    void neverSpeaksASpeakerLabel() {
        List<String> spoken = Collections.synchronizedList(new ArrayList<>());
        TextToSpeech fake = (text, voice) -> {
            spoken.add(text);
            return tone(2, 440);
        };

        GeneratedExercise exercise = ExerciseFixtures.validPart5();
        new DialogueRenderer(fake, assembler).render(exercise);

        assertThat(spoken).allSatisfy(text -> assertThat(text).doesNotContainPattern("^[A-Z][a-z]+:"));
        // Turns are synthesized concurrently, so call order is not dialogue
        // order; ordering of the *assembled* audio is covered separately.
        assertThat(spoken)
                .containsExactlyInAnyOrderElementsOf(
                        exercise.speakerTurns().stream().map(t -> t.text()).toList());
    }

    // --- helpers -----------------------------------------------------------

    private static void write(Path path, byte[] content) {
        try {
            Files.write(path, content);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private long countWorkDirectories(Path tempRoot) {
        try (var paths = Files.list(tempRoot)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("listenspeak-listening-"))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Rough dominant frequency of a window, via ffmpeg's zero-crossing count. */
    private double dominantFrequency(Path file, double startSeconds, double windowSeconds) {
        Ffmpeg.Result result = ffmpeg.ffmpeg(List.of(
                "-loglevel", "info",
                "-ss", String.valueOf(startSeconds),
                "-t", String.valueOf(windowSeconds),
                "-i", file.toString(),
                "-af", "astats=measure_overall=Zero_crossings_rate",
                "-f", "null", "-"));

        var matcher = java.util.regex.Pattern.compile("Zero crossings rate:\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(result.output());
        assertThat(matcher.find()).as("zero-crossing rate reported").isTrue();
        return Double.parseDouble(matcher.group(1));
    }
}
