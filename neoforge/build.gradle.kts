plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev")
    id("org.jetbrains.kotlin.plugin.compose")
}

val neoforgeVersion = project.property("neoforge_version").toString()
val modId = project.property("mod_id").toString()
val kotlinVersion = project.property("kotlin_version").toString()
val composeVersion = project.property("compose_version").toString()
val skikoVersion = project.property("skiko_version").toString()
val kotlinForForgeVersion = project.property("kotlin_for_forge_version").toString()
val composeRuntimeArtifact = "org.jetbrains.compose.runtime:runtime-desktop:${composeVersion}"
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

neoForge {
    version = neoforgeVersion
    val at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    runs {
        configureEach {
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            ideName = "NeoForge ${name.replaceFirstChar { it.uppercase() }} (${project.path})"
            logLevel = org.slf4j.event.Level.DEBUG
            systemProperty("terminal.jline", "true")
        }
        register("client") {
            client()
            gameDirectory = file("runs/client").also { it.mkdirs() }
        }
        register("data") {
            clientData()
            gameDirectory = file("runs/data").also { it.mkdirs() }
            programArguments.addAll("--mod", modId, "--all", "--output", file("src/generated/resources/").absolutePath, "--existing", file("src/main/resources/").absolutePath)
        }
        register("server") {
            server()
            gameDirectory = file("runs/server").also { it.mkdirs() }
        }
    }
    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

dependencies {
    implementation("maven.modrinth:kotlin-for-forge:${kotlinForForgeVersion}")
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

    composeAndSkikoArtifacts.forEach { artifact ->
        jarJar(artifact)
    }
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
val composeUiAttribute = Attribute.of("ui", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "neoforge")
            attribute(composeUiAttribute, "awt")
        }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName, getTaskName(null, "jarJar")).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "neoforge")
                attribute(composeUiAttribute, "awt")
            }
        }
    }
}
