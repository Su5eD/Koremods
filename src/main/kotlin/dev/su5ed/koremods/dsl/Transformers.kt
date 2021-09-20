package dev.su5ed.koremods.dsl

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

interface Transformer {
    val targetClassName: String
    
    val doComputeFrames: Boolean
    
    fun visitClass(node: ClassNode)
}

class TransformerBuilder(private val transformers: MutableList<Transformer>) {
    fun `class`(name: String, computeFrames: Boolean = false, block: ClassNode.() -> Unit) {
        transformers.add(ClassTransformer(name, computeFrames, block))
    }
    
    fun method(owner: String, name: String, desc: String, computeFrames: Boolean = false, block: MethodNode.() -> Unit) {
        transformers.add(MethodTransformer(owner, name, desc, computeFrames, block))
    }
    
    fun field(owner: String, name: String, computeFrames: Boolean = false, block: FieldNode.() -> Unit) {
        transformers.add(FieldTransformer(owner, name, computeFrames, block))
    }
}

class ClassTransformer(private val name: String, private val computeFrames: Boolean = false, private val block: ClassNode.() -> Unit) : Transformer {
    override val targetClassName: String
        get() = name
    
    override val doComputeFrames: Boolean
        get() = computeFrames

    override fun visitClass(node: ClassNode) {
        block(node)
    }
}

class MethodTransformer(private val owner: String, private val name: String, private val desc: String, private val computeFrames: Boolean = false, private val block: MethodNode.() -> Unit) : Transformer {
    override val targetClassName: String
        get() = owner

    override val doComputeFrames: Boolean
        get() = computeFrames

    override fun visitClass(node: ClassNode) {
        node.methods
            .first { it.name == this.name && it.desc == this.desc }
            .run(block)
    }
}

class FieldTransformer(private val owner: String, private val name: String, private val computeFrames: Boolean = false, private val block: FieldNode.() -> Unit) : Transformer {
    override val targetClassName: String
        get() = owner

    override val doComputeFrames: Boolean
        get() = computeFrames

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
