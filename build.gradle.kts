import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDateTime

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

val lwjglVersion = "3.2.3"
val lwjglComponents = listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb")
val lwjglNatives: String = org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem().let { os ->
	when {
		os.isWindows -> "natives-windows"
		os.isLinux -> "natives-linux"
		os.isMacOsX -> "natives-macos"
		else -> throw Error("Unrecognized or unsupported Operating system. Please set lwjglNatives manually")
	}
}

val splash: SourceSet by sourceSets.creating
val splashImplementation: Configuration by configurations
val shade: Configuration by configurations.creating

configurations {
    compileOnly {
        splashImplementation.extendsFrom(this)
    }
    
    implementation {
        extendsFrom(shade)
    }
    
    testImplementation {
        extendsFrom(compileOnly.get())
        extendsFrom(splashImplementation)
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
    
    // Dependencies shipped by Minecraft
    compileOnly(group = "org.ow2.asm", name = "asm-debug-all", version = "5.2")
    compileOnly(group = "org.apache.logging.log4j", name = "log4j-api", version = "2.14.1")
    compileOnly(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.14.1")
    compileOnly(group = "com.google.guava", "guava", "21.0")
    
    splashImplementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    
    lwjglComponents.forEach { 
        splashImplementation("org.lwjgl", it)
        splashImplementation("org.lwjgl", it, classifier = lwjglNatives)
    }
    
    implementation(splash.output)
    
    testImplementation(splash.output)
    testImplementation(kotlin("test"))
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
        from(splash.output)
        
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
