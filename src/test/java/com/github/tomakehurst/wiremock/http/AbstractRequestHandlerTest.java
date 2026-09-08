/*
 * Copyright (C) 2011-2026 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.tomakehurst.wiremock.http;

import static com.github.tomakehurst.wiremock.common.DataTruncationSettings.NO_TRUNCATION;
import static com.github.tomakehurst.wiremock.matching.MockRequest.mockRequest;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.common.RequestCache;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractRequestHandlerTest {

  private final ResponseRenderer renderer = mock(ResponseRenderer.class);
  private final HttpResponder responder = mock(HttpResponder.class);

  @BeforeEach
  void resetCache() {
    RequestCache.onRequestEnd();
    when(renderer.render(any(ServeEvent.class))).thenReturn(Response.response().status(200).build());
  }

  @AfterEach
  void clearCache() {
    RequestCache.onRequestEnd();
  }

  @Test
  void clearsCacheAfterSuccessfulRequest() {
    RequestCache cache = RequestCache.getCurrent();

    handler().handle(mockRequest(), responder, null);

    assertNotSame(cache, RequestCache.getCurrent());
  }

  @Test
  void clearsCacheWhenRequestHandlingFails() {
    RuntimeException failure = new RuntimeException("Request handling failed");
    AbstractRequestHandler handler =
        new AbstractRequestHandler(renderer, List.of(), List.of(), NO_TRUNCATION) {
          @Override
          protected ServeEvent handleRequest(ServeEvent serveEvent) {
            RequestCache.getCurrent();
            throw failure;
          }
        };
    RequestCache cache = RequestCache.getCurrent();

    assertSame(
        failure,
        assertThrows(RuntimeException.class, () -> handler.handle(mockRequest(), responder, null)));
    assertNotSame(cache, RequestCache.getCurrent());
  }

  @Test
  void clearsCacheWhenRenderingFails() {
    RuntimeException failure = new RuntimeException("Rendering failed");
    when(renderer.render(any(ServeEvent.class))).thenThrow(failure);
    RequestCache cache = RequestCache.getCurrent();

    assertSame(
        failure,
        assertThrows(
            RuntimeException.class, () -> handler().handle(mockRequest(), responder, null)));
    assertNotSame(cache, RequestCache.getCurrent());
  }

  @Test
  void clearsCacheWhenSendingResponseFails() {
    RuntimeException failure = new RuntimeException("Sending failed");
    RequestCache cache = RequestCache.getCurrent();
    HttpResponder failingResponder =
        (request, response, attributes) -> {
          assertSame(cache, RequestCache.getCurrent());
          throw failure;
        };

    assertSame(
        failure,
        assertThrows(
            RuntimeException.class, () -> handler().handle(mockRequest(), failingResponder, null)));
    assertNotSame(cache, RequestCache.getCurrent());
  }

  @Test
  void clearsCacheWhenAfterResponseCallbackFails() {
    RuntimeException failure = new RuntimeException("Callback failed");
    RequestCache cache = RequestCache.getCurrent();
    AbstractRequestHandler handler =
        new AbstractRequestHandler(renderer, List.of(), List.of(), NO_TRUNCATION) {
          @Override
          protected ServeEvent handleRequest(ServeEvent serveEvent) {
            return serveEvent.withResponseDefinition(ResponseDefinition.ok());
          }

          @Override
          protected void afterResponseSent(ServeEvent serveEvent, Response response) {
            assertSame(cache, RequestCache.getCurrent());
            throw failure;
          }
        };

    assertSame(
        failure,
        assertThrows(RuntimeException.class, () -> handler.handle(mockRequest(), responder, null)));
    assertNotSame(cache, RequestCache.getCurrent());
  }

  private AbstractRequestHandler handler() {
    return new AbstractRequestHandler(renderer, List.of(), List.of(), NO_TRUNCATION) {
      @Override
      protected ServeEvent handleRequest(ServeEvent serveEvent) {
        return serveEvent.withResponseDefinition(ResponseDefinition.ok());
      }
    };
  }
}
