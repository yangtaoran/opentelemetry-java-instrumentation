/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public final class ArmeriaTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.armeria-1.3";

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;
  private CapturedHttpHeaders capturedHttpClientHeaders = CapturedHttpHeaders.client(Config.get());
  private CapturedHttpHeaders capturedHttpServerHeaders = CapturedHttpHeaders.server(Config.get());

  private final List<AttributesExtractor<? super RequestContext, ? super RequestLog>>
      additionalExtractors = new ArrayList<>();

  private Function<
          SpanStatusExtractor<RequestContext, RequestLog>,
          ? extends SpanStatusExtractor<? super RequestContext, ? super RequestLog>>
      statusExtractorTransformer = Function.identity();

  ArmeriaTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public ArmeriaTracingBuilder setStatusExtractor(
      Function<
              SpanStatusExtractor<RequestContext, RequestLog>,
              ? extends SpanStatusExtractor<? super RequestContext, ? super RequestLog>>
          statusExtractor) {
    this.statusExtractorTransformer = statusExtractor;
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public ArmeriaTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super RequestContext, ? super RequestLog> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /** Sets the {@code peer.service} attribute for http client spans. */
  public void setPeerService(String peerService) {
    this.peerService = peerService;
  }

  /**
   * Configure the HTTP client instrumentation to capture chosen HTTP request and response headers
   * as span attributes.
   *
   * @param capturedHttpClientHeaders An instance of {@link CapturedHttpHeaders} containing the
   *     configured HTTP request and response names.
   */
  public ArmeriaTracingBuilder captureHttpClientHeaders(
      CapturedHttpHeaders capturedHttpClientHeaders) {
    this.capturedHttpClientHeaders = capturedHttpClientHeaders;
    return this;
  }

  /**
   * Configure the HTTP server instrumentation to capture chosen HTTP request and response headers
   * as span attributes.
   *
   * @param capturedHttpServerHeaders An instance of {@link CapturedHttpHeaders} containing the
   *     configured HTTP request and response names.
   */
  public ArmeriaTracingBuilder captureHttpServerHeaders(
      CapturedHttpHeaders capturedHttpServerHeaders) {
    this.capturedHttpServerHeaders = capturedHttpServerHeaders;
    return this;
  }

  public ArmeriaTracing build() {
    ArmeriaHttpClientAttributesExtractor httpClientAttributesExtractor =
        new ArmeriaHttpClientAttributesExtractor(capturedHttpClientHeaders);
    ArmeriaHttpServerAttributesExtractor serverAttributesExtractor =
        new ArmeriaHttpServerAttributesExtractor(capturedHttpServerHeaders);

    InstrumenterBuilder<ClientRequestContext, RequestLog> clientInstrumenterBuilder =
        Instrumenter.builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            HttpSpanNameExtractor.create(httpClientAttributesExtractor));
    InstrumenterBuilder<ServiceRequestContext, RequestLog> serverInstrumenterBuilder =
        Instrumenter.builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            HttpSpanNameExtractor.create(serverAttributesExtractor));

    Stream.of(clientInstrumenterBuilder, serverInstrumenterBuilder)
        .forEach(instrumenter -> instrumenter.addAttributesExtractors(additionalExtractors));

    ArmeriaNetClientAttributesExtractor netClientAttributesExtractor =
        new ArmeriaNetClientAttributesExtractor();

    clientInstrumenterBuilder
        .setSpanStatusExtractor(
            statusExtractorTransformer.apply(
                HttpSpanStatusExtractor.create(httpClientAttributesExtractor)))
        .addAttributesExtractor(netClientAttributesExtractor)
        .addAttributesExtractor(httpClientAttributesExtractor)
        .addRequestMetrics(HttpClientMetrics.get());
    serverInstrumenterBuilder
        .setSpanStatusExtractor(
            statusExtractorTransformer.apply(
                HttpSpanStatusExtractor.create(serverAttributesExtractor)))
        .addAttributesExtractor(new ArmeriaNetServerAttributesExtractor())
        .addAttributesExtractor(serverAttributesExtractor)
        .addRequestMetrics(HttpServerMetrics.get())
        .addContextCustomizer(ServerSpanNaming.get());

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(SemanticAttributes.PEER_SERVICE, peerService));
    } else {
      clientInstrumenterBuilder.addAttributesExtractor(
          PeerServiceAttributesExtractor.create(netClientAttributesExtractor));
    }

    return new ArmeriaTracing(
        clientInstrumenterBuilder.newClientInstrumenter(ClientRequestContextSetter.INSTANCE),
        serverInstrumenterBuilder.newServerInstrumenter(RequestContextGetter.INSTANCE));
  }
}
