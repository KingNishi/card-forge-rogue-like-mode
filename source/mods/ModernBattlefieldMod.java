package forge.adventure.util;
import forge.*;
import forge.Graphics;
import forge.assets.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import java.lang.reflect.Method;
import java.util.*;
public final class ModernBattlefieldMod {
 private static Texture board;
 // Battle boards per biome: baked MTG Arena battlefields, extracted locally by _mods/arena-boards (private use only).
 private static final String BOARDS="D:/forge/_mods/arena-boards/boards/",FALLBACK_BOARD="D:/forge/_mods/modern-battlefield/board.png";
 private static final Map<String,String[]> POOLS=Map.of(
  "PLAINS",new String[]{"AKH_Mobile","WOE_Mobile"},
  "ISLAND",new String[]{"KHM_Mobile","FRA"},
  "SWAMP",new String[]{"Fallback","MID_Mobile"},
  "MOUNTAIN",new String[]{"AFR_Mobile","AKR_Mobile"},
  "FOREST",new String[]{"BLB","DAR_Mobile"},
  "WASTE",new String[]{"XLN_Mobile","OTJ"},
  "CAVE",new String[]{"AFR_Mobile","MID_Mobile"},
  "COMMON",new String[]{"DAR_Mobile","XLN_Mobile","KHM_Mobile"});
 private static final Map<String,Color> MOTES=Map.of("PLAINS",new Color(1,.86f,.45f,1),"ISLAND",new Color(.43f,.76f,1,1),"SWAMP",new Color(.65f,1,.4f,1),
  "MOUNTAIN",new Color(1,.5f,.15f,1),"FOREST",new Color(.62f,1,.5f,1),"WASTE",new Color(.95f,.82f,.6f,1),"CAVE",new Color(1,.6f,.25f,1),"COMMON",new Color(.9f,.9f,1,1));
 private static final Map<String,Integer> MODES=Map.of("PLAINS",0,"ISLAND",1,"SWAMP",2,"MOUNTAIN",3,"FOREST",4,"WASTE",5,"CAVE",6,"COMMON",7);
 private static String biome="COMMON",boardFile;private static Object boardGame;
 /** one board per battle: a random pick from the biome's pool, kept until the next game */
 private static void board(String textureName){
  String b=textureName.startsWith("ADV_BG_")?textureName.substring(7):"COMMON";
  String pick=BattleUiSettings.lanBoard;boolean picked=pick!=null&&!pick.isEmpty();   // 1v1 LAN battlefield chooser (user 2026-11-14)
  if(picked)for(var e:POOLS.entrySet())if(Arrays.asList(e.getValue()).contains(pick)){b=e.getKey();break;}
  var controller=forge.screens.match.MatchController.instance;Object game=controller==null?null:controller.getGameView();
  if(board!=null&&game==boardGame&&b.equals(biome))return;
  boardGame=game;biome=POOLS.containsKey(b)?b:"COMMON";
  // reworked 4K boards (arena-boards/boards-hd, by Codex) are preferred over the 2048 px originals
  List<String> present=new ArrayList<>();for(String n:picked?List.of(pick):Arrays.asList(POOLS.get(biome))){String hd=BOARDS.replace("/boards/","/boards-hd/")+n+".jpg";
   if(Gdx.files.absolute(hd).exists())present.add(hd);else if(Gdx.files.absolute(BOARDS+n+".jpg").exists())present.add(BOARDS+n+".jpg");}
  String file=present.isEmpty()?FALLBACK_BOARD:present.get(new Random().nextInt(present.size()));
  if(file.equals(boardFile)&&board!=null)return;
  if(board!=null)board.dispose();
  board=new Texture(Gdx.files.absolute(file),true);board.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);boardFile=file;
 }
 private static Method originalDraw;
 private static final long START=com.badlogic.gdx.utils.TimeUtils.nanoTime();
 private static final Map<FImage,FImage> controls=new IdentityHashMap<>();
 public static void drawControl(Graphics g,FImage image,float x,float y,float w,float h){
  if(active() && image instanceof FSkinImage skin && skin.name().startsWith("BTN_")){
   if(!FantasyButtonArt.drawSegment(g,image,x,y,w,h))control(image).draw(g,x,y,w,h);
  }else g.drawImage(image,x,y,w,h);
 }
 public static Color phaseInk(Color original){return active()&&original==Color.BLACK?Color.valueOf("e5edf4"):literal(original);}
 public static void roundPanel(Graphics g,FSkinColor color,float x,float y,float w,float h){
  if(active())g.fillRoundRect(color,x+1,y+1,Math.max(0,w-2),Math.max(0,h-2),Math.min(4,Math.min(w,h)/4));
  else g.fillRect(color,x,y,w,h);
 }
 public static boolean active(){return Forge.getCurrentScene() instanceof forge.adventure.scene.ForgeScene;}
 public static Color literal(Color original){
  if(!active())return original;
  if(original==Color.BLACK)return Color.valueOf("131d2a");
  if(original==Color.RED)return Color.valueOf("543b43");
  if(original==Color.GREEN||original==Color.LIME)return Color.valueOf("456b59");
  if(original==Color.GRAY||original==Color.LIGHT_GRAY)return Color.valueOf("344355");
  return original;
 }
 public static FImage control(FImage original){
  return controls.computeIfAbsent(original,image->new FImage(){
   public float getWidth(){return image.getWidth();}public float getHeight(){return image.getHeight();}
   public void draw(Graphics g,float x,float y,float w,float h){
    if(!active()){image.draw(g,x,y,w,h);return;}
    String state=image instanceof FSkinImage skin?skin.name():"";
    g.fillRect(Color.valueOf(state.contains("DOWN")?"294959":state.contains("OVER")?"2d5065":state.contains("DISABLED")?"202b35":"1d2a3b"),x,y,w,h);g.drawLine(1,Color.valueOf("69849a"),x,y+1,x+w,y+1);g.drawLine(1,Color.valueOf("101722"),x,y+h-1,x+w,y+h-1);
   }
  });
 }
 public static void background(FSkinTexture image,Graphics g,float x,float y,float w,float h){
  boolean battle=Forge.getCurrentScene() instanceof forge.adventure.scene.DuelScene||(System.getProperty("forge.duo")!=null&&forge.Forge.getCurrentScreen() instanceof forge.screens.match.MatchScreen);   // forge.duo: 1v1 browser mode
  String name=image.name();
  // the old patterned Forge background (side columns of the battle, deck editor, menus): modern dark gradient instead
  if(name.equals("BG_TEXTURE")||name.equals("ADV_BG_TEXTURE")){
   g.fillGradientRect(Color.valueOf("161c27"),Color.valueOf("0a0d13"),true,x,y,w,h);
   return;
  }
  if(battle&&(name.contains("BG_MATCH")||name.matches("ADV_BG_(SWAMP|FOREST|MOUNTAIN|ISLAND|PLAINS|WASTE|COMMON|CAVE)"))&&(Gdx.files.absolute(BOARDS).exists()||Gdx.files.absolute(FALLBACK_BOARD).exists())){
   board(name);
   float t=(com.badlogic.gdx.utils.TimeUtils.nanoTime()-START)/1_000_000_000f;
   LivingBoard.draw(g,board,boardFile,x,y,w,h,t);
   BoardFx.particles(g,LivingBoard.particleBiome(boardFile),x,y,w,h,t);
   RoamingCritters.draw(g,boardFile,x,y,w,h,t);
   Gdx.graphics.requestRendering();
   return;
  }
  try{if(originalDraw==null)originalDraw=FSkinTexture.class.getDeclaredMethod("modernOriginalDraw",Graphics.class,float.class,float.class,float.class,float.class);originalDraw.invoke(image,g,x,y,w,h);}catch(Exception e){throw new IllegalStateException(e);}
 }

 /** biome particles drawn on the board, under the cards: leaves, embers, fireflies, sparkles, sand, golden dust */
 static final class BoardFx {
  private static float r(int n){n=n*374761393+668265263;n=(n^(n>>>13))*1274126177;return ((n^(n>>>16))&0xffff)/65535f;}
  private static float edge(float p){return Math.min(1,Math.min(p,1-p)*8);}   // fade in/out at the board edges
  static void particles(Graphics g,String biome,float x,float y,float w,float h,float t){
   float k=Math.max(1,w/1920f);
   switch(biome){
    case "FOREST"->leaves(g,x,y,w,h,t,k);
    case "MOUNTAIN","CAVE"->embers(g,x,y,w,h,t,k);
    case "SWAMP"->fireflies(g,x,y,w,h,t,k);
    case "ISLAND"->sparkles(g,x,y,w,h,t,k);
    case "WASTE"->sand(g,x,y,w,h,t,k);
    default->dust(g,x,y,w,h,t,k,biome.equals("PLAINS")?new Color(1,.86f,.5f,1):new Color(.92f,.92f,1,1));
   }
  }
  private static void leaves(Graphics g,float x,float y,float w,float h,float t,float k){
   Color[] tones={new Color(.42f,.66f,.24f,1),new Color(.68f,.74f,.28f,1),new Color(.86f,.52f,.2f,1),new Color(.55f,.38f,.18f,1)};
   for(int i=0;i<28;i++){
    float speed=.022f+.02f*r(i),p=(r(i+1)+t*speed)%1,cx=x+w*((r(i+2)+p*.09f)%1)+(float)Math.sin(t*.8+i)*w*.018f,cy=y+h*p;
    float size=(6+6*r(i+3))*k,a=(float)Math.toRadians(t*(60+80*r(i+4))+i*40),ca=(float)Math.cos(a),sa=(float)Math.sin(a);
    Color c=tones[i%tones.length];Color fill=new Color(c.r,c.g,c.b,.62f*edge(p));
    float lx=ca*size,ly=sa*size,wx=-sa*size*.42f,wy=ca*size*.42f;
    g.fillTriangle(fill,cx+lx,cy+ly,cx+wx,cy+wy,cx-lx,cy-ly);g.fillTriangle(fill,cx+lx,cy+ly,cx-wx,cy-wy,cx-lx,cy-ly);
   }
  }
  private static void embers(Graphics g,float x,float y,float w,float h,float t,float k){
   for(int i=0;i<44;i++){
    float p=(r(i)+t*(.045f+.05f*r(i+1)))%1,cx=x+w*r(i+2)+(float)Math.sin(t*1.4+i*1.3)*w*.012f,cy=y+h*(1-p);
    float flick=.55f+.45f*(float)Math.sin(t*7+i*2.7),a=(1-p)*flick*edge(p),size=(1.4f+2.2f*r(i+3))*k;
    g.fillCircle(new Color(1,.45f,.1f,.18f*a),cx,cy,size*3.2f);g.fillCircle(new Color(1,.78f,.35f,.85f*a),cx,cy,size);
   }
  }
  private static void fireflies(Graphics g,float x,float y,float w,float h,float t,float k){
   for(int i=0;i<26;i++){
    float cx=x+w*(r(i)+.035f*(float)Math.sin(t*.37+i*1.7)),cy=y+h*(r(i+9)+.05f*(float)Math.cos(t*.29+i*2.3));
    float pulse=(float)Math.pow(Math.max(0,Math.sin(t*1.6+i*2.1)),2);
    g.fillCircle(new Color(.7f,1,.35f,.16f*pulse),cx,cy,9*k);g.fillCircle(new Color(.9f,1,.6f,.85f*pulse),cx,cy,2.2f*k);
   }
  }
  private static void sparkles(Graphics g,float x,float y,float w,float h,float t,float k){
   for(int i=0;i<34;i++){
    float cyc=t*.35f+r(i),phase=cyc%1;int n=(int)cyc;
    float cx=x+w*r(i*31+n*7),cy=y+h*r(i*17+n*13),a=(float)Math.pow(Math.sin(Math.PI*phase),3),s=(5+5*r(i+5))*k;
    Color c=new Color(.82f,.93f,1,.8f*a);
    g.fillRect(c,cx-s,cy-.6f*k,2*s,1.2f*k);g.fillRect(c,cx-.6f*k,cy-s,1.2f*k,2*s);g.fillCircle(new Color(.7f,.85f,1,.25f*a),cx,cy,s*.8f);
   }
   for(int i=0;i<30;i++){   // slow snow over the icy boards
    float p=(r(i+50)+t*(.015f+.012f*r(i+51)))%1,cx=x+w*((r(i+52)+(float)Math.sin(t*.4+i)*.01f)%1),cy=y+h*p;
    g.fillCircle(new Color(.95f,.97f,1,.45f*edge(p)),cx,cy,(1.2f+1.6f*r(i+53))*k);
   }
  }
  private static void sand(Graphics g,float x,float y,float w,float h,float t,float k){
   for(int i=0;i<34;i++){
    float p=((r(i)-t*(.07f+.06f*r(i+1)))%1+1)%1,len=(26+50*r(i+2))*k,cy=y+h*r(i+3)+(float)Math.sin(t*.9+i)*6*k;
    g.fillRect(new Color(.96f,.84f,.62f,.20f*edge(p)),x+w*p,cy,len,1.3f*k);
   }
   dust(g,x,y,w,h,t,k,new Color(.95f,.82f,.6f,1));
  }
  private static void dust(Graphics g,float x,float y,float w,float h,float t,float k,Color c){
   for(int i=0;i<36;i++){
    float p=(r(i+70)+t*(.012f+.012f*r(i+71)))%1,cx=x+w*((r(i+72)+p*.06f)%1)+(float)Math.sin(t*.5+i)*w*.01f,cy=y+h*(1-p);
    float a=(.35f+.25f*(float)Math.sin(t*1.3+i))*edge(p),s=(1.5f+2*r(i+73))*k;
    g.fillCircle(new Color(c.r,c.g,c.b,.18f*a),cx,cy,s*3);g.fillCircle(new Color(c.r,c.g,c.b,.75f*a),cx,cy,s);
   }
  }
 }

 /** Independently moving animals, confined to scenery margins, below every card and UI control.
  * Vector joints keep these small silhouettes crisp at 4K without loading another texture atlas. */
 static final class RoamingCritters {
  record Actor(float x,float y,float size,float heading,float flap,int kind,float alpha){}
  static Actor actor(String file,int index,float t){
   String id=LivingBoard.id(file);float phase=index*2.39996f,period=22+index*7;
   float p=(t/period+index*.31f)%1,fade=Math.min(1,Math.min(p,1-p)*10);
   boolean desert=Set.of("OTJ","AKH_Mobile","AKR_Mobile","XLN_Mobile","AFR_Mobile").contains(id);
   int kind=desert?(id.equals("XLN_Mobile")?3:2):id.equals("KHM_Mobile")?1:0;
   // Two side corridors and a top border. No path crosses the readable centre of the play surface.
   float x=index==0?.055f+.026f*(float)Math.sin(p*Math.PI*2):index==1?.948f+.020f*(float)Math.sin(p*Math.PI*2):.20f+.60f*p;
   float y=index<2?.20f+.56f*p:.065f+.016f*(float)Math.sin(p*Math.PI*4+phase);
   float heading=index<2?(float)Math.PI/2:(float)Math.atan2(.016f*4*Math.PI*Math.cos(p*Math.PI*4+phase),.60);
   float size=kind==1?.0065f:kind==0?.005f:.006f;
   return new Actor(x,y,size,heading,(float)Math.sin(t*(kind==0?13:kind==1?5:7)+phase),kind,fade*.78f);
  }
  static Color ink(float a){return new Color(.10f,.15f,.18f,a);}
  static void draw(Graphics g,String file,float x,float y,float w,float h,float t){
   if(file==null)return;
   for(int i=0;i<3;i++){
    Actor a=actor(file,i,t);float cx=x+w*a.x,cy=y+h*a.y,s=w*a.size,alpha=a.alpha;
    if(alpha<=0)continue;
    g.startRotateTransform(cx,cy,(float)Math.toDegrees(a.heading));
    try{
     if(a.kind==0){ // butterfly: four separately hinged wings, narrow thorax, antennae
      float spread=.24f+.76f*Math.abs(a.flap),tip=s*spread;
      for(int side:new int[]{-1,1}){
       Color fill=new Color(.49f,.73f,.75f,alpha*.85f);
       g.fillTriangle(ink(alpha),cx-s*.5f,cy,cx-s*1.05f,cy+side*tip*1.65f,cx+s*.7f,cy+side*tip*1.2f);
       g.fillTriangle(fill,cx-s*.4f,cy+side*tip*.14f,cx-s*.85f,cy+side*tip*1.35f,cx+s*.35f,cy+side*tip*.9f);
       g.fillTriangle(new Color(.55f,.43f,.25f,alpha),cx+s*.15f,cy,cx+s*.9f,cy+side*tip*.85f,cx+s*1.1f,cy+side*tip*.15f);
      }
      g.drawLine(Math.max(.7f,s*.20f),ink(alpha),cx-s*.65f,cy,cx+s*.9f,cy);
      g.fillCircle(new Color(.9f,.83f,.58f,alpha),cx-s*.5f,cy,Math.max(.6f,s*.15f));
     }else if(a.kind==1){ // gliding bird with a wing downstroke and a quieter return stroke
      float tip=s*(.45f+.65f*a.flap);
      g.fillTriangle(ink(alpha),cx-s,cy,cx+s*.55f,cy-s*1.7f,cx+tip,cy-s*.55f);
      g.fillTriangle(ink(alpha),cx-s,cy,cx+s*.55f,cy+s*1.7f,cx+tip,cy+s*.55f);
      g.drawLine(Math.max(1,s*.24f),ink(alpha),cx-s,cy,cx+s,cy);
     }else{ // crab/scorpion: alternating leg pairs, flexing claws, articulated tail
      g.fillCircle(new Color(.035f,.025f,.015f,.15f*alpha),cx+s*.3f,cy+s*.3f,s*.9f);
      for(int n=0;n<4;n++)for(int side:new int[]{-1,1}){
       float bx=cx+(n-1.5f)*s*.35f,reach=(float)Math.sin(t*7+n*Math.PI+side)*s*.16f;
       float ex=bx+s*.38f+reach,ey=cy+side*s*(.65f+.11f*n);
       g.drawLine(Math.max(.7f,s*.12f),ink(alpha),bx,cy,ex,ey);
       g.drawLine(Math.max(.7f,s*.10f),ink(alpha),ex,ey,ex-s*.33f,ey+side*s*.18f);
      }
      g.fillCircle(new Color(.33f,.25f,.15f,alpha),cx,cy,s*.49f);
      for(int side:new int[]{-1,1}){
       float claw=side*s*(.65f+.12f*(float)Math.sin(t*1.7f+i));
       g.drawLine(Math.max(1,s*.17f),ink(alpha),cx-s*.25f,cy,cx-s,cy+claw);
       g.fillTriangle(new Color(.48f,.37f,.22f,alpha),cx-s,cy+claw,cx-s*1.4f,cy+claw-s*.22f,cx-s*1.3f,cy+claw+s*.24f);
      }
      if(a.kind==2){
       float px=cx+s*.35f,py=cy;for(int n=1;n<5;n++){
        float nx=cx+s*(.35f+n*.22f),ny=cy-(float)Math.sin(n*.42f+t*.28f)*s*n*.16f;
        g.drawLine(Math.max(.7f,s*(.19f-n*.025f)),ink(alpha),px,py,nx,ny);px=nx;py=ny;
       }
      }
     }
    }finally{g.endTransform();}
   }
  }
 }

 /** Material-local animation: water, falls, foliage, windblown sand and painted critter idle motion. */
 static final class LivingBoard {
  static final int MW=512,MH=256,MAX_CRITTERS=8;
  // Coordinates refer to the top-left of each complete board, not its current biome.
  // Each area is cx,cy,rx,ry. Critters add kind: 0 fish, 1 bird, 2 breathing, 3 insect.
  record Profile(float[][] water,float[][] falls,float[][] critters,boolean sand,boolean snow){}
  static float[] a(float... v){return v;}
  static float[][] areas(float[]... v){return v;}
  static final Map<String,Profile> PROFILES=Map.ofEntries(
   Map.entry("DAR_Mobile",new Profile(
    areas(a(.39f,.045f,.11f,.09f),a(.92f,.23f,.11f,.17f),a(.15f,.82f,.155f,.18f),a(.934f,.69f,.10f,.13f),a(.883f,.875f,.07f,.15f)),
    areas(a(.895f,.11f,.033f,.06f),a(.953f,.208f,.023f,.055f),a(.965f,.595f,.028f,.045f),a(.882f,.797f,.025f,.06f),a(.883f,.939f,.026f,.074f),a(.059f,.523f,.015f,.045f)),
    areas(a(.892f,.027f,.018f,.030f,1),a(.096f,.11f,.031f,.035f,2),a(.888f,.198f,.026f,.012f,0),a(.885f,.256f,.024f,.022f,0),a(.873f,.291f,.022f,.010f,0),a(.185f,.827f,.018f,.024f,0),a(.215f,.872f,.025f,.012f,0),a(.089f,.615f,.020f,.030f,2)),false,false)),
   Map.entry("AKH_Mobile",new Profile(
    areas(a(.048f,.49f,.045f,.35f),a(.956f,.46f,.039f,.32f),a(.50f,.012f,.27f,.035f)),
    areas(a(.055f,.21f,.025f,.075f),a(.958f,.24f,.023f,.085f)),
    areas(a(.481f,.028f,.021f,.026f,1),a(.050f,.483f,.020f,.022f,0),a(.939f,.46f,.016f,.019f,0),a(.175f,.34f,.023f,.028f,2),a(.811f,.268f,.023f,.027f,2)),true,false)),
   Map.entry("Fallback",new Profile(
    areas(a(.50f,.50f,.60f,.56f)),
    areas(a(.14f,.05f,.03f,.06f),a(.877f,.176f,.025f,.05f),a(.931f,.242f,.025f,.06f)),
    areas(a(.085f,.355f,.023f,.038f,2),a(.075f,.655f,.024f,.028f,2),a(.10f,.12f,.025f,.023f,3),a(.978f,.152f,.021f,.038f,1),a(.35f,.033f,.022f,.025f,2),a(.958f,.72f,.027f,.025f,3),a(.82f,.85f,.020f,.027f,3)),false,false)),
   Map.entry("XLN_Mobile",new Profile(
    areas(a(.13f,.10f,.13f,.14f),a(.88f,.12f,.11f,.14f),a(.19f,.79f,.15f,.10f),a(.90f,.87f,.12f,.14f)),
    areas(a(.873f,.142f,.033f,.065f)),
    areas(a(.15f,.031f,.023f,.027f,1),a(.06f,.162f,.019f,.025f,0),a(.07f,.62f,.033f,.040f,3),a(.948f,.512f,.021f,.022f,5),a(.18f,.794f,.025f,.030f,5)),true,false)),
   Map.entry("KHM_Mobile",new Profile(
    areas(a(.14f,.105f,.105f,.075f)),
    areas(a(.102f,.052f,.023f,.065f),a(.172f,.064f,.019f,.06f)),
    areas(a(.036f,.60f,.023f,.049f,7),a(.969f,.82f,.028f,.033f,2),a(.909f,.052f,.025f,.043f,1)),false,true)),
   Map.entry("WOE_Mobile",new Profile(
    areas(a(.24f,.125f,.10f,.09f),a(.65f,.095f,.10f,.085f),a(.24f,.88f,.10f,.10f),a(.78f,.84f,.10f,.12f)),
    areas(a(.245f,.07f,.025f,.055f),a(.84f,.835f,.028f,.065f)),
    areas(a(.055f,.123f,.023f,.030f,1),a(.941f,.19f,.025f,.046f,2),a(.154f,.71f,.024f,.045f,2),a(.966f,.819f,.035f,.054f,2),a(.889f,.486f,.024f,.025f,1),a(.087f,.932f,.021f,.034f,7)),false,false)),
   Map.entry("BLB",new Profile(
    areas(a(.25f,.21f,.12f,.17f),a(.28f,.86f,.105f,.12f),a(.635f,.74f,.10f,.16f),a(.87f,.50f,.065f,.50f)),
    areas(a(.15f,.17f,.035f,.07f),a(.77f,.28f,.035f,.09f),a(.81f,.38f,.035f,.085f),a(.90f,.88f,.035f,.10f)),
    areas(a(.209f,.217f,.030f,.049f,2),a(.052f,.35f,.023f,.042f,7),a(.126f,.77f,.038f,.037f,2),a(.69f,.06f,.023f,.030f,2),a(.934f,.125f,.018f,.028f,1),a(.84f,.70f,.021f,.030f,2),a(.70f,.62f,.024f,.028f,3),a(.09f,.45f,.020f,.028f,3)),false,false)),
   Map.entry("FRA",new Profile(
    areas(a(.08f,.83f,.085f,.095f),a(.88f,.20f,.07f,.11f),a(.917f,.35f,.035f,.055f)),
    areas(a(.089f,.76f,.014f,.045f),a(.097f,.94f,.024f,.05f),a(.87f,.105f,.020f,.045f),a(.91f,.31f,.025f,.06f)),
    areas(a(.055f,.12f,.024f,.049f,1),a(.06f,.66f,.019f,.029f,2),a(.078f,.832f,.021f,.015f,0),a(.965f,.54f,.028f,.04f,3)),false,false)),
   Map.entry("OTJ",new Profile(
    areas(a(.052f,.65f,.057f,.105f),a(.895f,.255f,.026f,.045f),a(.134f,.17f,.035f,.035f)),areas(),
    areas(a(.665f,.07f,.033f,.040f,1),a(.065f,.44f,.020f,.037f,7),a(.969f,.558f,.019f,.026f,2),a(.911f,.535f,.024f,.026f,6)),true,false)),
   Map.entry("AKR_Mobile",new Profile(
    areas(a(.22f,.035f,.22f,.055f),a(.39f,.19f,.04f,.17f),a(.416f,.46f,.025f,.15f),a(.407f,.70f,.025f,.15f),a(.68f,.09f,.06f,.08f)),areas(),
    areas(a(.913f,.936f,.012f,.011f,4),a(.987f,.941f,.012f,.011f,4)),true,false)),
   Map.entry("AFR_Mobile",new Profile(
    areas(a(.071f,.48f,.035f,.30f),a(.65f,.075f,.075f,.055f),a(.50f,.175f,.27f,.015f),a(.23f,.775f,.18f,.015f),a(.72f,.775f,.11f,.015f)),
    areas(a(.05f,.205f,.023f,.06f),a(.072f,.53f,.022f,.07f)),
    areas(a(.169f,.914f,.030f,.028f,2),a(.762f,.833f,.025f,.028f,2),a(.802f,.76f,.021f,.027f,2),a(.875f,.763f,.027f,.027f,2),a(.03f,.457f,.025f,.035f,2),a(.864f,.65f,.011f,.016f,3)),false,false)),
   Map.entry("MID_Mobile",new Profile(
    areas(a(.13f,.82f,.065f,.13f),a(.089f,.44f,.033f,.075f),a(.775f,.247f,.046f,.07f),a(.66f,.66f,.048f,.11f)),
    areas(a(.154f,.692f,.018f,.035f),a(.127f,.94f,.021f,.055f)),
    areas(a(.84f,.05f,.016f,.032f,1),a(.151f,.23f,.022f,.021f,2),a(.054f,.046f,.024f,.02f,3)),false,false))
  );
  static Texture mask;static String loaded;static Profile profile;static com.badlogic.gdx.graphics.glutils.ShaderProgram shader;
  static boolean failed;static String shaderLog="not initialized";
  static final String VERTEX="attribute vec4 a_position;attribute vec4 a_color;attribute vec2 a_texCoord0;uniform mat4 u_projTrans;varying vec4 v_color;varying vec2 v_texCoords;void main(){v_color=a_color;v_color.a*=255.0/254.0;v_texCoords=a_texCoord0;gl_Position=u_projTrans*a_position;}";
  static final String FRAGMENT=String.join("\n",
   "#ifdef GL_ES",
   "precision mediump float;",
   "#endif",
   "varying vec4 v_color;",
   "varying vec2 v_texCoords;",
   "uniform sampler2D u_texture;",
   "uniform sampler2D u_material;",
   "uniform sampler2D u_flow;",
   "uniform sampler2D u_waterNoise;",
   "uniform float u_time;",
   "uniform float u_sand;",
   "uniform int u_count;",
   "uniform vec4 u_critter[8];",
   "uniform float u_kind[8];",
   "uniform vec4 u_rig[8];",
   "",
   "float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}",
   "float sat(float x){return clamp(x,0.0,1.0);}",
   "float softEllipse(vec2 q,vec2 centre,vec2 radius){return 1.0-smoothstep(0.55,1.0,length((q-centre)/radius));}",
   "vec2 rotate2(vec2 p,float a){float s=sin(a),c=cos(a);return vec2(c*p.x-s*p.y,s*p.x+c*p.y);}",
   "float pigment(vec3 c){return smoothstep(0.04,0.22,c.r-max(c.g,c.b))*smoothstep(0.25,0.55,c.r);}",
   "float fishShape(vec2 p,vec2 radius){",
   " vec3 c=texture2D(u_texture,p).rgb;",
   " float red=pigment(c);",
   " float nearRed=max(max(pigment(texture2D(u_texture,p+vec2(radius.x*0.13,0.0)).rgb),pigment(texture2D(u_texture,p-vec2(radius.x*0.13,0.0)).rgb)),",
   "                   max(pigment(texture2D(u_texture,p+vec2(0.0,radius.y*0.18)).rgb),pigment(texture2D(u_texture,p-vec2(0.0,radius.y*0.18)).rgb)));",
   " float pale=smoothstep(0.48,0.82,min(c.r,min(c.g,c.b)));",
   " return max(red,pale*nearRed);",
   "}",
   "",
   "void main(){",
   " vec2 base=v_texCoords;",
   " vec4 material=texture2D(u_material,base);",
   " vec4 flowData=texture2D(u_flow,base);",
   " float t=u_time;",
   " vec2 uv=base;",
   " // Leaves only: small, slow bend. Water never drags the painted stones or shore.",
   " float breeze=sin(t*0.72+base.y*12.0)*0.65+sin(t*1.13+base.x*21.0)*0.35;",
   " float animal=0.0;",
   " for(int i=0;i<8;i++){",
   "  if(i>=u_count)break;",
   "  vec4 region=u_critter[i];",
   "  vec2 q=(base-region.xy)/region.zw;",
   "  float cover=1.0-smoothstep(0.65,1.0,length(q));",
   "  if(cover<=0.001)continue;",
   "  float kind=u_kind[i],phase=float(i)*1.71;",
   "  vec4 rig=u_rig[i];",
   "  vec2 local=rotate2(q,-rig.x);",
   "  vec2 pose=local;",
   "  if(kind<0.5){",
   "   // Fish: tail, fins and body have different joints, not a wobbly water patch.",
   "   float tail=softEllipse(local,vec2(-0.53,0.0),vec2(0.52,0.65));",
   "   vec2 tailJoint=vec2(-0.16,0.0);",
   "   vec2 moved=tailJoint+rotate2(local-tailJoint,0.19*sin(t*4.6+phase));",
   "   pose=mix(pose,moved,tail);",
   "   float body=softEllipse(local,vec2(0.16,0.0),vec2(0.65,0.47));",
   "   pose.y+=body*0.026*sin(t*2.3+phase);",
   "  }else if(kind<1.5){",
   "   // Bird: occasional head glance, chest breathing, tiny tail flick.",
   "   float cycle=mod(t+phase*2.0,8.0);",
   "   float glance=smoothstep(1.0,1.7,cycle)*(1.0-smoothstep(3.0,3.8,cycle));",
   "   vec2 joint=rig.yz+vec2(-rig.y*0.35,0.38);",
   "   float side=sin(floor((t+phase*2.0)/8.0)*1.618+phase)>0.0?1.0:-1.0;",
   "   vec2 looked=joint+rotate2(q-joint,0.12*glance*side);",
   "   pose=mix(pose,looked,softEllipse(q,rig.yz,vec2(0.47,0.48)));",
   "   float chest=softEllipse(q,vec2(-rig.y*0.40,0.20),vec2(0.47,0.51));",
   "   pose.y-=chest*(q.y+0.15)*0.018*sin(t*1.9+phase);",
   "   float tail=softEllipse(q,vec2(-rig.y*1.65,rig.w),vec2(0.34,0.43));",
   "   pose.x+=tail*0.055*sin(t*3.3+phase)*pow(max(0.0,sin(t*0.8+phase)),5.0);",
   "  }else if(kind<2.5||kind>6.5){",
   "   // Mammal/frog: anchored paws, breathing torso, occasional head/ear twitch.",
   "   float chest=softEllipse(q,vec2(-rig.y*0.45,0.10-rig.z*0.20),vec2(0.59,0.48));",
   "   pose.y-=chest*(q.y+0.23)*0.030*sin(t*1.5+phase);",
   "   float twitch=pow(max(0.0,sin(t*0.63+phase)),16.0)*sin(t*14.0+phase);",
   "   vec2 joint=rig.yz+vec2(-rig.y*0.25,0.28);",
   "   pose=mix(pose,joint+rotate2(q-joint,0.055*twitch),softEllipse(q,rig.yz,vec2(0.55,0.44)));",
   "   if(kind>6.5){",
   "    float ears=softEllipse(q,rig.yz+vec2(-0.35,-0.48),vec2(0.42,0.62));",
   "    pose.x+=ears*(q.y-rig.z)*0.045*twitch;",
   "   }",
   "  }else if(kind<3.5){",
   "   // Insect wings pivot about the thorax; movement is strongest at wing tips.",
   "   float wings=softEllipse(q,vec2(0.0,0.0),vec2(0.90,0.63));",
   "   pose.x+=wings*q.x*0.12*sin(t*15.0+phase);",
   "   pose.y+=wings*0.018*sin(t*3.1+phase);",
   "  }else if(kind<4.5){",
   "   // White gull silhouettes: wing tips flap around an anchored torso.",
   "   float wing=pow(sat(abs(q.x)),1.35);",
   "   pose.y+=wing*0.13*sin(t*3.8+phase);",
   "   pose.y+=0.018*sin(t*1.4+phase);",
   "  }else if(kind<5.5){",
   "   // Crabs: shell stays planted while pincers/outer legs flex.",
   "   float claw=softEllipse(q,vec2(-0.55,-0.15),vec2(0.43,0.66));",
   "   float legs=smoothstep(0.32,0.85,abs(q.x))*smoothstep(-0.2,0.6,q.y);",
   "   pose.y+=claw*0.045*sin(t*1.4+phase);",
   "   pose.x+=legs*0.022*sin(t*2.3+phase+q.y*7.0);",
   "  }else{",
   "   // Scorpion: a slow tail curl and small pincer articulation.",
   "   vec2 hinge=vec2(0.08,-0.16);",
   "   float tail=softEllipse(q,vec2(0.3,-0.55),vec2(0.52,0.56));",
   "   pose=mix(pose,hinge+rotate2(q-hinge,0.065*sin(t*0.8+phase)),tail);",
   "   pose.y+=softEllipse(q,vec2(-0.52,0.30),vec2(0.46,0.45))*0.028*sin(t*1.7+phase);",
   "  }",
   "  if(kind<0.5)pose=rotate2(pose,rig.x);",
   "  vec2 offset=(pose-q)*region.zw*cover;",
   "  float silhouette=1.0;",
   "  if(kind<0.5){",
   "   // Keep stationary riverbed pixels out of the fish tail rig.",
   "   silhouette=max(fishShape(base,region.zw),fishShape(base+offset,region.zw));",
   "  }else if(kind>3.5&&kind<4.5){",
   "   vec3 old=texture2D(u_texture,base).rgb,new=texture2D(u_texture,base+offset).rgb;",
   "   silhouette=max(smoothstep(0.35,0.75,min(old.r,min(old.g,old.b))),smoothstep(0.35,0.75,min(new.r,min(new.g,new.b))));",
   "  }",
   "  animal=max(animal,cover*silhouette);",
   "  uv+=offset*silhouette;",
   " }",
   " uv+=material.g*vec2(breeze*0.00024,breeze*0.00010)*(1.0-animal);",
   " vec4 painted=texture2D(u_texture,clamp(uv,vec2(0.0001),vec2(0.9999)));",
   " vec3 col=painted.rgb;",
   " float water=material.r*(1.0-animal);",
   " float fall=material.a*(1.0-animal);",
   " vec2 velocity=(flowData.rg*2.0-1.0)*mix(0.025,0.115,flowData.b);",
   " // A translucent surface advects over a stationary riverbed. Two layers have",
   " // different scale/velocity, so there is no periodic whole-image rubber warp.",
   " vec2 p=base*vec2(29.0,14.5);",
   " vec3 n1=texture2D(u_waterNoise,p-velocity*t*3.0).rgb;",
   " vec3 n2=texture2D(u_waterNoise,p*1.83-velocity*t*5.1+vec2(0.37,0.19)).rgb;",
   " vec2 normal=(n1.rg+n2.rg-1.0)*1.4;",
   " float spec=pow(max(0.0,1.0-length(normal-vec2(-0.18,0.20))*1.30),12.0);",
   " float caustic=pow(sat(1.0-abs(n1.b+n2.b-1.0)*3.0),9.0);",
   " float surface=(spec*0.14+caustic*0.035)*water;",
   " col+=vec3(0.68,0.85,0.91)*surface;",
   " col*=1.0-water*(n1.b-0.5)*0.035;",
   " // Two staggered flow-map phases move the painted blue-white waterfall",
   " // highlights downward, with a crossfade hiding the coordinate reset.",
   " float phaseA=fract(t*0.58),phaseB=fract(t*0.58+0.5);",
   " vec2 fallTravel=vec2(0.0,0.012);",
   " vec3 fallA=texture2D(u_texture,base-fallTravel*phaseA).rgb;",
   " vec3 fallB=texture2D(u_texture,base-fallTravel*phaseB).rgb;",
   " vec3 flowing=mix(fallA,fallB,abs(phaseA*2.0-1.0));",
   " float blueWhite=smoothstep(0.50,0.88,min(painted.g,painted.b))*smoothstep(-0.005,0.055,painted.b-painted.r);",
   " col=mix(col,flowing,fall*blueWhite*0.72);",
   " // Falling white streaks travel downward with gravity; the substrate is fixed.",
   " vec2 fallUV=base*vec2(120.0,26.0)-vec2(0.0,t*1.25);",
   " vec3 jet=texture2D(u_waterNoise,fallUV).rgb;",
   " float threads=pow(sat(jet.b*0.75+jet.r*0.55),5.0);",
   " float droplets=pow(sat(texture2D(u_waterNoise,base*vec2(80.0,43.0)-vec2(0.0,t*1.9)).b),10.0);",
   " col=mix(col,vec3(0.85,0.94,0.98),fall*(threads*0.23+droplets*0.15));",
   " // Thin bands of driven dust instead of sparkling independent noise pixels.",
   " vec3 wind=texture2D(u_waterNoise,base*vec2(8.0,95.0)-vec2(t*0.21,t*0.018)).rgb;",
   " float gust=smoothstep(0.49,0.78,wind.b)*(0.5+0.5*sin(t*0.4+base.y*7.0));",
   " col=mix(col,vec3(0.92,0.79,0.57),material.b*u_sand*gust*0.05);",
   " vec2 edge=(base-0.5)*vec2(1.0,1.5);",
   " col*=mix(0.93,1.0,sat(1.0-dot(edge,edge)*0.6));",
   " gl_FragColor=v_color*vec4(clamp(col,0.0,1.0),painted.a);",
   "}");
  static float clamp(float x){return Math.max(0,Math.min(1,x));}
  static float area(float x,float y,float[][] list){
   float best=0;for(float[] v:list){float dx=(x-v[0])/v[2],dy=(y-v[1])/v[3];float d=(float)Math.sqrt(dx*dx+dy*dy);best=Math.max(best,clamp((1-d)/.22f));}return best;
  }
  static String id(String file){String name=file.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);int dot=name.lastIndexOf('.');return dot<0?name:name.substring(0,dot);}
  static void load(String file){
   if(file.equals(loaded))return;
   if(mask!=null){mask.dispose();mask=null;}loaded=null;profile=PROFILES.get(id(file));if(profile==null)return;
   Pixmap src=new Pixmap(Gdx.files.absolute(file)),mat=new Pixmap(MW,MH,Pixmap.Format.RGBA8888);
   try{
    mat.setBlending(Pixmap.Blending.None);
    for(int y=0;y<MH;y++)for(int x=0;x<MW;x++){
     float u=(x+.5f)/MW,v=(y+.5f)/MH;int pixel=src.getPixel(Math.min(src.getWidth()-1,(int)(u*src.getWidth())),Math.min(src.getHeight()-1,(int)(v*src.getHeight())));
     float r=((pixel>>>24)&255)/255f,g=((pixel>>>16)&255)/255f,b=((pixel>>>8)&255)/255f;
     float edge=1-clamp(Math.min(Math.min(u,1-u)*4.5f,Math.min(v,1-v)*5.5f));
     float blue=clamp((Math.max(b,g*.78f)-r)*8f+.10f)*clamp((b-.12f)*6);
     float water=area(u,v,profile.water)*blue;
     float falls=area(u,v,profile.falls)*clamp((b-.17f)*5)*clamp((Math.max(b,g)-r)*5+.45f);
     float foliage=clamp((g-Math.max(r*.87f,b*1.08f)) * 10)*clamp((g-.12f)*5)*edge;
     // Autumn boards can also move red leaves, but never the matching sandstone.
     if(id(file).equals("MID_Mobile"))foliage=Math.max(foliage,edge*clamp((r-g*1.45f)*4)*clamp((.38f-b)*4));
     float sand=profile.sand?clamp((r-b*1.30f)*5)*clamp((g-b)*7)*(.25f+.75f*edge):0;
     // Keep the painted animals independent of the surrounding water/plant deformation.
     for(float[] c:profile.critters){if(c[4]==0)continue;float dx=(u-c[0])/c[2],dy=(v-c[1])/c[3],d=dx*dx+dy*dy;if(d<1){float protect=clamp(d);water*=protect;foliage*=protect;falls*=protect;}}
     mat.drawPixel(x,y,((int)(clamp(water)*255)<<24)|((int)(clamp(foliage)*255)<<16)|((int)(clamp(sand)*255)<<8)|(int)(clamp(falls)*255));
    }
    mask=new Texture(mat);mask.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);makeFlow(file);makeWaterNoise();loaded=file;
   }finally{src.dispose();mat.dispose();}
  }
  static Texture flowTexture,waterNoise;
  // Per-animal joint corrections, authored against the current board illustrations.
  // angle is for fish; head coordinates are local to each previously mapped animal.
  static float[] animalRig(String file,int n,float kind){
   float angle=0,hx=kind<1.5f?-.30f:-.2f,hy=kind<1.5f?-.48f:-.52f,tailY=.52f;
   String board=id(file);
   if(board.equals("DAR_Mobile")){
    if(n==1){hx=-.30f;hy=.45f;} // curled fox: head is below its sleeping torso
    if(n==2)angle=3.14159f;if(n==3)angle=-2.45f;if(n==4)angle=3.14159f;if(n==5)angle=-2.3f;if(n==6)angle=.55f;
    if(n==7){hx=.55f;hy=-.5f;}
   }else if(board.equals("AKH_Mobile")){
    if(n==1)angle=2.35f;if(n==2)angle=.65f;
    if(n==3){hx=.55f;hy=-.55f;}if(n==4){hx=-.55f;hy=-.5f;}
   }else if(board.equals("Fallback")){
    if(n<2){hx=.4f;hy=-.5f;}if(n==4){hx=.4f;hy=.3f;}
   }else if(board.equals("FRA")){
    if(n==0){hx=0;hy=-.48f;}if(n==1){hx=.25f;hy=.50f;}if(n==2)angle=-.6f;
   }else if(board.equals("XLN_Mobile")){
    if(n==0){hx=.35f;hy=-.4f;}if(n==1)angle=-.9f;
   }
   else if(board.equals("KHM_Mobile")){
    if(n==0){hx=.35f;hy=-.1f;}if(n==1){hx=-.4f;hy=.3f;}if(n==2){hx=-.4f;hy=-.5f;}
   }else if(board.equals("WOE_Mobile")){
    if(n==0){hx=0;hy=-.55f;}if(n==1){hx=-.4f;hy=-.55f;}if(n==2){hx=.35f;hy=-.55f;}
    if(n==3){hx=-.45f;hy=-.45f;}if(n==4){hx=-.5f;hy=-.4f;}if(n==5){hx=.3f;hy=-.2f;}
   }else if(board.equals("BLB")){
    if(n==0){hx=.3f;hy=-.45f;}if(n==1){hx=.4f;hy=-.05f;}
    if(n==2){hx=.48f;hy=.05f;}if(n==3){hx=.35f;hy=-.35f;}if(n==5){hx=.45f;hy=-.05f;}
   }else if(board.equals("OTJ")){
    if(n==0)tailY=-.4f;if(n==1){hx=.35f;hy=.05f;}if(n==2){hx=-.4f;hy=-.25f;}
   }else if(board.equals("MID_Mobile")){
    if(n==0){hx=0;hy=-.55f;}if(n==1){hx=-.45f;hy=-.2f;}
   }
   return new float[]{angle,hx,hy,tailY};
  }
  static final Map<String,float[][]> FLOW_DIRECTIONS=Map.ofEntries(
   Map.entry("DAR_Mobile",areas(a(.65f,.18f),a(-.25f,.90f),a(.65f,.55f),a(-.45f,.65f),a(.05f,.95f))),
   Map.entry("AKH_Mobile",areas(a(0,1),a(0,1),a(1,0))),
   Map.entry("Fallback",areas(a(.12f,.85f))),
   Map.entry("XLN_Mobile",areas(a(.8f,.25f),a(-.3f,.85f),a(.6f,-.25f),a(-.5f,.65f))),
   Map.entry("KHM_Mobile",areas(a(.25f,.7f))),
   Map.entry("WOE_Mobile",areas(a(.4f,.6f),a(.7f,.1f),a(.5f,.7f),a(-.6f,.7f))),
   Map.entry("BLB",areas(a(.35f,.9f),a(.65f,.55f),a(.15f,.85f),a(.15f,1))),
   Map.entry("FRA",areas(a(.35f,.9f),a(.1f,.9f),a(-.25f,.9f))),
   Map.entry("OTJ",areas(a(.35f,.1f),a(.1f,.15f),a(.2f,.05f))),
   Map.entry("AKR_Mobile",areas(a(1,.05f),a(.1f,1),a(.1f,1),a(.2f,1),a(.6f,.5f))),
   Map.entry("AFR_Mobile",areas(a(.1f,1),a(.5f,.2f),a(-1,0),a(1,0),a(1,0))),
   Map.entry("MID_Mobile",areas(a(.1f,.9f),a(.2f,.65f),a(.25f,.25f),a(.4f,.1f)))
  );
  static float nhash(int x,int y){int n=x*374761393+y*668265263;n=(n^(n>>>13))*1274126177;return ((n^(n>>>16))&0xffff)/65535f;}
  static float nvalue(float x,float y,int f){
   float px=x*f,py=y*f;int ix=(int)Math.floor(px),iy=(int)Math.floor(py);float fx=px-ix,fy=py-iy;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
   float aa=nhash(Math.floorMod(ix,f),Math.floorMod(iy,f)),bb=nhash(Math.floorMod(ix+1,f),Math.floorMod(iy,f));
   float cc=nhash(Math.floorMod(ix,f),Math.floorMod(iy+1,f)),dd=nhash(Math.floorMod(ix+1,f),Math.floorMod(iy+1,f));
   return (aa+(bb-aa)*fx)*(1-fy)+(cc+(dd-cc)*fx)*fy;
  }
  static void makeWaterNoise(){
   if(waterNoise!=null)return;int size=256;float[][] heights=new float[size][size];
   for(int y=0;y<size;y++)for(int x=0;x<size;x++){
    float u=x/(float)size,v=y/(float)size;heights[y][x]=.52f*nvalue(u,v,4)+.27f*nvalue(u,v,8)+.14f*nvalue(u,v,16)+.07f*nvalue(u,v,32);
   }
   Pixmap image=new Pixmap(size,size,Pixmap.Format.RGBA8888);image.setBlending(Pixmap.Blending.None);
   try{
    for(int y=0;y<size;y++)for(int x=0;x<size;x++){
     float dx=(heights[y][(x+1)%size]-heights[y][(x+size-1)%size])*9;
     float dy=(heights[(y+1)%size][x]-heights[(y+size-1)%size][x])*9;
     int r=(int)(clamp(.5f+dx)*255),g=(int)(clamp(.5f+dy)*255),b=(int)(clamp(heights[y][x])*255);
     image.drawPixel(x,y,(r<<24)|(g<<16)|(b<<8)|255);
    }
    waterNoise=new Texture(image);waterNoise.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);waterNoise.setWrap(Texture.TextureWrap.Repeat,Texture.TextureWrap.Repeat);
   }finally{image.dispose();}
  }
  static void makeFlow(String file){
   if(flowTexture!=null){flowTexture.dispose();flowTexture=null;}
   float[][] directions=FLOW_DIRECTIONS.get(id(file));Pixmap image=new Pixmap(MW,MH,Pixmap.Format.RGBA8888);image.setBlending(Pixmap.Blending.None);
   try{
    for(int y=0;y<MH;y++)for(int x=0;x<MW;x++){
     float u=(x+.5f)/MW,v=(y+.5f)/MH,best=0,vx=.1f,vy=.2f;
     for(int n=0;n<profile.water.length;n++){
      float[] region=profile.water[n];float dx=(u-region[0])/region[2],dy=(v-region[1])/region[3];float weight=1/(1+dx*dx+dy*dy);
      if(weight>best){best=weight;vx=directions[n][0]-dy*.08f;vy=directions[n][1]+dx*.08f;}
     }
     float fall=area(u,v,profile.falls);vx*=1-fall;vy=vy*(1-fall)+fall;
     float length=(float)Math.sqrt(vx*vx+vy*vy);if(length>1){vx/=length;vy/=length;}
     float speed=Math.min(1,length*.65f+fall*.5f);
     image.drawPixel(x,y,((int)(clamp(vx*.5f+.5f)*255)<<24)|((int)(clamp(vy*.5f+.5f)*255)<<16)|((int)(speed*255)<<8)|255);
    }
    flowTexture=new Texture(image);flowTexture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
   }finally{image.dispose();}
  }

  static void init(){
   if(shader!=null||failed)return;
   shader=new com.badlogic.gdx.graphics.glutils.ShaderProgram(VERTEX,FRAGMENT);shaderLog=shader.getLog();
   if(!shader.isCompiled()){System.err.println("[Living battlefield] "+shaderLog);shader.dispose();shader=null;failed=true;}
  }
  static void draw(Graphics g,Texture texture,String file,float x,float y,float w,float h,float t){
   init();load(file);
   if(shader==null||mask==null||profile==null){g.drawImage(texture,x,y,w,h);return;}
   var batch=g.getBatch();var previous=batch.getShader();batch.flush();
   try{
    batch.setShader(shader);mask.bind(1);flowTexture.bind(2);waterNoise.bind(3);Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);shader.bind();
    shader.setUniformi("u_material",1);shader.setUniformi("u_flow",2);shader.setUniformi("u_waterNoise",3);shader.setUniformf("u_time",t);shader.setUniformf("u_sand",profile.sand?1:0);
    int count=Math.min(MAX_CRITTERS,profile.critters.length);shader.setUniformi("u_count",count);
    for(int n=0;n<MAX_CRITTERS;n++){
     float[] c=n<count?profile.critters[n]:new float[]{0,0,1,1,0};
     shader.setUniformf("u_critter["+n+"]",c[0],c[1],c[2],c[3]);shader.setUniformf("u_kind["+n+"]",c[4]);float[] rig=animalRig(file,n,c[4]);shader.setUniformf("u_rig["+n+"]",rig[0],rig[1],rig[2],rig[3]);
    }
    g.drawImage(texture,x,y,w,h);batch.flush();
   }finally{batch.setShader(previous);Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);}
  }
  static String particleBiome(String file){return switch(id(file)){
   case "DAR_Mobile","BLB"->"FOREST";
   case "Fallback"->"SWAMP";
   case "MID_Mobile"->"FOREST";
   case "KHM_Mobile"->"ISLAND";
   case "AKH_Mobile","AKR_Mobile","XLN_Mobile","OTJ"->"WASTE";
   case "AFR_Mobile"->"CAVE";
   default->"PLAINS";
  };}
 }

}
