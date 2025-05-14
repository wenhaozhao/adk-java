package com.google.adk.web;

import com.google.adk.agents.BaseAgent;
import com.google.adk.web.config.AgentLoadingProperties;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * A service that compiles and loads agents from a source directory.
 *
 * <p>This class is responsible for:
 *
 * <ul>
 *   <li>Locating the ADK core JAR file.
 *   <li>Compiling agent source code using the Eclipse JDT batch compiler.
 *   <li>Loading compiled agents into memory.
 *   <li>Supporting agents in subdirectories (agent apps) and as individual .java files directly
 *       under the root source directory.
 * </ul>
 */
@Service
public class AgentCompilerLoader {
  private static final Logger logger = LoggerFactory.getLogger(AgentCompilerLoader.class);
  private final AgentLoadingProperties properties;
  private Path compiledAgentsOutputDir;
  private final String adkCoreJarPathForCompilation;

  /**
   * Constructs an AgentCompilerLoader with the specified agent loading properties.
   *
   * <p>This constructor initializes the loader and attempts to locate the ADK (Agent Development
   * Kit) core JAR. The ADK core JAR is essential for compiling agent source code, as it contains
   * the necessary base classes and interfaces (e.g., {@link com.google.adk.agents.LlmAgent}, {@link
   * com.google.adk.agents.BaseAgent}). The method {@link #locateAndPrepareAdkCoreJar()} is called
   * to find this JAR, which might involve extracting it if it's nested within another JAR (common
   * in Spring Boot applications).
   *
   * @param properties The configuration properties for agent loading, typically specifying the
   *     source directory of the agents and any additional classpath requirements.
   */
  public AgentCompilerLoader(AgentLoadingProperties properties) {
    this.properties = properties;
    this.adkCoreJarPathForCompilation = locateAndPrepareAdkCoreJar();
  }

