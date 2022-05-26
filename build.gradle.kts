import fr.brouillard.oss.jgitver.GitVersionCalculator
import fr.brouillard.oss.jgitver.Strategies
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL
import java.util.*

buildscript {
    dependencies {
        // TODO look for alternatives
        classpath(group = "fr.brouillard.oss", name = "jgitver", version = "_")
        classpath(group = "org.jetbrains.dokka", name = "dokka-base", version = "_")
    }
}

plugins {
    `maven-publish`
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.github.johnrengelman.shadow")
    id("org.cadixdev.licenser")
}

group = "wtf.gofancy.koremods"
version = getGitVersion()

project.afterEvaluate {
    // this will work for now to get a tag based version, but we should really look for a different plugin
    println("Version: ${project.version}")
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
 * Adds an api dependency to both source sets (main and splash).
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
    maven {
        name = "Garden of Fancy"
        url = uri("https://maven.gofancy.wtf/releases")
    }
    maven {
        name = "Minecraft"
        url = uri("https://libraries.minecraft.net")
    }
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

    withType<DokkaTask>() {
        dokkaSourceSets {
            moduleName.set("Koremods")
            
            named("main") {
                displayName.set("JVM")
                jdkVersion.set(java.toolchain.languageVersion.map(JavaLanguageVersion::asInt))
                platform.set(Platform.jvm)
                includes.from("docs/Module.md")
                suppressInheritedMembers.set(true)

                sourceLink {
                    localDirectory.set(file("src/main/kotlin"))
                    remoteUrl.set(URL("https://gitlab.com/gofancy/koremods/koremods/-/tree/master/src/main/kotlin"))
                    remoteLineSuffix.set("#L")
                }

                sourceLink {
                    localDirectory.set(file("src/main/java"))
                    remoteUrl.set(URL("https://gitlab.com/gofancy/koremods/koremods/-/tree/master/src/main/java"))
                    remoteLineSuffix.set("#L")
                }

                documentedVisibilities.set(
                    setOf(
                        DokkaConfiguration.Visibility.PUBLIC,
                        DokkaConfiguration.Visibility.PROTECTED,
                        DokkaConfiguration.Visibility.INTERNAL,
                        DokkaConfiguration.Visibility.PACKAGE,
                    )
                )
            }
        }

        pluginConfiguration<DokkaBase, DokkaBaseConfiguration> { 
            customStyleSheets = listOf(file("docs/logo-styles.css"))
            customAssets = listOf(file("docs/logo.png"))
            templatesDir = file("docs/templates") // TODO Swap base.ftl for source_set_selector.ftl with the next dokka release
        }
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
        val mavenUser = System.getenv("GOFANCY_MAVEN_USER")
        val mavenToken = System.getenv("GOFANCY_MAVEN_TOKEN")
        
        if (mavenUser != null && mavenToken != null) {
            maven {
                name = "gofancy"
                url = uri("https://maven.gofancy.wtf/releases")

                credentials {
                    username = mavenUser
                    password = mavenToken
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
