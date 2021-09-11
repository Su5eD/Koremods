package dev.su5ed.koremods

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

inline fun <reified T : Throwable> assertThrowsWithCause(message: String?, noinline executable: () -> Unit): T =
    assertThrowsWithCause(T::class.java, message, executable)

@Suppress("UNCHECKED_CAST")
fun <T: Throwable> assertThrowsWithCause(expectedCause: Class<T>, msg: String?, executable: () -> Unit): T {
    try {
        executable()
    } catch (actualException: Throwable) {
        return findCause(expectedCause, msg, actualException)
    }
    
    val cause = String.format("Expected %s to be thrown, but nothing was thrown.", expectedCause.canonicalName).prefix(msg)
    throw RuntimeException(cause)
}

@Suppress("UNCHECKED_CAST")
fun <T: Throwable> findCause(expectedCause: Class<T>, msg: String?, throwable: Throwable): T {
    return if (expectedCause.isInstance(throwable)) throwable as T 
    else if (throwable.cause != null) findCause(expectedCause, msg, throwable.cause!!)
    else throwable.cause?.let { cause -> findCause(expectedCause, msg, cause) } 
        ?: throw RuntimeException("expected <${expectedCause.name}> but was <${throwable.javaClass.name}>".prefix("Unexpected exception type thrown").prefix(msg), throwable)
}

private fun String.prefix(str: String?): String {
    val prefixed = str?.plus(" ==> ") ?: ""
    return prefixed + this
}
