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

package dev.su5ed.koremods.splash.log

import dev.su5ed.koremods.api.SplashScreen
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender

class SplashAppender(name: String, filter: Filter?, private val splash: SplashScreen) : AbstractAppender(name, filter, null, true) {
    companion object {
        fun createAppender(name: String, filter: Filter?, splash: SplashScreen): SplashAppender {
            return SplashAppender(name, filter, splash)
        }
    }
    
    override fun append(event: LogEvent) {
        if (!splash.isClosing()) splash.log(event.message.formattedMessage)
    }
}