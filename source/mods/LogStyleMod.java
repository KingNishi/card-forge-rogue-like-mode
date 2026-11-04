package forge.screens.match.views;

import com.badlogic.gdx.graphics.Color;
import forge.Graphics;
import forge.assets.FSkinColor;
import forge.assets.FSkinFont;
import forge.toolbox.FDisplayObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The game log in the modern battle style (user 2026-10-16: "Log hat noch ein veraltetes Design"):
 *  dark see-through rows like the stack, light readable text. VLog's colour getters start with
 *  {@code FSkinColor c=LogStyleMod.<getter>();if(c!=null)return c;} (PatchLogStyle).
 *  Clearer log (user 2026-10-18: "uebersichtlicher mit klaren Trennung fuer die Runden"): every "Turn N (player)" row is
 *  a tall coloured band with a gold divider (blue = your turn, red = the opponent's), life changes are green / red and
 *  the rules text of triggers is dimmed. LogEntryDisplay.draw starts with
 *  {@code if(LogStyleMod.drawEntry(this,g,text))return;} and getMinHeight ends in {@code LogStyleMod.height(h,text)}. */
public final class LogStyleMod {
 private static FSkinColor row,alt,fore,dim,gain,loss;
 private static final Pattern TURN=Pattern.compile("Turn (\\d+) \\((.*)\\)"),LIFE=Pattern.compile("Life: .* (\\d+) > (\\d+)");
 private static final Color GOLD=new Color(0xe0b24fff),MINE=new Color(.10f,.20f,.34f,.95f),THEIRS=new Color(.30f,.08f,.10f,.95f),
  MINE_TEXT=Color.valueOf("bfe0ff"),THEIRS_TEXT=Color.valueOf("ffc2c2");
 /** the entry being drawn right now: getForeColor picks its colour from it */
 private static String current;
 private static boolean on(){try{return forge.Forge.isLandscapeMode();}catch(Throwable t){return false;}}
 public static FSkinColor getRowColor(){if(!on())return null;if(row==null)row=FSkinColor.getStandardColor(new Color(.06f,.07f,.10f,.86f));return row;}
 public static FSkinColor getAltRowColor(){if(!on())return null;if(alt==null)alt=FSkinColor.getStandardColor(new Color(.11f,.12f,.16f,.86f));return alt;}
 public static FSkinColor getForeColor(){
  if(!on())return null;
  if(fore==null){fore=FSkinColor.getStandardColor(Color.valueOf("ece6d8"));dim=FSkinColor.getStandardColor(Color.valueOf("a9b1c2"));
   gain=FSkinColor.getStandardColor(Color.valueOf("8fe39a"));loss=FSkinColor.getStandardColor(Color.valueOf("ff8f8f"));}
  String t=current;if(t==null)return fore;
  Matcher m=LIFE.matcher(t);if(m.find()){try{return Integer.parseInt(m.group(2))>=Integer.parseInt(m.group(1))?gain:loss;}catch(NumberFormatException e){return fore;}}
  if(t.startsWith("Whenever ")||t.startsWith("When ")||t.startsWith("At the beginning")||t.contains("[Zone Changer:"))return dim;   // rules text of a trigger
  return fore;
 }
 /** true = the turn band was drawn here, skip the original row */
 public static boolean drawEntry(FDisplayObject d,Graphics g,String text){
  current=text;
  if(!on()||text==null)return false;
  Matcher m=TURN.matcher(text.trim());if(!m.matches())return false;
  float w=d.getWidth(),h=d.getHeight();boolean mine=isHuman(m.group(2));
  g.fillRect(mine?MINE:THEIRS,0,0,w,h);
  g.fillRect(GOLD,0,0,w,Math.max(2f,h*.07f));   // divider between the turns
  g.drawText("TURN "+m.group(1)+"   ·   "+m.group(2).toUpperCase(),FSkinFont.forHeight(h*.42f),mine?MINE_TEXT:THEIRS_TEXT,0,h*.04f,w,h,false,1,true);
  return true;
 }
 /** turn bands are taller than normal rows */
 public static float height(float original,String text){
  return on()&&text!=null&&TURN.matcher(text.trim()).matches()?original*1.7f:original;
 }
 private static boolean isHuman(String name){
  try{var gv=forge.screens.match.MatchController.instance.getGameView();
   if(gv!=null)for(var p:gv.getPlayers())if(name.equals(p.getName()))return !p.isAI();}catch(Throwable ignored){}
  return false;
 }
}
