package forge.adventure.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraButton;
import forge.adventure.stage.GameHUD;
import forge.adventure.player.AdventurePlayer;
import forge.assets.ImageCache;
import forge.deck.*;
import forge.item.PaperCard;
import java.util.*;

/** Adventure-only overlay. Uses existing groups and buttons, including their fade/input rules. */
public final class ModernHudMod extends Actor {
 private final GameHUD hud;
 private final BitmapFont font;
 private final Texture panelTexture;
 private final NinePatch panel;
 private float elapsed=1;
 private static final java.nio.file.Path SETTINGS=java.nio.file.Paths.get(System.getProperty("forge.hud.layout","D:/forge/_mods/modern-hud/layout.json"));
 private final HudCustomization preferences=HudCustomization.load(SETTINGS);
 private final Map<String,float[]> regions=new LinkedHashMap<>();
 private boolean editing,previousFreeze;
 private forge.adventure.stage.GameStage pausedStage;
 private String selection,dragging,status="Drag a panel, then adjust its opacity.";
 private float dragX,dragY;
 private boolean sliding;
 private Actor editor;
 private float[] rect(String id,float x,float y,float w,float h){
  float[] r=preferences.bounds(id,x,y,w,h,hud.ui.getWidth(),hud.ui.getHeight());regions.put(id,r);return r;
 }
 private void apply(String id,Actor a,float x,float y,float w,float h){
  if(a==null)return;float[] r=rect(id,x,y,w,h);a.setBounds(r[0],r[1],r[2],r[3]);a.getColor().a=preferences.alpha(id);
 }
 private static ModernHudMod instance(GameHUD hud){return hud.ui.findActor("modernAdventureHud");}

