import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryMarkdownReportRenderer

plugins {
  id("com.github.jk1.dependency-license-report")

  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("io.opentelemetry.instrumentation.javaagent-shadowing")
}

description = "OpenTelemetry Javaagent"
group = "io.opentelemetry.javaagent"

// this configuration collects libs that will be placed in the bootstrap classloader
val bootstrapLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}
// this configuration collects only required instrumentations and agent machinery
val baseJavaagentLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

// this configuration collects libs that will be placed in the agent classloader, isolated from the instrumented application code
val javaagentLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  extendsFrom(baseJavaagentLibs)
}
// this configuration collects just exporter libs (also placed in the agent classloader & isolated from the instrumented application)
val exporterLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}
// this configuration collects just exporter libs for slim artifact (also placed in the agent classloader & isolated from the instrumented application)
val exporterSlimLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

// exclude dependencies that are to be placed in bootstrap from agent libs - they won't be added to inst/
listOf(baseJavaagentLibs, javaagentLibs, exporterLibs, exporterSlimLibs).forEach {
  it.run {
    exclude("org.slf4j")
    exclude("io.opentelemetry", "opentelemetry-api")
    exclude("io.opentelemetry", "opentelemetry-api-metrics")
    exclude("io.opentelemetry", "opentelemetry-semconv")
  }
}

val licenseReportDependencies by configurations.creating {
  extendsFrom(bootstrapLibs)
}

