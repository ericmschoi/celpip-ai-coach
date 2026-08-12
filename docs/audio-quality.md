# Audio quality

Bad audio makes an exercise worthless, and the failure is usually silent — a hum, a clipped peak, or
ten seconds of dead air that nobody notices until someone is practising. Everything here exists to
make those failures loud and early.

## Voices

| Speaker slot | Voice    | Notes                                   |
| ------------ | -------- | --------------------------------------- |
| 1st speaker  | `marin`  | Highest-quality voice, used first        |
| 2nd speaker  | `cedar`  | Clearly distinct from `marin`            |
| 3rd speaker  | `sage`   | The third voice Part 5 needs             |
| 4th–6th      | `verse`, `coral`, `ash` | Headroom, not currently reached |

Assignment is by **first appearance in the dialogue**, so the same exercise always produces the same
voices. Two speakers in one exercise can never share a voice; the assigner throws rather than reuse
one, because "who said it" questions would become guesswork.

Source: [`VoiceAssignment.java`](../backend/src/main/java/com/listenspeak/coach/listening/audio/VoiceAssignment.java).

## Pipeline

```
speakerTurns
   │  one request per turn, max 4 in flight
   ▼
OpenAI TTS (gpt-4o-mini-tts, response_format=wav)
   │  results collected by turn index, never by completion order
   ▼
per-turn WAV + generated silence (pauseAfterMs)
   │  ffmpeg concat demuxer
   ▼
loudnorm=I=-18:TP=-1.5:LRA=11  →  alimiter=limit=0.95
   │
   ▼
24 kHz mono MP3 @ 64 kbps
   │
   ▼
quality gate  →  private S3  →  presigned GET (15 min)
```

**Why WAV in the middle.** Concatenating compressed audio means decoding and re-encoding each
segment, which stacks artefacts. Uncompressed segments are joined once and encoded once.

**Why 24 kHz mono 64 kbps.** Speech has almost nothing above 8 kHz, and stereo carries no
information here. A three-minute exercise lands around 1.4 MB, which loads quickly on a phone.

**Why the concurrency bound.** Four requests in flight keeps generation fast without letting one
exercise consume the whole provider rate limit.

**Why order is by index.** Turns are synthesized concurrently but assembled by their position in the
dialogue. A test asserts this by rendering ascending pitches and checking the assembled file's
frequency rises.

## FFmpeg filters

| Filter                             | Purpose                                                                 |
| ---------------------------------- | ----------------------------------------------------------------------- |
| `loudnorm=I=-18:TP=-1.5:LRA=11`    | Normalises to about -18 LUFS so no exercise is louder than another       |
| `alimiter=limit=0.95`              | Hard ceiling below full scale; catches any peak `loudnorm` leaves behind |
| `anullsrc` + `-t`                  | Generates the inter-turn silence, so gaps are exact                     |
| `astats=measure_overall=Peak_level+RMS_level` | Measurement for the quality gate                              |
| `silencedetect=noise=-45dB:d=4.0`  | Finds dead air                                                          |

## Quality gate

Every assembled file must pass all of these before it is stored. Failure returns
`AUDIO_QUALITY_FAILED` and no exercise is created.

| Check         | Threshold          | What it catches                          |
| ------------- | ------------------ | ---------------------------------------- |
| File size     | ≥ 10 000 bytes     | Truncated or empty response              |
| Decodable     | `ffprobe` reports a duration | Corrupt or non-audio bytes     |
| Duration      | 20 s – 420 s       | A turn dropped, or a runaway transcript  |
| Peak          | ≤ -0.5 dBFS        | Clipping                                 |
| RMS           | ≥ -40 dBFS         | A file that is technically valid but silent |
| Longest silence | ≤ 4.0 s          | Dead air from a failed segment           |

Source: [`AudioQualityGate.java`](../backend/src/main/java/com/listenspeak/coach/listening/audio/AudioQualityGate.java).

## Temp files

All intermediate files live in one `Files.createTempDirectory("listenspeak-listening-…")` per
assembly, deleted in a `finally` block whether assembly succeeded or threw. Two tests assert the temp
directory count is unchanged after both a successful and a failed assembly.

FFmpeg is invoked as an **argument array** through `ProcessBuilder`, never a shell string. Generated
text is never interpolated into a command, and a client-supplied filename is never used as a path.

## The seed fixture

`backend/src/main/resources/seed/audio/part-5-community-centre.mp3` is committed so the app works
with no API key. It is rendered by
[`scripts/generate-seed-audio.sh`](../scripts/generate-seed-audio.sh) using offline macOS `say`
voices — Samantha, Daniel, and Karen, which differ in accent as well as pitch — and then goes through
exactly the same normalisation and QA as generated audio.

Current measurements:

| Property | Value          |
| -------- | -------------- |
| Duration | 180.4 s        |
| Peak     | -1.72 dBFS     |
| RMS      | -17.9 dBFS     |
| Longest silence | none over 1.5 s |
| Format   | MP3, 24 kHz mono, 64 kbps, 1.44 MB |

Regenerate after editing the transcript:

```bash
./scripts/generate-seed-audio.sh part-5-community-centre
```

Then update `audioDurationSeconds` in the fixture JSON with the value the script prints.

The seed voices are noticeably more synthetic than the live TTS voices. That is deliberate: the
fixture exists so the flow works for free, not to demonstrate audio quality.

## Diagnosing a problem

**Continuous high-frequency tone or hum.** Almost always a TTS response that is not what it claims to
be. Check `response_format` is `wav` and that segments are not being re-encoded before assembly.
Inspect a single segment:

```bash
ffplay -f lavfi "amovie=segment.wav,showspectrum=s=800x400"
```

**Clipping.** Look for peaks above -0.5 dBFS:

```bash
ffmpeg -i exercise.mp3 -af astats=measure_overall=Peak_level -f null -
```

If `alimiter` is doing all the work, `loudnorm`'s input target is wrong for that material.

**Long silence.** Find where:

```bash
ffmpeg -i exercise.mp3 -af silencedetect=noise=-45dB:d=1.5 -f null - 2>&1 | grep silence
```

A gap at a turn boundary means a segment came back empty; a gap mid-turn means the model inserted a
pause and the text probably contains an ellipsis or a stage direction.

**A speaker name is spoken aloud.** The turn text carried a label. The validator rejects text
matching `^[A-Z][A-Za-z .'-]{1,24}:`, so if this reaches audio, the label is in an unusual form —
add it to that pattern.

**Voices sound the same.** Check the logged voice assignment (`Rendering N turns with voices {…}`).
If two speakers share a voice, the speaker ids differ only by case or whitespace upstream.

**Duration is wrong.** The stored `audioDurationSeconds` comes from `ffprobe` on the assembled file,
not from an estimate, so a mismatch means the stored file and the played file differ — check the
storage key.
