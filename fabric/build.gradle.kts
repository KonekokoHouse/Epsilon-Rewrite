plugins {
    id("multiloader-loader")
    id("net.fabricmc.fabric-loom")
    id("org.jetbrains.kotlin.plugin.compose")
}

val minecraftVersion = project.property("minecraft_version").toString()
val fabricLoaderVersion = project.property("fabric_loader_version").toString()
val fabricVersion = project.property("fabric_version").toString()
val kotlinVersion = project.property("kotlin_version").toString()
val composeVersion = project.property("compose_version").toString()
val skikoVersion = project.property("skiko_version").toString()
val composeRuntimeArtifact = "org.jetbrains.compose.runtime:runtime-desktop:${composeVersion}"
val fabricLanguageKotlinVersion = project.property("fabric_language_kotlin_version").toString()
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val skikoRuntimeArtifact = when {
    osName.contains("win") && (osArch.contains("aarch64") || osArch.contains("arm64")) -> "org.jetbrains.skiko:skiko-awt-runtime-windows-arm64:${skikoVersion}"
    osName.contains("win") -> "org.jetbrains.skiko:skiko-awt-runtime-windows-x64:${skikoVersion}"
    osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm64")) -> "org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:${skikoVersion}"
    osName.contains("mac") -> "org.jetbrains.skiko:skiko-awt-runtime-macos-x64:${skikoVersion}"
    osArch.contains("aarch64") || osArch.contains("arm64") -> "org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:${skikoVersion}"
    else -> "org.jetbrains.skiko:skiko-awt-runtime-linux-x64:${skikoVersion}"
}
val composeAndSkikoArtifacts = listOf(
    composeRuntimeArtifact,
    "org.jetbrains.compose.ui:ui-desktop:${composeVersion}",
    "org.jetbrains.compose.foundation:foundation-desktop:${composeVersion}",
    "org.jetbrains.compose.material3:material3-desktop:${composeVersion}",
    "org.jetbrains.skiko:skiko-awt:${skikoVersion}",
    skikoRuntimeArtifact
)
val modId = project.property("mod_id").toString()

dependencies {
    "minecraft"("com.mojang:minecraft:${minecraftVersion}")
    implementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
    implementation("net.fabricmc.fabric-api:fabric-api:${fabricVersion}")
    runtimeOnly("net.fabricmc:fabric-language-kotlin:${fabricLanguageKotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${kotlinVersion}")
    implementation(composeRuntimeArtifact)
    implementation("org.jetbrains.compose.ui:ui-desktop:${composeVersion}") {
        exclude(group = "org.jetbrains.skiko", module = "skiko")
    }
    implementation("org.jetbrains.compose.foundation:foundation-desktop:${composeVersion}") {
        exclude(group = "org.jetbrains.skiko", module = "skiko")
    }
    implementation("org.jetbrains.compose.material3:material3-desktop:${composeVersion}") {
        exclude(group = "org.jetbrains.skiko", module = "skiko")
    }
    implementation("org.jetbrains.skiko:skiko-awt:${skikoVersion}")
    runtimeOnly(skikoRuntimeArtifact)
    compileOnly(group = "com.google.code.findbugs", name = "jsr305", version = "3.0.2")

    composeAndSkikoArtifacts.forEach { artifact ->
        include(artifact)
    }
}

loom {
    val aw = project(":common").file("src/main/resources/${modId}.accesswidener")
    if (aw.exists()) {
        accessWidenerPath.set(aw)
    }
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("runs/client")
        }
        named("server") {
            server()
            configName = "Fabric Server"
            ideConfigGenerated(true)
            runDir("runs/server")
        }
    }
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
val composeUiAttribute = Attribute.of("ui", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements", "includeInternal", "modCompileClasspath").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "fabric")
            attribute(composeUiAttribute, "awt")
        }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "fabric")
                attribute(composeUiAttribute, "awt")
            }
        }
    }
}
