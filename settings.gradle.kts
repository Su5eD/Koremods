pluginManagement { 
    repositories { 
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net")
        maven("https://gitlab.com/api/v4/projects/26758973/packages/maven")
    }
    
    plugins {
        val kotlinVersion: String by settings
        kotlin("jvm") version kotlinVersion apply false
    }
    
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "net.minecraftforge.gradle") {
                useModule("${requested.id}:ForgeGradle:${requested.version}")
            }
        }
    }
}

rootProject.name = "koremods-script"
