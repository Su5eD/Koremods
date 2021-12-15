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

package wtf.gofancy.koremods.splash

import wtf.gofancy.koremods.launch.injectSplashLogger
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.LoggerContext
import kotlin.concurrent.thread

private val fakeLog = listOf(
    "Initializing Koremods",
    "Hello world",
    "Setting up environment",
    "Creating directories",
    "Discovering scripts",
    "More useless text",
    "Located script foo",
    "Located script bar",
    "Evaluating scripts",
    "Actually evaluating scripts",
    "Compiling scrips for the first time, this may take a while"
)

private val LOG: Logger = LogManager.getLogger("Koremods.Splash")

fun main() {
    val splash = KoremodsSplashScreen(LOG)
    splash.setTerminateOnClose(true)
    splash.startOnThread()
    injectSplashLogger(LogManager.getContext(false) as LoggerContext, splash::log)
    
    thread(name = "SplashLog") { 
        var fakeLogIndex = 0
        
        while (fakeLogIndex < fakeLog.size) {
            Thread.sleep(250)
            LOG.info(fakeLog[fakeLogIndex++])
        }
    }
    
    Thread.sleep(5000)
    LOG.info("Done")
    splash.close(true)
}
