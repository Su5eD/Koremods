package wtf.gofancy.koremods.api;

import wtf.gofancy.koremods.prelaunch.KoremodsPrelaunch;

import java.nio.file.Path;
import java.util.Map;

public interface KoremodsLaunchPlugin {
    boolean shouldEnableSplashScreen();
    
    SplashScreen createSplashScreen(KoremodsPrelaunch prelaunch);
    
    void appendLogMessage(String message);
    
    void verifyScriptPacks(Map<String, Path> namespaceSources);
}
