/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sdk.appender;

import io.opentelemetry.instrumentation.api.appender.LogEmitterBuilder;
import io.opentelemetry.instrumentation.api.appender.LogEmitterProvider;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;

public final class DelegatingLogEmitterProvider implements LogEmitterProvider {

  private final SdkLogEmitterProvider delegate;

  public static DelegatingLogEmitterProvider from(SdkLogEmitterProvider delegate) {
    return new DelegatingLogEmitterProvider(delegate);
  }

  private DelegatingLogEmitterProvider(SdkLogEmitterProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public LogEmitterBuilder logEmitterBuilder(String instrumentationName) {
    return new DelegatingLogEmitterBuilder(delegate.logEmitterBuilder(instrumentationName));
  }
}
