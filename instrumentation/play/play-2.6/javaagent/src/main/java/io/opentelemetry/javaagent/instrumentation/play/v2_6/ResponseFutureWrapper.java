/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_6;

import static io.opentelemetry.javaagent.instrumentation.play.v2_6.Play26Singletons.instrumenter;

import io.opentelemetry.context.Context;
import play.api.mvc.Result;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;

public class ResponseFutureWrapper {

  public static Future<Result> wrap(
      Future<Result> future, Context context, ExecutionContext executionContext) {

    return future.transform(
        new AbstractFunction1<Result, Result>() {
          @Override
          public Result apply(Result result) {
            instrumenter().end(context, null, null, null);
            return result;
          }
        },
        new AbstractFunction1<Throwable, Throwable>() {
          @Override
          public Throwable apply(Throwable throwable) {
            instrumenter().end(context, null, null, throwable);
            return throwable;
          }
        },
        executionContext);
  }
}
