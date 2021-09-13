package dev.su5ed.koremods.dsl

import codes.som.anthony.koffee.ClassAssembly
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

interface Transformer {
    fun getTargetClassName(): String
    
    fun visitClass(node: ClassNode)
}

class TransformerBuilder(private val transformers: MutableList<Transformer>) {
    fun `class`(name: String, block: ClassNode.() -> Unit) {
        transformers.add(ClassTransformer(name, block))
    }
    
    fun method(owner: String, name: String, desc: String, block: MethodNode.() -> Unit) {
        transformers.add(MethodTransformer(owner, name, desc, block))
    }
    
    fun field(owner: String, name: String, block: FieldNode.() -> Unit) {
        transformers.add(FieldTransformer(owner, name, block))
    }
}

class ClassTransformer(private val name: String, private val block: ClassNode.() -> Unit) : Transformer {
    override fun getTargetClassName(): String = name

    override fun visitClass(node: ClassNode) {
        block(node)
    }
}

class MethodTransformer(private val owner: String, private val name: String, private val desc: String, private val block: MethodNode.() -> Unit) : Transformer {
    override fun getTargetClassName(): String = owner

    override fun visitClass(node: ClassNode) {
        node.methods
            .first { it.name == this.name && it.desc == this.desc }
            .run(block)
    }
}

class FieldTransformer(private val owner: String, private val name: String, private val block: FieldNode.() -> Unit) : Transformer {
    override fun getTargetClassName(): String = owner

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
