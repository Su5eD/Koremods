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
import wtf.gofancy.koremods.dsl.ClassTransformer
import wtf.gofancy.koremods.dsl.FieldTransformer
import wtf.gofancy.koremods.dsl.MethodTransformer
import wtf.gofancy.koremods.dsl.Transformer
import wtf.gofancy.koremods.script.KoremodsKtsScript
import java.io.File
import kotlin.io.path.Path
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private val SCRIPT_DEPS = setOf("asm", "asm-analysis", "asm-commons", "asm-tree", "asm-util", "koffee", "log4j-api", "log4j-core")
private const val TEST_CLASS_NAME = "wtf.gofancy.koremods.transform.Person"

class KoremodTransformationTests {
    private val namespace = "unit_tests"
    private val logger: Logger = LogManager.getLogger()
    private val scriptLibraries: Array<String> = listOf(KoremodsKtsScript::class.java, javaClass)
        .map { File(it.protectionDomain.codeSource.location.toURI()).name }
        .plus(SCRIPT_DEPS)
        .toTypedArray()

    @Test
    fun testScriptLoader() {
        val path = Path("src/test/resources/foo")
        val loader = KoremodsLoader(CompileEvalLoad(scriptLibraries))
        
        assertDoesNotThrow { loader.loadKoremods(setOf(path)) }
    }

    @Test
    fun testClassTransformer() {
        val transformer = getFirstTransformer("transformClass", ClassTransformer::class.java)

        val cls = transformClass(transformer)
        val fooBar = assertDoesNotThrow { cls.getDeclaredField("fooBar") }
        assertEquals(String::class.java, fooBar.type)
    }

    @Test
    fun testImportedClassTransformer() {
        val transformer = getFirstTransformer("transformClassImported", ClassTransformer::class.java)

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
    fun testMethodInsertAt() {
        val transformer = getFirstTransformer("transformMethodInsertAt", MethodTransformer::class.java)

        val cls = transformMethod(transformer)
        val isTransformed = cls.getDeclaredMethod("filterList", List::class.java)
        val list = listOf("lorem", "ipsum", "dolor", "sit", "amet")
        val result = isTransformed.invoke(null, list) as List<String>
        
        assertContentEquals(listOf("lorem", "ipsum", "amet"), result)
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
        val path = Path("src/test/resources/foo/scripts/$name.core.kts")
        val source = readScriptSource(identifier, path)
        val script = RawScript(identifier, source)

        val compiled = script.compileScriptResult(scriptLibraries)
        val handler = evalTransformers(script.identifier, compiled, logger)
        return handler.getTransformers()
            .filterIsInstance(cls)
            .first()
    }
}

internal fun transformClass(transformer: ClassTransformer): Class<*> {
    return transform(transformer) { it }
}

internal fun transformMethod(transformer: MethodTransformer): Class<*> {
    return transform(transformer) { node ->
        node.methods.first { it.name == transformer.name && it.desc == transformer.desc }
    }
}

internal fun transformField(transformer: FieldTransformer): Class<*> {
    return transform(transformer) { node ->
        node.fields.first { it.name == transformer.name }
    }
}

fun <T> transform(transformer: Transformer<T>, finder: (ClassNode) -> T): Class<*> {
    val reader = ClassReader(TEST_CLASS_NAME)
    val node = ClassNode()
    reader.accept(node, 0)

    val transformNode = finder(node)
    applyTransform(node.name, listOf(transformer), transformNode)

    val writer = ClassWriter(COMPUTE_MAXS or COMPUTE_FRAMES)
    node.accept(writer)

    val cl = RawByteClassLoader(TEST_CLASS_NAME, writer.toByteArray())
    return cl.loadClass(TEST_CLASS_NAME)
}

class RawByteClassLoader(private val className: String, private val data: ByteArray) : ClassLoader() {
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        if (this.className == name) return defineClass(name, data, 0, data.size)
        return super.loadClass(name, resolve)
    }
}
