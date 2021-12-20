plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.restlet")
    module.set("org.restlet")
    versions.set("[1.0.0, 1.2-M1)")
    assertInverse.set(true)
  }
}

repositories {
  mavenCentral()
  maven("https://maven.restlet.talend.com/")
  mavenLocal()
}

dependencies {
  api(project(":instrumentation:restlet:restlet-1.0:library"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.restlet:org.restlet:1.1.5")
  library("com.noelios.restlet:com.noelios.restlet:1.1.5")

  implementation(project(":instrumentation:restlet:restlet-1.0:library"))
  testImplementation(project(":instrumentation:restlet:restlet-1.0:testing"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))

  latestDepTestLibrary("org.restlet:org.restlet:1.1.+")
  latestDepTestLibrary("com.noelios.restlet:com.noelios.restlet:1.1.+")
}
