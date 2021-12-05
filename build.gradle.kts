import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

val repackPackagePath: String by project
val relocatePackages: ((String, String) -> Unit) -> Unit by extra {{ relocate ->
    sequenceOf("com.typesafe.config", "io.github.config4k", "org.intellij.lang", "org.jetbrains.annotations")
        .forEach { relocate(it, "$repackPackagePath.$it") }
}}

val kotlinVersion: String by project
val lwjglVersion = "3.2.3"
val lwjglComponents = listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb")
val lwjglNatives = listOf("natives-windows", "natives-linux", "natives-macos")

val splash: SourceSet by sourceSets.creating
val splashCompile: Configuration by configurations.creating
val splashRuntime: Configuration by configurations.creating
val splashImplementation: Configuration by configurations

val shade: Configuration by configurations.creating
val shadeKotlin: Configuration by configurations.creating

val mavenDep: (Any?) -> Unit by rootProject.extra
val compileOnlyShared: Configuration by configurations.creating

configurations {
    compileOnly {
        extendsFrom(compileOnlyShared)
    }
    
    splash.compileOnlyConfigurationName {
        extendsFrom(compileOnlyShared)
        extendsFrom(splashCompile)
    }
    
    splash.runtimeOnlyConfigurationName {
        extendsFrom(splashRuntime)
    }
    
    implementation {
        extendsFrom(shade)
        extendsFrom(shadeKotlin)
    }
    
    testImplementation {
        extendsFrom(compileOnly.get())
        extendsFrom(splashCompile)
    }
    
    testRuntimeOnly {
        extendsFrom(splashRuntime)
    }
}

repositories {
    mavenCentral()
    maven("https://su5ed.jfrog.io/artifactory/maven")
    maven("https://libraries.minecraft.net")
}

dependencies {
    shadeKotlin(kotlin("compiler-embeddable"))
    shadeKotlin(kotlin("scripting-common"))
    shadeKotlin(kotlin("scripting-jvm"))
    mavenDep(shadeKotlin(kotlin("scripting-jvm-host")))
    mavenDep(shadeKotlin(kotlin("stdlib")))
    mavenDep(shadeKotlin(kotlin("stdlib-jdk8")))
    shadeKotlin(kotlin("reflect"))
    
    mavenDep(shade(group = "dev.su5ed", name = "koffee", version = "8.1.5"))
    shade(group = "io.github.config4k", name = "config4k", version = "0.4.2")
    
    // Dependencies shipped by Minecraft
    compileOnlyShared(group = "org.ow2.asm", name = "asm-debug-all", version = "5.2")
    compileOnlyShared(group = "org.apache.logging.log4j", name = "log4j-api", version = "2.8.1")
    compileOnlyShared(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.8.1")
    compileOnlyShared(group = "com.google.guava", name = "guava", version = "21.0")
    compileOnlyShared(group = "commons-io", name = "commons-io", version = "2.5")
    
    val platform = platform("org.lwjgl:lwjgl-bom:$lwjglVersion")
    splashCompile(platform)
    splashRuntime(platform)
    
    lwjglComponents.forEach { comp ->
        splashCompile("org.lwjgl", comp)
        lwjglNatives.forEach { os -> splashRuntime("org.lwjgl", comp, classifier = os) }
    }
    
    compileOnly(splash.output)
    
    testImplementation(kotlin("test"))
}

license {
    excludes.add("dev/su5ed/koremods/script/host/CoremodScriptHostConfiguration.kt")
    excludes.add("dev/su5ed/koremods/script/host/Directories.kt")
    excludes.add("dev/su5ed/koremods/splash/math/Matrix4f.kt")
}

val manifestAttributes = mapOf(
    "Specification-Title" to "Koremods-Script",
    "Specification-Vendor" to "Su5eD",
    "Specification-Version" to 1,
    "Implementation-Title" to "Koremods-Script",
    "Implementation-Version" to project.version,
    "Implementation-Vendor" to "Su5eD"
)

tasks {
    jar {
        from(splash.output)
        
        manifest.attributes(manifestAttributes)
    }
    
    shadowJar {
        dependsOn("classes", "jar")
        
        from(splash.output)
        manifest.attributes(manifestAttributes)
        configurations = listOf(shade)
        exclude("META-INF/versions/**")
        relocatePackages(::relocate)
        dependencies {
            exclude(dependency("org.jetbrains.kotlin::"))
        }
        
        archiveClassifier.set("shaded")
    }
    
    val lwjglDepsJar = create<ShadowJar>("lwjglDepsJar") {
        configurations = listOf(splashCompile, splashRuntime)
        exclude("META-INF/**")
        
        archiveBaseName.set("koremods-deps-lwjgl")
        archiveVersion.set(lwjglVersion)
    }
    
    val depsJar = create<ShadowJar>("kotlinDepsJar") {
        configurations = listOf(shadeKotlin)
        exclude("META-INF/versions/**")
        
        archiveBaseName.set("koremods-deps-kotlin")
        archiveVersion.set(kotlinVersion)
    }
    
    build {
        dependsOn(shadowJar, lwjglDepsJar, depsJar)
    }
    
    test {
        useJUnitPlatform()
    }
    
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}
