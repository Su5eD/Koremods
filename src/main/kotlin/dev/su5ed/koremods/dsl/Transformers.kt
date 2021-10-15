package dev.su5ed.koremods.dsl

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

interface Transformer {
    val targetClassName: String
    
    val ext: TransformerPropertiesExtension
    
    fun visitClass(node: ClassNode)
}

class TransformerPropertiesExtension internal constructor()

class TransformerBuilder(private val transformers: MutableList<Transformer>) {
    
    fun `class`(name: String, block: ClassNode.() -> Unit) {
        `class`(name, block) {}
    }
    
    fun `class`(name: String, block: ClassNode.() -> Unit, ext: TransformerPropertiesExtension.() -> Unit) {
        val props = TransformerPropertiesExtension().apply(ext)
        transformers.add(ClassTransformer(name, block, props))
    }
    
    fun method(owner: String, name: String, desc: String, block: MethodNode.() -> Unit) {
        method(owner, name, desc, block) {}
    }
    
    fun method(owner: String, name: String, desc: String, block: MethodNode.() -> Unit, ext: TransformerPropertiesExtension.() -> Unit) {
        val props = TransformerPropertiesExtension().also(ext)
        transformers.add(MethodTransformer(owner, name, desc, block, props)) 
    }
    
    fun field(owner: String, name: String, block: FieldNode.() -> Unit) {
        field(owner, name, block) {}
    }
    
    fun field(owner: String, name: String, block: FieldNode.() -> Unit, ext: TransformerPropertiesExtension.() -> Unit) {
        val props = TransformerPropertiesExtension().apply(ext)
        transformers.add(FieldTransformer(owner, name, block, props))
    }
}

class ClassTransformer(private val name: String, private val block: ClassNode.() -> Unit, override val ext: TransformerPropertiesExtension) : Transformer {
    override val targetClassName: String
        get() = name

    override fun visitClass(node: ClassNode) {
        block(node)
    }
}

class MethodTransformer(private val owner: String, private val name: String, private val desc: String, private val block: MethodNode.() -> Unit, override val ext: TransformerPropertiesExtension) : Transformer {
    override val targetClassName: String
        get() = owner

    override fun visitClass(node: ClassNode) {
        node.methods
            .first { it.name == this.name && it.desc == this.desc }
            .run(block)
    }
}

class FieldTransformer(private val owner: String, private val name: String, private val block: FieldNode.() -> Unit, override val ext: TransformerPropertiesExtension) : Transformer {
    override val targetClassName: String
        get() = owner

    override fun visitClass(node: ClassNode) {
        node.fields
            .first { it.name == this.name }
            .run(block)
    }
}

class TransformerHandler {
    private val transformers = mutableListOf<Transformer>()
        
    fun transformers(transformer: TransformerBuilder.() -> Unit) {
        transformer.invoke(TransformerBuilder(transformers))
    }
    
    fun getTransformers() = transformers
}
