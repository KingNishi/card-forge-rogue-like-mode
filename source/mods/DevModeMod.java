package forge;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.BufferUtils;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import forge.adventure.scene.RogueCommandersScene;
import forge.adventure.scene.Scene;
import forge.adventure.scene.UIScene;
import forge.screens.FScreen;
import forge.toolbox.FButton;
import forge.toolbox.FContainer;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FLabel;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Developer mode for automated testing (only active when Forge is started with -Dforge.devmode=PORT).
 * A tiny HTTP server on 127.0.0.1:PORT lets a test script look at and drive this Forge instance without touching
 * the real mouse: screenshots straight from the frame buffer, taps/keys injected into libGDX's input processor,
 * clicks on UI elements by name or text, a JSON dump of the visible UI, the console log, and Rogue Commanders
 * shortcuts. Hooked in at the end of Forge.render() (PatchDevMode.java), where the frame is complete.
 * -Dforge.autocontinue=true|rogue (rogue: the "Rogue Commanders" desktop shortcut) skips the start menus: "Update Later", "Adventure Mode",
 * then Continue (last save) or Rogue Commanders (with the running run) from the Adventure start menu.
 */
public final class DevModeMod {
 private static int port=-2;
 private static final ConcurrentLinkedQueue<Object[]> tasks=new ConcurrentLinkedQueue<>();   // {Long dueMs, Runnable}
 private static final ArrayDeque<String> lines=new ArrayDeque<>();
 private static volatile CompletableFuture<Object[]> shot;
 private static boolean auto,autoRogue,adventureTapped;private static long autoStart,autoNext,startSeen;
 /** one step per check: dismiss the update notice, pick Adventure on the splash screen, then Continue */
 static void autoContinue(){
  long now=System.currentTimeMillis();if(now<autoNext)return;autoNext=now+400;
  if(now-autoStart>180_000){auto=false;System.out.println("[auto] gave up");return;}
  try{
   Scene scene=Forge.getCurrentScene();
   if(scene instanceof forge.adventure.scene.StartScene start&&Forge.transitionScreen==null){
    if(startSeen==0){startSeen=now;return;}
    // the splash screen normally removes itself at the end of its fade; when the menus were skipped this fast it can
    // stay on top as a black screen, so it is cleared here once the Adventure start menu is up
    if(Forge.splashScreen!=null){if(now-startSeen<3000)return;Forge.clearSplashScreen();System.out.println("[auto] cleared splash screen");startSeen=now;return;}
    if(now-startSeen<1500)return;
    auto=false;
    if(autoRogue){System.out.println("[auto] rogue commanders");RogueCommandersScene.openFromMenu();}
    else{System.out.println("[auto] continue");start.Continue();}
    return;
   }
   FButton later=findButton("Update Later");
   if(later!=null){later.tap(0,0,1);System.out.println("[auto] update later");return;}
   forge.screens.SplashScreen splash=Forge.splashScreen;
   if(splash!=null&&splash.isShowModeSelector()){
    Field f=forge.screens.SplashScreen.class.getDeclaredField("btnAdventure");f.setAccessible(true);
    Object b=f.get(splash);
    if(!adventureTapped&&b instanceof FButton adventure&&adventure.isVisible()&&adventure.isEnabled()){adventureTapped=true;adventure.tap(0,0,1);System.out.println("[auto] adventure mode");}   // once: every tap restarts the fade
    return;
   }
  }catch(Throwable t){auto=false;System.err.println("[auto] "+t);}
 }
 static FButton findButton(String text){
  List<FDisplayObject> roots=new ArrayList<>();
  if(Forge.splashScreen!=null)roots.add(Forge.splashScreen);
  if(Forge.getCurrentScreen()!=null)roots.add(Forge.getCurrentScreen());
  for(forge.toolbox.FOverlay o:forge.toolbox.FOverlay.getOverlays())roots.add(o);
  for(FDisplayObject r:roots){FButton b=findButton(r,text,0);if(b!=null)return b;}
  return null;
 }
 private static FButton findButton(FDisplayObject o,String text,int depth){
  if(o==null||!o.isVisible()||depth>14)return null;
  if(o instanceof FButton b&&b.getText()!=null&&b.getText().trim().equalsIgnoreCase(text))return b;
  if(o instanceof FContainer c)for(int i=0;i<c.getChildCount();i++){FButton b=findButton(c.getChildAt(i),text,depth+1);if(b!=null)return b;}
  return null;
 }
 private static int frames;private static long fpsStart=System.currentTimeMillis();private static float fps;

