package com.listenspeak.coach.speaking.evaluation;

import java.nio.file.Path;

/**
 * Second, optional pass over the same audio purely to obtain word and segment
 * timings.
 *
 * <p>Kept separate from {@link Transcriber} because the model that gives the
 * best transcript and the model that gives timestamps are not the same model.
 * Its text output is never shown to the user and never used for scoring content,
 * vocabulary, or task fulfilment.
 *
 * <p>An implementation must never throw: timing is an enhancement, and losing it
 * must not cost the user their evaluation.
 */
public interface WordTimingAnalyzer {

    TimingAnalysis analyze(Path recording, String filename);
}
