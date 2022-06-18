/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021-2022 Garden of Fancy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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