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
import wtf.gofancy.koremods.dsl.*
import wtf.gofancy.koremods.prelaunch.KoremodsPrelaunch
import wtf.gofancy.koremods.script.KoremodsKtsScript
import java.io.File
import kotlin.script.experimental.api.SourceCode
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
        val transformer = getFirstTransformer("transformClass", ClassTransformer::class.java)

        val cls = transformClass(transformer)
        val fooBar = assertDoesNotThrow { cls.getDeclaredField("fooBar") }
        assertEquals(String::class.java, fooBar.type)
    }

    @Test
    fun testMethodTransformer() {
        val transformer = getFirstTransformer("transformMethod", MethodTransformer::class.java)

        val cls = transformMethod(transformer)
        val isTransformed = cls.getDeclaredMethod("isTransformed")
        val person = cls.getConstructor().newInstance()
        val result = isTransformed.invoke(person) as Boolean

        assert(result)
    }

    @Test
    fun testFieldTransformer() {
        val transformer = getFirstTransformer("transformField", FieldTransformer::class.java)

        val cls = transformField(transformer)
        val name = cls.getDeclaredField("name")

        assertNotEquals(0, name.modifiers and Opcodes.ACC_PUBLIC)
        assertNotEquals(0, name.modifiers and Opcodes.ACC_FINAL)
        assertEquals(0, name.modifiers and Opcodes.ACC_PRIVATE)
    }

    @Test
    fun testParseModConfig() {
        val file = File("src/test/resources/foo/META-INF/koremods.conf")
        val config: KoremodsPackConfig = parseConfig(file.bufferedReader())

        assertEquals(namespace, config.namespace)

        assertContains(config.scripts, "scripts/transformClass.core.kts")
        assertContains(config.scripts, "scripts/transformMethod.core.kts")
    }

    private fun <T> getFirstTransformer(name: String, cls: Class<T>): T {
        val identifier = Identifier(namespace, name)
        val script = File("src/test/resources/foo/scripts/$name.core.kts")

        val libraries = listOf(KoremodsKtsScript::class.java, javaClass)
            .map { File(it.protectionDomain.codeSource.location.toURI()).name } + KoremodsPrelaunch.ASM_DEP_NAMES + "koffee"
        val handler = compileAndEvalTransformers(identifier, script.toScriptSource(), logger, libraries.toTypedArray())
        return assertNotNull(handler).getTransformers()
            .filterIsInstance(cls)
            .first()
    }

    private fun transformClass(transformer: Transformer<ClassNode>): Class<*> {
        return transform(transformer) { it }
    }

    private fun transformMethod(transformer: MethodTransformer): Class<*> {
        return transform(transformer) { node ->
            node.methods
                .first { it.name == transformer.name && it.desc == transformer.desc }
        }
    }

    private fun transformField(transformer: FieldTransformer): Class<*> {
        return transform(transformer) { node ->
            node.fields
                .first { it.name == transformer.name }
        }
    }

    private fun <T> transform(transformer: Transformer<T>, finder: (ClassNode) -> T): Class<*> {
        val className = "wtf.gofancy.koremods.transform.Person"
        val reader = ClassReader(className)

        val node = ClassNode()
        reader.accept(node, 0)

        val transformNode = finder(node)
        applyTransform(node.name, listOf(transformer), transformNode)

        val writer = ClassWriter(reader, COMPUTE_MAXS or COMPUTE_FRAMES)
        node.accept(writer)

        val cl = RawByteClassLoader(className, writer.toByteArray())
        return cl.loadClass(className)
    }
}

internal fun compileAndEvalTransformers(identifier: Identifier, source: SourceCode, logger: Logger, libraries: Array<String>): TransformerHandler {
    val compiled = compileScriptResult(identifier, source, libraries)
    return evalTransformers(identifier, compiled, logger)
}

class RawByteClassLoader(private val className: String, private val data: ByteArray) : ClassLoader() {
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        if (this.className == name) return defineClass(name, data, 0, data.size)
        return super.loadClass(name, resolve)
    }
}
