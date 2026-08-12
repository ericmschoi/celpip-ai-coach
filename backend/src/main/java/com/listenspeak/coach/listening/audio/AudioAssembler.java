package com.listenspeak.coach.listening.audio;

import com.listenspeak.coach.platform.media.Ffmpeg;
import com.listenspeak.coach.platform.media.MediaProcessingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Joins per-turn WAV segments into one browser-friendly recording.
 *
 * <p>Every intermediate file lives under an application-created temp directory
 * with a generated name, and the whole directory is deleted in a
 * {@code finally} block whether or not assembly succeeded.
 */
@Component
public class AudioAssembler {

    private static final Logger log = LoggerFactory.getLogger(AudioAssembler.class);

    /** One speaker turn's rendered audio and the silence that follows it. */
    public record Segment(byte[] wav, int pauseAfterMs) {}

    public record Assembled(byte[] mp3, double durationSeconds) {}

    private static final int SAMPLE_RATE = 24_000;

    private final Ffmpeg ffmpeg;
    private final AudioQualityGate qualityGate;

    public AudioAssembler(Ffmpeg ffmpeg, AudioQualityGate qualityGate) {
        this.ffmpeg = ffmpeg;
        this.qualityGate = qualityGate;
    }

    public Assembled assemble(List<Segment> segments) {
        if (segments.isEmpty()) {
            throw new MediaProcessingException("Cannot assemble audio from zero segments");
        }

        Path work = ffmpeg.createWorkDirectory("listening");
        try {
            Path concatList = writeSegments(work, segments);
            Path output = work.resolve("exercise.mp3");

            // Normalise loudness to about -18 LUFS and hard-limit just below full
            // scale, so no exercise is noticeably louder than another and nothing
            // clips.
            Ffmpeg.Result result = ffmpeg.ffmpeg(List.of(
                    "-loglevel", "error",
                    "-f", "concat",
                    "-safe", "0",
                    "-i", concatList.toString(),
                    "-af", "loudnorm=I=-18:TP=-1.5:LRA=11,alimiter=limit=0.95",
                    "-ar", String.valueOf(SAMPLE_RATE),
                    "-ac", "1",
                    "-c:a", "libmp3lame",
                    "-b:a", "64k",
                    output.toString()));

            if (!result.succeeded()) {
                log.error("Audio assembly failed: {}", result.output());
                throw new MediaProcessingException("ffmpeg could not assemble the exercise audio");
            }

            AudioQualityGate.Report report = qualityGate.inspect(output);
            if (!report.passed()) {
                throw new MediaProcessingException("Assembled audio failed QA: " + report.summary());
            }

            return new Assembled(read(output), report.durationSeconds());

        } finally {
            ffmpeg.deleteQuietly(work);
        }
    }

    /**
     * Writes each turn as a WAV plus a generated silence file, and produces the
     * concat manifest. Paths in the manifest are ours, never derived from
     * generated text.
     */
    private Path writeSegments(Path work, List<Segment> segments) {
        List<String> lines = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);

            Path turn = work.resolve("turn-%03d.wav".formatted(i));
            write(turn, segment.wav());
            lines.add("file '%s'".formatted(turn));

            if (segment.pauseAfterMs() > 0) {
                Path gap = work.resolve("gap-%03d.wav".formatted(i));
                Ffmpeg.Result silence = ffmpeg.ffmpeg(List.of(
                        "-loglevel", "error",
                        "-f", "lavfi",
                        "-i", "anullsrc=r=%d:cl=mono".formatted(SAMPLE_RATE),
                        "-t", "%.3f".formatted(segment.pauseAfterMs() / 1000.0),
                        "-c:a", "pcm_s16le",
                        gap.toString()));
                if (!silence.succeeded()) {
                    throw new MediaProcessingException("ffmpeg could not generate inter-turn silence");
                }
                lines.add("file '%s'".formatted(gap));
            }
        }

        Path manifest = work.resolve("concat.txt");
        write(manifest, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return manifest;
    }

    private static void write(Path path, byte[] content) {
        try {
            Files.write(path, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + path.getFileName(), e);
        }
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read assembled audio", e);
        }
    }
}
