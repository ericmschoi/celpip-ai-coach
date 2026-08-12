package com.listenspeak.coach.speaking.evaluation;

import com.listenspeak.coach.platform.media.Ffmpeg;
import com.listenspeak.coach.platform.media.MediaProcessingException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Measures a recording locally with FFmpeg: how long it is, and how much of it
 * is silence.
 *
 * <p>These are coarse acoustic facts, nothing more. They are never presented as
 * a pronunciation assessment, and the scoring prompt is told the same.
 */
@Component
public class RecordingAnalyzer {

    /** Quiet enough to count as a pause in a single-speaker recording. */
    private static final String SILENCE_THRESHOLD = "-35dB";

    private static final double MIN_PAUSE_SECONDS = 0.6;

    private static final Pattern SILENCE_DURATION = Pattern.compile("silence_duration:\\s*(\\d+(?:\\.\\d+)?)");

    public record Measurements(double durationSeconds, double silenceRatio, double longestSilenceSeconds) {}

    private final Ffmpeg ffmpeg;

    public RecordingAnalyzer(Ffmpeg ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public Measurements analyze(Path recording) {
        double duration = ffmpeg.durationSeconds(recording)
                .orElseThrow(() -> new MediaProcessingException("Recording is not a decodable audio file"));

        if (duration <= 0) {
            throw new MediaProcessingException("Recording has no duration");
        }

        String output = ffmpeg.ffmpeg(List.of(
                        "-loglevel", "info",
                        "-i", recording.toString(),
                        "-af", "silencedetect=noise=%s:d=%.1f".formatted(SILENCE_THRESHOLD, MIN_PAUSE_SECONDS),
                        "-f", "null", "-"))
                .output();

        double totalSilence = 0;
        double longestSilence = 0;
        Matcher matcher = SILENCE_DURATION.matcher(output);
        while (matcher.find()) {
            double seconds = Double.parseDouble(matcher.group(1));
            totalSilence += seconds;
            longestSilence = Math.max(longestSilence, seconds);
        }

        // Clamp: rounding in silencedetect can otherwise exceed the duration.
        double ratio = Math.min(1.0, totalSilence / duration);

        return new Measurements(duration, ratio, longestSilence);
    }
}
