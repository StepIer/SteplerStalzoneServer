plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(ktorLibs.plugins.ktor)
}

group = "com.stepler"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "com.stepler.MainKt"
}

kotlin {
    jvmToolchain(21)
}

// Single deploy entry point: build the fat jar the container runs, nothing else.
// Invoked by the Dockerfile's build stage.
tasks.register("stage") {
    group = "distribution"
    description = "Builds the deployable fat jar."
    dependsOn(tasks.named("shadowJar"))
}

dependencies {
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.cors)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
}
