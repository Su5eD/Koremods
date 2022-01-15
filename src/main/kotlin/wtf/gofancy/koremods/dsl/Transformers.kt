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

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import kotlin.reflect.KProperty

interface Transformer {
    val targetClassName: String
    
    fun visitClass(node: ClassNode)
}

class TransformerPropertiesExtension internal constructor()

class DefaultProperty<T : Any>(default: T) {
    private var value: T? = default

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: throw IllegalStateException("Property ${property.name} should be initialized before get.")
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

class TransformerBuilder(private val transformers: MutableList<Transformer>, private val props: TransformerPropertiesExtension) {
    fun `class`(name: String, block: ClassNode.() -> Unit) {
        transformers.add(ClassTransformer(name, block))
    }
    
    fun method(owner: String, name: String, desc: String, block: MethodNode.() -> Unit) {
        transformers.add(MethodTransformer(owner, name, desc, block)) 
    }
    
    fun field(owner: String, name: String, block: FieldNode.() -> Unit) {
        transformers.add(FieldTransformer(owner, name, block))
    }
    
    fun ext(block: TransformerPropertiesExtension.() -> Unit) {
        block(props)
    }
}

class ClassTransformer(private val name: String, private val block: ClassNode.() -> Unit) : Transformer {
    override val targetClassName: String
        get() = name

    override fun visitClass(node: ClassNode) {
        block(node)
    }
}

class MethodTransformer(private val owner: String, private val name: String, private val desc: String, private val block: MethodNode.() -> Unit) :
    Transformer {
    override val targetClassName: String
        get() = owner

    override fun visitClass(node: ClassNode) {
        node.methods
            .first { it.name == this.name && it.desc == this.desc }
            .run(block)
    }
}

class FieldTransformer(private val owner: String, private val name: String, private val block: FieldNode.() -> Unit) :
    Transformer {
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
    private val props = TransformerPropertiesExtension()
        
    fun transformers(transformer: TransformerBuilder.() -> Unit) {
        transformer.invoke(TransformerBuilder(transformers, props))
    }
    
    fun getTransformers() = transformers.toList()
    
    fun getProps() = props
}
