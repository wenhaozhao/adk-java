package com.google.adk.sessions;

/** Indicates that a requested session could not be found. */
public class SessionNotFoundException extends SessionException {

  public SessionNotFoundException(String message) {
    super(message);
  }

  public SessionNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
