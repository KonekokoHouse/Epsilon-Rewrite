plugins {
    id("multiloader-loader")
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion = project.property("minecraft_version").toString()
val fabricLoaderVersion = project.property("fabric_loader_version").toString()
val fabricVersion = project.property("fabric_version").toString()
val modId = project.property("mod_id").toString()

dependencies {
    "minecraft"("com.mojang:minecraft:${minecraftVersion}")
    implementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
    implementation("net.fabricmc.fabric-api:fabric-api:${fabricVersion}")
    compileOnly(group = "com.google.code.findbugs", name = "jsr305", version = "3.0.2")

    implementation(include("org.bytedeco:javacpp:1.5.10")!!)
    implementation(include("org.bytedeco:javacv:1.5.10")!!)
    implementation(include("org.bytedeco:ffmpeg:6.1.1-1.5.10")!!)
    runtimeOnly(include("org.bytedeco:javacpp:1.5.10:windows-x86_64")!!)
    runtimeOnly(include("org.bytedeco:ffmpeg:6.1.1-1.5.10:windows-x86_64")!!)
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

tasks.register<Copy>("copyZkmLibs") {
    into("${rootProject.projectDir}/obf-workspace/zkmLibs")
    from(configurations.runtimeClasspath)
    from(configurations.compileClasspath)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
