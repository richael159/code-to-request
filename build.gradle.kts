plugins {
    id("java")
    kotlin("jvm") version "1.9.21"
    id("org.jetbrains.intellij.platform")
}

group = "com.code2request"
version = "0.3.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")

    intellijPlatform {
        intellijIdeaCommunity("2024.1.7")
        bundledPlugins("com.intellij.java", "org.jetbrains.kotlin")
        // Needed by the verifyPlugin task
        pluginVerifier()
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
    // ./gradlew verifyPlugin checks compatibility against the IDE versions JetBrains recommends
    pluginVerification {
        ides {
            ide("IC", "2024.1.7")
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
