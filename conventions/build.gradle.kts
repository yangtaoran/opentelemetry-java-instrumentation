plugins {
  `kotlin-dsl`
  // When updating, update below in dependencies too
  id("com.diffplug.spotless") version "5.16.0"
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("../buildscripts/spotless.license.java"), "(package|import|public)")
    target("src/**/*.java")
  }
}

repositories {
  mavenCentral()
  gradlePluginPortal()
  maven {
    url = uri("https://oss.sonatype.org/content/repositories/snapshots")
  }
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

dependencies {
  implementation(gradleApi())
  implementation(localGroovy())

  implementation("io.opentelemetry.instrumentation:gradle-plugins:1.9.1-alpha")

  implementation("org.eclipse.aether:aether-connector-basic:1.1.0")
  implementation("org.eclipse.aether:aether-transport-http:1.1.0")
  implementation("org.apache.maven:maven-aether-provider:3.3.9")

  // When updating, update above in plugins too
  implementation("com.diffplug.spotless:spotless-plugin-gradle:5.16.0")
  implementation("com.google.guava:guava:31.0.1-jre")
  implementation("gradle.plugin.com.google.protobuf:protobuf-gradle-plugin:0.8.18")
  implementation("gradle.plugin.com.github.johnrengelman:shadow:7.1.0")
  implementation("org.ow2.asm:asm:9.1")
  implementation("org.ow2.asm:asm-tree:9.1")
  implementation("org.apache.httpcomponents:httpclient:4.5.13")
  implementation("org.gradle:test-retry-gradle-plugin:1.3.1")
  implementation("ru.vyarus:gradle-animalsniffer-plugin:1.5.4")
  // When updating, also update dependencyManagement/build.gradle.kts
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.12.3")
  implementation("gradle.plugin.io.morethan.jmhreport:gradle-jmh-report:0.9.0")
  implementation("me.champeau.jmh:jmh-gradle-plugin:0.6.6")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:2.0.2")
  implementation("net.ltgt.gradle:gradle-nullaway-plugin:1.2.0")
  implementation("me.champeau.gradle:japicmp-gradle-plugin:0.3.0")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.8.2"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("org.assertj:assertj-core:3.21.0")
}