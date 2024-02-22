import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.9.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("io.ktor.plugin") version "2.3.7"
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.8"
val junitVersion = "5.10.2"
val jacksonVersion = "2.16.1"
val mockWebServerVersion = "4.12.0"
val logbackVersion = "1.4.14"
val logstashEncoderVersion = "7.4"
val tokenClientCoreVersion = "4.1.0"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("no.nav.security:token-client-core:$tokenClientCoreVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$mockWebServerVersion")
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

ktor {
    fatJar {
        archiveFileName.set("app.jar")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
        if (System.getenv("CI") == "true") {
            kotlinOptions.allWarningsAsErrors = true
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("STARTED", "PASSED", "FAILED", "SKIPPED")
        exceptionFormat = TestExceptionFormat.FULL
    }
    failFast = false
}
