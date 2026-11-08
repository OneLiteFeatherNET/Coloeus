plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    // Amounts in the public API are javax.money (JSR 354); Moneta is the implementation.
    api(libs.money.api)
    implementation(libs.moneta.core)
    implementation(libs.gson)

    testImplementation(mn.junit.jupiter.api)
    testImplementation(mn.junit.jupiter.params)
    testRuntimeOnly(mn.junit.jupiter.engine)
    testRuntimeOnly(mn.junit.platform.launcher)
}
