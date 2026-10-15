package forge.adventure.util;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import forge.screens.match.MatchScreen;
import forge.screens.match.MatchController;
import forge.screens.match.views.VPlayerPanel;
import forge.toolbox.*;
import java.lang.reflect.*;
import java.util.*;
/** User-controlled permanent sizing. Never scales the board art or hand. */
public final class BattleZoomMod {
 private static float scale=1;
 private static boolean manual;
 private static final Map<MatchScreen,FButton[]> CONTROLS=new WeakHashMap<>();
 public static float scale(){return ArenaMotionMod.active()?scale:1;}
 public static boolean manual(){return ArenaMotionMod.active()&&manual;}
 public static void setScale(float value){
  if(!Float.isFinite(value))return;
  scale=Math.max(.60f,Math.min(1.80f,value));manual=true;reflow();
 }
 public static void reset(){scale=1;manual=false;reflow();}
 private static void reflow(){
  MatchScreen screen=MatchController.getView();
  if(screen!=null){
   try{Field f=MatchScreen.class.getDeclaredField("scroller");f.setAccessible(true);Object scroller=f.get(screen);
    if(scroller!=null){Field extra=scroller.getClass().getDeclaredField("extraHeight");extra.setAccessible(true);extra.setFloat(scroller,0);((FScrollPane)scroller).setScrollTop(0);}
   }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
   screen.revalidate();layout(screen);
  }
  // Native card-change updates also revalidate these rows. This is deliberate:
  // no accumulating scale is applied to their already-scaled bounds.
  for(VPlayerPanel p:MatchScreen.getPlayerPanels().values()){p.revalidate();p.getField().revalidate();}
 }
 public static boolean gesture(FScrollPane pane,float x,float y,float amount){
  if(!ArenaMotionMod.active())return originalZoom(pane,x,y,amount);
  if(Float.isFinite(amount)&&amount!=0){
   float delta=Math.max(-.35f,Math.min(.35f,amount/Math.max(300,pane.getHeight())*2));
   setScale(scale*(float)Math.exp(delta));
  }
  return true;
 }
 private static boolean originalZoom(FScrollPane p,float x,float y,float a){
  try{Method m=p.getClass().getDeclaredMethod("battleOriginalZoom",float.class,float.class,float.class);m.setAccessible(true);return (Boolean)m.invoke(p,x,y,a);}
  catch(InvocationTargetException e){throw new IllegalStateException(e.getCause());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 public static boolean key(MatchScreen screen,int key){
  if(ArenaMotionMod.active()&&Gdx.input!=null&&(Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)||Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))){
   if(key==Input.Keys.PLUS||key==Input.Keys.EQUALS||key==Input.Keys.NUMPAD_ADD){setScale(scale*1.10f);return true;}
   if(key==Input.Keys.MINUS||key==Input.Keys.NUMPAD_SUBTRACT){setScale(scale/1.10f);return true;}
   if(key==Input.Keys.NUM_0||key==Input.Keys.NUMPAD_0){reset();return true;}
  }
  try{Method m=MatchScreen.class.getDeclaredMethod("battleOriginalKeyDown",int.class);m.setAccessible(true);return (Boolean)m.invoke(screen,key);}
  catch(InvocationTargetException e){throw new IllegalStateException(e.getCause());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 public static void layout(MatchScreen screen){
  // landscape Arena layout: zoom lives in the round match menu (MatchMenuMod); the floating buttons covered the portrait
  if(!ArenaMotionMod.active()||forge.Forge.isLandscapeMode()){FButton[] controls=CONTROLS.get(screen);if(controls!=null)for(FButton b:controls)b.setVisible(false);return;}
  FButton[] controls=CONTROLS.computeIfAbsent(screen,s->{
   FButton minus=new FButton("-"),auto=new FButton("Auto"),plus=new FButton("+");
   minus.setCommand((forge.gui.UiCommand)()->setScale(scale/1.10f));auto.setCommand((forge.gui.UiCommand)BattleZoomMod::reset);plus.setCommand((forge.gui.UiCommand)()->setScale(scale*1.10f));
   s.add(minus);s.add(auto);s.add(plus);return new FButton[]{minus,auto,plus};
  });
  float width=Math.min(144,screen.getWidth()*.11f),bh=30,x=screen.getWidth()-width-4,y;
  try{Field f=MatchScreen.class.getDeclaredField("bottomPlayerPrompt");f.setAccessible(true);FDisplayObject prompt=(FDisplayObject)f.get(screen);y=prompt!=null&&prompt.getTop()>0?prompt.getTop()-bh-6:screen.getHeight()-100-bh;}
  catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
  float small=width*.25f;
  controls[0].setBounds(x,y,small,bh);controls[1].setBounds(x+small,y,width-2*small,bh);controls[2].setBounds(x+width-small,y,small,bh);
  controls[1].setText(manual?Math.round(scale*100)+"%":"Auto");
  for(FButton b:controls)b.setVisible(true);
 }
}
