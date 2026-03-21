plugins {
    id("aphelion-lookups")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ktor)
}

kotlin {
    jvmToolchain(21)
    sourceSets.all {
        languageSettings {
            optIn("kotlin.time.ExperimentalTime")
            optIn("kotlin.uuid.ExperimentalUuidApi")
        }
    }
}

application {
    mainClass = "io.github.maxsh001.aphelion.server.MainKt"
}

tasks {
    test {
        useJUnitPlatform()
    }
}

dependencies {
    api(project(":common:kt"))
    api(libs.bundles.ktor.server)
    api(libs.bundles.logging.api)
    runtimeOnly(libs.bundles.logging.impl)
    testApi(libs.bundles.ktor.server.test)
}
