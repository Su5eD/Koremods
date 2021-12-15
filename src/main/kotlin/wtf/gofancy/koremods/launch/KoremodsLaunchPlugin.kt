package wtf.gofancy.koremods.launch

import wtf.gofancy.koremods.api.SplashScreen
import wtf.gofancy.koremods.prelaunch.KoremodsPrelaunch

interface KoremodsLaunchPlugin {
    val enableSplashScreen: Boolean
    
    fun createSplashScreen(prelaunch: KoremodsPrelaunch): SplashScreen?

    fun appendLogMessage(message: String)
}
