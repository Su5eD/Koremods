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

package wtf.gofancy.koremods.prelaunch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Holds shared information constants
 */
public final class KoremodsBlackboard {
    public static final String NAME = "Koremods";
    public static final String NAMESPACE = "koremods";
    /**
     * Logger name prefix used by Koremods logger instances
     */
    public static final String LOGGER_GROUP = "wtf.gofancy.koremods";
    /**
     * File name used by both global and script pack config files
     */
    public static final String CONFIG_FILE = NAMESPACE + ".conf";
    /**
     * Path to a script pack's configuration file relative to its root
     */
    public static final String CONFIG_FILE_LOCATION = "META-INF/" + CONFIG_FILE;

    /**
     * @param name the logger name suffix
     * @return a new {@link org.apache.logging.log4j.Logger} instance with the specified name inside the Koremods logger
     * group. The output is also wired up to the splash screen, if it's enabled.
     */
    public static Logger createLogger(String name) {
        return LogManager.getLogger(LOGGER_GROUP + "." + name);
    }

    private KoremodsBlackboard() {}
}
