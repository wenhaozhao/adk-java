package com.google.adk.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Properties for loading agents. */
@Component
@ConfigurationProperties(prefix = "adk.agents")
public class AgentLoadingProperties {
  private String sourceDir = "src/main/java";
  private String compileClasspath;

  public String getSourceDir() {
    return sourceDir;
  }

  public void setSourceDir(String sourceDir) {
    this.sourceDir = sourceDir;
  }

  public String getCompileClasspath() {
    return compileClasspath;
  }

  public void setCompileClasspath(String compileClasspath) {
    this.compileClasspath = compileClasspath;
  }
}
