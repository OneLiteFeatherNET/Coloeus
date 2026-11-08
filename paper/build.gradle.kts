plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(project(":client"))
    implementation(libs.money.api)
    implementation(libs.moneta.core)

    // Paper and Vault are provided by the server at runtime. Vault's own Bukkit dependency is excluded because the
    // Paper API already provides it.
    compileOnly(libs.paper.api)
    compileOnly(libs.vault.api) {
        exclude(group = "org.bukkit")
    }

    testImplementation(libs.paper.api)
    testImplementation(libs.vault.api) {
        exclude(group = "org.bukkit")
    }
    testImplementation(mn.junit.jupiter.api)
    testImplementation(mn.junit.jupiter.params)
    testRuntimeOnly(mn.junit.jupiter.engine)
    testRuntimeOnly(mn.junit.platform.launcher)
}

tasks {
    // The shaded jar is the plugin. A plain jar of the same name would collide with it in build/libs.
    named<Jar>("jar") {
        enabled = false
    }
    shadowJar {
        archiveBaseName.set("coloeus-paper")
        archiveClassifier.set("")
        // Gson is used by the client only. Relocated, it cannot clash with the copy another plugin ships.
        relocate("com.google.gson", "net.onelitefeather.coloeus.paper.libs.gson")
        // Moneta and the JSR 354 API find their implementations through ServiceLoader: merge the service files.
        // javax.money itself is not relocated, because JSR 354 looks it up under that name.
        mergeServiceFiles()
    }
    processResources {
        // plugin.yml carries the release version; it must be a declared input, or the build cache reuses another one.
        inputs.property("version", project.version.toString())
        filesMatching("plugin.yml") {
            expand(mapOf("version" to project.version))
        }
    }
    build {
        dependsOn(shadowJar)
    }
}
