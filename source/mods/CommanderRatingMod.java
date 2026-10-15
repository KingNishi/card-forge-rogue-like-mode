package forge.adventure.util;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.Graphics;
import forge.assets.FImage;
import forge.assets.FSkinFont;
import forge.deck.FDeckEditor;
import forge.item.PaperCard;
import forge.itemmanager.ItemManager;
import forge.screens.FScreen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Commander rating instead of the draft rating in card views: EDHREC rank (Scryfall bulk data, built by
 * _mods/commander-rating/build_ratings.py) mapped to 0-99, so Forge's S/A/B/C/D badges keep their meaning.
 * The badge is small and sits in the top-left corner of the art, so the card text stays readable.
 * Card prices are hidden everywhere except in the shop (selling).
 */
public final class CommanderRatingMod {
 /** the Forge folder: where the Forge JAR lies (fallback: working directory); -Dforge.root overrides it */
 static String root(){
  String r=System.getProperty("forge.root");
  if(r==null)try{java.io.File jar=new java.io.File(CommanderRatingMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
   if(jar.isFile()&&new java.io.File(jar.getParentFile(),"res").isDirectory())r=jar.getParent();}catch(Exception ignored){}
  if(r==null)r=new java.io.File("").getAbsolutePath();
  r=r.replace('\\','/');return r.endsWith("/")?r:r+"/";
 }
 static final String FILE=root()+"_mods/commander-rating/ratings.tsv";
 private static Map<String,Integer> ratings;

 static Map<String,Integer> ratings(){
  if(ratings==null){
   ratings=new HashMap<>();
   try{for(String line:Files.readAllLines(Paths.get(FILE),StandardCharsets.UTF_8)){
    String[] p=line.split("\t");if(p.length>=2)ratings.put(p[0],Integer.parseInt(p[1].trim()));}}
   catch(Exception e){System.err.println("[CommanderRating] "+e);}
  }
  return ratings;
 }
 /** replaces CardRanker.getRawScore: 0-99, higher is better */
 public static double score(PaperCard pc){
  if(pc==null)return 0;
  Integer r=ratings().get(pc.getName());
  if(r==null&&pc.getName().contains(" // "))r=ratings().get(pc.getName().split(" // ")[0]);
  return r==null?0:r;
 }
 /** card box from the original badge geometry (size = w/2, x2 = x + size/2, y2 = y + 0.9*size + 1) */
 private static float s(float w){return w*.2f;}
 private static float bx(float x,float w){return x+w*.07f;}
 private static float by(float y,float w){return y+w*1.396f*.115f;}
 public static void badge(Graphics g,FImage image,float x2,float y2,float size,float size2){
  float w=size*2,x=x2-size/2,y=y2-1-size*.9f;
  g.drawImage(image,bx(x,w),by(y,w),s(w),s(w));
 }
 public static void badgeText(Graphics g,String text,FSkinFont font,Color color,float x,float y,float w,float h,boolean wrap,int align,boolean center){
  float s=s(w);
  g.drawText(text,FSkinFont.forHeight(s*.4f),Color.WHITE,bx(x,w),by(y,w),s,s,false,Align.center,true);
 }
 /** prices only where they matter: the shop's sell view */
 public static boolean showPrice(ItemManager<?> manager){
  if(manager==null||!manager.showPriceInfo())return false;
  FScreen screen=Forge.getCurrentScreen();
  return screen instanceof FDeckEditor editor&&editor.getEditorConfig()!=null&&editor.getEditorConfig().getClass().getSimpleName().equals("ShopConfig");
 }
}
