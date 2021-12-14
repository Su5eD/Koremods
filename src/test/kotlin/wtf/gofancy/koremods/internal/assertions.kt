/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021 Garden of Fancy
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

@file:JvmName("0__Assertions_Internal_")

package wtf.gofancy.koremods.internal

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
