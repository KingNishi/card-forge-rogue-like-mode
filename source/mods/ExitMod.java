package forge.app;

/** The game really ends when its window closes (user 2026-11-16 "wenn man ein game beendet bleiben die alten instanzen
 *  manchmal noch aktiv"): after a LAN game, netty's server/client threads are not daemons and kept the JVM alive without
 *  a window - the invisible game held the LAN port and the next "host" failed with BindException.
 *  Main.main (patched) calls afterMain() once the libGDX loop has ended (window closed, Forge already saved while
 *  disposing). Windows only (there the Lwjgl3Application constructor blocks until the window closes). */
public final class ExitMod {
 private ExitMod(){}
 public static void afterMain(){
  try{if(!forge.util.OperatingSystem.isWindows())return;}catch(Throwable t){return;}
  Thread guard=new Thread(()->{try{Thread.sleep(5000);}catch(InterruptedException ignored){}Runtime.getRuntime().halt(0);},"exit-guard");
  guard.setDaemon(true);guard.start();   // a shutdown hook that hangs cannot keep the game alive either
  System.exit(0);
 }
}
