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
import codes.som.koffee.assemble
import org.objectweb.asm.tree.*

/**
 * Used for targeting a specific sequence of bytecode instructions in an [InsnList].
 * Because ASM's instruction classes don't implement `equals`, a custom method is used for matching instructions,
 * which currently supports most of them. See [insnEquals] for a full list.
 * 
 * Example usage:
 * ```kotlin
 * // Suppose we have a method that prints a local variable which you want to swap out for another
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
 * // Locate the sequence of printing instructions, then offset the result by one
 * val target = target(1) {
 *     getstatic("java/lang/System", "out", "java/io/PrintStream")
 *     aload_0                                                      // <- Offset 1
 *     invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
 * }
 * // Replace the loaded variable on the stack
 * method.insertAt(target) {
 *     pop
 *     aload_1
 * }
 * ```
 *
 * @param target the instructions to find
 * @param offset offset the located insn node index. Useful in cases where
 * you're matching some instructions before your target node, too.
 * @see insertAt
 * @see insertBeforeAt
 * @see insnEquals
 */
class InsnTarget(private val target: InsnList, private val offset: Int) {
    /**
     * Find a sequence of instructions matching the [target] in a method's instructions.
     */
    fun find(method: MethodNode): AbstractInsnNode? =
        find(method.instructions)

    /**
     * Find a sequence of instructions matching the [target] in an InsnList, then return a reference to the first
     * matched node with the specified [offset].
     * 
     * @param insns the InsnList to search in
     * @return if a sequence of instructions matching [target] is found, return the [AbstractInsnNode] at the first
     * matching position offset by [offset], otherwise return `null`.
     */
    fun find(insns: InsnList): AbstractInsnNode? {
        insns.forEachIndexed { i, _ ->
            val matches = target.allIndexed { j, targetNode ->
                insns[i + j].insnEquals(targetNode)
            }
            if (matches) return insns[i + offset]
        }
        return null
    }
}

/**
 * A functional wrapper for creating an [InsnTarget]
 * 
 * @see InsnTarget
 */
fun target(offset: Int = 0, block: BlockAssembly.() -> Unit): InsnTarget {
    val assembly = BlockAssembly(InsnList(), mutableListOf())
    block(assembly)
    return InsnTarget(assembly.instructions, offset)
}

/**
 * Insert instructions to a MethodNode **before** a specified target
 * 
 * @param target the target to find
 * @param failIfNotFound throw an exception if the target can not be found
 * @param routine callback for inserting instructions to the method
 */
fun MethodNode.insertBeforeAt(target: InsnTarget, failIfNotFound: Boolean = true, routine: BlockAssembly.() -> Unit) {
    insertOnTarget(target, failIfNotFound, routine, InsnList::insertBefore)
}

/**
 * Insert instructions to a MethodNode **after** a specified target
 * 
 * @param target the target to find
 * @param failIfNotFound throw an exception if the target can not be found
 * @param routine callback for inserting instructions to the method
 */
fun MethodNode.insertAt(target: InsnTarget, failIfNotFound: Boolean = true, routine: BlockAssembly.() -> Unit) {
    insertOnTarget(target, failIfNotFound, routine, InsnList::insert)
}

private fun MethodNode.insertOnTarget(target: InsnTarget, failIfNotFound: Boolean, routine: BlockAssembly.() -> Unit, callback: (InsnList, AbstractInsnNode, InsnList) -> Unit) {
    val targetInsn = target.find(instructions)
        ?: if (failIfNotFound) throw RuntimeException("Could not find target")
        else return
    val list = assemble(routine)
    callback(instructions, targetInsn, list)
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