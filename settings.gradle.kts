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
    }
    versionCatalogs {
        create("libs") {
            version("micronaut", "5.0.2")

            version("moneta", "1.4.5")
            version("money-api", "1.1")
            version("archunit", "1.5.1")

            library("money-api", "javax.money", "money-api").versionRef("money-api")
            library("moneta-core", "org.javamoney.moneta", "moneta-core").versionRef("moneta")
            library("archunit-junit5", "com.tngtech.archunit", "archunit-junit5").versionRef("archunit")
            // Version managed by the Micronaut platform BOM (mn.boms.testcontainers).
            library("testcontainers-junit-jupiter", "org.testcontainers", "testcontainers-junit-jupiter").withoutVersion()

            plugin("micronaut.application", "io.micronaut.application").versionRef("micronaut")
            plugin("micronaut.aot", "io.micronaut.aot").versionRef("micronaut")
        }
    }
}

include("backend")
include("client")
include("paper")
include("velocity")
include("web")