dependencies {
  bootstrapLibs(project(":instrumentation-api"))
  bootstrapLibs(project(":instrumentation-api-annotation-support"))
  bootstrapLibs(project(":instrumentation-api-appender"))
  bootstrapLibs(project(":javaagent-bootstrap"))
  bootstrapLibs(project(":javaagent-instrumentation-api"))
  bootstrapLibs("org.slf4j:slf4j-simple")

  baseJavaagentLibs(project(":javaagent-extension-api"))
  baseJavaagentLibs(project(":javaagent-tooling"))
  baseJavaagentLibs(project(":muzzle"))
  baseJavaagentLibs(project(":instrumentation:opentelemetry-annotations-1.0:javaagent"))
  baseJavaagentLibs(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  baseJavaagentLibs(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.4:javaagent"))
  baseJavaagentLibs(project(":instrumentation:executors:javaagent"))
  baseJavaagentLibs(project(":instrumentation:internal:internal-class-loader:javaagent"))
  baseJavaagentLibs(project(":instrumentation:internal:internal-eclipse-osgi-3.6:javaagent"))
  baseJavaagentLibs(project(":instrumentation:internal:internal-lambda:javaagent"))
  baseJavaagentLibs(project(":instrumentation:internal:internal-reflection:javaagent"))
  baseJavaagentLibs(project(":instrumentation:internal:internal-url-class-loader:javaagent"))

  exporterLibs(project(":javaagent-exporters"))

  exporterSlimLibs("io.opentelemetry:opentelemetry-exporter-otlp")
  exporterSlimLibs("io.opentelemetry:opentelemetry-exporter-otlp-metrics")

  // concurrentlinkedhashmap-lru and weak-lock-free are copied in to the instrumentation-api module
  licenseReportDependencies("com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru:1.4.2")
  licenseReportDependencies("com.blogspot.mydailyjava:weak-lock-free:0.18")
  // TODO ideally this would be :instrumentation instead of :javaagent-tooling
  //  in case there are dependencies (accidentally) pulled in by instrumentation modules
  //  but I couldn't get that to work
  licenseReportDependencies(project(":javaagent-tooling"))
  licenseReportDependencies(project(":javaagent-extension-api"))

  testCompileOnly(project(":javaagent-bootstrap"))
  testCompileOnly(project(":javaagent-instrumentation-api"))

  testImplementation("com.google.guava:guava")
  testImplementation("io.opentelemetry:opentelemetry-sdk")
  // TODO(anuraaga): Remove after github.com/open-telemetry/opentelemetry-java/issues/3999
  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation("io.opentracing.contrib.dropwizard:dropwizard-opentracing:0.2.2")
}

val javaagentDependencies = dependencies

// collect all bootstrap and javaagent instrumentation dependencies
project(":instrumentation").subprojects {
  val subProj = this

  plugins.withId("otel.javaagent-bootstrap") {
    javaagentDependencies.run {
      add(bootstrapLibs.name, project(subProj.path))
    }
  }

  plugins.withId("otel.javaagent-instrumentation") {
    javaagentDependencies.run {
      add(javaagentLibs.name, project(subProj.path))
    }
  }
}

tasks {
  processResources {
    from(rootProject.file("licenses")) {
      into("META-INF/licenses")
    }
  }

  val relocateBaseJavaagentLibs by registering(ShadowJar::class) {
    configurations = listOf(baseJavaagentLibs)

    duplicatesStrategy = DuplicatesStrategy.FAIL

    archiveFileName.set("baseJavaagentLibs-relocated.jar")

    excludeBootstrapJars()
  }

  val relocateJavaagentLibs by registering(ShadowJar::class) {
    configurations = listOf(javaagentLibs)

    duplicatesStrategy = DuplicatesStrategy.FAIL

    archiveFileName.set("javaagentLibs-relocated.jar")

    excludeBootstrapJars()
  }

  val relocateExporterLibs by registering(ShadowJar::class) {
    configurations = listOf(exporterLibs)

    archiveFileName.set("exporterLibs-relocated.jar")
  }

  val relocateExporterSlimLibs by registering(ShadowJar::class) {
    configurations = listOf(exporterSlimLibs)

    archiveFileName.set("exporterSlimLibs-relocated.jar")
  }

  // Includes everything needed for OOTB experience
  val shadowJar by existing(ShadowJar::class) {
    configurations = listOf(bootstrapLibs)

    dependsOn(relocateJavaagentLibs, relocateExporterLibs)
    isolateClasses(relocateJavaagentLibs.get().outputs.files)
    isolateClasses(relocateExporterLibs.get().outputs.files)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    archiveClassifier.set("")

    manifest {
      attributes(jar.get().manifest.attributes)
      attributes(
        "Main-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
        "Agent-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
        "Premain-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
        "Can-Redefine-Classes" to true,
        "Can-Retransform-Classes" to true
      )
    }
  }

  // Includes instrumentations plus the OTLP/gRPC exporters
  val slimShadowJar by registering(ShadowJar::class) {
    configurations = listOf(bootstrapLibs)

    dependsOn(relocateJavaagentLibs, relocateExporterSlimLibs)
    isolateClasses(relocateJavaagentLibs.get().outputs.files)
    isolateClasses(relocateExporterSlimLibs.get().outputs.files)

    archiveClassifier.set("slim")

    manifest {
      attributes(shadowJar.get().manifest.attributes)
    }
  }

  // Includes only the agent machinery and required instrumentations
  val baseJavaagentJar by registering(ShadowJar::class) {
    configurations = listOf(bootstrapLibs)

    dependsOn(relocateBaseJavaagentLibs)
    isolateClasses(relocateBaseJavaagentLibs.get().outputs.files)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    archiveClassifier.set("base")

    manifest {
      attributes(shadowJar.get().manifest.attributes)
    }
  }

  val baseJar by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
  }

  artifacts {
    add("baseJar", baseJavaagentJar)
  }

  jar {
    enabled = false
  }

  assemble {
    dependsOn(shadowJar, slimShadowJar, baseJavaagentJar)
  }

  withType<Test>().configureEach {
    dependsOn(shadowJar)

    jvmArgs("-Dotel.javaagent.debug=true")

    jvmArgumentProviders.add(JavaagentProvider(shadowJar.flatMap { it.archiveFile }))

    testLogging {
      events("started")
    }
  }

  val cleanLicenses by registering(Delete::class) {
    delete(rootProject.file("licenses"))
  }

  named("generateLicenseReport").configure {
    dependsOn(cleanLicenses)
  }

  publishing {
    publications {
      named<MavenPublication>("maven") {
        artifact(slimShadowJar)
        project.shadow.component(this)
      }
    }
  }
}

licenseReport {
  outputDir = rootProject.file("licenses").absolutePath

  renderers = arrayOf(InventoryMarkdownReportRenderer())

  configurations = arrayOf(licenseReportDependencies.name)

  excludeGroups = arrayOf(
    "io.opentelemetry.instrumentation",
    "io.opentelemetry.javaagent"
  )

  filters = arrayOf(LicenseBundleNormalizer("$projectDir/license-normalizer-bundle.json", true))
}

fun CopySpec.isolateClasses(jars: Iterable<File>) {
  jars.forEach {
    from(zipTree(it)) {
      // important to keep prefix "inst" short, as it is prefixed to lots of strings in runtime mem
      into("inst")
      rename("(^.*)\\.class\$", "\$1.classdata")
      // Rename LICENSE file since it clashes with license dir on non-case sensitive FSs (i.e. Mac)
      rename("""^LICENSE$""", "LICENSE.renamed")
      exclude("META-INF/INDEX.LIST")
      exclude("META-INF/*.DSA")
      exclude("META-INF/*.SF")
    }
  }
}

// exclude bootstrap projects from javaagent libs - they won't be added to inst/
fun ShadowJar.excludeBootstrapJars() {
  dependencies {
    exclude(project(":instrumentation-api"))
    exclude(project(":instrumentation-api-annotation-support"))
    exclude(project(":instrumentation-api-appender"))
    exclude(project(":javaagent-bootstrap"))
    exclude(project(":javaagent-instrumentation-api"))
  }
}

class JavaagentProvider(
  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  val agentJar: Provider<RegularFile>
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> = listOf(
    "-javaagent:${file(agentJar).absolutePath}"
  )
}
