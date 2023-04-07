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
        classpath(group = "org.jetbrains.dokka", name = "dokka-base", version = "_")
    }
}

plugins {
    `maven-publish`
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.github.johnrengelman.shadow")
    id("org.cadixdev.licenser")
    id("me.qoomon.git-versioning")
}

group = "wtf.gofancy.koremods"
version = "0.0.0-SNAPSHOT"

/**
 * Dependencies in this configuration will be shaded into the final jar.
 */
val shadeImplementation: Configuration by configurations.creating
val shadeApi: Configuration by configurations.creating

gitVersioning.apply {
    rev {
        version = "\${describe.tag.version.major}.\${describe.tag.version.minor}.\${describe.tag.version.patch.plus.describe.distance}"
    }
}

configurations {
    implementation {
        extendsFrom(shadeImplementation)
    }

    api {
        extendsFrom(shadeApi)
    }

    testImplementation {
        extendsFrom(compileOnly.get())
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

    shadeApi(group = "codes.som", name = "koffee", version = "_") {
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
    api(group = "org.apache.logging.log4j", name = "log4j-api", version = "_")
    api(group = "org.apache.logging.log4j", name = "log4j-core", version = "_")

    implementation(group = "com.google.guava", name = "guava", version = "_")
    implementation(group = "commons-io", name = "commons-io", version = "_")

    testImplementation(kotlin("test"))
}

license {
    header("NOTICE")

    properties {
        set("year", "2021-${Calendar.getInstance().get(Calendar.YEAR)}")
        set("name", "Garden of Fancy")
        set("app", "Koremods")
    }
}

tasks {
    shadowJar {
        val relocatePackagePath: String by project
        val relocatedPackages = sequenceOf("com.typesafe.config", "io.github.config4k")

        dependsOn("classes")
        configurations = listOf(shadeImplementation, shadeApi)
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

    withType<DokkaTask> {
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
            footerMessage = "Copyright \u00a9 ${license.getProperty("year")} ${license.getProperty("name")}"
            customStyleSheets = listOf(file("docs/logo-styles.css"))
            customAssets = listOf(file("docs/logo.png"))
            templatesDir = file("docs/templates")
        }
    }

    withType<Wrapper> {
        gradleVersion = "7.5.1"
        distributionType = Wrapper.DistributionType.BIN
    }
}

publishing {
    publications {
        create<MavenPublication>("java") {
            from(components["java"])
        }
    }

    repositories {
        val mavenUser = System.getenv("GOFANCY_MAVEN_USER")
        val mavenToken = System.getenv("GOFANCY_MAVEN_TOKEN")

        if (mavenUser != null && mavenToken != null) {
            maven {
                name = "gofancyReleases"
                url = uri("https://maven.gofancy.wtf/releases")

                credentials {
                    username = mavenUser
                    password = mavenToken
                }
            }

            maven {
                name = "gofancySnapshots"
                url = uri("https://maven.gofancy.wtf/snapshots")

                credentials {
                    username = mavenUser
                    password = mavenToken
                }
            }
        }
    }
}
