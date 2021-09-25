import codes.som.anthony.koffee.util.constructMethodDescriptor
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode

transformers { 
    method("dev.su5ed.koremods.transform.Person", "isTransformed", constructMethodDescriptor(Boolean::class), ::setPersonTransformed)
}

fun setPersonTransformed(node: MethodNode) {
    for (insn in node.instructions) {
        if (insn.opcode == ICONST_0) {
            node.instructions.set(insn, InsnNode(ICONST_1))
        }
    }
}
