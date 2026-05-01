pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "JetBrainsCompose"
            url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        }
        maven {
            name = "Modrinth"
            url = uri("https://api.modrinth.com/maven")
            content {
                includeGroup("maven.modrinth")
            }
        }
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "TheDarkColour"
                    url = uri("https://maven.thedarkcolour.me/releases")
                }
            }
            filter {
                includeGroup("thedarkcolour")
            }
        }
        exclusiveContent {
            forRepository {
                maven {
                    name = "Fabric"
                    url = uri("https://maven.fabricmc.net")
                }
            }
            filter {
                includeGroupAndSubgroups("net.fabricmc")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_PROJECT
    repositories {
        google()
        maven {
            name = "JetBrainsCompose"
            url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        }
        maven {
            name = "Modrinth"
            url = uri("https://api.modrinth.com/maven")
            content {
                includeGroup("maven.modrinth")
            }
        }
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "TheDarkColour"
                    url = uri("https://maven.thedarkcolour.me/releases")
                }
            }
            filter {
                includeGroup("thedarkcolour")
            }
        }
        exclusiveContent {
            forRepository {
                maven {
                    name = "Fabric"
                    url = uri("https://maven.fabricmc.net")
                }
            }
            filter {
                includeGroupAndSubgroups("net.fabricmc")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Epsilon"

include("common")
include("fabric")
include("neoforge")

