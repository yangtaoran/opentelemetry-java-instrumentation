/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.api.internal.SpanKey;

public class AgentSpanTesting {

  /**
   * Runs the provided {@code runnable} inside the scope of an SERVER span with name {@code
   * spanName}.
   */
  public static void runWithServerSpan(String spanName, Runnable runnable) {
    runnable.run();
  }

  /**
   * Runs the provided {@code runnable} inside the scope of an CLIENT span with name {@code
   * spanName}.
   */
  public static void runWithClientSpan(String spanName, Runnable runnable) {
    runnable.run();
  }

  /**
   * Runs the provided {@code runnable} inside the scope of an INTERNAL span with name {@code
   * spanName}. Span is added into context under all possible keys from {@link SpanKey}
   */
  public static void runWithAllSpanKeys(String spanName, Runnable runnable) {
    runnable.run();
  }
}
