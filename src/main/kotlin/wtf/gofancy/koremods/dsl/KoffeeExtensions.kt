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
import codes.som.koffee.insns.InstructionAssembly
import codes.som.koffee.types.TypeLike
import codes.som.koffee.types.coerceType
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Construct a method descriptor
 * 
 * @param returnType the return type of the method
 * @param parameterTypes the parameter types of the method
 * @return the method's descriptor string
 */
fun constructMethodDescriptor(returnType: TypeLike, vararg parameterTypes: TypeLike): String {
    return Type.getMethodDescriptor(coerceType(returnType),
            *parameterTypes.map(::coerceType).toTypedArray())
}

/**
 * Used to invoke a static method.
 */
fun InstructionAssembly.invokestatic(owner: TypeLike, name: String, returnType: TypeLike, vararg parameterTypes: TypeLike, isInterface: Boolean = false) {
    instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC, coerceType(owner).internalName, name, constructMethodDescriptor(returnType, *parameterTypes), isInterface))
}

/**
 * Create an [InsnList] used to represent an instruction list, using a [BlockAssembly].
 */
fun assemble(routine: BlockAssembly.() -> Unit): InsnList {
    val blockAssembly = BlockAssembly(InsnList(), mutableListOf())
    routine(blockAssembly)
    return blockAssembly.instructions
}

/**
 * Insert instructions into this method **after** the specified target, or at the beginning if the target is `null`.
 * 
 * @param target the target to insert at
 * @param routine instruction assembly callback
 */
fun MethodNode.insert(target: AbstractInsnNode? = null, routine: BlockAssembly.() -> Unit) {
    val list = assemble(routine)
    target
        ?.run { instructions.insert(target, list) } 
        ?: instructions.insert(list)
}

/**
 * Insert instructions into this method **before** the specified target.
 * 
 * @param target the target to insert before
 * @param routine instruction assembly callback
 */
fun MethodNode.insertBefore(target: AbstractInsnNode, routine: BlockAssembly.() -> Unit) {
    instructions.insertBefore(target, assemble(routine))
}