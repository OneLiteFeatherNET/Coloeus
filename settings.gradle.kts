rootProject.name = "coloeus"

plugins {
    id("io.micronaut.platform.catalog") version "5.0.2"
}

// Both onelitefeather.dev repos below share one account - read the same
// OneLiteFeatherRepositoryUsername/Password gradle properties (or CI env vars) for each,
// rather than each repo's own name-derived credential convention, so only one set of
// credentials needs to be configured locally/in CI.
fun org.gradle.api.artifacts.repositories.MavenArtifactRepository.oneLiteFeatherCredentials() {
    if (System.getenv("CI") != null) {
        credentials {
            username = System.getenv("ONELITEFEATHER_MAVEN_USERNAME")
            password = System.getenv("ONELITEFEATHER_MAVEN_PASSWORD")
        }
    } else {
        credentials {
            username = providers.gradleProperty("OneLiteFeatherRepositoryUsername").orNull
            password = providers.gradleProperty("OneLiteFeatherRepositoryPassword").orNull
        }
        authentication {
            create<BasicAuthentication>("basic")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "OneLiteFeatherRepository"
            url = uri("https://repo.onelitefeather.dev/onelitefeather")
            oneLiteFeatherCredentials()
        }
        // The aggregate "onelitefeather" repo above doesn't resolve artifacts published straight
        // to the underlying releases/snapshots repos - declare those explicitly too, same credentials.
        maven {
            name = "OneLiteFeatherReleasesRepository"
            url = uri("https://repo.onelitefeather.dev/onelitefeather-releases")
            oneLiteFeatherCredentials()
        }
        // Velocity and Paper APIs for the velocity and paper modules are published here.
        maven("https://repo.papermc.io/repository/maven-public/")
        // The Vault API (economy interface) for the paper module is published on jitpack only.
        maven("https://jitpack.io") { name = "jitpack" }
    }
    versionCatalogs {
        create("libs") {
            version("micronaut", "5.0.2")
            version("gson", "2.13.2")
            version("paper", "26.1.2.build.74-stable")
            version("vaultApi", "1.7")
            version("shadow", "9.6.1")

            library("gson", "com.google.code.gson", "gson").versionRef("gson")
            library("paper-api", "io.papermc.paper", "paper-api").versionRef("paper")
            library("vault-api", "com.github.MilkBowl", "VaultAPI").versionRef("vaultApi")

            version("moneta", "1.4.5")
            version("money-api", "1.1")
            version("archunit", "1.5.1")
            version("logstash-logback-encoder", "9.0")
            version("janino", "3.1.12")

            library("money-api", "javax.money", "money-api").versionRef("money-api")
            library("moneta-core", "org.javamoney.moneta", "moneta-core").versionRef("moneta")
            library("logstash-logback-encoder", "net.logstash.logback", "logstash-logback-encoder").versionRef("logstash-logback-encoder")
            library("janino", "org.codehaus.janino", "janino").versionRef("janino")
            library("archunit-junit5", "com.tngtech.archunit", "archunit-junit5").versionRef("archunit")
            // Version managed by the Micronaut platform BOM (mn.boms.testcontainers).
            library("testcontainers-junit-jupiter", "org.testcontainers", "testcontainers-junit-jupiter").withoutVersion()

            plugin("micronaut.application", "io.micronaut.application").versionRef("micronaut")
            plugin("micronaut.aot", "io.micronaut.aot").versionRef("micronaut")
            plugin("shadow", "com.gradleup.shadow").versionRef("shadow")
        }
    }
}

include("backend")
include("client")
include("paper")
include("velocity")
include("web")
