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

package wtf.gofancy.koremods

import codes.som.koffee.insns.jvm.*
import codes.som.koffee.insns.sugar.construct
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import wtf.gofancy.koremods.dsl.assemble
import wtf.gofancy.koremods.dsl.findTarget
import wtf.gofancy.koremods.dsl.invokestatic
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AssemblyTest {
    private val methodInsns = assemble {
        // List<String> list = List.of("hello", "world", "foo", "bar");
        ldc("hello")
        ldc("world")
        ldc("foo")
        ldc("bar")
        invokestatic(List::class, "of", List::class, Any::class, Any::class, Any::class, Any::class, isInterface = true)
        astore_0
        // List<String> filtered = new ArrayList();
        construct(ArrayList::class)
        astore_1
        // Iterator it = list.iterator();
        aload_0
        invokeinterface(List::class, "iterator", Iterator::class)
        astore_2
        // while(it.hasNext()) {
        +L["loop"]
        aload_2
        invokeinterface(Iterator::class, "hasNext", boolean)
        ifeq(L["end"])
        //   String str = (String) it.next();
        aload_2
        invokeinterface(Iterator::class, "next", Any::class)
        checkcast(String::class)
        astore_3
        //   if (str.contains("o")) {
        aload_3
        ldc("o")
        invokevirtual(String::class, "contains", boolean, CharSequence::class)
        ifeq(L["continue"])
        //     filtered.add(str)
        aload_1
        aload_3
        invokeinterface(List::class, "add", boolean, Any::class)
        pop
        //   }
        +L["continue"]
        goto(L["loop"])
        // }
        +L["end"]
        aload_1
        areturn
    }

    @Test
    fun testValidTarget() {
        val insnTarget = methodInsns.findTarget {
            aload_3
            ldc("o")
            invokevirtual(String::class, "contains", boolean, CharSequence::class)
            instructions.add(InsnNode(Opcodes.IFEQ)) // Matching jump instruction by opcode
        }

        val node = insnTarget.find(1)
        assertIs<LdcInsnNode>(node)
        assertEquals("o", node.cst, "Expected constant to be 'o'")
        
        insnTarget.insert(1) {
            assertEquals(node, target)
        }
    }

    @Test
    fun testInvalidTarget() {
        val target = assemble {
            aload_2
            ldc("d")
            invokevirtual(String::class, "contains", boolean, CharSequence::class)
            instructions.add(InsnNode(Opcodes.IFNE)) // Matching jump instruction by opcode
        }

        assertThrows<RuntimeException> { methodInsns.findTarget(target) }
    }
}