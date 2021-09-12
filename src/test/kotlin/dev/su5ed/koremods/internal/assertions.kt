@file:JvmName("0__Assertions_Internal_")

package dev.su5ed.koremods.internal

internal inline fun <reified T : Throwable> assertThrowsWithCause(message: String?, noinline executable: () -> Unit): T =
    assertThrowsWithCause(T::class.java, message, executable)

@Suppress("UNCHECKED_CAST")
internal fun <T: Throwable> assertThrowsWithCause(expectedCause: Class<T>, msg: String?, executable: () -> Unit): T {
    try {
        executable()
    } catch (actualException: Throwable) {
        return findCause(expectedCause, msg, actualException)
    }
    
    val cause = String.format("Expected %s to be thrown, but nothing was thrown.", expectedCause.canonicalName).prefix(msg)
    throw RuntimeException(cause)
}

@Suppress("UNCHECKED_CAST")
internal fun <T: Throwable> findCause(expectedCause: Class<T>, msg: String?, throwable: Throwable): T {
    return if (expectedCause.isInstance(throwable)) throwable as T 
    else if (throwable.cause != null) findCause(expectedCause, msg, throwable.cause!!)
    else throwable.cause?.let { cause -> findCause(expectedCause, msg, cause) } 
        ?: throw RuntimeException("expected <${expectedCause.name}> but was <${throwable.javaClass.name}>".prefix("Unexpected exception type thrown").prefix(msg), throwable)
}

private fun String.prefix(str: String?): String {
    val prefixed = str?.plus(" ==> ") ?: ""
    return prefixed + this
}
