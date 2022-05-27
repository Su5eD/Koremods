/*
 * This file is part of Koremods, licensed under the MIT License
 *
 * Copyright (c) 2021-2022 Garden of Fancy
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

package wtf.gofancy.koremods.launch

import org.apache.logging.log4j.Level

/**
 * Implemented by frontends for additional configuration of [wtf.gofancy.koremods.launch.KoremodsLaunch] 
 */
interface KoremodsLaunchPlugin {

    /**
     * Whether the Koremods Splash screen is available in the current environment.
     * Does NOT toggle the state of the screen, that is done by [wtf.gofancy.koremods.KoremodsConfig.enableSplashScreen]
     */
    val splashScreenAvailable: Boolean

    /**
     * Fallback logger appender callback used for [KoremodsLogAppender] when the splash screen is disabled/not avaiable.
     * 
     * @param level The logging Level
     * @param message the message string to be logger
     * 
     * @see wtf.gofancy.koremods.prelaunch.KoremodsBlackboard.createLogger
     */
    fun appendLogMessage(level: Level, message: String)
}