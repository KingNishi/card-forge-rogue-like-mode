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
 public static FOptionPane create(DuelScene scene,String message,String title,FBufferedImage oldImage,Runnable next){
  EnemySprite enemy=scene.enemy;
  String body=introText(title,message,enemy==null?null:enemy.getBossIntro(),enemy!=null&&enemy.getData().flying);
  String traits=enemy==null?"ENCOUNTER":(enemy.getData().boss?"BOSS ENCOUNTER":"ENCOUNTER")+(enemy.getData().flying?"  ·  FLYING":"");
  return new IntroPane(new IntroBody(title,body,traits,image(enemy,oldImage)),next);
 }
 static class IntroPane extends FOptionPane {
  IntroPane(IntroBody body,Runnable next){
   super(null,null,"",null,body,List.of("Begin battle"),0,result->{if(next!=null)next.run();});
   for(FDisplayObject child:getChildren())if(child instanceof forge.screens.match.views.VPrompt prompt)prompt.getBtnCancel().setVisible(false);
  }
  @Override protected boolean padAboveAndBelow(){return false;}
  @Override protected float layoutAndGetHeight(float width,float maxHeight){
   displayObj.setHeight(Math.max(80,Math.min(maxHeight-PADDING*2,Math.max(160,Math.min(width*.30f,Forge.getScreenHeight()*.38f)))));
   return super.layoutAndGetHeight(width,maxHeight);
  }
  @Override protected void drawBackground(Graphics g){
   g.fillRect(new Color(0,0,0,.55f),0,0,getWidth(),getHeight());
  }
  @Override public void drawOverlay(Graphics g){} // no legacy swipe-bar dots
 }
 static class IntroBody extends FDisplayObject {
  final String title,message,traits;final FImage image;
  IntroBody(String title,String message,String traits,FImage image){this.title=title;this.message=message;this.traits=traits;this.image=image;}
  @Override public void draw(Graphics g){
   float w=getWidth(),h=getHeight(),scale=Math.max(.8f,Math.min(2,Math.min(Forge.getScreenWidth()/1920f,Forge.getScreenHeight()/1080f)));
   float pad=Math.max(12,Math.min(w,h)*.055f),pw=Math.min(w-2*pad,1100*scale),ph=h-2*pad,x=(w-pw)/2,y=pad;
   g.fillRoundRect(Color.valueOf("0d1016"),x+3,y+5,pw,ph,10);
   g.fillGradientRect(Color.valueOf("1c2935"),Color.valueOf("101720"),true,x,y,pw,ph);
   g.drawRect(1.2f,GOLD,x,y,pw,ph);g.drawRect(.6f,Color.valueOf("43515f"),x+4,y+4,pw-8,ph-8);
   float inner=pad*.8f,iw=Math.min(ph-2*inner,pw*.33f),ix=x+inner,iy=y+inner;
   if(image!=null){
    // Fit original art without a tiny intermediate framebuffer or forced square crop.
    float imageScale=Math.min(1,Math.min(iw/image.getWidth(),(ph-2*inner)/image.getHeight())),aw=image.getWidth()*imageScale,ah=image.getHeight()*imageScale;
    g.drawImage(image,ix+(iw-aw)/2,iy+(ph-2*inner-ah)/2,aw,ah);
   }
   float tx=ix+iw+inner,tw=x+pw-inner-tx,ty=y+inner;
   int hs=Math.max(18,Math.min((int)(32*scale),(int)(h*.08f))),bs=Math.max(14,Math.min((int)(23*scale),(int)(h*.06f))),ss=Math.max(12,Math.min((int)(16*scale),(int)(h*.045f)));
   FSkinFont heading=FSkinFont.get(hs),body=FSkinFont.get(bs),small=FSkinFont.get(ss);
   // fit to the panel (Claude, 2026-10-15): the name stays on one line, traits on one line, the text inside the panel
   while(hs>10&&heading.getBounds(title).width>tw)heading=FSkinFont.get(--hs);
   while(ss>8&&small.getBounds(traits).width>tw)small=FSkinFont.get(--ss);
   float sh=small.getLineHeight(),hh=heading.getLineHeight()*(heading.getBounds(title).width>tw?2:1);
   float room=Math.max(1,y+ph-inner-(ty+sh+inner*.4f+hh+inner*.5f));
   while(bs>9&&body.getWrappedBounds(message,tw).height>room)body=FSkinFont.get(--bs);
   g.drawText(traits,small,DIM,tx,ty,tw,sh,false,Align.left,false);ty+=sh+inner*.4f;
   g.drawText(title,heading,GOLD,tx,ty,tw,hh,true,Align.left,false);ty+=hh+inner*.5f;
   g.drawText(message,body,TEXT,tx,ty,tw,Math.max(1,y+ph-inner-ty),true,Align.left,false);
  }
 }
}
