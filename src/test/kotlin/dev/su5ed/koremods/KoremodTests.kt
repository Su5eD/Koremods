package dev.su5ed.koremods

import dev.su5ed.koremods.internal.assertThrowsWithCause
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import javax.script.ScriptEngineManager
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KoremodTests {
    
    @Test
    fun testClasses() {
        val engine = ScriptEngineManager().getEngineByExtension("core.kts")!!
        
        assertDoesNotThrow { 
            engine.eval("java.lang.String::class")
        }
        
        val exception = assertThrowsWithCause<ClassNotFoundException>("Class shouldn't be allowed in sandbox") { 
            engine.eval("java.io.File::class")
        }
        assertEquals("Class not allowed in sandbox", exception.message)
    }
    
    @Test
    fun testSimpleEval() {
        val engine = ScriptEngineManager().getEngineByExtension("core.kts")!!
        val res1 = engine.eval("val x = 3")
        assertNull(res1)
        val res2 = engine.eval("x + 2")
        assertEquals(5, res2)
    }
}
