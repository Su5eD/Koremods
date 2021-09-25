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
    fun `class`(name: String, block: ClassNode.() -> Unit, computeFrames: Boolean = false) {
        transformers.add(ClassTransformer(name, block, computeFrames))
    }
    
    fun method(owner: String, name: String, desc: String, block: MethodNode.() -> Unit, computeFrames: Boolean = false) {
        transformers.add(MethodTransformer(owner, name, desc, block, computeFrames))
    }
    
    fun field(owner: String, name: String, block: FieldNode.() -> Unit, computeFrames: Boolean = false) {
        transformers.add(FieldTransformer(owner, name, block, computeFrames))
    }
}

class ClassTransformer(private val name: String, private val block: ClassNode.() -> Unit, private val computeFrames: Boolean = false) : Transformer {
    override val targetClassName: String
        get() = name
    
    override val doComputeFrames: Boolean
        get() = computeFrames

    override fun visitClass(node: ClassNode) {
        block(node)
    }
}

class MethodTransformer(private val owner: String, private val name: String, private val desc: String, private val block: MethodNode.() -> Unit, private val computeFrames: Boolean = false) : Transformer {
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

class FieldTransformer(private val owner: String, private val name: String, private val block: FieldNode.() -> Unit, private val computeFrames: Boolean = false) : Transformer {
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
