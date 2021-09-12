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
    
    testImplementation(kotlin("test"))
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            if (requested.name == "kotlin-scripting-compiler-embeddable" || requested.name == "kotlin-scripting-common") {
                useTarget("dev.su5ed.kotlin:${requested.name}:${requested.version}")
            }
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
    
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}
