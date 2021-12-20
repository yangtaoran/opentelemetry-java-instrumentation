plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("kafka-clients")
    versions.set("[0.11.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:kafka-clients:kafka-clients-0.11:bootstrap"))
  implementation(project(":instrumentation:kafka-clients:kafka-clients-common:library"))

  library("org.apache.kafka:kafka-clients:0.11.0.0")

  testImplementation("org.testcontainers:kafka")
  testImplementation(project(":instrumentation:kafka-clients:kafka-clients-0.11:testing"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
  }

  val testPropagationDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("KafkaClientPropagationDisabledTest")
      isFailOnNoMatchingTests = false
    }
    include("**/KafkaClientPropagationDisabledTest.*")
    jvmArgs("-Dotel.instrumentation.kafka.client-propagation.enabled=false")
  }

  val testReceiveSpansDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("KafkaClientSuppressReceiveSpansTest")
      isFailOnNoMatchingTests = false
    }
    include("**/KafkaClientSuppressReceiveSpansTest.*")
    jvmArgs("-Dotel.instrumentation.common.experimental.suppress-messaging-receive-spans=true")
  }

  test {
    dependsOn(testPropagationDisabled)
    dependsOn(testReceiveSpansDisabled)
    filter {
      excludeTestsMatching("KafkaClientPropagationDisabledTest")
      excludeTestsMatching("KafkaClientSuppressReceiveSpansTest")
      isFailOnNoMatchingTests = false
    }
  }
}
