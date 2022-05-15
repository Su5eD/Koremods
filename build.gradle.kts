import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    `maven-publish`

    kotlin("jvm") version "1.6.10"

    id("fr.brouillard.oss.gradle.jgitver") version "0.10.+" // TODO look for alternatives
    id("com.github.johnrengelman.shadow") version "7.1.+"
    id("org.cadixdev.licenser") version "0.6.+"
}

group = "wtf.gofancy.koremods"

project.afterEvaluate {
    // this will work for now to get a tag based version, but we should really look for a different plugin
    println("version: ${project.version}")
}

val relocatePackagePath: String by project
val relocatedPackages = sequenceOf("com.typesafe.config", "io.github.config4k")

val javaVersion: String by project
val kotlinVersion: String by project
val asmVersion: String by project
val lwjglVersion: String by project
val lwjglComponents = listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb")
val lwjglNatives = listOf("natives-windows", "natives-linux", "natives-macos")

/**
 * This source set contains the splash screen which gets shown during start up.
 *
 * In particular this source set requires several lwjgl dependencies which are not needed by the main source set.
 */
val splash: SourceSet by sourceSets.creating

// create references to the splash configurations, so we can add dependencies directly to them
val splashCompileOnly: Configuration by configurations.getting
val splashImplementation: Configuration by configurations.getting
val splashApi: Configuration by configurations.getting

/**
 * Adds an implementation dependency to both source sets (main and splash).
 */
val sharedImplementation: Configuration by configurations.creating

/**
 * Adds an api dependency to both source sets (main adn splash).
 */
val sharedApi: Configuration by configurations.creating

/**
 * Dependencies in this configuration will be shaded into the final jar.
 */
val shadeImplementation: Configuration by configurations.creating
val shadeApi: Configuration by configurations.creating

configurations {
    implementation {
        extendsFrom(shadeImplementation)
        extendsFrom(sharedImplementation)
    }
    splashImplementation.extendsFrom(sharedImplementation)

    api {
        extendsFrom(shadeApi)
        extendsFrom(sharedApi)
    }
    splashApi.extendsFrom(sharedApi)

    testImplementation {
        extendsFrom(compileOnly.get())
        extendsFrom(splashCompileOnly)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))

    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://su5ed.jfrog.io/artifactory/maven")
    maven("https://libraries.minecraft.net")
}

dependencies {
    // need these in API so that IntelliJ's syntax highlighting works in scripts
    api(kotlin("stdlib"))
    api(kotlin("stdlib-jdk8"))
    api(kotlin("scripting-jvm"))
    api(kotlin("scripting-jvm-host"))

    implementation(kotlin("reflect"))
    implementation(kotlin("scripting-common"))
    implementation(kotlin("scripting-compiler-embeddable"))

    compileOnly(splash.output)

    shadeApi(group = "dev.su5ed", name = "koffee", version = "8.1.5") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.ow2.asm")
    }
    shadeImplementation(group = "io.github.config4k", name = "config4k", version = "0.4.2") {
        exclude(group = "org.jetbrains.kotlin")
    }

    // need to be available in the script
    api(group = "org.ow2.asm", name = "asm", version = asmVersion)
    api(group = "org.ow2.asm", name = "asm-commons", version = asmVersion)
    api(group = "org.ow2.asm", name = "asm-tree", version = asmVersion)
    api(group = "org.ow2.asm", name = "asm-analysis", version = asmVersion)
    api(group = "org.ow2.asm", name = "asm-util", version = asmVersion)
    sharedApi(group = "org.apache.logging.log4j", name = "log4j-api", version = "2.17.1")
    sharedApi(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.17.1")

    sharedImplementation(group = "com.google.guava", name = "guava", version = "21.0")
    sharedImplementation(group = "commons-io", name = "commons-io", version = "2.5")

    // lwjgl dependencies
    val platform = platform("org.lwjgl:lwjgl-bom:$lwjglVersion")

    splashCompileOnly(platform)
    testRuntimeOnly(platform)

    lwjglComponents.forEach { comp ->
        splashCompileOnly("org.lwjgl", comp)
        lwjglNatives.forEach { os -> testRuntimeOnly("org.lwjgl", comp, classifier = os) }
    }

    testImplementation(kotlin("test"))
}

license {
    header("NOTICE")

    properties {
        set("year", "2021-${Calendar.getInstance().get(Calendar.YEAR)}")
        set("name", "Garden of Fancy")
        set("app", "Koremods")
    }

    exclude("wtf/gofancy/koremods/splash/math/Matrix4f.kt")
}

tasks {
    jar {
        from(splash.output)
    }
    
    named<Jar>("sourcesJar") {
        from(splash.allSource)
    }

    shadowJar {
        dependsOn("classes")

        configurations = listOf(shadeImplementation, shadeApi)
        from(splash.output)
        exclude("META-INF/versions/**")
        relocatedPackages.forEach { relocate(it, "$relocatePackagePath.$it") }

        archiveClassifier.set("shaded")
    }

    assemble {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }

    withType<Jar> {
        manifest.attributes(
            "Specification-Title" to project.name,
            "Specification-Vendor" to "Garden of Fancy",
            "Specification-Version" to 1,
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Garden of Fancy"
        )
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = javaVersion
    }

    withType<Wrapper> {
        gradleVersion = "7.4.2"
        distributionType = Wrapper.DistributionType.BIN
    }
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
                url = uri("https://gitlab.com/api/v4/projects/32090420/packages/maven")

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
