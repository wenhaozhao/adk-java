/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.adk.network;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import java.io.IOException;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Wraps a real HTTP response to expose the methods needed by the GenAI SDK. */
public final class HttpApiResponse extends ApiResponse {

  private final Response response;

  /** Constructs a HttpApiResponse instance with the response. */
  public HttpApiResponse(Response response) {
    throwFromResponse(response);
    this.response = response;
  }

  /** Returns the ResponseBody from the response. */
  @Override
  public ResponseBody getEntity() {
    return response.body();
  }

  /**
   * Throws an ApiException from the response if the response is not a OK status. This method is
   * adapted to work with OkHttp's Response.
   *
   * @param response The response from the API call.
   */
  private void throwFromResponse(Response response) {
    if (response.isSuccessful()) {
      return;
    }
    int code = response.code();
    String status = response.message();
    String message = getErrorMessageFromResponse(response);
    if (code >= 400 && code < 500) { // Client errors.
      throw new ClientException(code, status, message);
    } else if (code >= 500 && code < 600) { // Server errors.
      throw new ServerException(code, status, message);
    } else {
      throw new ApiException(code, status, message);
    }
  }

  private String getErrorMessageFromResponse(Response response) {
    try {
      if (response.body() != null) {
        return response.body().string(); // Consume only once.
      }
    } catch (IOException e) {
      // Log the error.  Return a generic message to avoid masking original exception.
      return "Error reading response body";
    }
    return "";
  }

  /** Closes the Http response. */
  @Override
  public void close() {
    response.close();
  }
}
