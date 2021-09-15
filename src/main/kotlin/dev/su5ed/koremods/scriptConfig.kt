package dev.su5ed.koremods

import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import java.io.Reader

fun parseConfig(reader: Reader): KoremodConfig {
    val config = ConfigFactory.parseReader(reader)
    return config.extract()
}

data class KoremodConfig(val modid: String, val scripts: Map<String, String>)
