package com.google.adk.sessions;

/** Represents a general error that occurred during session management operations. */
public class SessionException extends RuntimeException {

  public SessionException(String message) {
    super(message);
  }

  public SessionException(String message, Throwable cause) {
    super(message, cause);
  }

  public SessionException(Throwable cause) {
    super(cause);
  }
}
