plugins {
    `java-library`
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.run.paper) // Adds runServer and runMojangMappedServer tasks for testing

    // Shades and relocates dependencies into our plugin jar. See https://imperceptiblethoughts.com/shadow/introduction/
    alias(libs.plugins.shadow)
}

group = "com.moulberry.axiom"
version = "5.0.3+26.1"
description = "Serverside component for Axiom on Paper"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    maven("https://repo.viaversion.com") {
        content {
            includeGroup("com.viaversion")
        }
    }
    maven("https://maven.enginehub.org/repo/") { // WorldGuard
        content {
            includeGroup("com.sk89q.worldguard")
            includeGroup("com.sk89q.worldedit")
            includeGroup("com.sk89q.worldguard.worldguard-libs")
            includeGroup("com.sk89q.worldedit.worldedit-libs")
        }
    }
    maven("https://maven.playpro.com") { // CoreProtect
        content {
            includeGroup("net.coreprotect")
        }
    }
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("26.1.1.build.29-alpha")

    // Zstd Compression Library
    implementation(libs.zstd.jni)

    // LuckPerms event integration
    compileOnly(libs.luckperms)

    // ViaVersion support
    compileOnly(libs.viaversion.api)

    // WorldGuard support
    compileOnly(libs.worldguard.bukkit)

    // PlotSquared support
    implementation(platform(libs.bom.newest))
    compileOnly(libs.plotsquared.core)
    compileOnly(libs.plotsquared.bukkit) { isTransitive = false }

    // CoreProtect support
    compileOnly(libs.coreprotect)
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }
    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
        options.release.set(25)
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name() // We want UTF-8 for everything
        val props = mapOf(
                "name" to project.name,
                "version" to project.version,
                "description" to project.description,
                "apiVersion" to "26.1"
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
