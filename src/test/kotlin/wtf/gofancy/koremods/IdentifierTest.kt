package wtf.gofancy.koremods

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentifierTest {
    
    @Test
    fun testNamespace() {
        val maxLengthNamespace = "a".repeat(64)
        listOf("helloworld", "hello_world", "h4110_w0r1d", maxLengthNamespace).forEach { namespace ->
            assertTrue(Identifier.NAMESPACE_PATTERN.matches(namespace), "Expected valid namespace")
        }
        
        val tooLongNamespace = "a".repeat(65)
        listOf("helloWorld", "HelloWorld", "_HelloWorld", "0helloworld", "hello-world", "HELLO_WORLD", tooLongNamespace).forEach { namespace ->
            assertFalse(Identifier.NAMESPACE_PATTERN.matches(namespace), "Expected invalid namespace")
        }
    }
    
    @Test
    fun testName() {
        val maxLengthName = "a".repeat(64)
        listOf("foobar", "fooBar", "f00B4r", maxLengthName).forEach { name ->
            assertTrue(Identifier.NAME_PATTERN.matches(name), "Expected valid name")
        }
        
        val tooLongName = "a".repeat(65)
        listOf("foo_bar", "FooBar", "_foobar", "0foobar", "foo-bar", "FOO_BAR", tooLongName).forEach { name ->
            assertFalse(Identifier.NAME_PATTERN.matches(name), "Expected invalid name")
        }
    }
}