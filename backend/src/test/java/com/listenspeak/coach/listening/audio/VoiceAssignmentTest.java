package com.listenspeak.coach.listening.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.listenspeak.coach.listening.domain.ExerciseFixtures;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.SequencedSet;
import org.junit.jupiter.api.Test;

class VoiceAssignmentTest {

    private static SequencedSet<String> speakers(String... ids) {
        return new LinkedHashSet<>(java.util.List.of(ids));
    }

    @Test
    void givesEachSpeakerADifferentVoice() {
        Map<String, String> voices = VoiceAssignment.assign(speakers("ANA", "BEN", "CHRIS"));

        assertThat(voices).hasSize(3);
        assertThat(voices.values()).doesNotHaveDuplicates();
    }

    @Test
    void assignsTheSameVoicesEveryTimeForTheSameDialogue() {
        var exercise = ExerciseFixtures.validPart5();

        Map<String, String> first = VoiceAssignment.assign(exercise.speakerIds());
        Map<String, String> second = VoiceAssignment.assign(exercise.speakerIds());

        assertThat(second).isEqualTo(first);
    }

    @Test
    void assignsByFirstAppearanceSoTheMappingDoesNotDependOnHashOrder() {
        assertThat(VoiceAssignment.assign(speakers("ZED", "ALPHA")))
                .containsExactly(
                        Map.entry("ZED", VoiceAssignment.pool().get(0)),
                        Map.entry("ALPHA", VoiceAssignment.pool().get(1)));
    }

    @Test
    void prefersTheHighestQualityVoicesFirst() {
        assertThat(VoiceAssignment.pool()).startsWith("marin", "cedar");
    }

    @Test
    void refusesToRunOutOfDistinctVoicesSilently() {
        SequencedSet<String> tooMany = speakers("A", "B", "C", "D", "E", "F", "G");

        assertThatThrownBy(() -> VoiceAssignment.assign(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No distinct voice");
    }
}
