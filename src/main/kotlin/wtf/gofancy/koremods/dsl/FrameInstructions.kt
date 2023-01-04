/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021-2023 Garden of Fancy
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

import codes.som.koffee.insns.InstructionAssembly
import codes.som.koffee.labels.LabelLike
import codes.som.koffee.labels.coerceLabel
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FrameNode

/**
 * Add a label node to the instructions.
 *
 * @param label The label to add
 */
fun InstructionAssembly.label(label: LabelLike) {
    instructions.add(coerceLabel(label))
}

/**
 * Add an expanded frame node to the instructions.
 *
 * @see FrameNode
 */
fun InstructionAssembly.f_new(nLocal: Int, local: Array<Any>, nStack: Int, stack: Array<Any>) {
    instructions.add(FrameNode(Opcodes.F_NEW, nLocal, local, nStack, stack))
}

/**
 * Add a compressed frame with complete frame data to the instructions.
 *
 * @see FrameNode
 */
fun InstructionAssembly.f_full(nLocal: Int, local: Array<Any>, nStack: Int, stack: Array<Any>) {
    instructions.add(FrameNode(Opcodes.F_FULL, nLocal, local, nStack, stack))
}

/**
 * Add a compressed frame where locals are the same as the locals in the previous frame,
 * except that additional 1-3 locals are defined, and with an empty stack.
 *
 * @see FrameNode
 */
fun InstructionAssembly.f_append(nLocal: Int, local: Array<Any>) {
    instructions.add(FrameNode(Opcodes.F_APPEND, nLocal, local, 0, null))
}

/**
 * Add a compressed frame where locals are the same as the locals in the previous frame,
 * except that the last 1-3 locals are absent and with an empty stack.
 *
 * @see FrameNode
 */
fun InstructionAssembly.f_chop(nLocal: Int) {
    instructions.add(FrameNode(Opcodes.F_CHOP, nLocal, null, 0, null))
}

/**
 * Add a compressed frame with exactly the same locals as the previous frame and with an empty stack.
 *
 * @see FrameNode
 */
val InstructionAssembly.f_same: Unit
    get() {
        instructions.add(FrameNode(Opcodes.F_SAME, 0, null, 0, null))
    }

/**
 * Add a compressed frame with exactly the same locals as the previous frame and with a single value on the stack.
 *
 * @see FrameNode
 */
fun InstructionAssembly.f_same1(stack: Array<Any>) {
    instructions.add(FrameNode(Opcodes.F_SAME1, 0, null, 0, stack))
}