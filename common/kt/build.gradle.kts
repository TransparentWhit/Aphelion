plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
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

tasks {
    test {
        useJUnitPlatform()
    }
}

dependencies {
    api(libs.bundles.kotlin)
    testApi(libs.bundles.kotlin.test)
}
