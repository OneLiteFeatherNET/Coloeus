plugins {
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.micronaut.aot)
}

dependencies {
    annotationProcessor(mn.micronaut.serde.processor)
    annotationProcessor(mn.micronaut.data.processor)
    annotationProcessor(mn.micronaut.openapi)
    annotationProcessor(mn.micronaut.security.annotations)
    compileOnly(mn.micronaut.openapi.annotations)

    implementation(mn.micronaut.runtime)
    implementation(mn.micronaut.http.server.netty)
    implementation(mn.micronaut.management)
    implementation(mn.micronaut.serde.jackson)
    // Declarative client for the Mojang profile API (name lookups).
    implementation(mn.micronaut.http.client)
    // Resource server: Entra ID bearer tokens validated against the tenant's JWKS (no login flow, no session).
    implementation(mn.micronaut.security)
    implementation(mn.micronaut.security.jwt)
    // Micronaut reads application.yml through SnakeYAML.
    implementation(mn.snakeyaml)
    implementation(mn.logback.core)
    implementation(mn.logback.classic)
    // JSON logs in the cluster (LOG_JSON=true) for the log pipeline; janino enables the switch in logback.xml.
    implementation(libs.logstash.logback.encoder)
    runtimeOnly(libs.janino)

    // Persistence: Micronaut Data JDBC on a Hikari pool, schema by Flyway (forward-only, see db/migration).
    implementation(mn.micronaut.data.jdbc)
    implementation(mn.micronaut.jdbc.hikari)
    implementation(mn.micronaut.flyway)
    implementation(mn.flyway.postgresql)
    runtimeOnly(mn.postgresql)

    // JSR 354 money: the domain uses MonetaryAmount backed by Moneta.
    implementation(libs.money.api)
    implementation(libs.moneta.core)

    testImplementation(mn.junit.jupiter.api)
    testImplementation(mn.junit.jupiter.params)
    testRuntimeOnly(mn.junit.jupiter.engine)
    testImplementation(mn.micronaut.test.junit5)
    testImplementation(mn.micronaut.http.client)
    // Integration tests (@Tag("integration")) run against a real Postgres container.
    testImplementation(platform(mn.boms.testcontainers))
    testImplementation(mn.testcontainers.postgres)
    testImplementation(libs.testcontainers.junit.jupiter)
    // Architecture rules (BigDecimal stays inside the money mappers).
    testImplementation(libs.archunit.junit5)
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

// The OpenAPI document is generated at build time as META-INF/swagger/coloeus.yml. The processor takes the info version
// from its own default (1.0.0), so the version of the project replaces it once the file is written.
// Only compileJava owns the document. The test compilation runs the same processor without the Application class,
// so it writes its own (default-titled) document into the build scratch area instead of over the real one.
tasks.withType<JavaCompile>().configureEach {
    val isMain = name == "compileJava"
    val swaggerFile = if (isMain) {
        destinationDirectory.get().asFile.resolve("META-INF/swagger/coloeus.yml")
    } else {
        layout.buildDirectory.get().asFile.resolve("tmp/openapi/$name/coloeus.yml")
    }
    val projectVersion = project.version.toString()
    options.compilerArgs.add("-Amicronaut.openapi.target.file=${swaggerFile.absolutePath}")
    if (isMain) {
        doLast {
            if (swaggerFile.exists()) {
                swaggerFile.writeText(swaggerFile.readText().replaceFirst(Regex("(?m)^  version: .*$"), "  version: $projectVersion"))
            }
        }
    }
}

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
