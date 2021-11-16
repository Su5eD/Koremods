package dev.su5ed.koremods.api;

import org.apache.logging.log4j.core.LoggerContext;

public interface SplashScreen {
    void startThread();
    
    void awaitInit();
    
    void setTerminateOnClose(boolean terminate);
    
    void injectSplashLogger(LoggerContext context);
    
    void log(String message);
    
    void close(boolean delay);
    
    boolean isClosing();
}
