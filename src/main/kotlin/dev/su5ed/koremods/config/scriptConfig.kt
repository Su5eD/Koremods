package dev.su5ed.koremods.config

import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import java.io.File

fun parseConfig(file: File) {
    val config = ConfigFactory.parseFile(file)
    val data = config.extract<KoremodConfig>()
    println(data)
}

data class KoremodConfig(val modid: String, val scripts: Map<String, String>)
