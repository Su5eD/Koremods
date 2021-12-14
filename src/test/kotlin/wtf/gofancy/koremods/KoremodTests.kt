/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021 Garden of Fancy
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

import wtf.gofancy.koremods.dsl.Transformer
import wtf.gofancy.koremods.script.evalTransformers
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.File
import kotlin.script.experimental.host.toScriptSource
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class KoremodTransformationTests {
    private val namespace = "unitTests"
    private val logger: Logger = LogManager.getLogger()

    @Test
    fun testClassTransformer() {
        val transformer = getFirstTransformer("transformClass")

        val cls = transformClass(transformer)
        val fooBar = assertDoesNotThrow { cls.getDeclaredField("fooBar") }
        assertEquals(String::class.java, fooBar.type)
    }

    @Test
    fun testMethodTransformer() {
        val transformer = getFirstTransformer("transformMethod")

        val cls = transformClass(transformer)
        val isTransformed = cls.getDeclaredMethod("isTransformed")
        val person = cls.newInstance()
        val result = isTransformed.invoke(person) as Boolean
                            
        assert(result)
    }
    
    @Test
    fun testFieldTransformer() {
        val transformer = getFirstTransformer("transformField")
    
        val cls = transformClass(transformer)
        val name = cls.getDeclaredField("name")
        
        assertNotEquals(0, name.modifiers and Opcodes.ACC_PUBLIC)
        assertNotEquals(0, name.modifiers and Opcodes.ACC_FINAL)
        assertEquals(0, name.modifiers and Opcodes.ACC_PRIVATE)
    }

    @Test
    fun testParseModConfig() {
        val file = File("src/test/resources/META-INF/koremods.conf")
        val config: KoremodModConfig = parseConfig(file.bufferedReader())

        assertEquals(namespace, config.namespace)

        assertContains(config.scripts, "scripts/transformClass.core.kts")
        assertContains(config.scripts, "scripts/transformMethod.core.kts")
    }
    
    private fun getFirstTransformer(name: String): Transformer {
        val identifier = Identifier(namespace, name)
        val script = File("src/test/resources/scripts/$name.core.kts")
    
        val transformers: List<Transformer> = assertNotNull(evalTransformers(identifier, script.toScriptSource(), logger)).getTransformers()
        return transformers.first()
    }
    
    private fun transformClass(transformer: Transformer): Class<*> {
        val reader = ClassReader("wtf.gofancy.koremods.transform.Person")
    
        val node = ClassNode()
        reader.accept(node, 0)
        transformer.visitClass(node)
    
        val writer = ClassWriter(reader, COMPUTE_MAXS or COMPUTE_FRAMES)
        node.accept(writer)
    
        val name = transformer.targetClassName
        val cl = RawByteClassLoader(name, writer.toByteArray())
        return cl.loadClass(name)
    }
}

class RawByteClassLoader(private val name: String, private val data: ByteArray) : ClassLoader() {
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        if (this.name == name) return defineClass(name, data, 0, data.size)
        return super.loadClass(name, resolve)
    }
}
