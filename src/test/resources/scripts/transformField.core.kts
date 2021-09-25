import org.objectweb.asm.tree.FieldNode

transformers { 
    field("dev.su5ed.koremods.transform.Person", "name", ::setFieldAccessible)
}

fun setFieldAccessible(node: FieldNode) {
    node.access = ACC_PUBLIC or ACC_FINAL
}
