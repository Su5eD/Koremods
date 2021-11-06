package dev.su5ed.koremods

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class KoremodConfigTest {
    
    @Test
    fun testParseModConfig() {
        val file = File("src/test/resources/META-INF/koremods.conf")
        val config: KoremodModConfig = parseConfig(file.bufferedReader())
        
        assertEquals("examplemod", config.modid)
        
        assertContains(config.scripts, "personClassTransformer")
        assertEquals("scripts/transformClass.core.kts", config.scripts["personClassTransformer"])
        
        assertContains(config.scripts, "personMethodTransformer")
        assertEquals("scripts/transformMethod.core.kts", config.scripts["personMethodTransformer"])
    }
}
