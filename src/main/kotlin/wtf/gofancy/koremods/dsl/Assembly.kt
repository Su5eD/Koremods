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

import codes.som.koffee.BlockAssembly
import org.objectweb.asm.tree.*

/**
 * Used for manipulating bytecode around a target sequence of bytecode instructions in an [InsnList].
 * Because ASM's instruction classes don't implement `equals`, a custom method is used for matching instructions,
 * which currently supports most of them. See [insnEquals] for a full list.
 * 
 * Use [InsnList.findTarget] or one of its overloaded functions to create
 * an appropriate implementation of this interface.
 *
 * Example usage:
 * ```kotlin
 * val method = MethodNode().koffee {
 *     ldc("Hello World")
 *     astore_0
 *     ldc("Foo Bar")
 *     astore_1
 *
 *     getstatic("java/lang/System", "out", "java/io/PrintStream")
 *     // Load "Hello World"
 *     aload_0
 *     invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
 * }
 * // Locate the sequence of printing instructions
 * val target = method.findTarget {
 *     getstatic("java/lang/System", "out", "java/io/PrintStream")
 *     aload_0 // <- Offset 1
 *     invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
 * }
 * // Replace the loaded variable on the stack
 * target.insert(1) {
 *     pop
 *     aload_1
 * }
 * ```
 *
 * @see InsnList.findTarget
 * @see insnEquals
 */
sealed interface InsnTarget {
    fun insertBefore(offset: Int = 0, block: BlockAssembly.() -> Unit)
    
    fun insert(offset: Int = 0, block: BlockAssembly.() -> Unit)
    
    fun insertAfter(offset: Int = 0, block: BlockAssembly.() -> Unit)
    
    fun find(offset: Int): AbstractInsnNode
    
    class Found(private val origin: InsnList, private val first: AbstractInsnNode, private val last: AbstractInsnNode) : InsnTarget {
        override fun insertBefore(offset: Int, block: BlockAssembly.() -> Unit) {
            val insns = assemble(block)
            insert(first, offset, insns, origin::insertBefore)
        }

        override fun insert(offset: Int, block: BlockAssembly.() -> Unit) {
            val insns = assemble(block)
            insert(first, offset, insns, origin::insert)
        }

        override fun insertAfter(offset: Int, block: BlockAssembly.() -> Unit) {
            val insns = assemble(block)
            insert(last, offset, insns, origin::insert)
        }

        override fun find(offset: Int): AbstractInsnNode {
            return origin[origin.indexOf(first) + offset]
        }

        private fun insert(at: AbstractInsnNode, offset: Int, insns: InsnList, action: (AbstractInsnNode, InsnList) -> Unit) {
            val target = if (offset != 0) origin[origin.indexOf(at) + offset]
            else at
            action(target, insns)
        }
    }
    
    object NotFound : InsnTarget {
        override fun insertBefore(offset: Int, block: BlockAssembly.() -> Unit) {}

        override fun insert(offset: Int, block: BlockAssembly.() -> Unit) {}

        override fun insertAfter(offset: Int, block: BlockAssembly.() -> Unit) {}

        override fun find(offset: Int): AbstractInsnNode = throw UnsupportedOperationException()
    }
}

fun MethodNode.findTarget(failIfNotFound: Boolean = true, block: BlockAssembly.() -> Unit): InsnTarget =
    instructions.findTarget(failIfNotFound, block)

fun InsnList.findTarget(failIfNotFound: Boolean = true, block: BlockAssembly.() -> Unit): InsnTarget {
    val assembly = assemble(block)
    return findTarget(assembly, failIfNotFound)
}

/**
 * Find a sequence of instructions matching [insns] in this InsnList.
 *
 * @param insns the InsnList to search in
 * @param failIfNotFound throw an exception if the target can not be found
 * @return an operational implementation of [InsnTarget], or a NOOP implementation
 * if the target can not be found and failIfNotFound is `false`
 * 
 * @see InsnTarget
 */
fun InsnList.findTarget(insns: InsnList, failIfNotFound: Boolean = true): InsnTarget {
    forEachIndexed { i, node ->
        val matches = insns.allIndexed { j, targetNode ->
            this[i + j].insnEquals(targetNode)
        }
        if (matches) {
            return InsnTarget.Found(this, node, this[i + insns.size() - 1])
        }
    }

    return if (failIfNotFound) throw RuntimeException("Target not found")
    else InsnTarget.NotFound
}

/**
 * Used as a replacement for ASM's missing `equals` implementation on [AbstractInsnNode] and its subclasses.
 * This is used along with [InsnTarget] to match bytecode instructions in lists, and therefore only supports
 * necessary attributes. Array and [LabelNode] attributes are not supported.
 * 
 * Full list of supported attributes:
 * - All Opcodes
 * - [FieldInsnNode] - Matches all attributes
 * - [FrameNode] - Matches [type][FrameNode.type]
 * - [IincInsnNode] - Matches all attributes
 * - [IntInsnNode] - Matches all attributes
 * - [InvokeDynamicInsnNode] - Matches [name][InvokeDynamicInsnNode.name], [desc][InvokeDynamicInsnNode.desc]
 * and [bsm][InvokeDynamicInsnNode.bsm]
 * - [JumpInsnNode] - Matches opcode only
 * - [LabelNode] - Not supported
 * - [LdcInsnNode] - Matches all attributes
 * - [LineNumberNode] - Not supported
 * - [MethodInsnNode] - Matches all attributes
 * - [MultiANewArrayInsnNode] - Matches all attributes
 * - [TableSwitchInsnNode] - Matches opcode only
 * - [TypeInsnNode] - Matches all attributes
 * - [VarInsnNode] - Matches all attributes
 */
fun AbstractInsnNode.insnEquals(other: AbstractInsnNode): Boolean {
    return opcode == other.opcode && when(this) {
        is FieldInsnNode -> {
            other as FieldInsnNode
            owner == other.owner
            && name == other.name
            && desc == other.desc
        }
        is FrameNode -> {
            other as FrameNode
            type == other.type
            // local
            // stack
        }
        is IincInsnNode -> {
            other as IincInsnNode
            `var` == other.`var` 
            && incr == other.incr
        }
        is IntInsnNode -> {
            other as IntInsnNode
            operand == other.operand
        }
        is InvokeDynamicInsnNode -> {
            other as InvokeDynamicInsnNode
            name == other.name
            && desc == other.desc
            && bsm == other.bsm
            // bsmArgs
        }
        // JumpInsnNode
        // LabelNode
        is LdcInsnNode -> {
            other as LdcInsnNode
            cst == other.cst
        }
        // LineNumberNode
        is MethodInsnNode -> {
            other as MethodInsnNode
            owner == other.owner 
            && name == other.name 
            && desc == other.desc
            && itf == other.itf
        }
        is MultiANewArrayInsnNode -> {
            other as MultiANewArrayInsnNode
            desc == other.desc
            && dims == other.dims
        }
        // TableSwitchInsnNode
        is TypeInsnNode -> {
            other as TypeInsnNode
            desc == other.desc
        }
        is VarInsnNode -> {
            other as VarInsnNode
            `var` == other.`var`
        }
        else -> true
    }
}

/**
 * Enhanced version of kotlin's [Iterable.all] with an indexed predicate.
 * 
 * @return `true` if all entries match the given predicate.
 */
inline fun <T> Iterable<T>.allIndexed(predicate: (Int, T) -> Boolean): Boolean {
    if (this is Collection && isEmpty()) return true
    for ((index, element) in withIndex()) if (!predicate(index, element)) return false
    return true
}
