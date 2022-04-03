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
    id("com.github.johnrengelman.shadow") version "7.1.+"
    id("org.cadixdev.licenser") version "0.6.+"
    `maven-publish`
}

group = "wtf.gofancy.koremods"
version = getGitVersion()

val relocatePackagePath: String by project
val relocatedPackages = sequenceOf("com.typesafe.config", "io.github.config4k")

val javaVersion: String by project
val kotlinVersion: String by project
val asmVersion: String by project
val lwjglVersion: String by project
val lwjglComponents = listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb")
val lwjglNatives = listOf("natives-windows", "natives-linux", "natives-macos")

val splash: SourceSet by sourceSets.creating
val lwjglCompile: Configuration by configurations.creating
val lwjglRuntime: Configuration by configurations.creating

val shade: Configuration by configurations.creating
val sharedImplementation: Configuration by configurations.creating
val mavenRuntime: Configuration by configurations.creating

configurations {
    apiElements {
        extendsFrom(mavenRuntime)
    }
    
    splash.compileOnlyConfigurationName {
        extendsFrom(lwjglCompile)
    }
    
    splash.implementationConfigurationName {
        extendsFrom(sharedImplementation)
    }
    
    implementation {
        extendsFrom(shade)
        extendsFrom(sharedImplementation)
    }

    testImplementation {
        extendsFrom(compileOnly.get())
        extendsFrom(lwjglCompile)
    }

    testRuntimeOnly {
        extendsFrom(lwjglRuntime)
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
    implementation(kotlin("scripting-common"))
    implementation(kotlin("scripting-compiler-embeddable"))
    mavenDep(implementation(kotlin("scripting-jvm")))
    mavenDep(implementation(kotlin("scripting-jvm-host")))
    mavenDep(implementation(kotlin("stdlib")))
    mavenDep(implementation(kotlin("stdlib-jdk8")))
    implementation(kotlin("reflect"))
    
    compileOnly(splash.output)

    mavenDep(shade(group = "dev.su5ed", name = "koffee", version = "8.1.5") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.ow2.asm")
    })
    shade(group = "io.github.config4k", name = "config4k", version = "0.4.2") {
        exclude(group = "org.jetbrains.kotlin")
    }
    
    sharedImplementation(group = "org.ow2.asm", name = "asm", version = asmVersion)
    sharedImplementation(group = "org.ow2.asm", name = "asm-commons", version = asmVersion)
    sharedImplementation(group = "org.ow2.asm", name = "asm-tree", version = asmVersion)
    sharedImplementation(group = "org.apache.logging.log4j", name = "log4j-api", version = "2.17.1")
    sharedImplementation(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.17.1")
    sharedImplementation(group = "com.google.guava", name = "guava", version = "21.0")
    sharedImplementation(group = "commons-io", name = "commons-io", version = "2.5")

    val platform = platform("org.lwjgl:lwjgl-bom:$lwjglVersion")
    lwjglCompile(platform)
    lwjglRuntime(platform)

    lwjglComponents.forEach { comp ->
        lwjglCompile("org.lwjgl", comp)
        lwjglNatives.forEach { os -> lwjglRuntime("org.lwjgl", comp, classifier = os) }
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

        configurations = listOf(shade)
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

fun mavenDep(dep: Dependency?) {
    if (dep != null) mavenRuntime.dependencies.add(dep)
}

fun getGitVersion(): String {
    val jgitver = GitVersionCalculator.location(rootDir)
        .setNonQualifierBranches("master")
        .setStrategy(Strategies.SCRIPT)
        .setScript("print \"\${metadata.CURRENT_VERSION_MAJOR};\${metadata.CURRENT_VERSION_MINOR};\${metadata.CURRENT_VERSION_PATCH + metadata.COMMIT_DISTANCE}\"")
    return jgitver.version
}
