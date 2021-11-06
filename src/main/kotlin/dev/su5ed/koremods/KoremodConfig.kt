package dev.su5ed.koremods

import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import java.io.Reader

/**
 * Individual config for each discovered Koremod
 */
data class KoremodModConfig(val modid: String, val scripts: Map<String, String>)

/**
 * Configuration of Koremods itself
 */
data class KoremodConfig(val enableSplashScreen: Boolean)

inline fun <reified T> parseConfig(reader: Reader): T {
    return ConfigFactory.parseReader(reader).extract()
}
