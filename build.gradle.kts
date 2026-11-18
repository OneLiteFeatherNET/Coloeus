import org.cyclonedx.gradle.CyclonedxDirectTask

plugins {
    alias(libs.plugins.cyclonedx)
}

// gradle.properties' `version` line carries a `# x-release-please-version` marker comment that
// release-please rewrites on every release. Java .properties syntax only treats `#` as a comment
// starter at the start of a line, so the raw value includes that trailing comment - strip it here.
allprojects {
    group = "net.onelitefeather"
    version = (version as String).substringBefore('#').trim()
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    tasks {
        getByName<JavaCompile>("compileJava") {
            // Matches the toolchain (JavaLanguageVersion 25).
            options.release.set(25)
            options.encoding = "UTF-8"
        }
        getByName<JacocoReport>("jacocoTestReport") {
            dependsOn(project.tasks.named("test"))
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
        getByName<Test>("test") {
            useJUnitPlatform()
            // Coverage reports follow every test run, so `./gradlew test` leaves them in build/reports/jacoco.
            finalizedBy("jacocoTestReport")
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}

// One aggregate SBOM for the build (build/reports/cyclonedx/bom.json), composed of the Direct SBOM of every
// subproject. Only the runtime classpath ships, so test, annotation processor and compile-only dependencies are left out.
allprojects {
    tasks.withType<CyclonedxDirectTask>().configureEach {
        includeConfigs = listOf("runtimeClasspath")
        failOnUnresolvedDependencies = true
    }
}
