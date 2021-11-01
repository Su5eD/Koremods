package dev.su5ed.koremods.splash

import org.lwjgl.opengl.GL30.*
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.IntBuffer

internal fun InputStream.toManagedByteBuffer(): ByteBuffer {
    val size = this.available()
    if (size < 1) throw IllegalStateException("Empty resource input stream")
    val array = ByteArray(size) // Prepare buffer

    var backendLength = size // The length of the allocated memory region
    var backendBuffer = MemoryUtil.memAlloc(backendLength) // The allocated memory region
    var currentLength = 0 // How much content has been read

    while (true) {
        val bytesRead = this.read(array)
        if (bytesRead < 0) {
            // We hit EOF: break the loop, then perform resizing
            break
        }
        currentLength += bytesRead
        if (backendLength < currentLength) { // Not enough space in memory: reallocate
            backendLength = (backendLength * 3) / 2
            backendBuffer = MemoryUtil.memRealloc(backendBuffer, backendLength)
        }
        backendBuffer.put(array, 0, bytesRead)
        array.fill(0.toByte())
    }

    // Now, resize the memory area by keeping only what's needed
    backendBuffer = MemoryUtil.memRealloc(backendBuffer, currentLength)
    backendBuffer.rewind()
    return backendBuffer
}

internal fun bufferVertices(VBO: Int, vertices: FloatArray, usage: Int) {
    glBindBuffer(GL_ARRAY_BUFFER, VBO)
    glBufferData(GL_ARRAY_BUFFER, vertices, usage)
}

internal fun bufferIndices(EBO: Int, indices: IntArray, usage: Int) {
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, EBO)
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, usage)
}

internal fun <T> loadImage(name: String, flip: Boolean, block: (MemoryStack, IntBuffer, IntBuffer, ByteBuffer) -> T): T {
    val resourceBuf = getContextResourceAsStream(name).toManagedByteBuffer()
    
    MemoryStack.stackPush().use { stack ->
        val width = stack.mallocInt(1)
        val height = stack.mallocInt(1)
        val nrChannels = stack.mallocInt(1)
        STBImage.stbi_set_flip_vertically_on_load(flip)
        val image = STBImage.stbi_load_from_memory(resourceBuf, width, height, nrChannels, STBImage.STBI_rgb_alpha) ?: throw RuntimeException("Failed to load image $name")

        val ret = block(stack, width, height, image)

        STBImage.stbi_image_free(image)
        
        return ret
    }
}

internal fun getContextResourceAsStream(name: String): InputStream {
    return Thread.currentThread().contextClassLoader.getResourceAsStream(name) ?: throw IllegalArgumentException("Coudln't find resource $name")
}
