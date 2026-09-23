plugins {
    kotlin("jvm") version "2.4.20"
    id("com.gradleup.shadow") version "8.3.5"

}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.4")

    // 2. AWS SDK v2 for DynamoDB
    implementation("software.amazon.awssdk:dynamodb:2.25.0")

    implementation("org.bouncycastle:bcpkix-jdk18on:1.84")

    // 4. JSON Parser
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "com.example.VerifyHandler"
    }
}