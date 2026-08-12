package com.listenspeak.coach.speaking.evaluation;

import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Demo-mode transcriber. It cannot hear anything, so it returns a fixed sample
 * answer and the UI labels the whole evaluation as demo output.
 *
 * <p>This exists so the recorder, the timers, the upload validation, the
 * metrics, and the results screen are all exercisable with no API key.
 */
@Component
@ConditionalOnMissingBean(OpenAiTranscriber.class)
public class SeedTranscriber implements Transcriber {

    static final String SAMPLE_TRANSCRIPT =
            """
            So, um, I think she should probably take the promotion, because it's a lot more money \
            and, you know, that kind of opportunity doesn't come around every year. But I mean, \
            moving away from her family is a big thing, especially if her parents are getting older \
            and they need help sometimes. I would tell her to, uh, to ask the company if she can \
            try it for six months first, and then decide. That way she doesn't have to sell her \
            house right away. And honestly, the hockey team, she can find another team, that's not \
            the main issue. The main issue is the family. So my advice is take it, but ask for a \
            trial period, and go home once a month.
            """;

    @Override
    public String transcribe(Path recording, String filename) {
        return SAMPLE_TRANSCRIPT.replaceAll("\\s+", " ").trim();
    }
}
