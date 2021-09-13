@file:JvmName("0__Assertions_Internal_")

package dev.su5ed.koremods.internal

internal inline fun <reified T : Throwable> assertThrowsWithCause(noinline executable: () -> Unit): T =
    assertThrowsWithCause(T::class.java, executable)

internal fun <T: Throwable> assertThrowsWithCause(expectedCause: Class<T>, executable: () -> Unit): T {
    try {
        executable()
    } catch (actualException: Throwable) {
        return findCause(expectedCause, actualException)
    }
    
    val cause = String.format("Expected %s to be thrown, but nothing was thrown.", expectedCause.canonicalName)
    throw RuntimeException(cause)
}

@Suppress("UNCHECKED_CAST")
internal fun <T: Throwable> findCause(expectedCause: Class<T>, throwable: Throwable): T {
    return if (expectedCause.isInstance(throwable)) throwable as T
    else throwable.cause?.let { cause -> findCause(expectedCause, cause) } 
        ?: throw RuntimeException("expected <${expectedCause.name}> but was <${throwable.javaClass.name}>".prefix("Unexpected exception type thrown"), throwable)
}

private fun String.prefix(str: String?): String {
    val prefixed = str?.plus(" ==> ") ?: ""
    return prefixed + this
}
