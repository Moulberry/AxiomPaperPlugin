plugins {
    `java-library`
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.run.paper) // Adds runServer and runMojangMappedServer tasks for testing

    // Shades and relocates dependencies into our plugin jar. See https://imperceptiblethoughts.com/shadow/introduction/
    alias(libs.plugins.shadow)
}

group = "com.moulberry.axiom"
version = "1.5.11"
description = "Serverside component for Axiom on Paper"

java {
    // Configure the java toolchain. This allows gradle to auto-provision JDK 17 on systems that only have JDK 8 installed for example.
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://maven.playpro.com")
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper)
    implementation(libs.reflection.remapper)
    implementation(libs.cloud.paper)

    // Zstd Compression Library
    implementation(libs.zstd.jni)

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
    // Configure reobfJar to run when invoking the build task
    assemble {
        dependsOn(reobfJar)
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything

        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release.set(17)
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
                "apiVersion" to "1.20"
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        // helper function to relocate a package into our package
        fun reloc(pkg: String) = relocate(pkg, "com.moulberry.axiom.dependency.$pkg")
        reloc("xyz.jpenilla:reflection-remapper")
    }
}
