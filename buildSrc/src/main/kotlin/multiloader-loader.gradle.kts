plugins {
    id("multiloader-common")
}

val commonJava by configurations.creating {
    isCanBeResolved = true
}
val commonResources by configurations.creating {
    isCanBeResolved = true
}
val commonKotlin by configurations.creating {
    isCanBeResolved = true
}

dependencies {
    val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
    compileOnly(project(":common")) {
        attributes {
            attribute(loaderAttribute, "common")
        }
    }
    commonJava(project(path = ":common", configuration = "commonJava"))
    commonResources(project(path = ":common", configuration = "commonResources"))
    commonKotlin(project(path = ":common", configuration = "commonKotlin"))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(commonJava)
    source(commonJava)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(commonResources)
    from(commonResources)
}

tasks.named("compileKotlin") {
    dependsOn(commonKotlin)
    javaClass.methods
        .firstOrNull { it.name == "source" }
        ?.invoke(this, arrayOf(commonKotlin) as Any)
}

tasks.named<Javadoc>("javadoc") {
    dependsOn(commonJava)
    source(commonJava)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(commonJava)
    from(commonJava)
    dependsOn(commonKotlin)
    from(commonKotlin)
    dependsOn(commonResources)
    from(commonResources)
}

