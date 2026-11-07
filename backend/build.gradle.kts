plugins {
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.micronaut.aot)
}

dependencies {
    annotationProcessor(mn.micronaut.serde.processor)

    implementation(mn.micronaut.runtime)
    implementation(mn.micronaut.http.server.netty)
    implementation(mn.micronaut.management)
    implementation(mn.micronaut.serde.jackson)
    // Micronaut reads application.yml through SnakeYAML.
    implementation(mn.snakeyaml)
    implementation(mn.logback.core)
    implementation(mn.logback.classic)

    testImplementation(mn.junit.jupiter.api)
    testImplementation(mn.junit.jupiter.params)
    testRuntimeOnly(mn.junit.jupiter.engine)
    testImplementation(mn.micronaut.test.junit5)
    testImplementation(mn.micronaut.http.client)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("net.onelitefeather.coloeus.Application")
}

graalvmNative.toolchainDetection.set(false)

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("net.onelitefeather.coloeus.*")
    }
    aot {
        // Please review carefully the optimizations enabled below
        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading.set(false)
        convertYamlToJava.set(false)
        precomputeOperations.set(true)
        cacheEnvironment.set(true)
        optimizeClassLoading.set(true)
        deduceEnvironment.set(true)
        optimizeNetty.set(true)
        // Keep logback.xml parsed at runtime so the env-driven JSON/plain switch
        // and ${...} substitutions work in the optimized (Docker/prod) jar.
        replaceLogbackXml.set(false)
    }
}

// The Docker image runs as an unprivileged user (uid 10001) so the Helm chart can enforce runAsNonRoot.
// The application files stay owned by root and are only readable by that user, so the process cannot
// rewrite its own code. The JVM only needs /tmp to be writable; the chart mounts an emptyDir there
// because it also runs the container with a read-only root file system.
tasks.named<io.micronaut.gradle.docker.MicronautDockerfile>("optimizedDockerfile") {
    instruction("RUN groupadd --system --gid 10001 coloeus && useradd --system --uid 10001 --gid coloeus --no-create-home coloeus")
    instruction("USER 10001:10001")
}

// The layers keep the file modes of the Gradle cache, where a jar can be owner-only (0600). The image runs as
// uid 10001, which then cannot read it (e.g. janino, so logback has no console appender). Make the context
// world-readable before Docker copies it.
tasks.named("optimizedBuildLayers") {
    doLast {
        outputs.files.asFileTree.forEach { it.setReadable(true, false) }
    }
}

// Integration tests (@Tag("integration")) need Docker for Testcontainers. Run the rest without it
// via ./gradlew :backend:test -PexcludeTags=integration.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("excludeTags").orNull?.let { tags ->
        useJUnitPlatform { excludeTags(tags) }
    }
}
