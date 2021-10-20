package dev.su5ed.koremods

import dev.su5ed.koremods.dsl.Transformer
import dev.su5ed.koremods.script.evalScript
import dev.su5ed.koremods.script.evalTransformers
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
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.host.toScriptSource
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class KoremodTransformationTests {
    private val logger: Logger = LogManager.getLogger()
    
    @Test
    fun testPreloadTime() {
        val time = measureTimeMillis { 
            val eval = evalScript("transformers {}".toScriptSource(), logger)
            val instance = eval.valueOrThrow().returnValue.scriptInstance
            println(instance)
        }
                
        println("took $time ms")
    }

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
    
    private fun getFirstTransformer(fileName: String): Transformer {
        val script = File("src/test/resources/scripts/$fileName.core.kts")
    
        val transformers: List<Transformer> = assertNotNull(evalTransformers(fileName, script.toScriptSource(), logger)).getTransformers()
        return transformers.first()
    }
    
    private fun transformClass(transformer: Transformer): Class<*> {
        val reader = ClassReader("dev.su5ed.koremods.transform.Person")
    
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
