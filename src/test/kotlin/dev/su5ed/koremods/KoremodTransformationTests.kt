package dev.su5ed.koremods

import dev.su5ed.koremods.dsl.Transformer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.File
import javax.script.Invocable
import javax.script.ScriptEngineManager
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class KoremodTransformationTests {

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
}

@Suppress("UNCHECKED_CAST")
private fun getFirstTransformer(fileName: String): Transformer {
    val engine = ScriptEngineManager().getEngineByExtension("core.kts")!!
    val script = File("src/test/resources/$fileName.core.kts")
    engine.eval(script.reader())

    val transformers: List<Transformer> = (engine as Invocable).invokeFunction("getTransformers") as List<Transformer>
    return transformers.first()
}

private fun transformClass(transformer: Transformer): Class<*> {
    val reader = ClassReader("dev.su5ed.koremods.transform.Person")

    val node = ClassNode()
    reader.accept(node, 0)
    transformer.visitClass(node)

    val writer = ClassWriter(reader, COMPUTE_MAXS or COMPUTE_FRAMES)
    node.accept(writer)

    val name = transformer.getTargetClassName().replace('/', '.')
    val cl = RawByteClassLoader(name, writer.toByteArray())
    return cl.loadClass(name)
}

class RawByteClassLoader(private val name: String, private val data: ByteArray) : ClassLoader() {
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        if (this.name == name) return defineClass(name, data, 0, data.size)
        return super.loadClass(name, resolve)
    }
}