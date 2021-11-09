@file:JvmName("ClassTransformation")
package dev.su5ed.koremods

import dev.su5ed.koremods.dsl.TransformerPropertiesExtension
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.objectweb.asm.tree.ClassNode

private val logger: Logger = LogManager.getLogger("KoremodsTransformer")

fun transformClass(name: String, node: ClassNode): List<TransformerPropertiesExtension> {
    val props = mutableListOf<TransformerPropertiesExtension>()
    
    if (KoremodDiscoverer.isInitialized()) {
        KoremodDiscoverer.transformers.forEach { (modid, scripts) ->
            scripts.forEach { script ->
                val used = script.handler.getTransformers()
                    .filter { transformer ->
                        if (transformer.targetClassName == name) {
                            logger.debug("Transforming class $name with transformer script ${script.name} of mod $modid")
                            try {
                                transformer.visitClass(node)
                                return@filter true
                            } catch (t: Throwable) {
                                logger.error("Error transforming class $name with script ${script.name} of mod $modid", t)
                            }
                        }
                        
                        return@filter false
                    }
                    .any()
                
                if (used) props.add(script.handler.getProps())
            }
        }
    }
    
    return props.toList()
}
