package forge.adventure.util;
import forge.Forge;
import forge.assets.*;
import forge.deck.*;
import forge.menu.*;
import forge.model.FModel;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.localinstance.achievements.AchievementBoosterMod;
import forge.screens.constructed.SleeveSelector;
import forge.player.GamePlayerUtil;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.*;
import java.lang.reflect.Method;
public final class CombatStyleMod {
 private static final ThreadLocal<Boolean> SELECTING=ThreadLocal.withInitial(()->false);
 private static boolean adventure(){return Forge.getCurrentScene() instanceof forge.adventure.scene.ForgeScene;}
 public static FSkinColor color(FSkinColor.Colors kind){
  if(!adventure())return FSkinColor.get(kind);
  String name=kind.name().replace("ADV_","");String hex=switch(name){
   case "CLR_THEME","CLR_THEME2"->"172332";
   case "CLR_BORDERS"->"60798f";
   case "CLR_ZEBRA","CLR_INACTIVE"->"233246";
   case "CLR_HOVER","CLR_ACTIVE"->"42687e";
   case "CLR_TEXT"->"e5edf4";
   case "CLR_PHASE_ACTIVE_ENABLED"->"60b9c9";
   case "CLR_PHASE_INACTIVE_ENABLED"->"718ca0";
   case "CLR_PHASE_INACTIVE_DISABLED","CLR_PHASE_ACTIVE_DISABLED"->"394858";
   default->null;
  };
  return hex==null?FSkinColor.get(kind):FSkinColor.getStandardColor(Color.valueOf(hex));
 }
 public static java.util.List<Integer> eligible(java.util.Collection<Integer> ids,int count){
  return ids.stream().sorted().limit(Math.max(1,count)).toList();
 }
 public static Map<Integer,TextureRegion> available(){
  Map<Integer,TextureRegion> all=FSkin.getSleeves();if(!SELECTING.get())return all;
  return unlocked(all,AchievementBoosterMod.unlockedSleeveCount());
 }
 private static Map<Integer,TextureRegion> unlockedCache;private static Object unlockedFrom;private static int unlockedCount=-1;
 /** per card back drawn, so cached (2026-11-05: Android frame time) */
 private static Map<Integer,TextureRegion> unlocked(Map<Integer,TextureRegion> all,int count){
  if(all==unlockedFrom&&count==unlockedCount&&unlockedCache!=null)return unlockedCache;
  unlockedCache=unlocked0(all,count);unlockedFrom=all;unlockedCount=count;return unlockedCache;
 }
 private static Map<Integer,TextureRegion> unlocked0(Map<Integer,TextureRegion> all,int count){
  Map<Integer,TextureRegion> result=new LinkedHashMap<>();for(Integer id:eligible(all.keySet(),count))result.put(id,all.get(id));return result;
 }
 public static void label(SleeveSelector selector,FImage image,int index){
  if(SELECTING.get()&&index<0)return;
  try{Method m=SleeveSelector.class.getDeclaredMethod("addSleevesLabel",FImage.class,int.class);m.setAccessible(true);m.invoke(selector,image,index);}catch(Exception e){throw new IllegalStateException(e);}
 }
 public static void showSleeves(){
  Map<Integer,TextureRegion> choices=unlocked(FSkin.getSleeves(),AchievementBoosterMod.unlockedSleeveCount());
  if(choices.isEmpty())return;int current=GamePlayerUtil.getGuiPlayer().getSleeveIndex();if(!choices.containsKey(current))current=choices.keySet().iterator().next();
  SELECTING.set(true);
  try{SleeveSelector.show("Adventure — "+choices.size()+" unlocked; earn achievements for more",current,Collections.emptyList(),selected->{
   if(!choices.containsKey(selected))return;
   GamePlayerUtil.getGuiPlayer().setSleeveArtKey(null);GamePlayerUtil.getGuiPlayer().setSleeveIndex(selected);
   String[] old=FModel.getPreferences().getPref(FPref.UI_SLEEVES).split(",");String opponent=old.length>1?old[1]:"1";
   FModel.getPreferences().setPref(FPref.UI_SLEEVES,selected+","+opponent);FModel.getPreferences().save();
  });}finally{SELECTING.set(false);}
 }
 public static void deckMenu(FDropDownMenu menu,FDeckEditor editor){
  if(editor instanceof forge.adventure.scene.AdventureDeckEditor)menu.addItem(new FMenuItem("Card sleeves ("+Math.min(FSkin.getSleeves().size(),AchievementBoosterMod.unlockedSleeveCount())+" unlocked)",event->showSleeves()));
 }
 public static void combatMenu(FDropDownMenu menu){
  if(adventure()){
   menu.addItem(new FMenuItem("Card sleeves — achievement unlocks",event->showSleeves()));
   menu.addItem(new FMenuItem("Card placement animation: "+FModel.getPreferences().getPref(FPref.UI_CARD_PLAY_ANIMATION_STYLE),event->{
    java.util.List<String> options=java.util.Arrays.asList("Off","Slide","Rotate","Popup");
    forge.toolbox.FOptionPane.showOptionDialog("Choose the card placement effect.","Card placement animation",null,options,index->{
     if(index==null||index<0||index>=options.size())return;
     FModel.getPreferences().setPref(FPref.UI_CARD_PLAY_ANIMATION_STYLE,options.get(index));FModel.getPreferences().save();
    });
   }));
  }
 }
 public static FImage playerSleeve(FImage original,forge.game.player.PlayerView player){
  if(!adventure()||player==null||player.getAvatarIndex()!=90000)return original;
  Map<Integer,TextureRegion> choices=unlocked(FSkin.getSleeves(),AchievementBoosterMod.unlockedSleeveCount());
  TextureRegion selected=choices.get(GamePlayerUtil.getGuiPlayer().getSleeveIndex());
  if(selected==null&&!choices.isEmpty())selected=choices.values().iterator().next();
  return selected==null?original:new FTextureRegionImage(selected);
 }
}
