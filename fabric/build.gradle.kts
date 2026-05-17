plugins {
    id("multiloader-loader")
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion = project.property("minecraft_version").toString()
val fabricLoaderVersion = project.property("fabric_loader_version").toString()
val fabricVersion = project.property("fabric_version").toString()
val modId = project.property("mod_id").toString()
val vulkanSdkPath = providers.environmentVariable("VULKAN_SDK")
    .orElse(providers.gradleProperty("vulkan_sdk"))
val vulkanValidationLayer = providers.environmentVariable("VULKAN_VALIDATION_LAYER")

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")
    implementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
    implementation("net.fabricmc.fabric-api:fabric-api:${fabricVersion}")
    compileOnly(group = "com.google.code.findbugs", name = "jsr305", version = "3.0.2")
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

            if (vulkanValidationLayer.orNull == "1") programArgs.add("--vulkanValidation")
        }
    }
}

tasks.withType<JavaExec>()
    .matching { it.name == "runClient" || it.name == "runFabricClient" }
    .configureEach {
        // Only MacOS
        if (!org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            return@configureEach
        }

        val sdkPath = vulkanSdkPath.orNull
        if (sdkPath.isNullOrBlank()) {
            logger.warn("[fabric] Vulkan validation layers are disabled in dev run: set VULKAN_SDK or -Pvulkan_sdk=<path> to enable layer discovery.")
            return@configureEach
        }

        systemProperty("org.lwjgl.vulkan.libname", "$sdkPath/lib/libvulkan.1.dylib")
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements", "includeInternal", "modCompileClasspath").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "fabric")
        }
    }
}

sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "fabric")
            }
        }
    }
}

/*
tasks.register<Copy>("extractRuntimeClasspath") {
    from(configurations.runtimeClasspath)
    into("$projectDir/build/runtimeClasspath")
    doFirst {
        file("$projectDir/build/runtimeClasspath").mkdirs()
    }
}
*/
