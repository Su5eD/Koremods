import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    `maven-publish`

    kotlin("jvm")

    id("fr.brouillard.oss.gradle.jgitver") // TODO look for alternatives
    id("com.github.johnrengelman.shadow")
    id("org.cadixdev.licenser")
}

group = "wtf.gofancy.koremods"

project.afterEvaluate {
    // this will work for now to get a tag based version, but we should really look for a different plugin
    println("version: ${project.version}")
}

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
    toolchain.languageVersion.set(JavaLanguageVersion.of(11))

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

    shadeApi(group = "dev.su5ed", name = "koffee", version = "_") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.ow2.asm")
    }
    shadeImplementation(group = "io.github.config4k", name = "config4k", version = "_") {
        exclude(group = "org.jetbrains.kotlin")
    }

    // need to be available in the script
    api(platform("org.ow2.asm:asm-bom:_"))
    api(group = "org.ow2.asm", name = "asm")
    api(group = "org.ow2.asm", name = "asm-commons")
    api(group = "org.ow2.asm", name = "asm-tree")
    api(group = "org.ow2.asm", name = "asm-analysis")
    api(group = "org.ow2.asm", name = "asm-util")
    sharedApi(group = "org.apache.logging.log4j", name = "log4j-api", version = "_")
    sharedApi(group = "org.apache.logging.log4j", name = "log4j-core", version = "_")

    sharedImplementation(group = "com.google.guava", name = "guava", version = "_")
    sharedImplementation(group = "commons-io", name = "commons-io", version = "_")

    // lwjgl dependencies
    val lwjglComponents = listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb")
    val lwjglNatives = listOf("natives-windows", "natives-linux", "natives-macos")

    val platform = platform("org.lwjgl:lwjgl-bom:_")

    splashCompileOnly(platform)
    testRuntimeOnly(platform)

    lwjglComponents.forEach { comp ->
        splashCompileOnly(group = "org.lwjgl", name = comp)
        lwjglNatives.forEach { os -> testRuntimeOnly(group = "org.lwjgl", name = comp, classifier = os) }
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
        val relocatePackagePath: String by project
        val relocatedPackages = sequenceOf("com.typesafe.config", "io.github.config4k")

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
        kotlinOptions.jvmTarget = "11"
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
