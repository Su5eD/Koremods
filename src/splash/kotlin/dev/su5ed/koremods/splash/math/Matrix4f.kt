/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017, Heiko Brumme
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

package dev.su5ed.koremods.splash.math

import java.nio.FloatBuffer

/**
 * Minimal version of Heiko Brumme's Matrix4f implementation
 * 
 * This class represents a 4x4-Matrix. GLSL equivalent to mat4.
 *
 * @author Heiko Brumme
 */
class Matrix4f {
    private var m00 = 0f
    private var m01 = 0f
    private var m02 = 0f
    private var m03 = 0f
    private var m10 = 0f
    private var m11 = 0f
    private var m12 = 0f
    private var m13 = 0f
    private var m20 = 0f
    private var m21 = 0f
    private var m22 = 0f
    private var m23 = 0f
    private var m30 = 0f
    private var m31 = 0f
    private var m32 = 0f
    private var m33 = 0f

    /**
     * Creates a 4x4 identity matrix.
     */
    init {
        setIdentity()
    }

    /**
     * Sets this matrix to the identity matrix.
     */
    private fun setIdentity() {
        m00 = 1f
        m11 = 1f
        m22 = 1f
        m33 = 1f
        m01 = 0f
        m02 = 0f
        m03 = 0f
        m10 = 0f
        m12 = 0f
        m13 = 0f
        m20 = 0f
        m21 = 0f
        m23 = 0f
        m30 = 0f
        m31 = 0f
        m32 = 0f
    }

    /**
     * Stores the matrix in a given Buffer.
     *
     * @param buffer The buffer to store the matrix data
     */
    fun toBuffer(buffer: FloatBuffer) {
        buffer.put(m00).put(m01).put(m02).put(m03)
        buffer.put(m10).put(m11).put(m12).put(m13)
        buffer.put(m20).put(m21).put(m22).put(m23)
        buffer.put(m30).put(m31).put(m32).put(m33)
        buffer.flip()
    }
    
    /**
     * Creates a translation matrix. Similar to
     * `glTranslate(x, y, z)`.
     *
     * @param x x coordinate of translation vector
     * @param y y coordinate of translation vector
     * @param z z coordinate of translation vector
     *
     * @return Translation matrix
     */
    fun translate(x: Float, y: Float, z: Float): Matrix4f {
        m30 = fma(m00, x, fma(m10, y, fma(m20, z, m30)))
        m31 = fma(m01, x, fma(m11, y, fma(m21, z, m31)))
        m32 = fma(m02, x, fma(m12, y, fma(m22, z, m32)))
        m33 = fma(m03, x, fma(m13, y, fma(m23, z, m33)))
        return this
    }
    
    private fun fma(a: Float, b: Float, c: Float): Float {
        return a * b + c
    }

    companion object {
        /**
         * Creates a orthographic projection matrix. Similar to
         * `glOrtho(left, right, bottom, top, near, far)`.
         *
         * @param left   Coordinate for the left vertical clipping pane
         * @param right  Coordinate for the right vertical clipping pane
         * @param bottom Coordinate for the bottom horizontal clipping pane
         * @param top    Coordinate for the bottom horizontal clipping pane
         * @param near   Coordinate for the near depth clipping pane
         * @param far    Coordinate for the far depth clipping pane
         *
         * @return Orthographic matrix
         */
        fun ortho2D(left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float): Matrix4f {
            val ortho = Matrix4f()
            val tx = -(right + left) / (right - left)
            val ty = -(top + bottom) / (top - bottom)
            ortho.m00 = 2f / (right - left)
            ortho.m11 = 2f / (top - bottom)
            ortho.m22 = -2f / (far - near)
            ortho.m30 = tx
            ortho.m31 = ty
            return ortho
        }
    }
}

