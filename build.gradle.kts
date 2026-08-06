import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val edgeReaderLocalIdePath = providers.gradleProperty("edgeReaderLocalIdePath")

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    implementation("org.commonmark:commonmark:0.29.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("org.apache.pdfbox:jbig2-imageio:3.0.5")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("org.xerial:sqlite-jdbc:3.53.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")

    intellijPlatform {
        pluginVerifier("1.409")
        if (edgeReaderLocalIdePath.isPresent) {
            local(edgeReaderLocalIdePath.get())
        } else {
            intellijIdeaCommunity("2026.2.0.1") {
                useInstaller = false
            }
        }
    }
}

kotlin {
    jvmToolchain(25)
}

intellijPlatform {
    pluginVerification {
        ides {
            if (edgeReaderLocalIdePath.isPresent) {
                local(file(edgeReaderLocalIdePath.get()))
            } else {
                current()
            }
        }
    }

    pluginConfiguration {
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "262"
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf("default")
    }
}

tasks {
    withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
        compilerOptions.jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }

    test {
        useJUnitPlatform()
    }

    named<org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask>("buildPlugin") {
        from(listOf("LICENSE", "PRIVACY.md", "THIRD_PARTY_NOTICES.md"))
    }
}
