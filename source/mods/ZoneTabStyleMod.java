package forge.screens.match.views;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.Graphics;
import forge.assets.FSkinFont;
import forge.game.zone.ZoneType;
import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Zone tabs of the battle HUD (hand, graveyard, library, flashback, exile, extra zones) in one modern style: every tab the
 * same dark tile, a crisp white icon (game-icons.net, CC BY 3.0, _mods/zone-icons) and a clear count; the open tab gold,
 * empty zones dimmed. Forge drew old pixel icons and only some tabs with a box. VPlayerPanel$InfoTab.draw starts with
 * "if(ZoneTabStyleMod.draw(this,g))return;" (PatchZoneTabStyle); the mana pool tab and portrait mode keep Forge's drawing.
 */
public final class ZoneTabStyleMod {
 private ZoneTabStyleMod(){}
 static final Color BG=Color.valueOf("111722"),BG_HOVER=Color.valueOf("17202e"),BG_OPEN=Color.valueOf("1c2738"),
  GOLD=Color.valueOf("e0b24f"),TEXT=Color.valueOf("f4efe4"),DIM=Color.valueOf("8a93a2");
 /** one clear colour per zone: hand gold, graveyard violet, library blue, flashback orange, exile teal, others light gold */
 static Color zoneColor(String key){
  return Color.valueOf(switch(key){case "hand"->"f2c14e";case "graveyard"->"b07cf0";case "library"->"5aa7ff";
   case "flashback"->"ff8a3d";case "exile"->"3fd3c6";case "command"->"f2c14e";default->"e8d9a8";});
 }
 /** opaque blend of the tile colour towards c (translucent round rects show seams in Forge) */
 static Color mix(Color base,Color c,float t){return new Color(base.r+(c.r-base.r)*t,base.g+(c.g-base.g)*t,base.b+(c.b-base.b)*t,1);}
 static final String ROOT=root();
 private static final Map<String,Texture> icons=new HashMap<>();private static final Set<String> missing=new HashSet<>();
 private static Field zoneField;

