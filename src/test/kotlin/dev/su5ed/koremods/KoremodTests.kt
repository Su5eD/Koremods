package dev.su5ed.koremods

import dev.su5ed.koremods.internal.assertThrowsWithCause
import dev.su5ed.koremods.script.ClassNotAvailableInSandboxException
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
        
        assertThrowsWithCause<ClassNotAvailableInSandboxException> { 
            engine.eval("java.io.File::class")
        }
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
