#!/usr/bin/env bash
#
# Renders the deterministic seed listening audio from its JSON fixture, using
# offline macOS voices so the demo path costs nothing and works with no API key.
#
# The live pipeline uses OpenAI TTS voices instead; this script exists only to
# produce the committed fixture. Re-run it after editing a seed transcript.
#
# Requires: macOS `say`, ffmpeg, ffprobe, python3.
# Usage:  ./scripts/generate-seed-audio.sh [seed-id]
set -euo pipefail

cd "$(dirname "$0")/.."

SEED_ID="${1:-part-5-community-centre}"
SEED_JSON="backend/src/main/resources/seed/listening/${SEED_ID}.json"
OUT_DIR="backend/src/main/resources/seed/audio"
OUT_FILE="${OUT_DIR}/${SEED_ID}.mp3"

for tool in say ffmpeg ffprobe python3; do
  command -v "$tool" >/dev/null 2>&1 || { echo "missing required tool: $tool" >&2; exit 1; }
done
[ -f "$SEED_JSON" ] || { echo "no such seed: $SEED_JSON" >&2; exit 1; }

# Distinct offline voices per speaker. Different accents keep them clearly
# separable, which is the property the exercise depends on.
voice_for() {
  case "$1" in
    PRIYA)  echo "Samantha" ;;
    MARCUS) echo "Daniel" ;;
    DALE)   echo "Karen" ;;
    *)      echo "Samantha" ;;
  esac
}

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

echo "Rendering $SEED_ID"

# Emit one "speakerId<TAB>pauseMs<TAB>text" line per turn.
python3 - "$SEED_JSON" > "$WORK/turns.tsv" <<'PY'
import json, sys
doc = json.load(open(sys.argv[1], encoding='utf-8'))
for turn in doc['speakerTurns']:
    text = ' '.join(turn['text'].split())
    print(f"{turn['speakerId']}\t{turn['pauseAfterMs']}\t{text}")
PY

INDEX=0
: > "$WORK/concat.txt"

while IFS=$'\t' read -r speaker pause text; do
  [ -n "$speaker" ] || continue
  INDEX=$((INDEX + 1))
  SEG=$(printf '%s/turn-%03d.wav' "$WORK" "$INDEX")
  GAP=$(printf '%s/gap-%03d.wav' "$WORK" "$INDEX")
  VOICE=$(voice_for "$speaker")

  # Pass text through a file so quoting and apostrophes are never a shell issue.
  printf '%s' "$text" > "$WORK/line.txt"
  say -v "$VOICE" -r 172 -f "$WORK/line.txt" -o "$WORK/raw.aiff"
  ffmpeg -y -loglevel error -i "$WORK/raw.aiff" -ac 1 -ar 24000 -c:a pcm_s16le "$SEG"

  # Silence between turns, matching the fixture's pauseAfterMs.
  SECONDS_GAP=$(awk -v ms="$pause" 'BEGIN { printf "%.3f", ms / 1000 }')
  ffmpeg -y -loglevel error -f lavfi -i anullsrc=r=24000:cl=mono -t "$SECONDS_GAP" -c:a pcm_s16le "$GAP"

  echo "file '$SEG'" >> "$WORK/concat.txt"
  echo "file '$GAP'" >> "$WORK/concat.txt"
  printf '  turn %02d  %-7s %s\n' "$INDEX" "$speaker" "$VOICE"
done < "$WORK/turns.tsv"

mkdir -p "$OUT_DIR"

# Concatenate, normalise loudness near -18 LUFS, guard against clipping, and
# export a browser-friendly MP3.
ffmpeg -y -loglevel error -f concat -safe 0 -i "$WORK/concat.txt" \
  -af "loudnorm=I=-18:TP=-1.5:LRA=11,alimiter=limit=0.95" \
  -ar 24000 -ac 1 -c:a libmp3lame -b:a 64k "$OUT_FILE"

DURATION=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT_FILE")
PEAK=$(ffmpeg -hide_banner -i "$OUT_FILE" -af astats=measure_overall=Peak_level -f null - 2>&1 \
  | awk -F': ' '/Peak level dB/ {print $2; exit}')
BYTES=$(wc -c < "$OUT_FILE" | tr -d ' ')

printf '\n%s\n' "$OUT_FILE"
printf '  duration : %.1f s\n' "$DURATION"
printf '  peak     : %s dBFS\n' "${PEAK:-n/a}"
printf '  size     : %s bytes\n' "$BYTES"
printf '\nSet "audioDurationSeconds": %d in %s\n' "$(python3 -c "print(round(float('$DURATION')))")" "$SEED_JSON"
