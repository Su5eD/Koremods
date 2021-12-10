import fr.brouillard.oss.jgitver.GitVersionCalculator
import fr.brouillard.oss.jgitver.Strategies
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

buildscript {
    dependencies { 
        classpath(group = "fr.brouillard.oss", name = "jgitver", version = "0.14.0")
    }
}

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("org.cadixdev.licenser") version "0.6.1"
    `maven-publish`
}

group = "dev.su5ed.koremods"
version = getGitVersion()

val manifestAttributes = mapOf(
    "Specification-Title" to project.name,
    "Specification-Vendor" to "Garden of Fancy",
    "Specification-Version" to 1,
    "Implementation-Title" to project.name,
    "Implementation-Version" to project.version,
    "Implementation-Vendor" to "Garden of Fancy"
)

val repackPackagePath: String by project
val relocatedPackages: Sequence<String> = sequenceOf("com.typesafe.config", "io.github.config4k", "org.intellij.lang", "org.jetbrains.annotations")

val kotlinVersion: String by project
val lwjglVersion: String by project
val lwjglComponents = listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb")
val lwjglNatives = listOf("natives-windows", "natives-linux", "natives-macos")

val splash: SourceSet by sourceSets.creating
val lwjglCompile: Configuration by configurations.creating
val lwjglRuntime: Configuration by configurations.creating
val splashImplementation: Configuration by configurations

val shade: Configuration by configurations.creating

val compileOnlyShared: Configuration by configurations.creating

val mavenRuntime: Configuration by configurations.creating // TODO Remove?
val mavenDep: (Dependency?) -> Unit = { if (it != null) { mavenRuntime.dependencies.add(it) } }

configurations {
    compileOnly {
        extendsFrom(compileOnlyShared)
    }
    
    splash.compileOnlyConfigurationName {
        extendsFrom(compileOnlyShared)
        extendsFrom(lwjglCompile)
    }
    
    splash.runtimeOnlyConfigurationName {
        extendsFrom(lwjglRuntime)
    }
    
    implementation {
        extendsFrom(shade)
    }
    
    runtimeElements {
        setExtendsFrom(setOf(mavenRuntime))
    }
    
    testImplementation {
        extendsFrom(compileOnly.get())
        extendsFrom(lwjglCompile)
    }
    
    testRuntimeOnly {
        extendsFrom(lwjglRuntime)
    }
}

repositories {
    mavenCentral()
    maven("https://su5ed.jfrog.io/artifactory/maven")
    maven("https://libraries.minecraft.net")
}

dependencies {
    implementation(kotlin("compiler-embeddable"))
    implementation(kotlin("scripting-common"))
    implementation(kotlin("scripting-jvm"))
    mavenDep(implementation(kotlin("scripting-jvm-host")))
    mavenDep(implementation(kotlin("stdlib")))
    mavenDep(implementation(kotlin("stdlib-jdk8")))
    implementation(kotlin("reflect"))
    
    mavenDep(shade(group = "dev.su5ed", name = "koffee", version = "8.1.5"))
    shade(group = "io.github.config4k", name = "config4k", version = "0.4.2")
    
    // Dependencies shipped by Minecraft
    compileOnlyShared(group = "org.ow2.asm", name = "asm-debug-all", version = "5.2")
    compileOnlyShared(group = "org.apache.logging.log4j", name = "log4j-api", version = "2.15.0")
    compileOnlyShared(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.15.0")
    compileOnlyShared(group = "com.google.guava", name = "guava", version = "21.0")
    compileOnlyShared(group = "commons-io", name = "commons-io", version = "2.5")
    
    val platform = platform("org.lwjgl:lwjgl-bom:$lwjglVersion")
    lwjglCompile(platform)
    lwjglRuntime(platform)
    
    lwjglComponents.forEach { comp ->
        lwjglCompile("org.lwjgl", comp)
        lwjglNatives.forEach { os -> lwjglRuntime("org.lwjgl", comp, classifier = os) }
    }
    
    compileOnly(splash.output)
    
    testImplementation(kotlin("test"))
}

license {
    header(file("NOTICE"))
    
    properties {
        set("year", Calendar.getInstance().get(Calendar.YEAR))
        set("name", "Garden of Fancy")
        set("app", "Koremods")
    }
    
    excludes.add("dev/su5ed/koremods/script/host/CoremodScriptHostConfiguration.kt")
    excludes.add("dev/su5ed/koremods/script/host/Directories.kt")
    excludes.add("dev/su5ed/koremods/splash/math/Matrix4f.kt")
}

tasks {
    jar {
        from(splash.output)
        
        manifest.attributes(manifestAttributes)
    }
    
    shadowJar {
        dependsOn("classes", "jar")
        
        configurations = listOf(shade)
        from(splash.output)
        exclude("META-INF/versions/**")
        manifest.attributes(manifestAttributes)
        relocatedPackages.forEach { relocate(it, "$repackPackagePath.$it") }
        
        dependencies {
            exclude(dependency("org.jetbrains.kotlin::"))
        }
        
        archiveClassifier.set("shaded")
    }
    
    assemble {
        dependsOn(shadowJar)
    }
    
    test {
        useJUnitPlatform()
    }
    
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
    
    withType<Wrapper> {
        gradleVersion = "7.3"
        distributionType = Wrapper.DistributionType.BIN
    }
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>(project.name) { 
            from(components["java"])
        }
    }

    repositories {
        val ciJobToken = System.getenv("CI_JOB_TOKEN")
        val deployToken = project.findProperty("DEPLOY_TOKEN") as String?
        if (ciJobToken != null || deployToken != null) {
            maven {
                name = "GitLab"
                url = uri("https://gitlab.com/api/v4/projects/29540985/packages/maven")

                credentials(HttpHeaderCredentials::class) {
                    if (ciJobToken != null) {
                        name = "Job-Token"
                        value = ciJobToken
                    } else {
                        name = "Deploy-Token"
                        value = deployToken
                    }
                }
                authentication {
                    create("header", HttpHeaderAuthentication::class)
                }
            }
        }
        
        if (project.hasProperty("artifactoryPassword")) {
            maven {
                name = "artifactory"
                url = uri("https://su5ed.jfrog.io/artifactory/maven")
                credentials {
                    username = project.properties["artifactoryUser"] as String
                    password = project.properties["artifactoryPassword"] as String
                }
            }
        }
    }
}

fun getGitVersion(): String {
    val jgitver = GitVersionCalculator.location(rootDir)
        .setNonQualifierBranches("master")
        .setStrategy(Strategies.SCRIPT)
        .setScript("print \"\${metadata.CURRENT_VERSION_MAJOR};\${metadata.CURRENT_VERSION_MINOR};\${metadata.CURRENT_VERSION_PATCH + metadata.COMMIT_DISTANCE}\"")
    return jgitver.version
}
