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
 // ------------------------------------------------------------------ field focus with 3-4 players (user 2026-11-15:
 // "zoom funktion mit mausrad in der ich einzelne spielfelder ran zoomen und wieder raus zoomen kann")
 /** the player whose field is zoomed in (null = all fields the same) and the zoom step 0..FOCUS_MAX */
 private static forge.game.player.PlayerView focus;private static int focusLevel;
 public static final int FOCUS_MAX=3;
 /** weight of a field in the 3-4 player layout: 1 normally, more for the zoomed field (ArenaBattleHudMod.split);
  *  Float.POSITIVE_INFINITY = this field takes everything the other (slim) fields leave */
 private static Object focusGame;
 public static float focusWeight(forge.game.player.PlayerView p){
  Object g=MatchController.instance==null?null:MatchController.instance.getGameView();
  if(g!=focusGame){focusGame=g;focus=null;focusLevel=0;}   // a new game starts unzoomed
  if(focus==null||focusLevel<=0||p==null||p.getId()!=focus.getId())return 1;
  return focusLevel>=FOCUS_MAX?Float.POSITIVE_INFINITY:focusLevel==1?2.2f:4.5f;
 }
 public static boolean focused(){return focus!=null&&focusLevel>0;}
 public static int focusLevel(){return focusLevel;}
 public static forge.game.player.PlayerView focusPlayer(){return focusLevel>0?focus:null;}
 /** 3-4 players still in the game (Forge drops a player who lost from the panel list) */
 public static boolean multi(){
  if(MatchScreen.getPlayerPanels().size()<=2)return false;
  MatchScreen v=MatchController.getView();if(v==null)return true;
  return v.getPlayerPanelsList().size()>2;   // Forge drops a player who lost from this list (hidden panels are only zoomed away)
 }
 /** Forge's mouse wheel (MainInputProcessor.scrolled, patched): over a battlefield in a 3-4 player game it zooms that
  *  field in (wheel up) or out (wheel down) step by step; Ctrl / Shift + wheel keep Forge's own zoom / pan */
 public static boolean wheel(float amountY){
  try{
   if(amountY==0||!multi()||!(forge.Forge.getCurrentScreen() instanceof MatchScreen)||forge.card.CardZoom.isOpen())return false;
   if(forge.Forge.KeyInputAdapter.isCtrlKeyDown()||forge.Forge.KeyInputAdapter.isShiftKeyDown())return false;
   for(forge.toolbox.FOverlay o:forge.toolbox.FOverlay.getOverlays())if(o instanceof forge.toolbox.FDialog&&o.isVisible())return false;   // a dialog is open: the wheel scrolls its list
   float mx=forge.Forge.mouseMovedX,my=forge.Forge.mouseMovedY;
   VPlayerPanel hit=null;
   for(VPlayerPanel p:MatchScreen.getPlayerPanels().values()){if(!p.isVisible())continue;var r=p.screenPos;if(r!=null&&mx>=r.x&&mx<r.x+r.width&&my>=r.y&&my<r.y+r.height)hit=p;}
   if(hit==null)return false;
   if(hit.getSelectedTab()!=null){var d=hit.getSelectedTab().getDisplayArea();var r=d==null?null:d.screenPos;   // over the hand: Forge scrolls the hand
    if(d!=null&&d.isVisible()&&r!=null&&mx>=r.x&&mx<r.x+r.width&&my>=r.y&&my<r.y+r.height)return false;}
   Object g=MatchController.instance==null?null:MatchController.instance.getGameView();if(g!=focusGame){focusGame=g;focus=null;focusLevel=0;}
   forge.game.player.PlayerView pv=hit.getPlayer();boolean same=focus!=null&&pv!=null&&focus.getId()==pv.getId();
   if(amountY<0){if(same&&focusLevel>0)focusLevel=Math.min(FOCUS_MAX,focusLevel+1);else{focus=pv;focusLevel=1;}}   // wheel up: closer
   else if(focusLevel>0){focusLevel--;if(focusLevel==0)focus=null;}   // wheel down: back out (whichever field the mouse is over)
   else return true;
   refocus();return true;
  }catch(Throwable t){System.err.println("[BattleZoomMod] wheel "+t);return false;}
 }
 /** MulliganService (patched): the first mulligan is free in every Commander game - Forge only did that with 3+ players or
  *  Brawl (user 2026-11-15 "der erste mulligan muesste free sein in commander"). Rogue runs only set the game type. */
 public static boolean freeMulligan(forge.game.GameRules rules,forge.game.GameType variant){
  if(rules.hasAppliedVariant(variant)||rules.hasCommander())return true;
  forge.game.GameType t=rules.getGameType();
  return t==forge.game.GameType.Commander||t==forge.game.GameType.Oathbreaker||t==forge.game.GameType.TinyLeaders||t==forge.game.GameType.Brawl;
 }
 /** LondonMulligan.tuckCardsDuringMulligan (patched): pre-game option "all mulligans free" in 1v1 vs AI / LAN (user
  *  2026-11-15 "unendlich viele free mulligans als pre game option") - nobody puts cards on the bottom. The AI gets 5 free
  *  mulligans, then counts on normally, so a picky AI cannot mulligan forever. Runs keep Forge's rule. */
 private static Method allFree;
 /** LondonMulligan.mulliganDraw (patched): Forge asks "return 0 card(s) to the bottom" after every free mulligan and waits
  *  for OK (user 2026-11-16 "Mulligan war bei mir bugged") - with nothing to return the hand is simply kept as drawn */
 public static forge.game.card.CardCollectionView tuckVia(forge.game.player.PlayerController pc,forge.game.card.CardCollectionView hand,int n){
  if(n<=0)return forge.game.card.CardCollection.EMPTY;
  return pc.tuckCardsViaMulligan(hand,n);
 }
 public static int tuck(int n,forge.game.mulligan.AbstractMulligan m){
  try{
   if(n<=0)return n;
   if(allFree==null)allFree=Class.forName("forge.adventure.util.RogueLan").getMethod("allMulligansFree");   // RogueLan is installed by a later step
   if(!(Boolean)allFree.invoke(null))return n;
   var p=m==null?null:m.getPlayer();
   if(p!=null&&p.getController().isAI())return Math.min(n,Math.max(0,timesMulliganed(m)-5));
   return 0;
  }catch(Throwable t){return n;}
 }
 private static int timesMulliganed(forge.game.mulligan.AbstractMulligan m){
  try{Field f=forge.game.mulligan.AbstractMulligan.class.getDeclaredField("timesMulliganed");f.setAccessible(true);return f.getInt(m);}catch(ReflectiveOperationException e){return 99;}
 }
 public static void clearFocus(){if(focus==null&&focusLevel==0)return;focus=null;focusLevel=0;refocus();}
 static void refocus(){
  MatchScreen screen=MatchController.getView();if(screen==null)return;
  screen.revalidate();
  for(VPlayerPanel p:MatchScreen.getPlayerPanels().values()){p.revalidate();p.getField().revalidate();}
  com.badlogic.gdx.Gdx.graphics.requestRendering();
 }
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
 private static Method battleKey;
 public static boolean key(MatchScreen screen,int key){
  // keyboard shortcuts from Settings > Keys (ArenaBattleHudMod, installed by a later step: reflection)
  try{if(battleKey==null)battleKey=Class.forName("forge.adventure.util.ArenaBattleHudMod").getMethod("battleKey",int.class);
   if((Boolean)battleKey.invoke(null,key))return true;}catch(Throwable ignored){}
  if(key==Input.Keys.ESCAPE&&focused()&&multi()){clearFocus();return true;}   // Esc: all fields back to normal size
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
