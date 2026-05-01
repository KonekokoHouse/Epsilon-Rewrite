plugins {
    id("multiloader-common")
    id("net.neoforged.moddev")
    id("org.jetbrains.kotlin.plugin.compose")
}

val kotlinVersion = project.property("kotlin_version").toString()
val composeVersion = project.property("compose_version").toString()
val skikoVersion = project.property("skiko_version").toString()

neoForge {
    neoFormVersion = project.property("neo_form_version").toString()
    val at = file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
}

dependencies {
    compileOnly(group = "org.spongepowered", name = "mixin", version = "0.8.5")
    compileOnly(group = "io.github.llamalad7", name = "mixinextras-common", version = "0.5.3")
    annotationProcessor(group = "io.github.llamalad7", name = "mixinextras-common", version = "0.5.3")
    compileOnly(group = "org.ow2.asm", name = "asm", version = "9.8")
    compileOnly(group = "com.google.code.findbugs", name = "jsr305", version = "3.0.2")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:${kotlinVersion}")
    compileOnly("org.jetbrains.compose.runtime:runtime:${composeVersion}")
    compileOnly("org.jetbrains.compose.ui:ui-desktop:${composeVersion}") {
        exclude(group = "org.jetbrains.skiko", module = "skiko")
    }
    compileOnly("org.jetbrains.compose.foundation:foundation-desktop:${composeVersion}") {
        exclude(group = "org.jetbrains.skiko", module = "skiko")
    }
    compileOnly("org.jetbrains.compose.material3:material3-desktop:${composeVersion}") {
        exclude(group = "org.jetbrains.skiko", module = "skiko")
    }
    compileOnly("org.jetbrains.skiko:skiko-awt:${skikoVersion}")
}

configurations {
    create("commonJava") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("commonKotlin") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("commonResources") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
}

artifacts {
    add("commonJava", sourceSets.main.get().java.sourceDirectories.singleFile)
    add("commonKotlin", file("src/main/kotlin"))
    add("commonResources", sourceSets.main.get().resources.sourceDirectories.singleFile)
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
val composeUiAttribute = Attribute.of("ui", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "common")
            attribute(composeUiAttribute, "awt")
        }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "common")
                attribute(composeUiAttribute, "awt")
            }
        }
    }
}
