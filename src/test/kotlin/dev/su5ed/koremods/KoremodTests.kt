package dev.su5ed.koremods

import dev.su5ed.koremods.internal.assertThrowsWithCause
import dev.su5ed.koremods.script.ClassNotAvailableInSandboxException
import dev.su5ed.koremods.script.getKoremodEngine
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import javax.script.ScriptEngine
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KoremodTests : KoremodTestBase() {
    
    @Test
    fun testClasses() {
        assertDoesNotThrow { 
            engine.eval("java.lang.String::class")
        }
        
        assertThrowsWithCause<ClassNotAvailableInSandboxException> { 
            engine.eval("java.io.File::class")
        }
    }
    
    @Test
    fun testAllowClass() {
        assertDoesNotThrow {
            engine.eval("""
                @file:Allow("java.io.File")
                
                java.io.File::class
            """.trimIndent())
        }
    }
    
    @Test
    fun testSimpleEval() {
        val res1 = engine.eval("val x = 3")
        assertNull(res1)
        val res2 = engine.eval("x + 2")
        assertEquals(5, res2)
    }
}

abstract class KoremodTestBase {
    protected val logger: Logger = LogManager.getLogger(javaClass)
        
    protected lateinit var engine: ScriptEngine
        
    @BeforeEach
    fun setupEngine() {
        engine = getKoremodEngine(logger)
    }
}