  private String locateAndPrepareAdkCoreJar() {
    try {
      URL agentClassUrl = BaseAgent.class.getProtectionDomain().getCodeSource().getLocation();
      if (agentClassUrl == null) {
        logger.warn("Could not get location for BaseAgent.class. ADK Core JAR might not be found.");
        return "";
      }

      if ("file".equals(agentClassUrl.getProtocol()) && agentClassUrl.getPath().endsWith(".jar")) {
        File directJarFile = Paths.get(agentClassUrl.toURI()).toFile();
        if (directJarFile.exists()) {
          logger.info(
              "ADK Core JAR found directly on classpath: {}", directJarFile.getAbsolutePath());
          return directJarFile.getAbsolutePath();
        }
      } else if ("jar".equals(agentClassUrl.getProtocol())) {
        String urlPath = agentClassUrl.getPath();
        if (urlPath.startsWith("file:")) {
          urlPath = urlPath.substring("file:".length());
        }
        int separator = urlPath.indexOf("!/");
        if (separator == -1) {
          logger.warn("Malformed JAR URL for BaseAgent.class: {}", agentClassUrl);
          return "";
        }
        String outerJarPath = urlPath.substring(0, separator);
        File outerJarFile = new File(outerJarPath);

        if (outerJarFile.exists() && urlPath.contains("!/BOOT-INF/lib/google-adk-")) {
          String nestedJarUrlString = agentClassUrl.toString();
          int nestedJarEnd = nestedJarUrlString.lastIndexOf("!/");
          if (nestedJarEnd > 0) {
            String actualNestedJarUrl = nestedJarUrlString.substring(0, nestedJarEnd);
            // This URL should be to the nested JAR itself, like:
            // jar:file:/path/to/google-adk-dev.jar!/BOOT-INF/lib/google-adk-core-0.1.0.jar
            Path tempFile = Files.createTempFile("adk-core-extracted-", ".jar");
            try (InputStream is = new URL(actualNestedJarUrl).openStream()) {
              Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile.toFile().deleteOnExit();
            logger.info("Extracted ADK Core JAR to: {}", tempFile.toAbsolutePath());
            return tempFile.toAbsolutePath().toString();
          }
        }
      }
    } catch (Exception e) {
      logger.error("Error trying to locate or extract ADK Core JAR", e);
    }
    logger.warn(
        "ADK Core JAR could not be reliably located for compilation. Agent compilation might"
            + " fail.");
    return "";
  }

  /**
   * Compiles and loads agents from the source directory specified in the {@link
   * AgentLoadingProperties}.
   *
   * <p>This method performs the following key steps:
   *
   * <ol>
   *   <li>Checks if the agent source directory is configured. If not, it returns an empty map.
   *   <li>Creates a temporary directory to store the compiled agent classes. This directory is
   *       marked for deletion on JVM exit.
   *   <li>Iterates through each entry (subdirectory or .java file) in the agent source root. Each
   *       such entry is considered an "agent unit".
   *   <li>For each agent unit:
   *       <ul>
   *         <li>Collects all relevant {@code .java} files (all files in a subdirectory, or the
   *             single .java file if the unit is a root file).
   *         <li>If Java files are found, it compiles them using the Eclipse JDT batch compiler
   *             (ECJ) into a dedicated subdirectory within the compiled agents' output directory.
   *             The compilation classpath includes the ADK core JAR and any user-defined classpath
   *             entries.
   *         <li>If compilation is successful, it creates a new {@link URLClassLoader} pointing to
   *             the output directory of the compiled classes for that unit and any additional
   *             classpath entries.
   *         <li>It then scans the compiled {@code .class} files for that unit, looking for classes
   *             that contain a public static field named {@code ROOT_AGENT} of a type assignable to
   *             {@link BaseAgent}.
   *         <li>If such a field is found and its value is not null, the {@link BaseAgent} instance
   *             is retrieved and added to the map of loaded agents, keyed by its name. A warning is
   *             logged if an agent with the same name already exists.
   *       </ul>
   * </ol>
   *
   * @return A map where keys are agent names and values are the corresponding {@link BaseAgent}
   *     instances. Returns an empty map if no agents are found or if the source directory is not
   *     configured.
   * @throws IOException if an I/O error occurs during file operations, such as creating temporary
   *     directories or reading source files.
   */
  public Map<String, BaseAgent> loadAgents() throws IOException {
    if (properties.getSourceDir() == null || properties.getSourceDir().isEmpty()) {
      logger.info(
          "Agent source directory (adk.agents.source-dir) not configured. No dynamic agents will be"
              + " loaded.");
      return Collections.emptyMap();
    }

    Path agentsSourceRoot = Paths.get(properties.getSourceDir());
    if (!Files.isDirectory(agentsSourceRoot)) {
      logger.warn("Agent source directory does not exist: {}", agentsSourceRoot);
      return Collections.emptyMap();
    }

    this.compiledAgentsOutputDir = Files.createTempDirectory("adk-compiled-agents-");
    this.compiledAgentsOutputDir.toFile().deleteOnExit();
    logger.info("Compiling agents from {} to {}", agentsSourceRoot, compiledAgentsOutputDir);

    Map<String, BaseAgent> loadedAgents = new HashMap<>();

    try (Stream<Path> stream = Files.list(agentsSourceRoot)) {
      List<Path> entries = stream.collect(Collectors.toList());

      for (Path entry : entries) {
        List<String> javaFilesToCompile = new ArrayList<>();
        String agentUnitName; // Used for logging and creating unique output subdirectories

        if (Files.isDirectory(entry)) {
          agentUnitName = entry.getFileName().toString();
          logger.info("Processing agent sources from directory: {}", agentUnitName);
          try (Stream<Path> javaFilesStream =
              Files.walk(entry)
                  .filter(p -> p.toString().endsWith(".java") && Files.isRegularFile(p))) {
            javaFilesToCompile =
                javaFilesStream
                    .map(p -> p.toAbsolutePath().toString())
                    .collect(Collectors.toList());
          }
        } else if (Files.isRegularFile(entry) && entry.getFileName().toString().endsWith(".java")) {
          String fileName = entry.getFileName().toString();
          agentUnitName = fileName.substring(0, fileName.length() - ".java".length());
          logger.info("Processing agent source file: {}", entry.getFileName());
          javaFilesToCompile.add(entry.toAbsolutePath().toString());
        } else {
          logger.trace("Skipping non-agent entry in agent source root: {}", entry.getFileName());
          continue;
        }

        if (javaFilesToCompile.isEmpty()) {
          logger.info("No .java files found for agent unit: {}", agentUnitName);
          continue;
        }

        Path unitSpecificOutputDir = compiledAgentsOutputDir.resolve(agentUnitName);
        Files.createDirectories(unitSpecificOutputDir);

        boolean compilationSuccess =
            compileSourcesWithECJ(javaFilesToCompile, unitSpecificOutputDir);

        if (compilationSuccess) {
          try {
            List<URL> classLoaderUrls = new ArrayList<>();
            classLoaderUrls.add(unitSpecificOutputDir.toUri().toURL());

            if (properties.getCompileClasspath() != null
                && !properties.getCompileClasspath().isEmpty()) {
              for (String cpEntry : properties.getCompileClasspath().split(File.pathSeparator)) {
                try {
                  classLoaderUrls.add(Paths.get(cpEntry).toUri().toURL());
                } catch (MalformedURLException e) {
                  logger.warn("Invalid classpath entry for classloader: {}", cpEntry, e);
                }
              }
            }

            URLClassLoader agentClassLoader =
                new URLClassLoader(
                    classLoaderUrls.toArray(new URL[0]),
                    AgentCompilerLoader.class.getClassLoader());

            Files.walk(unitSpecificOutputDir)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(
                    classFile -> {
                      try {
                        String relativePath =
                            unitSpecificOutputDir.relativize(classFile).toString();
                        String className =
                            relativePath
                                .substring(0, relativePath.length() - ".class".length())
                                .replace(File.separatorChar, '.');

                        Class<?> loadedClass = agentClassLoader.loadClass(className);
                        Field rootAgentField = null;
                        try {
                          rootAgentField = loadedClass.getField("ROOT_AGENT");
                        } catch (NoSuchFieldException e) {
                          // Common, not every class will have it.
                          return;
                        }

                        if (Modifier.isStatic(rootAgentField.getModifiers())
                            && BaseAgent.class.isAssignableFrom(rootAgentField.getType())) {
                          BaseAgent agentInstance = (BaseAgent) rootAgentField.get(null);
                          if (agentInstance != null) {
                            if (loadedAgents.containsKey(agentInstance.name())) {
                              logger.warn(
                                  "Found another agent with name {}. This will overwrite the"
                                      + " original agent loaded with this name from unit {} using"
                                      + " class {}",
                                  agentInstance.name(),
                                  agentUnitName,
                                  className);
                            }
                            loadedAgents.put(agentInstance.name(), agentInstance);
                            logger.info(
                                "Successfully loaded agent '{}' from unit: {} using class {}",
                                agentInstance.name(),
                                agentUnitName,
                                className);
                          } else {
                            logger.warn(
                                "ROOT_AGENT field in class {} from unit {} was null",
                                className,
                                agentUnitName);
                          }
                        }
                      } catch (ClassNotFoundException | IllegalAccessException e) {
                        logger.error(
                            "Error loading or accessing agent from class file {} for unit {}",
                            classFile,
                            agentUnitName,
                            e);
                      } catch (Exception e) {
                        logger.error(
                            "Unexpected error processing class file {} for unit {}",
                            classFile,
                            agentUnitName,
                            e);
                      }
                    });
          } catch (Exception e) {
            logger.error(
                "Error during class loading setup for unit {}: {}",
                agentUnitName,
                e.getMessage(),
                e);
          }
        } else {
          logger.error("Compilation failed for agent unit: {}", agentUnitName);
        }
      }
    }
    return loadedAgents;
  }

  private boolean compileSourcesWithECJ(List<String> javaFilePaths, Path outputDir) {
    List<String> ecjArgs = new ArrayList<>();
    ecjArgs.add("-17"); // Java version
    ecjArgs.add("-nowarn");
    ecjArgs.add("-d");
    ecjArgs.add(outputDir.toAbsolutePath().toString());

    List<String> effectiveClasspath = new ArrayList<>();
    String systemClasspath = System.getProperty("java.class.path");
    boolean useSystemClasspath =
        systemClasspath != null
            && !systemClasspath.isEmpty()
            && systemClasspath.contains("google-adk-");

    if (useSystemClasspath) {
      effectiveClasspath.add(systemClasspath);
      logger.info("Using system classpath for ECJ: {}", systemClasspath);
      if (properties.getCompileClasspath() != null && !properties.getCompileClasspath().isEmpty()) {
        logger.info(
            "User provided compile classpath (adk.agents.compile-classpath) will be appended to"
                + " system classpath: {}",
            properties.getCompileClasspath());
        effectiveClasspath.add(properties.getCompileClasspath());
      }
    } else {
      if (this.adkCoreJarPathForCompilation != null
          && !this.adkCoreJarPathForCompilation.isEmpty()) {
        effectiveClasspath.add(this.adkCoreJarPathForCompilation);
      } else {
        logger.error(
            "ADK Core JAR path is missing, and system classpath doesn't seem to contain it."
                + " Compilation will likely fail.");
      }
      if (properties.getCompileClasspath() != null && !properties.getCompileClasspath().isEmpty()) {
        effectiveClasspath.add(properties.getCompileClasspath());
      }
      logger.info(
          "Using constructed classpath for ECJ: {}",
          String.join(File.pathSeparator, effectiveClasspath));
    }

    if (!effectiveClasspath.isEmpty()) {
      ecjArgs.add("-cp");
      ecjArgs.add(effectiveClasspath.stream().collect(Collectors.joining(File.pathSeparator)));
    }

    ecjArgs.addAll(javaFilePaths);

    logger.debug("ECJ Args: {}", String.join(" ", ecjArgs));

    PrintWriter outWriter = new PrintWriter(System.out, true);
    PrintWriter errWriter = new PrintWriter(System.err, true);

    boolean success =
        BatchCompiler.compile(ecjArgs.toArray(new String[0]), outWriter, errWriter, null);
    if (!success) {
      logger.error("ECJ Compilation failed. See console output for details.");
    }
    return success;
  }
}