 /** called at the end of every Forge.render() */
 private static long lastRestore;
 /** Forge keeps the window size it had at start (Forge.screenWidth/Height and Forge.camera) and never updates them:
  *  after the window changed size (maximize, other monitor, Windows shrinking a too-big window) the match and every
  *  dialog were drawn in the old size, stretched over the new window, while clicks arrived in the real window size -
  *  the bottom buttons (OK, Play/Draw) sat a bit above their click area (player reports 0.2.0, 2026-10-16).
  *  Runs at the end of every frame: bring size and camera in line with the real window. */
 static void followWindowSize(){
  try{
   int w=Gdx.graphics.getWidth(),h=Gdx.graphics.getHeight();
   if(w<=0||h<=0||(w==Forge.getScreenWidth()&&h==Forge.getScreenHeight()))return;   // minimized or unchanged
   for(String name:new String[]{"screenWidth","screenHeight"}){Field f=Forge.class.getDeclaredField(name);f.setAccessible(true);f.setInt(null,name.endsWith("Width")?w:h);}
   if(Forge.camera!=null){
    Forge.camera.viewportWidth=w;Forge.camera.viewportHeight=h;
    Forge.camera.position.set(w/2f,h/2f,0);Forge.camera.update();
   }
   FScreen screen=Forge.getCurrentScreen();if(screen!=null)screen.setSize(w,h);
  }catch(Throwable t){System.err.println("[window size] "+t);}
 }
 public static void frame(){
  followWindowSize();                           // keep Forge's drawing size in step with the real window
  RogueCommandersScene.drawSubtitle();          // voice line subtitles on top of every screen
  RogueCommandersScene.drawStandaloneCover();   // standalone shortcut: own loading/closing screen over Forge's
  if(port==-2){
   String mode=System.getProperty("forge.autocontinue");
   auto=mode!=null&&!mode.isBlank()&&!mode.equalsIgnoreCase("false");autoRogue="rogue".equalsIgnoreCase(mode);autoStart=System.currentTimeMillis();
   String p=System.getProperty("forge.devmode");
   if(p==null||p.isBlank())port=-1;
   else{try{port=Integer.parseInt(p.trim());}catch(NumberFormatException e){port=8790;}start();}
  }
  if(port==-1&&!auto)return;
  // test instance: "show desktop" / Win+D minimizes the off-screen window too, which then renders at 0x0 - restore it
  if(port>0&&Gdx.graphics.getWidth()==0&&System.currentTimeMillis()-lastRestore>2000){lastRestore=System.currentTimeMillis();
   try{var win=((Lwjgl3Graphics)Gdx.graphics).getWindow();win.restoreWindow();win.setPosition(-6000,-6000);}catch(Throwable ignored){}}
  if(auto)autoContinue();
  long now=System.currentTimeMillis();
  for(Iterator<Object[]> it=tasks.iterator();it.hasNext();){
   Object[] t=it.next();
   if((Long)t[0]<=now){it.remove();try{((Runnable)t[1]).run();}catch(Throwable e){log("[dev] task failed: "+e);}}
  }
  CompletableFuture<Object[]> s=shot;
  if(s!=null){shot=null;try{s.complete(capture());}catch(Throwable e){s.completeExceptionally(e);}}
  frames++;if(now-fpsStart>=1000){fps=frames*1000f/(now-fpsStart);frames=0;fpsStart=now;}
 }
 private static Object[] capture(){
  int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
  Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,0);
  Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT,1);
  ByteBuffer buf=BufferUtils.newByteBuffer(w*h*4);
  Gdx.gl.glReadPixels(0,0,w,h,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,buf);
  return new Object[]{buf,w,h};
 }
 static void later(long delayMs,Runnable r){tasks.add(new Object[]{System.currentTimeMillis()+delayMs,r});}
 static <T> T onGl(Callable<T> c)throws Exception{
  CompletableFuture<T> f=new CompletableFuture<>();
  later(0,()->{try{f.complete(c.call());}catch(Throwable e){f.completeExceptionally(e);}});
  return f.get(10,TimeUnit.SECONDS);
 }

 // ------------------------------------------------------------------ log capture
 static synchronized void log(String s){lines.addLast(s);while(lines.size()>5000)lines.removeFirst();}
 private static void tee(){
  for(int k=0;k<2;k++){
   PrintStream orig=k==0?System.out:System.err;
   PrintStream t=new PrintStream(new OutputStream(){
    final StringBuilder sb=new StringBuilder();
    @Override public void write(int b){orig.write(b);if(b=='\n'){log(sb.toString());sb.setLength(0);}else if(b!='\r')sb.append((char)b);}
    @Override public void flush(){orig.flush();}
   },true);
   if(k==0)System.setOut(t);else System.setErr(t);
  }
 }

 // ------------------------------------------------------------------ server
 private static HttpServer server;
 static void shutdown(){
  HttpServer s=server;server=null;if(s!=null)new Thread(()->s.stop(0)).start();
  Thread killer=new Thread(()->{try{Thread.sleep(6000);}catch(InterruptedException ignored){}Runtime.getRuntime().halt(0);});killer.setDaemon(true);killer.start();
 }
 private static void start(){
  tee();
  try{
   HttpServer srv=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
   srv.createContext("/",DevModeMod::handle);
   srv.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"devmode");t.setDaemon(true);return t;}));
   srv.start();server=srv;
   // the HTTP dispatcher is not a daemon thread: stop it with the app, otherwise the JVM would stay alive after closing
   Gdx.app.addLifecycleListener(new com.badlogic.gdx.LifecycleListener(){
    @Override public void pause(){}
    @Override public void resume(){}
    @Override public void dispose(){shutdown();}
   });
   log("[dev] devmode listening on 127.0.0.1:"+port);
  }catch(Exception e){System.err.println("[dev] devmode failed: "+e);port=-1;}
 }
 private static Map<String,String> query(HttpExchange ex){
  Map<String,String> q=new HashMap<>();String raw=ex.getRequestURI().getRawQuery();
  if(raw!=null)for(String kv:raw.split("&")){int i=kv.indexOf('=');
   if(i>0)q.put(URLDecoder.decode(kv.substring(0,i),StandardCharsets.UTF_8),URLDecoder.decode(kv.substring(i+1),StandardCharsets.UTF_8));
   else q.put(URLDecoder.decode(kv,StandardCharsets.UTF_8),"");}
  return q;
 }
 private static void reply(HttpExchange ex,int code,String type,byte[] body)throws java.io.IOException{
  ex.getResponseHeaders().set("Content-Type",type);ex.sendResponseHeaders(code,body.length);
  try(OutputStream o=ex.getResponseBody()){o.write(body);}
 }
 private static void text(HttpExchange ex,int code,String s)throws java.io.IOException{reply(ex,code,s.startsWith("{")||s.startsWith("[")?"application/json":"text/plain; charset=utf-8",s.getBytes(StandardCharsets.UTF_8));}
 private static void handle(HttpExchange ex)throws java.io.IOException{
  String path=ex.getRequestURI().getPath();Map<String,String> q=query(ex);
  try{
   switch(path){
    case "/state"->text(ex,200,onGl(DevModeMod::state));
    case "/shot"->{
     CompletableFuture<Object[]> f=new CompletableFuture<>();shot=f;
     Object[] r=f.get(10,TimeUnit.SECONDS);
     reply(ex,200,"image/jpeg",encode((ByteBuffer)r[0],(Integer)r[1],(Integer)r[2],Integer.parseInt(q.getOrDefault("w","1280"))));
    }
    case "/tap"->{float[] p=point(q);tap(p[0],p[1],Long.parseLong(q.getOrDefault("ms","70")),"right".equals(q.get("button"))?Input.Buttons.RIGHT:Input.Buttons.LEFT);text(ex,200,"ok");}
    case "/drag"->{float[] a=point(Map.of("x",q.get("x1"),"y",q.get("y1"))),b=point(Map.of("x",q.get("x2"),"y",q.get("y2")));drag(a,b,Long.parseLong(q.getOrDefault("ms","400")));text(ex,200,"ok");}
    case "/click"->text(ex,200,onGl(()->click(q.get("name"),q.get("text"),Long.parseLong(q.getOrDefault("ms","70")))));
    case "/key"->{int key=Input.Keys.valueOf(q.getOrDefault("k","ESCAPE"));
     later(0,()->input().keyDown(key));later(60,()->input().keyUp(key));text(ex,200,key<0?"unknown key":"ok");}
    case "/type"->{String t=q.getOrDefault("text","");later(0,()->{for(char ch:t.toCharArray())input().keyTyped(ch);});text(ex,200,"ok");}
    case "/scroll"->{float[] p=point(q);float amount=Float.parseFloat(q.getOrDefault("amount","1"));
     later(0,()->{input().mouseMoved((int)p[0],(int)p[1]);input().scrolled(0,amount);});text(ex,200,"ok");}
    case "/log"->{int n=Integer.parseInt(q.getOrDefault("n","200"));List<String> all;synchronized(DevModeMod.class){all=new ArrayList<>(lines);}
     text(ex,200,String.join("\n",all.subList(Math.max(0,all.size()-n),all.size())));}
    case "/window"->{String mode=q.getOrDefault("mode","hide");text(ex,200,onGl(()->{
     var win=((Lwjgl3Graphics)Gdx.graphics).getWindow();win.restoreWindow();   // a minimized window renders at 0x0
     if(mode.equals("show"))win.setPosition(Integer.parseInt(q.getOrDefault("x","120")),Integer.parseInt(q.getOrDefault("y","80")));
     else win.setPosition(-6000,-6000);return "ok";}));}
    case "/mouse"->text(ex,200,mouse(q));
    case "/duo"->text(ex,200,onGl(()->duo(q)));
    case "/rogue"->text(ex,200,onGl(()->rogue(q.getOrDefault("action",""))));
   case "/scene"->text(ex,200,onGl(()->sceneDev(q.getOrDefault("action",""),q.getOrDefault("arg",""))));
   case "/match"->text(ex,200,match(q));
    case "/quit"->{text(ex,200,"bye");later(100,()->Gdx.app.exit());
     Thread killer=new Thread(()->{try{Thread.sleep(8000);}catch(InterruptedException ignored){}Runtime.getRuntime().halt(0);});killer.setDaemon(true);killer.start();}
    default->text(ex,404,"endpoints: /state /shot?w= /tap?x=&y= /drag?x1=&y1=&x2=&y2= /click?name=|text= /key?k= /type?text= /scroll?x=&y=&amount= /log?n= /window?mode=hide|show /rogue?action=open|win|lose|state /quit");
   }
  }catch(Throwable e){text(ex,500,"error: "+e);}
 }

 // ------------------------------------------------------------------ Forge 1v1 Browser (user 2026-11-10)
 /** /mouse?k=down|move|up|rclick|wheel&x=&y=&dy= - raw pointer events for the browser opponent (x/y like /tap) */
 static boolean duoDown;
 static String mouse(Map<String,String> q){
  float[] p=point(q);int x=(int)p[0],y=(int)p[1];String k=q.getOrDefault("k","move");
  switch(k){
   case "down"->later(0,()->{input().mouseMoved(x,y);input().touchDown(x,y,0,Input.Buttons.LEFT);duoDown=true;});
   case "move"->later(0,()->{if(duoDown)input().touchDragged(x,y,0);else input().mouseMoved(x,y);});
   case "up"->later(0,()->{input().touchUp(x,y,0,Input.Buttons.LEFT);duoDown=false;});
   case "rclick"->{later(0,()->{input().mouseMoved(x,y);input().touchDown(x,y,0,Input.Buttons.RIGHT);});later(60,()->input().touchUp(x,y,0,Input.Buttons.RIGHT));}
   case "wheel"->{float dy=Float.parseFloat(q.getOrDefault("dy","1"));later(0,()->{input().mouseMoved(x,y);input().scrolled(0,dy);});}
   default->{return "k: down|move|up|rclick|wheel";}
  }
  return "ok";
 }
 static Object field(Object o,String name)throws ReflectiveOperationException{
  for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}}
  throw new NoSuchFieldException(name);
 }
 static Object call(Object o,String name,Class<?>[] types,Object... args)throws ReflectiveOperationException{
  for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()){try{java.lang.reflect.Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(NoSuchMethodException ignored){}}
  throw new NoSuchMethodException(name);
 }
 /** /duo?action=info|variant|deck&name=|ready|start - operates the online lobby of this instance (host or client) */
 static String duo(Map<String,String> q){
  try{
   if("online".equals(q.get("action"))){forge.screens.online.OnlineMenu.OnlineScreen.Lobby.open();return "opening online lobby";}
   if("screen".equals(q.get("action")))return Forge.getCurrentScreen()==null?"none":Forge.getCurrentScreen().getClass().getSimpleName();
   if(!(Forge.getCurrentScreen() instanceof forge.screens.constructed.LobbyScreen ls))
    return "screen "+(Forge.getCurrentScreen()==null?"none":Forge.getCurrentScreen().getClass().getSimpleName());
   @SuppressWarnings("unchecked") List<Object> panels=(List<Object>)field(ls,"playerPanels");
   String action=q.getOrDefault("action","info");
   switch(action){
    case "variant"->{
     ls.getLobby().applyVariant(forge.game.GameType.Commander);
     return "ok "+ls.getLobby().hasVariant(forge.game.GameType.Commander);}
    case "deck"->{
     String name=q.getOrDefault("name","");StringBuilder seen=new StringBuilder();
     for(Object pp:panels){
      if(!Boolean.TRUE.equals(field(pp,"mayEdit")))continue;
      int idx=(Integer)field(pp,"index");
      forge.deck.FDeckChooser ch=(forge.deck.FDeckChooser)call(pp,"getCommanderDeckChooser",new Class<?>[0]);
      if(ch.getSelectedDeckType()!=forge.deck.DeckType.COMMANDER_DECK){ch.setSelectedDeckType(forge.deck.DeckType.COMMANDER_DECK);return "retry: deck type set";}
      var lst=ch.getLstDecks();Object match=null;
      for(var e:lst.getPool()){Object item=e.getKey();String n=item.toString();
       try{n=String.valueOf(item.getClass().getMethod("getName").invoke(item));}catch(Exception ignored){}
       if(seen.length()<300)seen.append(n).append(" | ");
       if(n.equalsIgnoreCase(name))match=item;}
      if(match==null)return "missing deck; seen: "+seen;
      call(lst,"setSelectedItem",new Class<?>[]{forge.item.InventoryItem.class},match);
      call(ls,"updateDeck",new Class<?>[]{int.class},idx);
      return "ok slot "+idx;
     }
     return "no editable slot";}
    case "ready"->{
     int n=0;
     for(Object pp:panels){
      if(!Boolean.TRUE.equals(field(pp,"mayEdit")))continue;
      int idx=(Integer)field(pp,"index");
      call(pp,"setIsReady",new Class<?>[]{boolean.class},true);
      call(ls,"setReady",new Class<?>[]{int.class,boolean.class},idx,true);n++;}
     return "ready "+n;}
    case "start"->{call(ls,"startMatch",new Class<?>[0]);return "starting";}
    default->{
     StringBuilder sb=new StringBuilder("players "+panels.size()+" commander "+ls.getLobby().hasVariant(forge.game.GameType.Commander)+"\n");
     for(Object pp:panels)sb.append(field(pp,"index")).append(" edit=").append(field(pp,"mayEdit")).append(" ready=").append(field(pp,"isReady")).append('\n');
     return sb.toString();}
   }
  }catch(java.lang.reflect.InvocationTargetException e){return "error: "+e.getCause();}
  catch(Exception e){return "error: "+e;}
 }

 // ------------------------------------------------------------------ input
 static InputProcessor input(){
  InputProcessor p=Gdx.input.getInputProcessor();
  return p!=null?p:new com.badlogic.gdx.InputAdapter();
 }
 /** x/y <= 1 are fractions of the window, larger values are window pixels (logical, y down) */
 static float[] point(Map<String,String> q){
  float x=Float.parseFloat(q.getOrDefault("x","0.5")),y=Float.parseFloat(q.getOrDefault("y","0.5"));
  int w=Gdx.graphics.getWidth(),h=Gdx.graphics.getHeight();
  return new float[]{x<=1?x*w:x,y<=1?y*h:y};
 }
 static void tap(float x,float y,long ms){tap(x,y,ms,Input.Buttons.LEFT);}
 static void tap(float x,float y,long ms,int button){
  later(0,()->{input().mouseMoved((int)x,(int)y);input().touchDown((int)x,(int)y,0,button);});
  later(Math.max(20,ms),()->input().touchUp((int)x,(int)y,0,button));
 }
 static void drag(float[] a,float[] b,long ms){
  later(0,()->input().touchDown((int)a[0],(int)a[1],0,Input.Buttons.LEFT));
  int steps=12;
  for(int i=1;i<=steps;i++){float t=i/(float)steps;int x=(int)(a[0]+(b[0]-a[0])*t),y=(int)(a[1]+(b[1]-a[1])*t);later(ms*i/steps,()->input().touchDragged(x,y,0));}
  later(ms+30,()->input().touchUp((int)b[0],(int)b[1],0,Input.Buttons.LEFT));
 }

 // ------------------------------------------------------------------ UI inspection
 static final class Widget{String type,name,text;float x,y,w,h;boolean disabled;}
 static Stage stage(Scene scene){
  if(!(scene instanceof UIScene))return null;
  for(Class<?> c=scene.getClass();c!=null;c=c.getSuperclass()){
   try{Field f=c.getDeclaredField("stage");f.setAccessible(true);Object s=f.get(scene);if(s instanceof Stage st)return st;}catch(ReflectiveOperationException ignored){}
  }
  return null;
 }
 static String fieldText(Object o,String... names){
  for(String n:names)for(Class<?> c=o.getClass();c!=null&&c!=Object.class;c=c.getSuperclass()){
   try{Field f=c.getDeclaredField(n);f.setAccessible(true);Object v=f.get(o);if(v instanceof String s)return s;}catch(ReflectiveOperationException ignored){}
  }
  return null;
 }
 static List<Widget> widgets(){
  List<Widget> out=new ArrayList<>();
  boolean transition=Forge.transitionScreen!=null;
  Scene scene=Forge.getCurrentScene();FScreen screen=Forge.getCurrentScreen();
  Stage st=stage(scene);
  if(st!=null&&!transition)walk(st,st.getRoot(),out);
  if(screen!=null&&!transition)walk(screen,0,0,out,0);
  for(forge.toolbox.FOverlay o:forge.toolbox.FOverlay.getOverlays())walk(o,0,0,out,0);   // dialogs, option panes, menus
  return out;
 }
 private static void walk(Stage st,Actor a,List<Widget> out){
  if(!a.isVisible())return;
  String type=a.getClass().getSimpleName();if(type.isEmpty())type=a.getClass().getName();
  String text=null;
  if(a instanceof TextraButton b)text=b.getTextraLabel()==null?null:b.getTextraLabel().storedText;
  else if(a instanceof TextraLabel l)text=l.storedText;
  else if(a instanceof Label l)text=l.getText().toString();
  else text=fieldText(a,"label","name");
  boolean interesting=a instanceof Button||a.getListeners().size>0||a.getName()!=null||(text!=null&&!text.isBlank());
  if(interesting&&a.getWidth()>0&&a.getHeight()>0){
   Vector2 p0=a.localToStageCoordinates(new Vector2(0,a.getHeight())),p1=a.localToStageCoordinates(new Vector2(a.getWidth(),0));
   st.stageToScreenCoordinates(p0);st.stageToScreenCoordinates(p1);
   Widget w=new Widget();w.type=type;w.name=a.getName();w.text=text;w.x=Math.min(p0.x,p1.x);w.y=Math.min(p0.y,p1.y);w.w=Math.abs(p1.x-p0.x);w.h=Math.abs(p1.y-p0.y);
   w.disabled=a instanceof Button b&&b.isDisabled();
   out.add(w);
  }
  if(a instanceof Group g)for(Actor c:g.getChildren().toArray(Actor.class))walk(st,c,out);
 }
 private static void walk(FDisplayObject o,float ox,float oy,List<Widget> out,int depth){
  if(!o.isVisible()||depth>14)return;
  float x=ox+o.getLeft(),y=oy+o.getTop();
  String text=o instanceof FButton b?b.getText():o instanceof FLabel l?l.getText():null;
  if((o instanceof FButton||o instanceof FLabel)&&o.getWidth()>0&&o.getHeight()>0){
   Widget w=new Widget();w.type=o.getClass().getSimpleName().isEmpty()?o.getClass().getName():o.getClass().getSimpleName();w.text=text;
   w.x=x;w.y=y;w.w=o.getWidth();w.h=o.getHeight();out.add(w);
  }
  if(o instanceof FContainer c)for(int i=0;i<c.getChildCount();i++)walk(c.getChildAt(i),x,y,out,depth+1);
 }
 static String click(String name,String text,long ms){
  for(Widget w:widgets()){
   boolean match=(name!=null&&name.equals(w.name))||(text!=null&&w.text!=null&&w.text.replaceAll("\\[[^\\]]*\\]","").trim().equalsIgnoreCase(text.trim()));
   if(match){if(w.disabled)return "disabled: "+(w.name!=null?w.name:w.text);tap(w.x+w.w/2,w.y+w.h/2,ms);return "tapped "+w.type+" '"+(w.text!=null?w.text:w.name)+"' at "+Math.round(w.x+w.w/2)+","+Math.round(w.y+w.h/2);}
  }
  return "not found";
 }
 static String esc(String s){return s==null?"null":"\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";}
 static String state(){
  StringBuilder sb=new StringBuilder("{");
  Scene scene=Forge.getCurrentScene();FScreen screen=Forge.getCurrentScreen();
  sb.append("\"screen\":").append(esc(screen==null?null:screen.getClass().getName()));
  sb.append(",\"scene\":").append(esc(scene==null?null:scene.getClass().getName()));
  sb.append(",\"transition\":").append(Forge.transitionScreen!=null);
  forge.toolbox.FOverlay top=forge.toolbox.FOverlay.getTopOverlay();
  sb.append(",\"overlay\":").append(esc(top==null?null:top.getClass().getName()));
  sb.append(",\"splash\":").append(Forge.splashScreen!=null);
  sb.append(",\"width\":").append(Gdx.graphics.getWidth()).append(",\"height\":").append(Gdx.graphics.getHeight());
  sb.append(",\"fps\":").append(Math.round(fps));
  sb.append(",\"widgets\":[");
  boolean first=true;
  for(Widget w:widgets()){
   if(!first)sb.append(',');first=false;
   sb.append("{\"type\":").append(esc(w.type)).append(",\"name\":").append(esc(w.name)).append(",\"text\":").append(esc(w.text))
     .append(",\"x\":").append(Math.round(w.x)).append(",\"y\":").append(Math.round(w.y)).append(",\"w\":").append(Math.round(w.w)).append(",\"h\":").append(Math.round(w.h));
   if(w.disabled)sb.append(",\"disabled\":true");
   sb.append('}');
  }
  return sb.append("]}").toString();
 }
 static String rogue(String action){
  switch(action){
   case "open"->{RogueCommandersScene.openFromMenu();return "opening";}
   case "win"->{RogueCommandersScene.instance().setWinner(true,false);return "win";}
   case "lose"->{RogueCommandersScene.instance().setWinner(false,false);return "lose";}
   default->{return "actions: open, win, lose";}
  }
 }
 // ------------------------------------------------------------------ test tools (user 2026-10-17: "fuege mehr dev tools ein")
 /** /scene?action=..&arg=.. -> RogueCommandersScene.dev (reflection: the scene is installed by another step) */
 static String sceneDev(String action,String arg){
  try{return String.valueOf(RogueCommandersScene.class.getMethod("dev",String.class,String.class).invoke(null,action,arg));}
  catch(java.lang.reflect.InvocationTargetException e){return "error: "+e.getCause();}catch(Exception e){return "error: "+e;}
 }
 /** /match?action=info|win|lose|mana|lands|life|hand|battlefield|graveyard&who=me|ai&card=NAME&value=N - direct engine
  *  changes in the running duel, no chooser dialogs (dev instance only) */
 static String match(Map<String,String> q){
  var ui=forge.screens.match.MatchController.instance;
  if(ui==null||ui.getGameView()==null)return "no match running";
  if(!(ui.getGameController() instanceof forge.player.PlayerControllerHuman h))return "no human controller";
  forge.game.Game game=h.getGame();forge.game.player.Player me=h.getPlayer(),ai=null;
  for(forge.game.player.Player pl:game.getPlayers())if(pl!=me)ai=pl;
  final forge.game.player.Player who="ai".equals(q.get("who"))?ai:me;final forge.game.player.Player foe=ai;
  String action=q.getOrDefault("action","info");
  switch(action){
   case "info"->{
    StringBuilder sb=new StringBuilder();var gv=ui.getGameView();
    sb.append("turn ").append(gv.getTurn()).append(" ").append(gv.getPhase()).append(" active ").append(gv.getPlayerTurn()==null?"-":gv.getPlayerTurn().getName()).append('\n');
    for(forge.game.player.Player pl:game.getPlayers()){
     sb.append(pl==me?"ME ":"AI ").append(pl.getName()).append(" life ").append(pl.getLife()).append('\n');
     for(forge.game.zone.ZoneType z:new forge.game.zone.ZoneType[]{forge.game.zone.ZoneType.Hand,forge.game.zone.ZoneType.Battlefield,forge.game.zone.ZoneType.Command}){
      List<String> names=new ArrayList<>();for(var c:pl.getCardsIn(z))names.add(c.getName()+(c.isTapped()?"(T)":""));
      sb.append("  ").append(z).append(" ").append(names.size()).append(": ").append(String.join(", ",names)).append('\n');}}
    sb.append("stack ").append(game.getStack().size()).append('\n');
    return sb.toString();}
   case "win","lose"->{final forge.game.player.Player loser="win".equals(action)?foe:me;
    forge.util.ThreadUtil.invokeInGameThread(()->{loser.setLife(0,null);game.getAction().checkStateEffects(true);});return action+" (life of "+loser.getName()+" set to 0)";}
   case "dialog"->{   // swipe the open choice dialog away (down) or back (up) - tests "Back to the choice" (2026-10-18)
    if(!(forge.toolbox.FOverlay.getTopOverlay() instanceof forge.toolbox.FDialog d))return "no dialog";
    boolean down=!"up".equals(q.get("dir"));com.badlogic.gdx.Gdx.app.postRunnable(()->d.fling(0f,down?3000f:-3000f));return "dialog "+(down?"down":"up");}
   case "prompt"->{   // a long trigger text in the prompt bar, with my commander as the prompt's card (2026-10-18)
    String t=q.getOrDefault("text","Ureni of the Unwritten - Whenever Ureni enters or attacks, look at the top eight cards of your library. You may put a Dragon creature card from among them onto the battlefield. Put the rest on the bottom of your library in a random order. Deal 3 damage to any target.");
    forge.game.card.CardView cv=null;for(var c:me.getCardsIn(forge.game.zone.ZoneType.Command))if(c.isCommander()){cv=c.getView();break;}
    final forge.game.card.CardView card=cv;
    com.badlogic.gdx.Gdx.app.postRunnable(()->{if(forge.Forge.getCurrentScreen() instanceof forge.screens.match.MatchScreen ms)ms.getActivePrompt().setMessage(t,card);});return "prompt set";}
   case "menu"->{   // a popup menu like the stack item menu (Auto-Yield / Yield to stack / ...) to check its look (2026-10-18)
    com.badlogic.gdx.Gdx.app.postRunnable(()->{
     forge.assets.FImage warn=null;try{warn=forge.assets.FSkinImage.valueOf("WARNING");}catch(Throwable ignored){}
     final forge.assets.FImage w=warn;
     forge.menu.FPopupMenu m=new forge.menu.FPopupMenu(){@Override protected void buildMenu(){
      addItem(new forge.menu.FCheckBoxMenuItem("Auto-Yield",true,e->{}));addItem(new forge.menu.FMenuItem("Yield to stack",w,e->{}));
      addItem(new forge.menu.FMenuItem("Resolve entire stack",w,e->{}));addItem(new forge.menu.FMenuItem("Zoom/Details",e->{}));}};
     if(forge.Forge.getCurrentScreen() instanceof forge.screens.match.MatchScreen ms)m.show(ms.getActivePrompt(),ms.getActivePrompt().getWidth()*.45f,-forge.Forge.getScreenHeight()*.35f);});
    return "menu";}
   case "order"->{   // opens a "Select Order" dialog (trigger order) to check its look (2026-10-18)
    List<String> items=List.of("Old Gnawbone - trigger","Wrathful Red Dragon - trigger","Dragon Tempest - trigger");
    com.badlogic.gdx.Gdx.app.postRunnable(()->new forge.toolbox.DualListBox<String>("Select Order",items.size(),new ArrayList<>(items),null,x->{}).show());return "order dialog";}
   case "mana"->{forge.util.ThreadUtil.invokeInGameThread(()->h.cheat().generateMana());return "mana";}
   case "lands"->{h.cheat().setCanPlayUnlimitedLands(true);return "unlimited lands";}
   case "life"->{int v=Integer.parseInt(q.getOrDefault("value","20"));forge.util.ThreadUtil.invokeInGameThread(()->{who.setLife(v,null);game.getAction().checkStateEffects(true);});return who.getName()+" life "+v;}
   case "hand","battlefield","graveyard"->{
    String name=q.get("card");forge.item.PaperCard pc=name==null?null:forge.model.FModel.getMagicDb().getCommonCards().getCard(name);
    if(pc==null)return "unknown card: "+name;
    forge.util.ThreadUtil.invokeInGameThread(()->{forge.game.card.Card c=forge.game.card.Card.fromPaperCard(pc,who);
     if("hand".equals(action))game.getAction().moveToHand(c,null);
     else if("graveyard".equals(action))game.getAction().moveToGraveyard(c,null);
     else game.getAction().moveToPlay(c,who,null,null);
     game.getAction().checkStateEffects(true);});
    return pc.getName()+" -> "+action+" of "+who.getName();}
   default->{return "actions: info, win, lose, mana, lands, life (who, value), hand|battlefield|graveyard (who, card)";}
  }
 }

 // ------------------------------------------------------------------ jpeg
 static byte[] encode(ByteBuffer buf,int w,int h,int target)throws java.io.IOException{
  BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
  int[] row=new int[w];
  for(int y=0;y<h;y++){
   int base=(h-1-y)*w*4;
   for(int x=0;x<w;x++){int i=base+x*4;row[x]=((buf.get(i)&255)<<16)|((buf.get(i+1)&255)<<8)|(buf.get(i+2)&255);}
   img.setRGB(0,y,w,1,row,0,w);
  }
  if(target>0&&target<w){
   int th=Math.max(1,Math.round(h*target/(float)w));
   BufferedImage small=new BufferedImage(target,th,BufferedImage.TYPE_INT_RGB);
   var g=small.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
   g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);g.drawImage(img,0,0,target,th,null);g.dispose();img=small;
  }
  ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(img,"jpg",out);return out.toByteArray();
 }
}
