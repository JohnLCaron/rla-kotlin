plugins {
    kotlin("jvm") version "1.9.23"
}

group = "org.cryptobiotic"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-csv:1.4")
    // commons-io</groupId> <artifactId>commons-io</artifactId> <version>2.6 TODO has vulnerability
    implementation("commons-io:commons-io:2.17.0")
    implementation("ch.obermuhlner:big-math:2.3.2")
    implementation(libs.bundles.logging)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}