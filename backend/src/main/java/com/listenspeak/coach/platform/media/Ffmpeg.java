package com.listenspeak.coach.platform.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thin, safe wrapper around the ffmpeg and ffprobe binaries.
 *
 * <p>Commands are always built as an argument array and handed to
 * {@link ProcessBuilder} directly. There is no shell, so generated text,
 * filenames, and transcripts can never be interpreted as command syntax. All
 * paths passed in are application-created UUID temp paths.
 */
@Component
public class Ffmpeg {

    private static final Logger log = LoggerFactory.getLogger(Ffmpeg.class);
    private static final long TIMEOUT_SECONDS = 120;

    private final String ffmpegBinary;
    private final String ffprobeBinary;

    /**
     * Root for working directories. Defaults to the system temp directory;
     * tests point it somewhere isolated so that counting leftover directories
     * cannot be perturbed by another process on the same machine.
     */
    private final Path tempRoot;

    @org.springframework.beans.factory.annotation.Autowired
    public Ffmpeg(
            @Value("${app.media.ffmpeg-path:ffmpeg}") String ffmpegBinary,
            @Value("${app.media.ffprobe-path:ffprobe}") String ffprobeBinary,
            @Value("${app.media.temp-dir:}") String tempDir) {
        this.ffmpegBinary = ffmpegBinary;
        this.ffprobeBinary = ffprobeBinary;
        this.tempRoot = (tempDir == null || tempDir.isBlank())
                ? Path.of(System.getProperty("java.io.tmpdir"))
                : Path.of(tempDir);
    }

    /** Convenience for tests; the system temp directory is used. */
    public Ffmpeg(String ffmpegBinary, String ffprobeBinary) {
        this(ffmpegBinary, ffprobeBinary, null);
    }

    /** Where working directories are created. */
    public Path tempRoot() {
        return tempRoot;
    }

    public record Result(int exitCode, String output) {

        public boolean succeeded() {
            return exitCode == 0;
        }
    }

    /** True when the binaries are actually callable, so callers can fail early with a clear message. */
    public boolean isAvailable() {
        try {
            return run(List.of(ffmpegBinary, "-version")).succeeded();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Result ffmpeg(List<String> arguments) {
        List<String> command = new ArrayList<>(arguments.size() + 3);
        command.add(ffmpegBinary);
        command.add("-y");
        command.add("-hide_banner");
        command.addAll(arguments);
        return run(command);
    }

    public Result ffprobe(List<String> arguments) {
        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add(ffprobeBinary);
        command.addAll(arguments);
        return run(command);
    }

    /** Duration in seconds, or empty when the file is not a decodable media file. */
    public java.util.Optional<Double> durationSeconds(Path file) {
        Result result = ffprobe(List.of(
                "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file.toString()));

        if (!result.succeeded()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Double.parseDouble(result.output().trim()));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private Result run(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);

        Process process = null;
        try {
            process = builder.start();
            byte[] output = process.getInputStream().readAllBytes();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MediaProcessingException("ffmpeg timed out after " + TIMEOUT_SECONDS + "s");
            }
            return new Result(process.exitValue(), new String(output, StandardCharsets.UTF_8));

        } catch (IOException e) {
            throw new MediaProcessingException("Could not run " + command.get(0), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaProcessingException("Interrupted while running " + command.get(0), e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** Creates an isolated working directory whose name the caller never derives from user input. */
    public Path createWorkDirectory(String purpose) {
        try {
            Files.createDirectories(tempRoot);
            return Files.createTempDirectory(tempRoot, "listenspeak-" + purpose + "-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a working directory", e);
        }
    }

    /** Best-effort recursive cleanup; always call from a {@code finally} block. */
    public void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete temp file {}", path.getFileName());
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean up temp directory {}", directory.getFileName());
        }
    }
}
