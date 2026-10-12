plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.bundles.ktorServer)
    implementation(libs.bundles.persistence)
    implementation(libs.koinKtor)
    implementation(libs.koinLoggerSlf4j)
    runtimeOnly(libs.logback)

    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.ktorServerTestHost)
    testImplementation(libs.testcontainersPostgresql)
}

application {
    mainClass = "library.AppKt"
}
