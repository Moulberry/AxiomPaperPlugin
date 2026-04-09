pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version("0.5.0")
}

rootProject.name = "AxiomPaper"
