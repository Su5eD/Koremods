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
    maven("https://su5ed.jfrog.io/artifactory/maven/")
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
    
    implementation(group = "org.ow2.asm", name = "asm-debug-all", version = "5.2")
    shade(group = "codes.som.anthony", name = "koffee", version = "8.0.2-legacy") {
        exclude(group = "org.ow2.asm")
    }
    shade(group = "io.github.config4k", name = "config4k", version = "0.4.2")
    
    // Dependencies shipped by Forge
    compileOnly(group = "org.apache.logging.log4j", name = "log4j-api", version = "2.14.1")
    compileOnly(group = "com.google.guava", "guava", "21.0")

    testImplementation(kotlin("test"))
    testImplementation(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.14.1")
}

tasks {
    shadowJar {
        dependsOn("classes", "jar")
        
        configurations = listOf(shade)
        exclude("**/module-info.class")
        archiveClassifier.set("shaded")
    }
    
    test {
        useJUnitPlatform()
    }
    
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

artifacts { 
    archives(tasks.shadowJar.get())
}
