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
import codes.som.koffee.TryCatchContainer
import codes.som.koffee.insns.InstructionAssembly
import codes.som.koffee.labels.KoffeeLabel
import codes.som.koffee.sugar.ModifiersAccess
import codes.som.koffee.sugar.TypesAccess
import org.objectweb.asm.tree.*

/**
 * An extended version of [BlockAssembly] with information about the [target] node we're inserting bytecode at.
 */
class TargetedAssembly(override val instructions: InsnList, override val tryCatchBlocks: MutableList<TryCatchBlockNode>, labels: MutableMap<String, LabelNode>, val target: AbstractInsnNode)
    : InstructionAssembly, TryCatchContainer, ModifiersAccess, TypesAccess {
    val L: SharedLabelRegistry = SharedLabelRegistry(labels, this)
}

class SharedLabelRegistry(private val labels: MutableMap<String, LabelNode>, private val insns: InstructionAssembly) {
    /**
     * Get a label by an [index].
     */
    operator fun get(index: Int): KoffeeLabel = this["label_$index"]

    /**
     * Get a label by [name].
     */
    operator fun get(name: String): KoffeeLabel {
        return KoffeeLabel(insns, labels.getOrPut(name, ::LabelNode))
    }
}

/**
 * Used for manipulating bytecode around a target sequence of bytecode instructions in an [InsnList].
 * Because ASM's instruction classes don't implement `equals`, a custom method is used for matching instructions,
 * which currently supports most of them. See [insnEquals] for a full list.
 * 
 * Provides a shared label registry through [TargetedAssembly] for sharing labels across insertions
 * within the same target.
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
    /**
     * Insert instructions before the target.
     * 
     * @param offset offset the insertion target node index
     * @param block bytecode assembly routine
     */
    fun insertBefore(offset: Int = 0, block: TargetedAssembly.() -> Unit)

    /**
     * Insert instructions at the first insn node in this target.
     *
     * @param offset offset the insertion target node index
     * @param block bytecode assembly routine
     */
    fun insert(offset: Int = 0, block: TargetedAssembly.() -> Unit)

    /**
     * Insert instructions after the last insn node in this target.
     *
     * @param offset offset the insertion target node index
     * @param block bytecode assembly routine
     */
    fun insertAfter(offset: Int = 0, block: TargetedAssembly.() -> Unit)

    /**
     * Find an insn node in this target at a specific index
     * 
     * @param index the relating index of the insn node to find
     */
    fun find(index: Int): AbstractInsnNode
    
    class Found(private val origin: InsnList, private val first: AbstractInsnNode, private val last: AbstractInsnNode) : InsnTarget {
        private val labels: MutableMap<String, LabelNode> = mutableMapOf()
        
        override fun insertBefore(offset: Int, block: TargetedAssembly.() -> Unit) {
            insert(first, offset, block, origin::insertBefore)
        }

        override fun insert(offset: Int, block: TargetedAssembly.() -> Unit) {
            insert(first, offset, block, origin::insert)
        }

        override fun insertAfter(offset: Int, block: TargetedAssembly.() -> Unit) {
            insert(last, offset, block, origin::insert)
        }

        override fun find(index: Int): AbstractInsnNode {
            return origin[origin.indexOf(first) + index]
        }

        private inline fun insert(at: AbstractInsnNode, offset: Int, block: TargetedAssembly.() -> Unit, action: (AbstractInsnNode, InsnList) -> Unit) {
            val target = if (offset != 0) origin[origin.indexOf(at) + offset] else at
            val assembly = TargetedAssembly(InsnList(), mutableListOf(), labels, target)
            block(assembly)
            action(target, assembly.instructions)
        }
    }
    
    object NotFound : InsnTarget {
        override fun insertBefore(offset: Int, block: TargetedAssembly.() -> Unit) {}

        override fun insert(offset: Int, block: TargetedAssembly.() -> Unit) {}

        override fun insertAfter(offset: Int, block: TargetedAssembly.() -> Unit) {}

        override fun find(index: Int): AbstractInsnNode = throw UnsupportedOperationException()
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
    return locateTargetOrNull(insns)
        ?: if (failIfNotFound) throw RuntimeException("Target not found") 
        else InsnTarget.NotFound
}

/**
 * Used as a replacement for ASM's missing `equals` implementation on [AbstractInsnNode] and its subclasses.
 * This is used along with [InsnTarget] to match bytecode instructions in lists, and therefore only supports
 * necessary attributes. [LabelNode]s, [LineNumberNode]s and [FrameNode]s are not supported.
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
        // FrameNode
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
 * Find a sequence of bytecode instructions matching [list] in this InsnList
 * 
 * @return if found, an InsnTarget ranging from the first to last matching insn node, otherwise null
 */
fun InsnList.locateTargetOrNull(list: InsnList): InsnTarget? {
    outer@for (primary in this) {
        if (primary is LabelNode || primary is LineNumberNode || primary is FrameNode) continue
        
        var current: AbstractInsnNode? = primary
        for (secondary in list) {
            while (current is LabelNode || current is LineNumberNode || current is FrameNode) current = current.next

            if (current != null && current.insnEquals(secondary)) current = current.next
            else continue@outer
        }
        return InsnTarget.Found(this, primary, current!!.previous)
    }
    return null
}
