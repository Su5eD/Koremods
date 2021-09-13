import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven("https://su5ed.jfrog.io/artifactory/maven/")
}

dependencies {
    implementation(kotlin("compiler-embeddable"))
    implementation(kotlin("scripting-jvm"))
    implementation(kotlin("scripting-jvm-host"))
    implementation(kotlin("scripting-jsr223"))
    
    implementation(group = "org.ow2.asm", name = "asm-debug-all", version = "5.2")
    implementation(group = "codes.som.anthony", name = "koffee", version = "8.0.2-legacy")
    
    implementation(group = "io.github.config4k", name = "config4k", version = "0.4.2")

    testImplementation(kotlin("test"))
}

tasks {
    test {
        useJUnitPlatform()
    }
    
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}