 /** the Forge folder: where the Forge JAR lies (fallback: working directory) */
 static String root(){
  String r=System.getProperty("forge.root");
  if(r==null)try{File jar=new File(ZoneTabStyleMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
   if(jar.isFile()&&new File(jar.getParentFile(),"res").isDirectory())r=jar.getParent();}catch(Exception ignored){}
  if(r==null)r=new File("").getAbsolutePath();
  r=r.replace('\\','/');return r.endsWith("/")?r:r+"/";
 }

 static Texture icon(String key){
  Texture t=icons.get(key);if(t!=null||missing.contains(key))return t;
  try{var f=Gdx.files.absolute(ROOT+"_mods/zone-icons/"+key+".png");
   if(f.exists()){t=new Texture(f,true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);icons.put(key,t);return t;}
  }catch(Exception e){System.err.println("[ZoneTabStyle] "+e);}
  missing.add(key);return null;
 }

 static String key(VPlayerPanel.InfoTab tab){
  if(tab instanceof VPlayerPanel.InfoTabExtra)return "extra";
  if(tab instanceof VPlayerPanel.InfoTabZone)try{
   if(zoneField==null){zoneField=VPlayerPanel.InfoTabZone.class.getDeclaredField("zoneType");zoneField.setAccessible(true);}
   ZoneType z=(ZoneType)zoneField.get(tab);
   return switch(z){case Hand->"hand";case Graveyard->"graveyard";case Library->"library";case Flashback->"flashback";
    case Exile->"exile";case Command->"command";default->"extra";};
  }catch(ReflectiveOperationException e){return null;}
  return null;                                     // mana pool tab: Forge's own drawing
 }

 /** mana pool tile in the same style: a small five-colour mana symbol instead of Forge's "M" (user 2026-10-16) */
 static boolean drawMana(VPlayerPanel.InfoTab tab,Graphics g,float w,float h){
  String value=tab.value==null?"":tab.value;boolean empty=value.isEmpty()||"0".equals(value),open=tab.isSelected();
  Color base=open?BG_OPEN:tab.isHovered()?BG_HOVER:BG;float r=Math.min(h*.22f,9),lw=Math.max(1.5f,h*.05f);
  // named, and glowing while mana floats in the pool (tester 2026-10-19: "Wo ist mana in pool??")
  if(!empty){float pulse=.5f+.5f*(float)Math.sin(System.currentTimeMillis()/220.0);g.fillRoundRect(new Color(GOLD.r,GOLD.g,GOLD.b,.25f+.3f*pulse),-3*lw,-3*lw,w+6*lw,h+6*lw,r+3*lw);}
  g.drawText("Mana pool",FSkinFont.forHeight(h*.44f),empty?Color.valueOf("c9c1b0"):GOLD,-w*1.7f,0,w*1.7f-h*.22f,h,false,Align.right,true);
  g.fillRoundRect(mix(base,GOLD,open?.8f:.38f),0,0,w,h,r);g.fillRoundRect(base,lw,lw,w-2*lw,h-2*lw,Math.max(0,r-lw));
  float bs=h-4*lw,cx=2*lw+bs/2,cy=h/2,rad=bs*.40f;
  Color[] pips={Color.valueOf("f4efd2"),Color.valueOf("3d8fd6"),Color.valueOf("5a4d55"),Color.valueOf("e0483c"),Color.valueOf("2f9a5a")};
  g.fillCircle(Color.valueOf("0b0d12"),cx,cy,rad+lw);
  for(int k=0;k<5;k++){double a0=Math.toRadians(-90+k*72),a1=Math.toRadians(-90+(k+1)*72);
   for(int i=0;i<6;i++){double t0=a0+(a1-a0)*i/6,t1=a0+(a1-a0)*(i+1)/6;
    g.fillTriangle(empty?mix(base,pips[k],.55f):pips[k],cx,cy,cx+(float)Math.cos(t0)*rad,cy+(float)Math.sin(t0)*rad,cx+(float)Math.cos(t1)*rad,cy+(float)Math.sin(t1)*rad);}}
  g.fillCircle(Color.valueOf("0b0d12"),cx,cy,rad*.30f);
  float tx=2*lw+bs+h*.08f;
  g.drawText(value,FSkinFont.forHeight(h*.66f),empty?DIM:TEXT,tx,0,w-tx-h*.08f,h,false,Align.center,true);
  return true;
 }
 public static boolean draw(VPlayerPanel.InfoTab tab,Graphics g){
  try{
   if(!Forge.isLandscapeMode())return false;
   float w=tab.getWidth(),h=tab.getHeight();
   if(h<8||w<h*1.15f)return false;
   String key=key(tab);   // zone tabs first: InfoTabZone is itself an InfoTabSingleDisplay
   if(key==null)return tab instanceof VPlayerPanel.InfoTabSingleDisplay&&drawMana(tab,g,w,h);   // only the mana pool: five-colour symbol
   Texture icon=icon(key);if(icon==null)return false;
   String value=tab.value==null?"":tab.value;
   boolean open=tab.isSelected(),empty=value.isEmpty()||"0".equals(value);
   Color zc=zoneColor(key),base=open?BG_OPEN:tab.isHovered()?BG_HOVER:BG;
   float r=Math.min(h*.22f,9),lw=Math.max(1.5f,h*.05f);
   // tile with a coloured rim (stronger when this zone is open)
   g.fillRoundRect(open?zc:mix(base,zc,.38f),0,0,w,h,r);
   g.fillRoundRect(base,lw,lw,w-2*lw,h-2*lw,Math.max(0,r-lw));
   // coloured badge with the icon
   float bs=h-4*lw,bx=2*lw,by=2*lw;
   g.fillRoundRect(mix(base,zc,empty?.16f:.30f),bx,by,bs,bs,Math.max(0,r-lw));
   float size=bs*.78f,ix=bx+(bs-size)/2,iy=by+(bs-size)/2;
   Color tint=empty?mix(base,zc,.55f):zc;
   g.setColorRGBA(tint.r,tint.g,tint.b,1f);g.drawImage(icon,ix,iy,size,size);g.resetColorRGBA(1f);
   float tx=bx+bs+h*.08f;
   g.drawText(value,FSkinFont.forHeight(h*.66f),empty?DIM:TEXT,tx,0,w-tx-h*.08f,h,false,Align.center,true);
   return true;
  }catch(Throwable t){return false;}
 }
}
