/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.rabbitmq.client.ConnectionFactory
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.Shared

import java.time.Duration

import static com.google.common.net.InetAddresses.isInetAddress
import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runInternalSpan

class ContextPropagationTest extends AgentInstrumentationSpecification {

  @Shared
  GenericContainer rabbitMqContainer
  @Shared
  ConfigurableApplicationContext applicationContext
  @Shared
  ConnectionFactory connectionFactory

  def setupSpec() {
    rabbitMqContainer = new GenericContainer('rabbitmq:latest')
      .withExposedPorts(5672)
      .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
      .withStartupTimeout(Duration.ofMinutes(2))
    rabbitMqContainer.start()

    def app = new SpringApplication(ConsumerConfig)
    app.setDefaultProperties([
      "spring.jmx.enabled"              : false,
      "spring.main.web-application-type": "none",
      "spring.rabbitmq.host"            : rabbitMqContainer.containerIpAddress,
      "spring.rabbitmq.port"            : rabbitMqContainer.getMappedPort(5672),
    ])
    applicationContext = app.run()

    connectionFactory = new ConnectionFactory(
      host: rabbitMqContainer.containerIpAddress,
      port: rabbitMqContainer.getMappedPort(5672)
    )
  }

  def cleanupSpec() {
    rabbitMqContainer?.stop()
    applicationContext?.close()
  }

  def "should propagate context to consumer"() {
    given:
    def connection = connectionFactory.newConnection()
    def channel = connection.createChannel()

    when:
    runWithSpan("parent") {
      applicationContext.getBean(AmqpTemplate)
        .convertAndSend(ConsumerConfig.TEST_QUEUE, "test")
    }

    then:
    assertTraces(2) {
      trace(0, 5) {
        span(0) {
          name "parent"
        }
        span(1) {
          // created by rabbitmq instrumentation
          name "<default> send"
          kind PRODUCER
          childOf span(0)
          attributes {
            // "localhost" on linux, null on windows
            "$SemanticAttributes.NET_PEER_NAME" { it == "localhost" || it == null }
            "$SemanticAttributes.NET_PEER_IP" { isInetAddress(it as String) }
            "$SemanticAttributes.NET_PEER_PORT" Long
            "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$SemanticAttributes.MESSAGING_DESTINATION" "<default>"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY" String
          }
        }
        // spring-cloud-stream-binder-rabbit listener puts all messages into a BlockingQueue immediately after receiving
        // that's why the rabbitmq CONSUMER span will never have any child span (and propagate context, actually)
        span(2) {
          // created by rabbitmq instrumentation
          name "testQueue process"
          kind CONSUMER
          childOf span(1)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$SemanticAttributes.MESSAGING_DESTINATION" "<default>"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY" String
          }
        }
        span(3) {
          // created by spring-rabbit instrumentation
          name "testQueue process"
          kind CONSUMER
          childOf span(1)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testQueue"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
          }
        }
        span(4) {
          name "consumer"
          childOf span(3)
        }
      }
      trace(1, 1) {
        span(0) {
          // created by rabbitmq instrumentation
          name "basic.ack"
          kind CLIENT
          attributes {
            // "localhost" on linux, null on windows
            "$SemanticAttributes.NET_PEER_NAME" { it == "localhost" || it == null }
            "$SemanticAttributes.NET_PEER_IP" { isInetAddress(it as String) }
            "$SemanticAttributes.NET_PEER_PORT" Long
            "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"
          }
        }
      }
    }

    cleanup:
    channel?.close()
    connection?.close()
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class ConsumerConfig {

    static final String TEST_QUEUE = "testQueue"

    @Bean
    Queue testQueue() {
      new Queue(TEST_QUEUE)
    }

    @RabbitListener(queues = TEST_QUEUE)
    void consume(String ignored) {
      runInternalSpan("consumer")
    }
  }
}
