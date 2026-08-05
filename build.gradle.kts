import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val edgeReaderLocalIdePath = providers.gradleProperty("edgeReaderLocalIdePath")

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.53.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")

    intellijPlatform {
        if (edgeReaderLocalIdePath.isPresent) {
            local(edgeReaderLocalIdePath.get())
        } else {
            intellijIdeaCommunity("2025.2.6.2") {
                useInstaller = false
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "242"
        }
    }
}

tasks {
    withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }

    test {
        useJUnitPlatform()
    }
}
