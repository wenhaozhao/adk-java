package com.google.adk.agents;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.genai.types.AudioTranscriptionConfig;
import com.google.genai.types.Modality;
import com.google.genai.types.SpeechConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RunConfigTest {

  @Test
  public void testBuilderWithVariousValues() {
    SpeechConfig speechConfig = SpeechConfig.builder().build();
    AudioTranscriptionConfig audioTranscriptionConfig = AudioTranscriptionConfig.builder().build();

    RunConfig runConfig =
        RunConfig.builder() // Use the static builder method
            .setSpeechConfig(speechConfig)
            .setResponseModalities(ImmutableList.of(new Modality(Modality.Known.TEXT)))
            .setSaveInputBlobsAsArtifacts(true)
            .setStreamingMode(RunConfig.StreamingMode.SSE)
            .setOutputAudioTranscription(audioTranscriptionConfig)
            .setMaxLlmCalls(10)
            .build();

    assertThat(runConfig.speechConfig()).isEqualTo(speechConfig);
    assertThat(runConfig.responseModalities()).containsExactly(new Modality(Modality.Known.TEXT));
    assertThat(runConfig.saveInputBlobsAsArtifacts()).isTrue();
    assertThat(runConfig.streamingMode()).isEqualTo(RunConfig.StreamingMode.SSE);
    assertThat(runConfig.outputAudioTranscription()).isEqualTo(audioTranscriptionConfig);
    assertThat(runConfig.maxLlmCalls()).isEqualTo(10);
  }

  @Test
  public void testBuilderDefaults() {
    RunConfig runConfig = RunConfig.builder().build(); // Build with default values

    assertThat(runConfig.speechConfig()).isNull();
    assertThat(runConfig.responseModalities()).isEmpty();
    assertThat(runConfig.saveInputBlobsAsArtifacts()).isFalse();
    assertThat(runConfig.streamingMode()).isEqualTo(RunConfig.StreamingMode.NONE);
    assertThat(runConfig.outputAudioTranscription()).isNull();
    assertThat(runConfig.maxLlmCalls()).isEqualTo(500); // Default maxLlmCalls
  }

  @Test
  public void testMaxLlmCalls_negativeValueAllowedInSetterButLoggedAndBuilt() {
    // AutoValue builder setters don't typically have validation.
    // Validation happens in the build() method.
    // A negative value is allowed in the setter, but the build logs a warning.
    RunConfig runConfig = RunConfig.builder().setMaxLlmCalls(-1).build();
    assertThat(runConfig.maxLlmCalls()).isEqualTo(-1);
    // Note: We don't typically assert on logging output in unit tests
  }

  @Test
  public void testMaxLlmCalls_maxValueThrowsExceptionOnBuild() {
    // The validation for Integer.MAX_VALUE is in the build() method.
    assertThrows(
        IllegalArgumentException.class,
        () -> RunConfig.builder().setMaxLlmCalls(Integer.MAX_VALUE).build());
  }

  @Test
  public void testBuilderWithDifferentValues() {
    SpeechConfig speechConfig = SpeechConfig.builder().build();
    AudioTranscriptionConfig audioTranscriptionConfig = AudioTranscriptionConfig.builder().build();

    RunConfig runConfig =
        RunConfig.builder() // Use the static builder method
            .setSpeechConfig(speechConfig)
            .setResponseModalities(ImmutableList.of(new Modality(Modality.Known.AUDIO)))
            .setSaveInputBlobsAsArtifacts(true)
            .setStreamingMode(RunConfig.StreamingMode.BIDI)
            .setOutputAudioTranscription(audioTranscriptionConfig)
            .setMaxLlmCalls(20)
            .build();

    assertThat(runConfig.speechConfig()).isEqualTo(speechConfig);
    assertThat(runConfig.responseModalities()).containsExactly(new Modality(Modality.Known.AUDIO));
    assertThat(runConfig.saveInputBlobsAsArtifacts()).isTrue();
    assertThat(runConfig.streamingMode()).isEqualTo(RunConfig.StreamingMode.BIDI);
    assertThat(runConfig.outputAudioTranscription()).isEqualTo(audioTranscriptionConfig);
    assertThat(runConfig.maxLlmCalls()).isEqualTo(20);
  }
}