 private int outside,total,deckCount;
 private java.util.List<PaperCard> commanders=Collections.emptyList();
 private String commanderName="No commander",deckName="";
 private Texture[] commanderImages=new Texture[0];
 // fantasy palette: parchment text, aged-gold labels, amber accents (TEAL keeps its name for the accent colour)
 private static final Color TEXT=Color.valueOf("f3e7c9"),MUTED=Color.valueOf("c8b089"),GOLD=Color.valueOf("f2cf63"),TEAL=Color.valueOf("e0b24f");
 private Texture heart,coin,crystal;
 /** small painted stat icons: heart (life), coin (gold), crystal (shards) */
 private static Texture icon(int kind){
  int s=32;Pixmap p=new Pixmap(s,s,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  if(kind==0){p.setColor(Color.valueOf("3b0d0f"));p.fillCircle(10,12,8);p.fillCircle(21,12,8);p.fillTriangle(3,15,28,15,16,29);
   p.setColor(Color.valueOf("c8323a"));p.fillCircle(10,12,6);p.fillCircle(21,12,6);p.fillTriangle(5,15,26,15,16,26);
   p.setColor(Color.valueOf("f28a8a"));p.fillCircle(9,10,2);}
  else if(kind==1){p.setColor(Color.valueOf("5a3d0c"));p.fillCircle(16,16,14);p.setColor(Color.valueOf("d9a632"));p.fillCircle(16,16,12);
   p.setColor(Color.valueOf("f4d77a"));p.fillCircle(16,16,8);p.setColor(Color.valueOf("b5841f"));p.fillRectangle(14,10,4,12);p.setColor(Color.valueOf("fff1c2"));p.fillCircle(11,11,2);}
  else{p.setColor(Color.valueOf("1a1f4a"));p.fillTriangle(16,1,4,13,28,13);p.fillTriangle(4,13,28,13,16,31);
   p.setColor(Color.valueOf("6f7ee6"));p.fillTriangle(16,4,7,13,25,13);p.fillTriangle(7,14,25,14,16,28);
   p.setColor(Color.valueOf("c9d2ff"));p.fillTriangle(16,4,9,13,16,13);}
  Texture t=new Texture(p);p.dispose();t.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);return t;
 }
 public static void install(GameHUD hud){
  if(hud.ui.findActor("modernAdventureHud")!=null)return;
  ModernHudMod overlay=new ModernHudMod(hud);
  Actor original=hud.ui.findActor("gamehud");
  Group parent=original.getParent();parent.addActorAt(0,overlay);
  for(String name:new String[]{"gamehud","blank","avatarborder","lifePoints","money","shards"}){
   Actor a=hud.ui.findActor(name);if(a!=null)a.setVisible(false);
  }
  layout(hud);
  Actor map=hud.ui.findActor("map"),border=hud.ui.findActor("mapborder");
  if(border!=null)border.setVisible(false);
  if(map!=null){
   Actor frame=new Actor(){@Override public void draw(Batch batch,float alpha){
    Color saved=new Color(batch.getColor());float x=map.getX()-1,y=map.getY()-1,w=map.getWidth()+2,h=map.getHeight()+2;
    overlay.box(batch,x,y,w,1,TEAL,alpha*map.getColor().a*.65f);overlay.box(batch,x,y+h-1,w,1,TEAL,alpha*map.getColor().a*.65f);
    overlay.box(batch,x,y,1,h,TEAL,alpha*map.getColor().a*.65f);overlay.box(batch,x+w-1,y,1,h,TEAL,alpha*map.getColor().a*.65f);batch.setColor(saved);
   }};
   frame.setTouchable(Touchable.disabled);map.getParent().addActor(frame);
  }
  String[] names={"menu","bookmark","deck","inventory","logbook","exittoworldmap","openmap"};
  String[] labels={"Menu","Bookmark","Deck","Inventory","Quests","Exit","Map / Zoom"};
  String[] shortcuts={"Esc","B","E","I","Q","F4","M"};
  for(int i=0;i<names.length;i++){
   Actor a=hud.ui.findActor(names[i]);
   if(a instanceof TextraButton button){
    RpgThemeMod.button(button);button.setText("");
    final String label=labels[i],shortcut=shortcuts[i];
    Actor caption=new Actor(){@Override public void draw(Batch batch,float alpha){
     float a=alpha*(button.isDisabled()?.72f:1f);
     if(button.getHeight()<16){
      overlay.text(batch,"Map / Zoom",getX()+3,getY()+10,5.7f,TEXT,button.getWidth()-14,false,a);
      overlay.text(batch,shortcut,getX()+button.getWidth()-7,getY()+9,4,MUTED,5,false,a);
     }else{
      overlay.text(batch,label,getX()+3,getY()+button.getHeight()-4,label.length()>7?4.8f:5.7f,TEXT,button.getWidth()-6,false,a);
      overlay.text(batch,shortcut,getX()+3,getY()+6,3.8f,MUTED,button.getWidth()-6,false,a);
     }
    }};
    caption.setTouchable(Touchable.disabled);button.addActor(caption);
   }
  }
  overlay.editor=new Actor(){@Override public void draw(Batch batch,float alpha){overlay.drawEditor(batch);}};
  overlay.editor.setName("hudCustomizationEditor");overlay.editor.setTouchable(Touchable.disabled);hud.ui.addActor(overlay.editor);
 }
 private ModernHudMod(GameHUD hud){
  this.hud=hud;setName("modernAdventureHud");setTouchable(Touchable.disabled);
  FreeTypeFontGenerator generator=new FreeTypeFontGenerator(Gdx.files.absolute(RpgThemeMod.fontPath()));
  FreeTypeFontGenerator.FreeTypeFontParameter p=new FreeTypeFontGenerator.FreeTypeFontParameter();
  p.size=48;p.minFilter=Texture.TextureFilter.Linear;p.magFilter=Texture.TextureFilter.Linear;
  font=generator.generateFont(p);generator.dispose();font.setUseIntegerPositions(false);
  Pixmap image=new Pixmap(40,40,Pixmap.Format.RGBA8888);
  image.setColor(Color.WHITE);image.fillRectangle(6,0,28,40);image.fillRectangle(0,6,40,28);
  image.fillCircle(6,6,6);image.fillCircle(33,6,6);image.fillCircle(6,33,6);image.fillCircle(33,33,6);
  panelTexture=new Texture(image);image.dispose();panelTexture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
  panel=RpgThemeMod.panel();setSize(hud.ui.getWidth(),hud.ui.getHeight());
  heart=icon(0);coin=icon(1);crystal=icon(2);
 }
 /** Subtract copies by card name, so alternate printings do not inflate the free collection. */
 public static int countOutside(CardPool owned,Deck deck){
  Map<String,Integer> used=new HashMap<>();
  if(deck!=null)for(Map.Entry<DeckSection,CardPool> section:deck)
   for(Map.Entry<PaperCard,Integer> entry:section.getValue())used.merge(entry.getKey().getName(),entry.getValue(),Integer::sum);
  Map<String,Integer> collected=new HashMap<>();
  for(Map.Entry<PaperCard,Integer> entry:owned)collected.merge(entry.getKey().getName(),entry.getValue(),Integer::sum);
  int result=0;for(Map.Entry<String,Integer> entry:collected.entrySet())result+=Math.max(0,entry.getValue()-used.getOrDefault(entry.getKey(),0));
  return result;
 }
 @Override public void act(float delta){
  super.act(delta);elapsed+=delta;if(elapsed<.5f)return;elapsed=0;
  AdventurePlayer player=Current.player();if(player==null)return;
  Deck deck=player.getSelectedDeck();total=player.getCards().countAll();outside=countOutside(player.getCards(),deck);
  deckCount=0;if(deck!=null)for(Map.Entry<DeckSection,CardPool> section:deck)deckCount+=section.getValue().countAll();
  commanders=deck==null?Collections.emptyList():deck.getCommanders();
  commanderName=commanders.isEmpty()?"No commander":String.join(" / ",commanders.stream().map(PaperCard::getName).toList());
  commanderImages=new Texture[Math.min(commanders.size(),2)];
  for(int i=0;i<commanderImages.length;i++)commanderImages[i]=ImageCache.getInstance().getImage(commanders.get(i));
  deckName=deck==null?"":deck.getName();
 }
 private void text(Batch b,String value,float x,float y,float size,Color color,float width,boolean wrap,float alpha){
  font.getData().setScale(size/48f);font.setColor(color.r,color.g,color.b,alpha);
  font.draw(b,value,x,y,width,Align.left,wrap);
 }
 private void box(Batch b,float x,float y,float w,float h,Color color,float alpha){
  b.setColor(color.r,color.g,color.b,alpha);
  if(h<8||w<8)b.draw(panelTexture,x,y,w,h,.5f,.5f,.525f,.525f);else panel.draw(b,x,y,w,h);
 }
 public static void layout(GameHUD hud){
  ModernHudMod m=instance(hud);if(m==null)return;float h=hud.ui.getHeight();
  Actor old=hud.ui.findActor("gamehud");if(old!=null)old.setBounds(0,0,0,0);
  m.apply("Minimap",hud.ui.findActor("map"),12,h-64,58,58);
  Actor border=hud.ui.findActor("mapborder");if(border!=null)border.setVisible(false);
  float[] portrait=m.rect("Portrait",hud.ui.getWidth()-35,h-35,32,32);
  Actor avatar=hud.ui.findActor("avatar");if(avatar!=null){avatar.setBounds(portrait[0]+3,portrait[1]+3,26,26);avatar.getColor().a=m.preferences.alpha("Portrait");}
  m.apply("Keys",hud.ui.findActor("keyCollection"),8,h-177,70,14);
  m.apply("Map button",hud.ui.findActor("openmap"),12,h-78,58,13);
  String[] names={"menu","bookmark","deck","inventory","logbook","exittoworldmap"};
  String[] labels={"Menu","Bookmark","Deck","Inventory","Quests","Exit"};
  for(int i=0;i<names.length;i++)m.apply(labels[i],hud.ui.findActor(names[i]),8+(i%2)*35,h-197-(i/2)*18,33,16);
  m.rect("Commander",8,h-132,70,50);m.rect("Collection",8,h-159,70,24);
  m.rect("Stats",96,6,Math.min(210,hud.ui.getWidth()-103),24);
  for(Object hint:hud.ui.keyLabels)((Actor)hint).setVisible(false);
  for(String name:new String[]{"openmap","menu","bookmark","deck","inventory","logbook","exittoworldmap"}){
   Actor a=hud.ui.findActor(name);if(a instanceof TextraButton button)button.setText("");
  }
  layoutAbilities(hud);
  m.extraActors();if(m.editor!=null)m.editor.toFront();
 }
 private void extraActors(){
  for(String name:new String[]{"notificationPane","enemyCounterBackground","enemyCounterText"}){
   try{
    java.lang.reflect.Field f=GameHUD.class.getDeclaredField(name);f.setAccessible(true);Actor a=(Actor)f.get(hud);if(a==null)continue;
    String id=name.equals("notificationPane")?"Notifications":"Enemy count";
    if(name.equals("enemyCounterText"))continue;
    if(!a.isVisible()){regions.remove(id);continue;}
    float[] r=regions.get(id);if(r==null)r=rect(id,a.getX(),a.getY(),a.getWidth(),a.getHeight());
    float[] moved=preferences.bounds(id,r[0],r[1],a.getWidth(),a.getHeight(),hud.ui.getWidth(),hud.ui.getHeight());
    if(name.equals("enemyCounterBackground")){
     java.lang.reflect.Field tf=GameHUD.class.getDeclaredField("enemyCounterText");tf.setAccessible(true);Actor label=(Actor)tf.get(hud);
     if(label!=null){label.moveBy(moved[0]-a.getX(),moved[1]-a.getY());label.getColor().a=preferences.alpha(id);}
    }
    a.setPosition(moved[0],moved[1]);a.getColor().a=preferences.alpha(id);regions.put(id,moved);
   }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
  }
 }
 public static void layoutAbilities(GameHUD hud){
  ModernHudMod m=instance(hud);if(m==null)return;
  try{
   java.lang.reflect.Field f=GameHUD.class.getDeclaredField("abilityButtonMap");f.setAccessible(true);
   com.badlogic.gdx.utils.Array<?> buttons=(com.badlogic.gdx.utils.Array<?>)f.get(hud);if(buttons==null)return;
   for(int i=0;i<buttons.size;i++){RpgThemeMod.button((Actor)buttons.get(i));m.apply("Ability "+(i+1),(Actor)buttons.get(i),hud.ui.getWidth()-104+i*50,36,45,35);}
  }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 @Override public void draw(Batch b,float parentAlpha){
  AdventurePlayer player=Current.player();if(player==null)return;
  Color before=new Color(b.getColor());float alpha=parentAlpha*getColor().a;
  float[] portrait=rect("Portrait",hud.ui.getWidth()-35,hud.ui.getHeight()-35,32,32);
  box(b,portrait[0],portrait[1],32,32,Color.WHITE,alpha*preferences.alpha("Portrait"));
  float[] commander=rect("Commander",8,hud.ui.getHeight()-132,70,50);
  float top=commander[1]+50,x=commander[0],parent=alpha;alpha=parent*preferences.alpha("Commander");
  box(b,x,commander[1],70,50,Color.WHITE,alpha);
  text(b,player.getName(),x+5,top-6,6,TEXT,60,true,alpha);
  text(b,"Commander",x+5,top-15,4f,TEAL,60,false,alpha);
  int n=Math.min(commanders.size(),2);float cardWidth=17,cardHeight=cardWidth*1.4f;
  for(int i=0;i<n;i++){
   Texture texture=commanderImages.length>i?commanderImages[i]:null;
   if(texture!=null){b.setColor(1,1,1,alpha);b.draw(texture,x+5+i*18,commander[1]+4,cardWidth,cardHeight);}
  }
  text(b,commanderName,x+(n==2?42:26),top-26,4.5f,TEXT,n==2?23:39,true,alpha);
  float[] collection=rect("Collection",8,hud.ui.getHeight()-159,70,24);alpha=parent*preferences.alpha("Collection");
  box(b,collection[0],collection[1],70,24,Color.WHITE,alpha);
  text(b,deckCount+" in deck",collection[0]+5,collection[1]+18,5f,MUTED,60,false,alpha);
  text(b,outside+" outside deck",collection[0]+5,collection[1]+8,5f,GOLD,60,false,alpha);
  float[] stats=rect("Stats",96,6,Math.min(210,hud.ui.getWidth()-103),24);alpha=parent*preferences.alpha("Stats");
  float statsX=stats[0],statsY=stats[1],statsWidth=stats[2];
  box(b,statsX,statsY,statsWidth,24,Color.WHITE,alpha);
  float cell=statsWidth/3;
  box(b,statsX+7,statsY+3,cell-14,2,Color.valueOf("352725"),alpha);
  box(b,statsX+7,statsY+3,(cell-14)*Math.max(0,Math.min(1,(float)player.getLife()/Math.max(1,player.getMaxLife()))),2,Color.valueOf("b55349"),alpha);
  b.setColor(1,1,1,alpha);
  b.draw(heart,statsX+6,statsY+7,10,10);b.draw(coin,statsX+cell+6,statsY+7,10,10);b.draw(crystal,statsX+2*cell+6,statsY+7,10,10);
  text(b,"Life",statsX+19,statsY+20,4.3f,MUTED,cell-22,false,alpha);
  text(b,player.getLife()+" / "+player.getMaxLife(),statsX+19,statsY+13,7f,TEXT,cell-22,false,alpha);
  text(b,"Gold",statsX+cell+19,statsY+20,4.3f,MUTED,cell-22,false,alpha);
  text(b,Integer.toString(player.getGold()),statsX+cell+19,statsY+13,7f,GOLD,cell-22,false,alpha);
  text(b,"Shards",statsX+2*cell+19,statsY+20,4.3f,MUTED,cell-22,false,alpha);
  text(b,Integer.toString(player.getShards()),statsX+2*cell+19,statsY+13,7f,Color.valueOf("b9c3ff"),cell-22,false,alpha);
  b.setColor(before);
 }
 public void toggleEditing(){
  if(editing){if(!saveLayout())return;editing=false;if(pausedStage!=null){pausedStage.startPause(0);pausedStage=null;}forge.Forge.advFreezePlayerControls=previousFreeze;dragging=null;sliding=false;return;}
  if(forge.Forge.advFreezePlayerControls)return;
  for(String name:new String[]{"console","dialog"}){
   try{java.lang.reflect.Field f=GameHUD.class.getDeclaredField(name);f.setAccessible(true);Actor actor=(Actor)f.get(hud);if(actor!=null&&actor.isVisible()&&actor.getStage()!=null)return;}
   catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
  }
  previousFreeze=forge.Forge.advFreezePlayerControls;editing=true;forge.Forge.advFreezePlayerControls=true;
  try{
   java.lang.reflect.Field field=GameHUD.class.getDeclaredField("gameStage");field.setAccessible(true);
   forge.adventure.stage.GameStage stage=(forge.adventure.stage.GameStage)field.get(hud);
   if(stage!=null){
    if(stage.isPaused()){editing=false;forge.Forge.advFreezePlayerControls=previousFreeze;return;}
    stage.setTouchKnobInput(0,0);if(stage.getPlayerSprite()!=null){stage.startPause(Float.MAX_VALUE);pausedStage=stage;}
   }
   if(hud.getTouchpad()!=null)hud.getTouchpad().setVisible(false);
  }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
  layout(hud);
 }
 private boolean saveLayout(){
  try{preferences.save(SETTINGS);status="Layout saved.";return true;}
  catch(java.io.IOException e){status="Could not save layout. Try again.";System.err.println("[HUD] Save failed: "+e);return false;}
 }
 private void outline(Batch b,float[] r,Color c){
  box(b,r[0],r[1],r[2],.6f,c,.85f);box(b,r[0],r[1]+r[3]-.6f,r[2],.6f,c,.85f);
  box(b,r[0],r[1],.6f,r[3],c,.85f);box(b,r[0]+r[2]-.6f,r[1],.6f,r[3],c,.85f);
 }
 private void drawEditor(Batch b){
  Color saved=new Color(b.getColor());
  box(b,6,2,58,12,Color.WHITE,.95f);text(b,"Edit HUD  F6",10,11,5.7f,TEXT,50,false,1);
  if(editing){
   for(Map.Entry<String,float[]> e:regions.entrySet()){
    outline(b,e.getValue(),e.getKey().equals(selection)?GOLD:TEAL);
    text(b,e.getKey(),e.getValue()[0]+2,Math.min(hud.ui.getHeight()-2,e.getValue()[1]+e.getValue()[3]+6),4.5f,TEAL,e.getValue()[2],false,1);
   }
   float x=hud.ui.getWidth()/2-170,y=hud.ui.getHeight()-62;
   box(b,x,y,340,56,Color.WHITE,.98f);
   text(b,"HUD EDITOR  ·  "+(selection==null?"All panels":selection),x+8,y+48,7,TEXT,320,false,1);
   text(b,status,x+8,y+35,5.5f,MUTED,320,false,1);
   box(b,x+8,y+6,39,17,Color.WHITE,1);text(b,"Done",x+13,y+18,6,TEXT,32,false,1);
   box(b,x+51,y+6,42,17,Color.WHITE,1);text(b,"Reset",x+56,y+18,6,TEXT,32,false,1);
   box(b,x+97,y+6,35,17,Color.WHITE,1);text(b,"All",x+105,y+18,6,TEXT,28,false,1);
   float value=selection==null?preferences.opacity:preferences.widgets.get(selection)[2];
   text(b,"Opacity "+Math.round(value*100)+"%",x+139,y+20,5.5f,TEXT,90,false,1);
   box(b,x+139,y+8,190,3,MUTED,.5f);box(b,x+139+value*187,y+6,3,8,TEAL,1);
  }
  b.setColor(saved);
 }
 private static boolean contains(float[] r,float x,float y){return x>=r[0]&&x<=r[0]+r[2]&&y>=r[1]&&y<=r[1]+r[3];}
 private com.badlogic.gdx.math.Vector2 pointer(int x,int y){
  return hud.ui.stageToLocalCoordinates(hud.screenToStageCoordinates(new com.badlogic.gdx.math.Vector2(x,y)));
 }
 public boolean editDown(float x,float y){
  if(!editing){if(contains(new float[]{6,2,58,12},x,y)){toggleEditing();return true;}return false;}
  float tx=hud.ui.getWidth()/2-170,ty=hud.ui.getHeight()-62;
  if(contains(new float[]{tx,ty,340,56},x,y)){
   if(y>=ty+6&&y<=ty+23){
    if(x>=tx+8&&x<=tx+47)toggleEditing();
    else if(x>=tx+51&&x<=tx+93){preferences.reset();selection=null;status="Default layout restored.";layout(hud);saveLayout();}
    else if(x>=tx+97&&x<=tx+132)selection=null;
    else if(x>=tx+139&&x<=tx+329){sliding=true;opacityAt(x);}
   }
   return true;
  }
  java.util.List<String> ids=new ArrayList<>(regions.keySet());Collections.reverse(ids);
  for(String id:ids){float[] r=regions.get(id);if(contains(r,x,y)){
   selection=id;dragging=id;dragX=x-r[0];dragY=y-r[1];
   preferences.move(id,r[0],r[1],r[2],r[3],hud.ui.getWidth(),hud.ui.getHeight());return true;
  }}
  selection=null;return true;
 }
 private void opacityAt(float x){float start=hud.ui.getWidth()/2-170+139;preferences.setOpacity(selection,(x-start)/190);layout(hud);}
 public void editDrag(float x,float y){
  if(sliding){opacityAt(x);return;}
  if(dragging!=null){float[] r=regions.get(dragging);preferences.move(dragging,x-dragX,y-dragY,r[2],r[3],hud.ui.getWidth(),hud.ui.getHeight());layout(hud);}
 }
 public void editUp(){dragging=null;sliding=false;if(editing)saveLayout();}
 private static boolean original(GameHUD hud,String name,int... args){
  try{
   Class<?>[] types=new Class<?>[args.length];Arrays.fill(types,int.class);Object[] values=Arrays.stream(args).boxed().toArray();
   return (boolean)GameHUD.class.getMethod("hudOriginal_"+name,types).invoke(hud,values);
  }catch(java.lang.reflect.InvocationTargetException e){throw new IllegalStateException("HUD input failed",e.getCause());}
  catch(ReflectiveOperationException e){throw new IllegalStateException("HUD input hook missing",e);}
 }
 public static boolean touchDown(GameHUD hud,int x,int y,int pointer,int button){
  ModernHudMod m=instance(hud);com.badlogic.gdx.math.Vector2 pos=m.pointer(x,y);
  if(m.editDown(pos.x,pos.y))return true;return original(hud,"touchDown",x,y,pointer,button);
 }
 public static boolean touchDragged(GameHUD hud,int x,int y,int pointer){
  ModernHudMod m=instance(hud);if(m.editing){com.badlogic.gdx.math.Vector2 pos=m.pointer(x,y);m.editDrag(pos.x,pos.y);return true;}return original(hud,"touchDragged",x,y,pointer);
 }
 public static boolean touchUp(GameHUD hud,int x,int y,int pointer,int button){
  ModernHudMod m=instance(hud);if(m.editing){m.editUp();return true;}return original(hud,"touchUp",x,y,pointer,button);
 }
 public static boolean keyDown(GameHUD hud,int key){
  ModernHudMod m=instance(hud);if(key==com.badlogic.gdx.Input.Keys.F6){m.toggleEditing();return true;}
  if(m.editing){if(key==com.badlogic.gdx.Input.Keys.ESCAPE)m.toggleEditing();return true;}return original(hud,"keyDown",key);
 }
 public static boolean keyUp(GameHUD hud,int key){
  ModernHudMod m=instance(hud);if(m.editing||key==com.badlogic.gdx.Input.Keys.F6)return true;return original(hud,"keyUp",key);
 }
 public static void dispose(GameHUD hud){
  Actor actor=hud.ui.findActor("modernAdventureHud");
  if(actor instanceof ModernHudMod m){if(m.editing){m.saveLayout();if(m.pausedStage!=null)m.pausedStage.startPause(0);forge.Forge.advFreezePlayerControls=m.previousFreeze;}if(m.editor!=null)m.editor.remove();m.font.dispose();m.panelTexture.dispose();m.heart.dispose();m.coin.dispose();m.crystal.dispose();m.remove();}
 }
}
