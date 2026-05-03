plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev")
}

val neoforgeVersion = project.property("neoforge_version").toString()
val modId = project.property("mod_id").toString()

dependencies {
    implementation(jarJar("org.bytedeco:javacpp:1.5.10")!!)
    implementation(jarJar("org.bytedeco:javacv:1.5.10")!!)
    implementation(jarJar("org.bytedeco:ffmpeg:6.1.1-1.5.10")!!)
    runtimeOnly(jarJar("org.bytedeco:javacpp:1.5.10:windows-x86_64")!!)
    runtimeOnly(jarJar("org.bytedeco:ffmpeg:6.1.1-1.5.10:windows-x86_64")!!)
}

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

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "neoforge")
        }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName, getTaskName(null, "jarJar")).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "neoforge")
            }
        }
    }
}

tasks.register<Copy>("copyZkmLibs") {
    into("${rootProject.projectDir}/obf-workspace/zkmLibsNeo")
    from(configurations.runtimeClasspath)
    from(configurations.compileClasspath)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
