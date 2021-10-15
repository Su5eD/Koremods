package dev.su5ed.koremods

import dev.su5ed.koremods.dsl.TransformerPropertiesExtension
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.objectweb.asm.tree.ClassNode

private val logger: Logger = LogManager.getLogger("KoremodsTransformer")

fun transformClass(name: String?, node: ClassNode): TransformerPropertiesExtension? {
    if (KoremodDiscoverer.isInitialized()) {
        KoremodDiscoverer.transformers.forEach { (modid, scripts) ->
            scripts.forEach { script ->
                script.transformers
                    .filter { transformer -> name == null || transformer.targetClassName == name }
                    .forEach { transformer ->
                        logger.debug("Transforming class $name with transformer script ${script.name} of mod $modid")
                        try {
                            transformer.visitClass(node)
                            return transformer.ext
                        } catch (t: Throwable) {
                            logger.error("Error transforming class $name with script ${script.name} of mod $modid", t)
                        }
                    }
            }
        }
    }
    return null
}
