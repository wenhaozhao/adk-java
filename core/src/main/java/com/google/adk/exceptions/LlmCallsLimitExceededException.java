package com.google.adk.exceptions;

/** An error indicating that the limit for calls to the LLM has been exceeded. */
public final class LlmCallsLimitExceededException extends Exception {

  public LlmCallsLimitExceededException(String message) {
    super(message);
  }
}
