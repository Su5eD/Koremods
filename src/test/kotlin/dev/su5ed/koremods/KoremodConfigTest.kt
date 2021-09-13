package dev.su5ed.koremods

import dev.su5ed.koremods.config.parseConfig
import org.junit.jupiter.api.Test
import java.io.File

class KoremodConfigTest {
    
    @Test
    fun testParseConfig() {
        val file = File("src/test/resources/META-INF/koremods.conf")
        parseConfig(file)
    }
}
