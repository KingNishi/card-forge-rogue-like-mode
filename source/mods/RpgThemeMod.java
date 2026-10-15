package forge.adventure.util;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.github.tommyettinger.textra.TextraButton;
import forge.adventure.scene.UIScene;
public final class RpgThemeMod {
 private static final Texture[] textures=new Texture[4];
 private static final NinePatch[] frames=new NinePatch[4];
 private static BitmapFont menuFont;
 private static final java.util.Map<Button,Button.ButtonStyle> styled=new java.util.WeakHashMap<>();
 private static final java.util.Set<Actor> panels=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
 public static final Color TEXT=Color.valueOf("f3e7c9"),GOLD=Color.valueOf("e0b24f");
 /** fantasy font (Book Antiqua bold), falls back to the old UI font */
 public static String fontPath(){
  var f=com.badlogic.gdx.Gdx.files.absolute("D:/forge/_mods/modern-hud/fantasy.ttf");
  return f.exists()?f.path():"D:/forge/_mods/modern-hud/ui.ttf";
 }
 public static NinePatch panel(){return panel(0);}
 /** warm dark leather with a double gold rim and corner rivets. variant: 0 normal, 1 hover, 2 pressed, 3 disabled */
 public static NinePatch panel(int variant){
  if(frames[variant]!=null)return frames[variant];
  int s=48;Pixmap p=new Pixmap(s,s,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  float lift=variant==1?1.22f:variant==2?.78f:1f;
  for(int y=0;y<s;y++)for(int x=0;x<s;x++){
   float t=y/(float)(s-1),grain=(hash(x,y)-.5f)*7f;
   float r=(60-25*t+grain)*lift,g=(43-19*t+grain*.8f)*lift,b=(29-14*t+grain*.6f)*lift;
   if(variant==3){float l=(r+g+b)/3;r=g=b=l*.85f;}
   p.setColor(r/255f,g/255f,b/255f,1);p.drawPixel(x,y);
  }
  Color dark=Color.valueOf("120b06"),base=Color.valueOf(variant==3?"7d7465":"b08a3e"),light=Color.valueOf(variant==3?"a59a86":"ead08a"),shade=Color.valueOf(variant==3?"4f4a40":"6e5020");
  p.setColor(dark);p.drawRectangle(0,0,s,s);
  p.setColor(base);p.drawRectangle(1,1,s-2,s-2);
  p.setColor(light);p.drawLine(2,1,s-3,1);p.drawLine(1,2,1,s-3);
  p.setColor(shade);p.drawLine(2,s-2,s-3,s-2);p.drawLine(s-2,2,s-2,s-3);
  p.setColor(dark);p.drawRectangle(2,2,s-4,s-4);
  p.setColor(Color.valueOf(variant==3?"59524a":"775530"));p.drawRectangle(3,3,s-6,s-6);   // opaque inner rim (no see-through ring)
  for(int[] c:new int[][]{{4,4},{s-6,4},{4,s-6},{s-6,s-6}}){p.setColor(dark);p.fillRectangle(c[0]-1,c[1]-1,4,4);p.setColor(light);p.fillRectangle(c[0],c[1],2,2);}
  textures[variant]=new Texture(p);p.dispose();textures[variant].setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
  NinePatch n=new NinePatch(textures[variant],8,8,8,8);n.scale(.5f,.5f);frames[variant]=n;return n;
 }
 private static float hash(int x,int y){int n=x*374761393+y*668265263;n=(n^(n>>>13))*1274126177;return ((n^(n>>>16))&0xffff)/65535f;}
 public static void button(Actor a){
  if(!(a instanceof Button b))return;
  if(styled.get(b)==b.getStyle())return;
  Button.ButtonStyle s=b instanceof ImageButton i?new ImageButton.ImageButtonStyle(i.getStyle()):b instanceof TextraButton t?new TextButton.TextButtonStyle(t.getStyle()):new Button.ButtonStyle(b.getStyle());
  s.up=new NinePatchDrawable(panel(0));s.over=new NinePatchDrawable(panel(1));
  s.down=new NinePatchDrawable(panel(2));
  s.checked=s.over;s.checkedOver=s.over;s.checkedDown=s.down;
  s.disabled=new NinePatchDrawable(panel(3));if(s instanceof TextButton.TextButtonStyle t){t.fontColor=TEXT;t.disabledFontColor=Color.valueOf("a3977f");}b.setStyle(s);styled.put(b,s);
 }
 private static BitmapFont font(){
  if(menuFont==null){
   var generator=new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator(com.badlogic.gdx.Gdx.files.absolute(fontPath()));
   var parameters=new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter();parameters.size=64;parameters.minFilter=Texture.TextureFilter.Linear;parameters.magFilter=Texture.TextureFilter.Linear;
   BitmapFont bitmap=generator.generateFont(parameters);generator.dispose();
   menuFont=bitmap;menuFont.setUseIntegerPositions(false);
  }
  return menuFont;
 }
 private static void children(Group g){for(Actor a:g.getChildren()){
  button(a);
  if(a instanceof TextraButton button){
   button.getTextraLabel().setVisible(false);
   if(button.findActor("rpgMenuCaption")==null){
    Actor caption=new Actor(){public void draw(Batch batch,float alpha){
     BitmapFont f=font();f.getData().setScale(Math.min(9,button.getHeight()*.32f)/64);f.setColor(TEXT.r,TEXT.g,TEXT.b,alpha*(button.isDisabled()?.6f:1));
     String text=button.getTextraLabel().storedText;
     GlyphLayout layout=new GlyphLayout(f,text==null?"":text.replaceAll("\\[[^\\]]*\\]",""));float x=(button.getWidth()-layout.width)/2,y=(button.getHeight()+layout.height)/2;
     f.draw(batch,layout,getX()+x,getY()+y);
    }};
    caption.setName("rpgMenuCaption");caption.setTouchable(Touchable.disabled);button.addActor(caption);
   }
  }
  if(a instanceof Group child)children(child);
 }}
 public static void scene(UIScene scene){
  if(scene instanceof forge.adventure.scene.InventoryScene){inventory(scene);return;}
  if(!(scene instanceof forge.adventure.scene.StartScene))return;
  Group ui=scene.getUI();children(ui);
  Actor old=ui.findActor("rpgMenuFrame");if(old!=null)old.remove();
  float left=Float.MAX_VALUE,bottom=Float.MAX_VALUE,right=0,top=0;
  for(Actor a:ui.getChildren())if(a instanceof TextraButton&&a.isVisible()){
   left=Math.min(left,a.getX());bottom=Math.min(bottom,a.getY());right=Math.max(right,a.getX()+a.getWidth());top=Math.max(top,a.getY()+a.getHeight());
  }
  if(left==Float.MAX_VALUE)return;
  Actor backing=new Actor(){public void draw(Batch b,float alpha){Color old=new Color(b.getColor());b.setColor(1,1,1,alpha);panel().draw(b,getX(),getY(),getWidth(),getHeight());b.setColor(old);}};
  backing.setName("rpgMenuFrame");backing.setTouchable(Touchable.disabled);backing.setBounds(left-8,bottom-8,right-left+16,top-bottom+16);ui.addActorAt(Math.min(1,ui.getChildren().size),backing);
 }
 private static void inventory(UIScene scene){
  Group ui=scene.getUI();children(ui);
  for(Actor hint:scene.getUI().keyLabels)hint.setVisible(false);
  for(Actor a:ui.getChildren()){
   if(a instanceof Image&&Math.abs(a.getWidth()-129)<1&&Math.abs(a.getHeight()-243)<1)a.setVisible(false);
   if(a instanceof Window w&&panels.add(a)){Window.WindowStyle s=new Window.WindowStyle(w.getStyle());s.background=new NinePatchDrawable(panel());w.setStyle(s);}
   if(a instanceof ScrollPane pane&&panels.add(a)){ScrollPane.ScrollPaneStyle s=new ScrollPane.ScrollPaneStyle(pane.getStyle());s.background=new NinePatchDrawable(panel());pane.setStyle(s);}
  }
  if(ui.findActor("modernEquipmentPanel")!=null)return;
  Actor backing=new Actor(){public void draw(Batch b,float alpha){
   Color saved=new Color(b.getColor());b.setColor(1,1,1,alpha);panel().draw(b,8,19,129,243);
   var avatar=Current.player().avatar();b.setColor(.95f,.85f,.65f,alpha*.20f);b.draw(avatar,24,90,96,96);
   BitmapFont f=font();f.getData().setScale(7f/64);f.setColor(TEXT.r,TEXT.g,TEXT.b,alpha);f.draw(b,"EQUIPMENT",43,255);b.setColor(saved);
  }};
  backing.setName("modernEquipmentPanel");backing.setTouchable(Touchable.disabled);ui.addActorAt(Math.min(1,ui.getChildren().size),backing);
 }
}
