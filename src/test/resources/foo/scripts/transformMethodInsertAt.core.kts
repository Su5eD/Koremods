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

import codes.som.koffee.insns.jvm.aload_3
import codes.som.koffee.insns.jvm.invokevirtual
import codes.som.koffee.insns.jvm.ldc
import codes.som.koffee.insns.jvm.pop
import org.objectweb.asm.tree.InsnNode

transformers {
    method("wtf.gofancy.koremods.transform.Person", "filterList", constructMethodDescriptor(List::class, List::class), ::modifyMethod)
}

fun modifyMethod(node: MethodNode) {
    val target = node.findTarget {
        aload_3
        ldc("o")
        invokevirtual(String::class, "contains", boolean, CharSequence::class)
        instructions.add(InsnNode(IFEQ)) // Matching jump instruction by opcode
    }
    
    target.insert(1) {
        pop
        ldc("m")
    }
}
