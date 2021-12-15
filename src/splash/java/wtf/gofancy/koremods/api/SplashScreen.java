package wtf.gofancy.koremods.api;

public interface SplashScreen {
    void startOnThread();

    void setTerminateOnClose(boolean terminate);

    void log(String message);

    void close(boolean delay);
}
