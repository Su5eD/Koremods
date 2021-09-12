package dev.su5ed.koremods.dsl

import kotlin.reflect.KClass

fun describeMethod(returnType: KClass<*> = Unit::class, vararg params: KClass<*>): String {
    val returnDesc = describe(returnType)
    val paramDesc = params.joinToString(transform = ::describe)
    return "($paramDesc)$returnDesc"
}

fun describe(type: Any): String {
    return when(type) {
        is KClass<*> -> return when(type) {
            Unit::class -> "V"
            Boolean::class -> "Z"
            Byte::class -> "B"
            Char::class -> "C"
            Short::class -> "S"
            Int::class -> "I"
            Long::class -> "J"
            Float::class -> "F"
            Double::class -> "D"
            else -> 'L' + type.java.name.replace('.', '/') + ';'
        }
        is Class<*> -> describe(type.kotlin)
        else -> describe(type::class)
    }
}
