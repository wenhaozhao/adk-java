/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.adk.agents;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.adk.agents.ConfigAgentUtils.ConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ConfigAgentUtils}. */
@RunWith(JUnit4.class)
public final class ConfigAgentUtilsTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void fromConfig_nonExistentFile_throwsException() {
    String nonExistentPath = new File(tempFolder.getRoot(), "nonexistent.yaml").getAbsolutePath();
    ConfigurationException exception =
        assertThrows(
            ConfigurationException.class, () -> ConfigAgentUtils.fromConfig(nonExistentPath));
    assertThat(exception).hasMessageThat().isEqualTo("Config file not found: " + nonExistentPath);
  }

  @Test
  public void fromConfig_invalidYaml_throwsException() throws IOException {
    File configFile = tempFolder.newFile("invalid.yaml");
    Files.writeString(configFile.toPath(), "name: test\n  description: invalid indent");
    String configPath = configFile.getAbsolutePath();

    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> ConfigAgentUtils.fromConfig(configPath));
    assertThat(exception).hasMessageThat().startsWith("Failed to load or parse config file:");
  }

  @Test
  public void fromConfig_validYamlLlmAgent_attemptsToCreateLlmAgent()
      throws IOException, ConfigurationException {
    File configFile = tempFolder.newFile("valid.yaml");
    Files.writeString(
        configFile.toPath(),
        "name: testAgent\n"
            + "description: A test agent\n"
            + "instruction: test instruction\n"
            + "agent_class: LlmAgent\n");
    String configPath = configFile.getAbsolutePath();
    BaseAgent agent = ConfigAgentUtils.fromConfig(configPath);
    assertThat(agent).isNotNull();
    assertThat(agent).isInstanceOf(LlmAgent.class);
  }

  @Test
  public void fromConfig_customAgentClass_throwsUnsupportedException() throws IOException {
    File configFile = tempFolder.newFile("custom.yaml");
    String customAgentClass = "com.example.CustomAgent";
    Files.writeString(
        configFile.toPath(),
        String.format(
            "name: customAgent\n" + "description: A custom agent\n" + "agent_class: %s \n",
            customAgentClass));
    String configPath = configFile.getAbsolutePath();
    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> ConfigAgentUtils.fromConfig(configPath));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "agentClass '"
                + customAgentClass
                + "' is not supported. It must be a subclass of BaseAgent.");
  }

  @Test
  public void fromConfig_baseAgentClass_throwsUnsupportedException() throws IOException {
    File configFile = tempFolder.newFile("custom.yaml");
    String customAgentClass = "BaseAgent";
    Files.writeString(
        configFile.toPath(),
        "name: customAgent\n" + "description: A custom agent\n" + "agent_class: BaseAgent \n");
    String configPath = configFile.getAbsolutePath();
    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> ConfigAgentUtils.fromConfig(configPath));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "agentClass '"
                + customAgentClass
                + "' is not supported. It must be a subclass of BaseAgent.");
  }

  @Test
  public void fromConfig_emptyAgentClass_defaultsToLlmAgent()
      throws IOException, ConfigurationException {
    File configFile = tempFolder.newFile("empty_class.yaml");
    Files.writeString(
        configFile.toPath(),
        "name: emptyClassAgent\n"
            + "description: Agent with empty class\n"
            + "instruction: test instruction\n"
            + "agent_class: \"\"\n");
    String configPath = configFile.getAbsolutePath();
    BaseAgent agent = ConfigAgentUtils.fromConfig(configPath);
    assertThat(agent).isNotNull();
    assertThat(agent).isInstanceOf(LlmAgent.class);
  }

  @Test
  public void fromConfig_withoutAgentClass_defaultsToLlmAgent()
      throws IOException, ConfigurationException {
    File configFile = tempFolder.newFile("empty_class.yaml");
    Files.writeString(
        configFile.toPath(),
        "name: emptyClassAgent\n"
            + "description: Agent with empty class\n"
            + "instruction: test instruction\n");
    String configPath = configFile.getAbsolutePath();
    BaseAgent agent = ConfigAgentUtils.fromConfig(configPath);
    assertThat(agent).isNotNull();
    assertThat(agent).isInstanceOf(LlmAgent.class);
  }

  @Test
  public void fromConfig_yamlWithExtraFields_ignoresUnknownProperties()
      throws IOException, ConfigurationException {
    File configFile = tempFolder.newFile("extra_fields.yaml");
    Files.writeString(
        configFile.toPath(),
        "name: flexibleAgent\n"
            + "description: Agent with extra fields\n"
            + "instruction: test instruction\n"
            + "agent_class: LlmAgent\n"
            + "unknown_field: some_value\n"
            + "another_unknown: 123\n"
            + "nested_unknown:\n"
            + "  key: value\n");
    String configPath = configFile.getAbsolutePath();

    BaseAgent agent = ConfigAgentUtils.fromConfig(configPath);

    assertThat(agent).isNotNull();
    assertThat(agent).isInstanceOf(LlmAgent.class);
    assertThat(agent.name()).isEqualTo("flexibleAgent");
    assertThat(agent.description()).isEqualTo("Agent with extra fields");
  }

  @Test
  public void fromConfig_missingRequiredFields_throwsException() throws IOException {
    File configFile = tempFolder.newFile("incomplete.yaml");
    Files.writeString(
        configFile.toPath(),
        "description: Agent missing required fields\n" + "agent_class: LlmAgent\n");
    String configPath = configFile.getAbsolutePath();

    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> ConfigAgentUtils.fromConfig(configPath));

    assertThat(exception).hasMessageThat().contains("Failed to create agent from config");
    assertThat(exception.getCause()).isNotNull();
  }
}
