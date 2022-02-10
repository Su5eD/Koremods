package wtf.gofancy.koremods.api

import org.apache.logging.log4j.Level
import wtf.gofancy.koremods.prelaunch.KoremodsPrelaunch

interface KoremodsLaunchPlugin {
    fun shouldEnableSplashScreen(): Boolean
    
    fun createSplashScreen(prelaunch: KoremodsPrelaunch): SplashScreen
    
    fun appendLogMessage(level: Level, message: String)
}
