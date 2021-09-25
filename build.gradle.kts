import java.time.LocalDateTime
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

val shade: Configuration by configurations.creating

configurations {
    implementation {
        extendsFrom(shade)
    }
}

repositories {
    mavenCentral()
    maven("https://su5ed.jfrog.io/artifactory/maven")
    maven("https://libraries.minecraft.net")
}

dependencies {
    shade(kotlin("compiler-embeddable"))
    shade(kotlin("scripting-common"))
    shade(kotlin("scripting-jvm"))
    shade(kotlin("scripting-jvm-host"))
    shade(kotlin("stdlib"))
    shade(kotlin("stdlib-jdk7"))
    shade(kotlin("stdlib-jdk8"))
    shade(kotlin("reflect"))
    
    shade(group = "dev.su5ed", name = "koffee", version = "8.1.5") {
        exclude(group = "org.ow2.asm")
    }
    shade(group = "io.github.config4k", name = "config4k", version = "0.4.2")
    
    // Dependencies shipped by Forge
    compileOnly(group = "org.ow2.asm", name = "asm-debug-all", version = "5.2")
    compileOnly(group = "org.apache.logging.log4j", name = "log4j-api", version = "2.14.1")
    compileOnly(group = "com.google.guava", "guava", "21.0")

    testImplementation(kotlin("test"))
    testImplementation(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.14.1")
    testImplementation(group = "org.ow2.asm", name = "asm-debug-all", version = "5.2")
}

val manifestAttributes = mapOf(
    "Specification-Title" to "Koremods-Script",
    "Specification-Vendor" to "Su5eD",
    "Specification-Version" to 1,
    "Implementation-Title" to "Koremods-Script",
    "Implementation-Version" to project.version,
    "Implementation-Vendor" to "Su5eD",
    "Implementation-Timestamp" to LocalDateTime.now()
)

tasks {
    jar {
        manifest.attributes(manifestAttributes)
    }
    
    shadowJar {
        dependsOn("classes", "jar")
        
        manifest.attributes(manifestAttributes)
        
        configurations = listOf(shade)
        exclude("META-INF/versions/9/**")
        archiveClassifier.set("shaded")
    }
    
    test {
        useJUnitPlatform()
    }
    
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}
