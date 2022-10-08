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

package wtf.gofancy.koremods.dsl

import codes.som.koffee.types.TypeLike
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import wtf.gofancy.koremods.Identifier
import wtf.gofancy.koremods.launch.KoremodsLaunch
import kotlin.script.experimental.util.PropertiesCollection

/**
 * Represents an arbitrary transformer that belongs to a Script.
 *
 * @param T The type being transformed
 */
interface Transformer<T> {
    /**
     * The owner script's identifier
     */
    val scriptIdentifier: Identifier

    /**
     * Additional transformer properties
     */
    val props: TransformerPropertiesStore

    /**
     * The name of the class to select for transformation
     */
    val targetClassName: String

    /**
     * Transform an arbitrary object of this transformer's type.
     * 
     * @param node The object being transformed
     */
    fun visit(node: T)
}

/**
 * Common extension point for Transformer extension properties.
 */
interface TransformerPropertyKeys

/**
 * Stores configured transformer properties.
 * 
 * @param baseConfigurations Parent properties to apply to this store
 * @param body Property builder function
 */
open class TransformerPropertiesStore internal constructor(baseConfigurations: Iterable<TransformerPropertiesStore>, body: Builder.() -> Unit)
    : PropertiesCollection(Builder(baseConfigurations).apply(body).data) {
    
    constructor(body: Builder.() -> Unit = {}) : this(emptyList(), body)
    
    companion object : TransformerPropertyKeys
    
    object Default : TransformerPropertiesStore()

    /**
     * Used to configure initial store values.
     */
    class Builder internal constructor(baseConfigurations: Iterable<TransformerPropertiesStore>)
        : TransformerPropertyKeys, PropertiesCollection.Builder(baseConfigurations)
}

/**
 * Allows the easy creation of Transformers.
 */
class TransformerBuilder internal constructor(private val scriptIdentifier: Identifier, private val transformers: MutableList<Transformer<*>>) {
    internal var props: TransformerPropertiesStore = TransformerPropertiesStore.Default
        private set
    
    /**
     * Add a class transformer.
     * 
     * @param name The fully qualified target class name
     * @param block The transformer function
     */
    fun `class`(name: String, block: ClassNode.() -> Unit) {
        val params = ClassTransformerParams(name)
        val mapped = KoremodsLaunch.PLUGIN?.mapClassTransformer(params) ?: params
        transformers.add(ClassTransformer(scriptIdentifier, props, mapped.name, block))
    }

    /**
     * Add a method transformer.
     *
     * @param owner The fully qualified target class name
     * @param name The method name
     * @param returnType The method's return type
     * @param block The transformer function
     */
    fun method(owner: String, name: String, returnType: TypeLike, block: MethodNode.() -> Unit) {
        method(owner, name, constructMethodDescriptor(returnType), block)
    }

    /**
     * Add a method transformer.
     *
     * @param owner The fully qualified target class name
     * @param name The method name
     * @param returnType The method's return type
     * @param parameterTypes The method's parameter types
     * @param block The transformer function
     */
    fun method(owner: String, name: String, returnType: TypeLike, parameterTypes: Array<TypeLike>, block: MethodNode.() -> Unit) {
        method(owner, name, constructMethodDescriptor(returnType, *parameterTypes), block)
    }

    /**
     * Add a method transformer.
     *
     * @param owner The fully qualified target class name
     * @param name The method name
     * @param desc The method descriptor
     * @param block The transformer function
     */
    fun method(owner: String, name: String, desc: String, block: MethodNode.() -> Unit) {
        val params = MethodTransformerParams(owner, name, desc)
        val mapped = KoremodsLaunch.PLUGIN?.mapMethodTransformer(params) ?: params
        transformers.add(MethodTransformer(scriptIdentifier, props, mapped.owner, mapped.name, mapped.desc, block))
    }

    /**
     * Add a field transformer.
     *
     * @param owner The fully qualified target class name
     * @param name The field name
     * @param block The transformer function
     */
    fun field(owner: String, name: String, block: FieldNode.() -> Unit) {
        val params = FieldTransformerParams(owner, name)
        val mapped = KoremodsLaunch.PLUGIN?.mapFieldTransformer(params) ?: params
        transformers.add(FieldTransformer(scriptIdentifier, props, mapped.owner, mapped.name, block))
    }

    /**
     * Configure the extra properties of this script.
     */
    fun props(block: TransformerPropertiesStore.Builder.() -> Unit) {
        props = TransformerPropertiesStore(block)
    }
}

/**
 * Stores the script's transformers.
 * 
 * @property scriptIdentifier The script's identifier
 */
class TransformerHandler internal constructor(private val scriptIdentifier: Identifier) {
    private val transformers = mutableListOf<Transformer<*>>()
    private var props: TransformerPropertiesStore = TransformerPropertiesStore.Default

    /**
     * Invoke the transformer builder DSL that allows adding transformers to this handler.
     * 
     * @param transformer The builder function
     */
    fun transformers(transformer: TransformerBuilder.() -> Unit) {
        val builder = TransformerBuilder(scriptIdentifier, transformers)
        transformer.invoke(builder)
        props = builder.props
    }

    /**
     * Get all transformers of this script.
     */
    fun getTransformers(): List<Transformer<*>> = transformers.toList()
}

/**
 * Applies transformations to a Class.
 */
class ClassTransformer internal constructor(override val scriptIdentifier: Identifier, override val props: TransformerPropertiesStore, override val targetClassName: String, private val block: ClassNode.() -> Unit) : Transformer<ClassNode> {
    override fun visit(node: ClassNode) = block(node)
}

/**
 * Applies transformations to a Method.
 */
class MethodTransformer internal constructor(override val scriptIdentifier: Identifier, override val props: TransformerPropertiesStore, override val targetClassName: String, val name: String, val desc: String, private val block: MethodNode.() -> Unit) : Transformer<MethodNode> {
    override fun visit(node: MethodNode) = block(node)
}

/**
 * Applies transformations to a Field.
 */
class FieldTransformer internal constructor(override val scriptIdentifier: Identifier, override val props: TransformerPropertiesStore, override val targetClassName: String, val name: String, private val block: FieldNode.() -> Unit) : Transformer<FieldNode> {
    override fun visit(node: FieldNode) = block(node)
}

/**
 * Stores Class Transformer parameters for mapping with [wtf.gofancy.koremods.launch.KoremodsLaunchPlugin.mapClassTransformer].
 */
data class ClassTransformerParams internal constructor(val name: String)

/**
 * Stores Method Transformer parameters for mapping with [wtf.gofancy.koremods.launch.KoremodsLaunchPlugin.mapMethodTransformer].
 */
data class MethodTransformerParams internal constructor(val owner: String, val name: String, val desc: String)

/**
 * Stores Field Transformer parameters for mapping with [wtf.gofancy.koremods.launch.KoremodsLaunchPlugin.mapFieldTransformer].
 */
data class FieldTransformerParams internal constructor(val owner: String, val name: String)
