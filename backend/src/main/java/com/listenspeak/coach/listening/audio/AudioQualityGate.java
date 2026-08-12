package com.listenspeak.coach.listening.audio;

import com.listenspeak.coach.platform.media.Ffmpeg;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Refuses to hand the user audio that is broken, silent, clipped, or full of
 * dead air. Any of those makes an exercise unusable, and the failure mode is
 * far cheaper to catch here than in a practice session.
 */
@Component
public class AudioQualityGate {

    /** Below this the file cannot plausibly contain a listening exercise. */
    private static final long MIN_BYTES = 10_000;

    private static final double MIN_DURATION_SECONDS = 20;
    private static final double MAX_DURATION_SECONDS = 420;

    /** Peaks this close to full scale mean the mix is clipping. */
    private static final double MAX_PEAK_DBFS = -0.5;

    /** Below this the file is effectively silence, however long it is. */
    private static final double MIN_RMS_DBFS = -40;

    /** A gap longer than this is dead air, not a natural pause. */
    private static final double MAX_SILENCE_SECONDS = 4.0;

    private static final Pattern PEAK = Pattern.compile("Peak level dB:\\s*(-?\\d+(?:\\.\\d+)?|-?inf)");
    private static final Pattern RMS = Pattern.compile("RMS level dB:\\s*(-?\\d+(?:\\.\\d+)?|-?inf)");
    private static final Pattern SILENCE = Pattern.compile("silence_duration:\\s*(\\d+(?:\\.\\d+)?)");

    private final Ffmpeg ffmpeg;

    public AudioQualityGate(Ffmpeg ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public record Report(
            boolean passed,
            List<String> failures,
            double durationSeconds,
            long bytes,
            double peakDbfs,
            double rmsDbfs,
            double longestSilenceSeconds) {

        public String summary() {
            return String.join("; ", failures);
        }
    }

    public Report inspect(Path file) {
        List<String> failures = new ArrayList<>();

        long bytes = sizeOf(file);
        if (bytes < MIN_BYTES) {
            failures.add("file is only %d bytes".formatted(bytes));
        }

        double duration = ffmpeg.durationSeconds(file).orElse(-1d);
        if (duration <= 0) {
            failures.add("file is not decodable");
            return new Report(false, failures, 0, bytes, 0, 0, 0);
        }
        if (duration < MIN_DURATION_SECONDS) {
            failures.add("only %.1fs of audio".formatted(duration));
        }
        if (duration > MAX_DURATION_SECONDS) {
            failures.add("%.1fs is longer than a listening set should be".formatted(duration));
        }

        // astats gives peak and RMS; silencedetect gives dead air. One pass each,
        // both reading from the same decoded stream.
        String stats = ffmpeg.ffmpeg(List.of(
                        "-loglevel", "info",
                        "-i", file.toString(),
                        "-af", "astats=measure_overall=Peak_level+RMS_level,silencedetect=noise=-45dB:d=%.1f"
                                .formatted(MAX_SILENCE_SECONDS),
                        "-f", "null", "-"))
                .output();

        double peak = firstMatch(PEAK, stats, 0);
        double rms = firstMatch(RMS, stats, -100);
        double longestSilence = maxMatch(SILENCE, stats);

        if (peak > MAX_PEAK_DBFS) {
            failures.add("peaks at %.2f dBFS, which clips".formatted(peak));
        }
        if (rms < MIN_RMS_DBFS) {
            failures.add("average level %.1f dBFS is effectively silent".formatted(rms));
        }
        if (longestSilence > MAX_SILENCE_SECONDS) {
            failures.add("contains %.1fs of continuous silence".formatted(longestSilence));
        }

        return new Report(failures.isEmpty(), List.copyOf(failures), duration, bytes, peak, rms, longestSilence);
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    private static double firstMatch(Pattern pattern, String haystack, double fallback) {
        Matcher matcher = pattern.matcher(haystack);
        if (!matcher.find()) {
            return fallback;
        }
        String value = matcher.group(1);
        if (value.toLowerCase(Locale.ROOT).endsWith("inf")) {
            // -inf peak means pure digital silence.
            return value.startsWith("-") ? -100 : 0;
        }
        return Double.parseDouble(value);
    }

    private static double maxMatch(Pattern pattern, String haystack) {
        Matcher matcher = pattern.matcher(haystack);
        double max = 0;
        while (matcher.find()) {
            max = Math.max(max, Double.parseDouble(matcher.group(1)));
        }
        return max;
    }
}
