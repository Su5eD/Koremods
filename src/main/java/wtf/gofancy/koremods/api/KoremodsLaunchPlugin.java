package wtf.gofancy.koremods.api;

import wtf.gofancy.koremods.prelaunch.KoremodsPrelaunch;

public interface KoremodsLaunchPlugin {
    boolean shouldEnableSplashScreen();
    
    SplashScreen createSplashScreen(KoremodsPrelaunch prelaunch);
    
    void appendLogMessage(String message);
    
    void verifyScriptPacks();
}
