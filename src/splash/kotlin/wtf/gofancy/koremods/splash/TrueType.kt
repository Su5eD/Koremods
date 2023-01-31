/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021-2023 Garden of Fancy
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

package wtf.gofancy.koremods.splash

import wtf.gofancy.koremods.splash.math.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.glfwGetMonitorContentScale
import org.lwjgl.opengl.GL30.*
import org.lwjgl.stb.STBTTAlignedQuad
import org.lwjgl.stb.STBTTBakedChar
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.*
import org.lwjgl.system.MemoryStack.stackPush
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.math.roundToInt

class TrueType(font: String, val fontHeight: Float, private val windowSize: Pair<Int, Int>) {
    private val ttf: ByteBuffer = getSplashResourceAsStream(font).toManagedByteBuffer()
    private val info: STBTTFontinfo = STBTTFontinfo.create()
    
    private var contentScaleX: Float = 0f
    private var contentScaleY: Float = 0f
    
    private var bitmapWidth: Int = 0
    private var bitmapHeight: Int = 0
    private var fontTexture: Int = 0
    private lateinit var cdata: STBTTBakedChar.Buffer

    init {
        check(stbtt_InitFont(info, ttf)) { "Failed to initialize font information." }
    }
    
    fun initWindow(monitor: Long) {
        stackPush().use { stack ->
            val px = stack.mallocFloat(1)
            val py = stack.mallocFloat(1)
            glfwGetMonitorContentScale(monitor, px, py)
            contentScaleX = px.get()
            contentScaleY = py.get()
        }
    }
    
    fun initFont() {
        bitmapWidth = (512 * contentScaleX).roundToInt()
        bitmapHeight = (512 * contentScaleY).roundToInt()
        
        glEnable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        
        fontTexture = glGenTextures()
        val chardata = STBTTBakedChar.malloc(224)
        val bitmap: ByteBuffer = BufferUtils.createByteBuffer(bitmapWidth * bitmapHeight)
        
        stbtt_BakeFontBitmap(ttf, fontHeight * contentScaleY, bitmap, bitmapWidth, bitmapHeight, 32, chardata)
        
        glBindTexture(GL_TEXTURE_2D, fontTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, bitmapWidth, bitmapHeight, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        
        cdata = chardata
    }
    
    fun free() {
        cdata.free()
    }
    
    @Suppress("UNUSED_PARAMETER")
    fun renderText(text: String, color: Triple<Float, Float, Float>, VBO: Int, offsetY: Float = 0f) {
        stackPush().use { stack ->
            val pCodePoint = stack.mallocInt(1)
            val x = stack.floats(0.0f)
            val y = stack.floats(0.0f)
            val quad = STBTTAlignedQuad.malloc(stack)
            var i = 0
            val to = text.length
            while (i < to) {
                i += getCP(text, to, i, pCodePoint)
                val cp = pCodePoint[0]
                stbtt_GetBakedQuad(cdata, bitmapWidth, bitmapHeight, cp - 32, x, y, quad, true)
                val x0 = quad.x0()
                val x1 = quad.x1()
                val y0 = quad.y0()
                val y1 = quad.y1()
                
                val s0 = quad.s0()
                val s1 = quad.s1()
                val t0 = quad.t0()
                val t1 = quad.t1()

                val vertices = floatArrayOf(
                    // positions    // texture coords
                    x0, y0,         s0, t0,
                    x1, y0,         s1, t0,
                    x1, y1,         s1, t1,
                    x0, y1,         s0, t1
                )
                val view = Matrix4f
                    .ortho2D(0f, windowSize.first.toFloat(), windowSize.second.toFloat(), 0f, -1f, 1f)
                    .translate(5f, fontHeight * 0.5f + 4.0f + offsetY, 0f)

                glBindTexture(GL_TEXTURE_2D, fontTexture)
                UniformView.update(view)
                UniformTextColor.update(color)

                bufferVertices(VBO, vertices, GL_DYNAMIC_DRAW)
                
                glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0)
            }
        }
    }
}

private fun getCP(text: String, to: Int, i: Int, cpOut: IntBuffer): Int {
    val c1 = text[i]
    if (Character.isHighSurrogate(c1) && i + 1 < to) {
        val c2 = text[i + 1]
        if (Character.isLowSurrogate(c2)) {
            cpOut.put(0, Character.toCodePoint(c1, c2))
            return 2
        }
    }
    cpOut.put(0, c1.code)
    return 1
}
