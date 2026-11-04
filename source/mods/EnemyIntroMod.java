package forge.adventure.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.google.gson.Gson;
import forge.Forge;
import forge.Graphics;
import forge.adventure.character.EnemySprite;
import forge.assets.*;
import forge.toolbox.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Battle introduction: original-resolution art and a readable, styled encounter card. */
public final class EnemyIntroMod {
 private static final String MANIFEST="D:/forge/_mods/enemy-intro/portraits.json";
 static class Portrait{String sprite,path,url;int[] crop;}
 /** Missing artwork is fetched once from Scryfall in the background, like Forge does with card pictures (public
  *  packages ship no card illustrations); until it arrives the sprite avatar is shown. */
 private static final Set<String> FETCHING=Collections.synchronizedSet(new HashSet<>());
 static void fetchArt(String path,String url){
  if(path==null||url==null||!url.startsWith("https://cards.scryfall.io/")||!FETCHING.add(path))return;
  Thread t=new Thread(()->{
   try{
    java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();
    c.setRequestProperty("User-Agent","ForgeRogueCommanders/1.0");c.setConnectTimeout(15000);c.setReadTimeout(30000);
    if(c.getResponseCode()!=200)return;
    Path target=Path.of(path),tmp=Path.of(path+".part");Files.createDirectories(target.getParent());
    try(java.io.InputStream in=c.getInputStream()){Files.copy(in,tmp,StandardCopyOption.REPLACE_EXISTING);}
    Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);
   }catch(Exception e){System.err.println("[Enemy art] download: "+e);}
  },"enemy-art-download");
  t.setDaemon(true);t.start();
 }

 private static Map<String,Portrait> portraits;
 private static final Map<String,Texture> textures=new LinkedHashMap<>();
 private static final Color GOLD=Color.valueOf("e0b24f"),TEXT=Color.valueOf("f3ead8"),DIM=Color.valueOf("b3a895");
 static String introText(String name,String supplied,String authored,boolean flying){
  if(authored!=null&&!authored.isBlank())return authored;
  return supplied==null||supplied.isBlank()?"The stage of battle is set!":supplied;
 }
 static Portrait portrait(String sprite){
  if(portraits==null){portraits=new HashMap<>();try{
   for(Portrait p:new Gson().fromJson(Files.readString(Path.of(MANIFEST),StandardCharsets.UTF_8),Portrait[].class))portraits.put(p.sprite,p);
  }catch(Exception e){System.err.println("[Enemy intro] portrait manifest: "+e);}}
  return portraits.get(sprite);
 }
 static FImage image(EnemySprite enemy,FImage fallback){
  if(enemy==null)return fallback;Portrait p=portrait(enemy.getData().sprite);if(p==null)return fallback;
  try{
   Texture t=textures.get(p.path);if(t==null){
    if(!Files.exists(Path.of(p.path))){fetchArt(p.path,p.url);return fallback;}
    t=new Texture(Gdx.files.absolute(p.path));t.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
    if(textures.size()>=4){String first=textures.keySet().iterator().next();textures.remove(first).dispose();}
    textures.put(p.path,t);
   }
   TextureRegion region=p.crop!=null&&p.crop.length==4?new TextureRegion(t,p.crop[0],p.crop[1],p.crop[2],p.crop[3]):new TextureRegion(t);
   return new FTextureRegionImage(region);
  }catch(Exception e){System.err.println("[Enemy intro] original artwork: "+e);return fallback;}
 }
 /** Rogue Commanders duel label ("ROGUE COMMANDERS · ROUND 3/15[ · BOSS]") or null outside a Rogue run (read-only, reflection) */
 static String rogueLabel(){
  try{return (String)Class.forName("forge.adventure.scene.RogueCommandersScene").getMethod("duelLabel").invoke(null);}catch(Throwable t){return null;}
 }
 /** the 512px original portrait painted for Rogue Commanders (RogueCommandersScene.enemyPortrait), null if none */
 static Texture roguePortrait(String... names){
  try{var m=Class.forName("forge.adventure.scene.RogueCommandersScene").getDeclaredMethod("enemyPortrait",String.class);m.setAccessible(true);
   for(String n:names)if(n!=null){Object t=m.invoke(null,n);if(t instanceof Texture tex)return tex;}}catch(Throwable t){}
  return null;
 }
 public static FOptionPane create(DuelScene scene,String message,String title,FBufferedImage oldImage,Runnable next){
  EnemySprite enemy=scene.enemy;
  if(FONTS.size()>12){FONTS.values().forEach(com.badlogic.gdx.graphics.g2d.BitmapFont::dispose);FONTS.clear();}   // not while drawing
  String body=introText(title,message,enemy==null?null:enemy.getBossIntro(),enemy!=null&&enemy.getData().flying);
  if(body!=null)body=body.replaceAll("</?[A-Za-z]{1,6}>","").trim();   // Forge marks italics as <i>..<i> - never show raw tags (user 2026-10-17)
  String rogue=rogueLabel(),dot="  \u00b7  ";boolean boss;String traits;
  if(rogue!=null){   // Rogue Commanders: only every fifth round is a boss (user 2026-10-17: "der Boss sollte erst bei 5 kommen")
   boss=rogue.endsWith("BOSS");java.util.regex.Matcher r=java.util.regex.Pattern.compile("ROUND \\d+/\\d+").matcher(rogue);
   traits=(boss?"BOSS ENCOUNTER":"NEXT OPPONENT")+(r.find()?dot+r.group():"");
  }else{boss=enemy!=null&&enemy.getData().boss;traits=boss?"BOSS ENCOUNTER":"ENCOUNTER";}
  if(enemy!=null&&enemy.getData().flying)traits+=dot+"FLYING";
  Texture own=rogue==null?null:roguePortrait(title,enemy==null?null:enemy.getData().getName(),enemy==null?null:enemy.getData().name);
  return new IntroPane(new IntroBody(title,body,traits,boss,own,image(enemy,oldImage)),next);
 }
 static class IntroPane extends FOptionPane {
  IntroPane(IntroBody body,Runnable next){
   super(null,null,"",null,body,List.of("Begin battle"),0,result->{if(next!=null)next.run();});
   for(FDisplayObject child:getChildren())if(child instanceof forge.screens.match.views.VPrompt prompt)prompt.getBtnCancel().setVisible(false);
  }
  @Override protected boolean padAboveAndBelow(){return false;}
  @Override protected float layoutAndGetHeight(float width,float maxHeight){
   displayObj.setHeight(Math.max(80,Math.min(maxHeight-PADDING*2,Math.max(160,Math.min(width*.30f,Forge.getScreenHeight()*.40f)))));
   return super.layoutAndGetHeight(width,maxHeight);
  }
  @Override protected void drawBackground(Graphics g){g.fillRect(new Color(0,0,0,.22f),0,0,getWidth(),getHeight());}   // faint shade, board stays visible
  @Override public void drawOverlay(Graphics g){} // no legacy swipe-bar dots
 }
 /** crisp text (user 2026-10-17: "low res Schrift, falsche Schriftart"): the mod fonts (fantasy.ttf = Cinzel Decorative
  *  for titles, ui.ttf = Cinzel for text) rendered by FreeType at the exact pixel size, never upscaled */
 private static final Map<Integer,com.badlogic.gdx.graphics.g2d.BitmapFont> FONTS=new HashMap<>();
 static com.badlogic.gdx.graphics.g2d.BitmapFont font(boolean title,int px){
  px=Math.max(8,Math.min(160,px));int key=(title?1000:0)+px;var f=FONTS.get(key);if(f!=null)return f;
  String path="D:/forge/_mods/modern-hud/"+(title?"fantasy.ttf":"ui.ttf");if(!Files.exists(Path.of(path)))path="D:/forge/_mods/modern-hud/ui.ttf";
  if(!Files.exists(Path.of(path)))return null;
  try{var g=new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator(Gdx.files.absolute(path));
   var p=new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter();
   p.size=px;p.minFilter=Texture.TextureFilter.Linear;p.magFilter=Texture.TextureFilter.Linear;
   p.characters=com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.DEFAULT_CHARS+"\u00e4\u00f6\u00fc\u00c4\u00d6\u00dc\u00df\u2013\u2014\u00b7\u2026\u2019\u2018\u201c\u201d";
   f=g.generateFont(p);g.dispose();f.setUseIntegerPositions(false);
   FONTS.put(key,f);return f;
  }catch(Throwable t){System.err.println("[Enemy intro] font: "+t);return null;}
 }
 static final com.badlogic.gdx.graphics.g2d.GlyphLayout LAYOUT=new com.badlogic.gdx.graphics.g2d.GlyphLayout();
 static float measure(com.badlogic.gdx.graphics.g2d.BitmapFont f,String s,float wrap){
  LAYOUT.setText(f,s,Color.WHITE,wrap,Align.left,wrap>0);return wrap>0?LAYOUT.height:LAYOUT.width;
 }
 /** largest font <= px whose single line fits width w (h<=0) or whose text wrapped at w fits height h */
 static com.badlogic.gdx.graphics.g2d.BitmapFont fit(boolean title,int px,String s,float w,float h){
  var f=font(title,px);
  for(int i=0;f!=null&&i<4;i++){float m=h>0?measure(f,s,w):measure(f,s,0),lim=h>0?h:w;if(m<=lim||px<=9)break;
   px=Math.max(9,(int)(px*(h>0?(float)Math.sqrt(lim/m):lim/m)*.97f));f=font(title,px);}
  return f;
 }
 static void text(Graphics g,com.badlogic.gdx.graphics.g2d.BitmapFont f,String s,Color c,float x,float y,float w,boolean wrap){
  f.setColor(c.r,c.g,c.b,c.a*g.getfloatAlphaComposite());f.draw(g.getBatch(),s,g.adjustX(x),g.adjustY(y,0),w,Align.left,wrap);
 }
 static class IntroBody extends FDisplayObject {
  final String title,message,traits;final boolean boss;final Texture own;final FImage image;
  IntroBody(String title,String message,String traits,boolean boss,Texture own,FImage image){this.title=title;this.message=message;this.traits=traits;this.boss=boss;this.own=own;this.image=image;}
  @Override public void draw(Graphics g){
   // semi-transparent panel like the battle choice dialogs (user 2026-10-17), sized from the real screen: 720p .. 4K
   float w=getWidth(),h=getHeight(),scale=Math.max(.6f,Math.min(2.2f,Math.min(Forge.getScreenWidth()/1920f,Forge.getScreenHeight()/1080f)));
   float pad=Math.max(8,Math.min(w,h)*.05f),pw=Math.min(w-2*pad,1200*scale),ph=h-2*pad,x=(w-pw)/2,y=pad,rad=Math.max(6,10*scale);
   g.fillRoundRect(new Color(.03f,.04f,.07f,.62f),x,y,pw,ph,rad);
   g.drawRoundRect(Math.max(1,1.5f*scale),new Color(GOLD.r,GOLD.g,GOLD.b,boss?.85f:.5f),x,y,pw,ph,rad);
   float inner=Math.max(8,ph*.08f),is=Math.min(ph*.84f,pw*.36f),ix=x+inner,iy=y+(ph-is)/2;   // portrait ~84% of the box height
   if(own!=null){g.drawImage(own,ix,iy,is,is);g.drawRect(Math.max(1,scale),new Color(GOLD.r,GOLD.g,GOLD.b,.7f),ix,iy,is,is);}
   else if(image!=null){
    float s=Math.min(is/image.getWidth(),is/image.getHeight()),aw=image.getWidth()*s,ah=image.getHeight()*s;
    g.drawImage(image,ix+(is-aw)/2,iy+(is-ah)/2,aw,ah);
   }else is=0;
   float tx=ix+is+inner*1.2f,tw=x+pw-inner-tx,ty=y+inner;
   var small=fit(false,Math.round(Math.min(26*scale,ph*.075f)),traits,tw,0);var heading=fit(true,Math.round(Math.min(64*scale,ph*.19f)),title,tw,0);
   if(small==null||heading==null){   // mod fonts missing: Forge skin fonts
    g.drawText(traits,FSkinFont.get(14),DIM,tx,ty,tw,ph*.1f,false,Align.left,false);
    g.drawText(title,FSkinFont.get(28),GOLD,tx,ty+ph*.14f,tw,ph*.2f,false,Align.left,false);
    g.drawText(message,FSkinFont.get(18),TEXT,tx,ty+ph*.4f,tw,ph*.5f,true,Align.left,false);return;
   }
   text(g,small,traits,boss?GOLD:DIM,tx,ty,tw,false);ty+=small.getLineHeight()*1.25f;
   text(g,heading,title,GOLD,tx,ty,tw,false);ty+=heading.getLineHeight()*1.1f;
   g.fillRect(new Color(GOLD.r,GOLD.g,GOLD.b,.45f),tx,ty,tw*.6f,Math.max(1,scale));ty+=inner*.6f;
   var body=fit(false,Math.round(Math.min(34*scale,ph*.11f)),message,tw,Math.max(1,y+ph-inner-ty));
   text(g,body,message,TEXT,tx,ty,tw,true);
  }
 }
}
