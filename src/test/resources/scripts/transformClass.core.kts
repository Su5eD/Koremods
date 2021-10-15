import codes.som.anthony.koffee.util.constructDescriptor
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

transformers { 
    `class`("dev.su5ed.koremods.transform.Person", ::addFieldToClass)
}

fun addFieldToClass(node: ClassNode) {
    node.fields.add(FieldNode(ACC_PUBLIC, "fooBar", constructDescriptor(String::class), null, null))
}
