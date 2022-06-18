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
import codes.som.koffee.insns.InstructionAssembly
import org.objectweb.asm.tree.*

class InsnTarget(private val insns: InsnList, private val offset: Int) {
    fun find(method: MethodNode): AbstractInsnNode? =
        find(method.instructions)

    fun find(matchInsns: InsnList): AbstractInsnNode? {
        matchInsns.forEachIndexed { i, _ ->
            val matches = insns.allIndexed { j, targetNode ->
                matchInsns[i + j].insnEquals(targetNode)
            }
            if (matches) return matchInsns[i + offset]
        }
        return null
    }
}

fun target(offset: Int = 0, block: BlockAssembly.() -> Unit): InsnTarget {
    val assembly = BlockAssembly(InsnList(), mutableListOf())
    block(assembly)
    return InsnTarget(assembly.instructions, offset)
}

fun MethodNode.insertAt(target: InsnTarget, failIfNotFound: Boolean = true, routine: InstructionAssembly.() -> Unit) {
    val targetInsn = target.find(instructions)
        ?: if (failIfNotFound) throw RuntimeException("Could not find target")
        else return

    val list = assemble(routine)
    instructions.insert(targetInsn, list)
}

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

inline fun <T> Iterable<T>.allIndexed(predicate: (Int, T) -> Boolean): Boolean {
    if (this is Collection && isEmpty()) return true
    for ((index, element) in withIndex()) if (!predicate(index, element)) return false
    return true
}