package wtf.gofancy.koremods.api

interface SplashScreen {
    var terminateOnClose: Boolean
    
    fun startOnThread()
    
    fun close(delay: Boolean)
    
    fun log(message: String)
}
