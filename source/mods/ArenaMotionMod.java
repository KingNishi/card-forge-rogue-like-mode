package forge.adventure.util;
import forge.Graphics;
import forge.assets.FSkinFont;
import forge.card.CardRenderer;
import forge.game.card.CardView;
import forge.card.CardTypeView;
import forge.game.zone.ZoneType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import forge.screens.match.CardFlightOverlay;
import forge.screens.match.MatchScreen;
import forge.screens.match.views.VCardDisplayArea.CardAreaPanel;
import forge.screens.match.views.VZoneDisplay;
import forge.screens.match.views.VPlayerPanel;
import java.lang.reflect.*;
import java.util.*;
/** Cosmetic motion only; Forge retains flight scheduling, visibility and completion. */
public final class ArenaMotionMod {
 private static final Map<Class<?>,Map<String,Field>> FIELDS=new HashMap<>();
 private static final Map<String,Method> ORIGINALS=new HashMap<>();
 /** Forge 1v1 Browser (2026-11-10): -Dforge.duo=1 shows the Rogue battle look in online matches too */
 public static boolean duo(){return (System.getProperty("forge.duo")!=null&&forge.Forge.getCurrentScreen() instanceof forge.screens.match.MatchScreen);}
 public static boolean active(){return forge.Forge.getCurrentScene() instanceof forge.adventure.scene.DuelScene||duo();}
 public static float smooth(float p){float t=Math.max(0,Math.min(1,p));return t*t*t*(t*(t*6-15)+10);}
 public static float eased(float p){return active()?smooth(p):p;}
 public static float duration(float nativeNanos){return active()?(nativeNanos==450_000_000f?500_000_000f:260_000_000f):nativeNanos;}
 public static float tapDuration(float original){return active()?.24f:original;}
 public static float transformDuration(float original){return active()?.28f:original;}
 private static Field fieldOf(Object o,String name)throws ReflectiveOperationException{
  Map<String,Field> map=FIELDS.computeIfAbsent(o.getClass(),c->new HashMap<>());Field f=map.get(name);
  if(f==null){f=o.getClass().getDeclaredField(name);f.setAccessible(true);map.put(name,f);}return f;
 }
 private static Object field(Object o,String name){try{return fieldOf(o,name).get(o);}catch(ReflectiveOperationException e){throw new IllegalStateException("Card motion field: "+name,e);}}
 private static void setFloat(Object o,String name,float value){try{fieldOf(o,name).setFloat(o,value);}catch(ReflectiveOperationException e){throw new IllegalStateException("Card motion field: "+name,e);}}
 private static float number(Object o,String name){return ((Number)field(o,name)).floatValue();}
 private static void original(String name,Graphics g,Object flight,float progress){try{
  Method m=ORIGINALS.get(name);if(m==null){m=Class.forName("forge.screens.match.CardFlightOverlay").getDeclaredMethod(name,Graphics.class,flight.getClass(),float.class);m.setAccessible(true);ORIGINALS.put(name,m);}m.invoke(null,g,flight,progress);
 }catch(ReflectiveOperationException e){throw new IllegalStateException("Original card flight",e);}}
 public record Pose(float cx,float cy,float height,float angle,float alpha,float lift){}
 public static Pose pose(float x0,float y0,float h0,float x1,float y1,float h1,float angle,float progress,boolean leave,boolean popup){
  float t=Math.max(0,Math.min(1,progress)),s=smooth(t),bump=16*t*t*(1-t)*(1-t);
  float h=h0+(h1-h0)*s,x=x0+(x1-x0)*s,y=y0+(y1-y0)*s,a=leave?angle*(1-s):angle*s,alpha=1;
  if(popup&&!leave){x=x1;y=y1;h=h1*(.86f+.14f*s);a=angle;alpha=smooth(Math.min(1,t*3));}
  else if(!leave){y-=Math.min(22,h1*.08f)*bump;h*=1+.035f*bump;}
  if(leave)alpha=1-smooth(Math.max(0,(t-.55f)/.45f));
  return new Pose(x,y,Math.max(1,h),a,alpha,bump);
 }
 /** progress at which a card played to the battlefield touches down */
 public static final float LAND=.8f;
 /** card played to the battlefield: lifts towards the viewer, glides over, drops and settles with a small squash */
 public static Pose place(float x0,float y0,float h0,float x1,float y1,float h1,float angle,float progress){
  float t=Math.max(0,Math.min(1,progress)),u=Math.min(1,t/LAND),s=smooth(u),lift=(float)Math.sin(Math.PI*u);
  float x=x0+(x1-x0)*s,y=y0+(y1-y0)*s-Math.min(46,h1*.14f)*lift,h=(h0+(h1-h0)*s)*(1+.16f*lift);
  float a=angle*s+(x1>=x0?1:-1)*6f*lift*(1-u);
  if(t>LAND){float k=(t-LAND)/(1-LAND);x=x1;y=y1;h=h1*(1-.05f*(float)Math.sin(Math.PI*k));a=angle;}
  return new Pose(x,y,Math.max(1,h),a,1,t>LAND?0:lift);
 }
 public static void enter(Graphics g,Object flight,float progress){
  if(!active()){original("arenaOriginalDrawEnter",g,flight,progress);return;}draw(g,flight,progress,false);
 }
 public static void leave(Graphics g,Object flight,float progress){
  if(!active()){original("arenaOriginalDrawLeave",g,flight,progress);return;}draw(g,flight,progress,true);
 }
 private static final Map<Object,Boolean> landed=new WeakHashMap<>(),extended=new WeakHashMap<>();
 // Hand arrivals use Forge's own flight lifecycle: no duplicate panel or private opponent card is rendered.
 private static final Map<VZoneDisplay,Set<Integer>> hands=new WeakHashMap<>();
 private static final Map<Integer,Long> arrivals=new HashMap<>();
 private static final Set<Object> draws=Collections.newSetFromMap(new WeakHashMap<>());
 private static Object motionGame;
 private static synchronized void session(){
  var ui=forge.screens.match.MatchController.instance;Object game=ui==null?null:ui.getGameView();
  if(!active()||game!=motionGame){hands.clear();arrivals.clear();draws.clear();landed.clear();extended.clear();impacts.clear();BasicCardEffectsMod.cues.clear();motionGame=game;}
 }
 /** Called after a hand's actual card list changes, including while that tab is folded away. */
 public static synchronized void handUpdated(VZoneDisplay hand){
  session();if(!active()||hand.getZoneType()!=ZoneType.Hand)return;
  Set<Integer> current=new HashSet<>();for(CardView card:hand.getOrderedCards())current.add(card.getId());
  Set<Integer> previous=hands.put(hand,current);long now=System.nanoTime();
  arrivals.entrySet().removeIf(e->now-e.getValue()>850_000_000L);
  // The initial hand and tab layout are snapshots, not seven false draw events.
  if(previous==null)return;
  for(Integer id:current)if(!previous.contains(id)&&arrivals.size()<32)arrivals.put(id,now);
 }
 /** Called before native panel drawing. Native bookkeeping, flight hiding and cleanup stay in charge. */
 public static synchronized void handPanel(CardAreaPanel panel){
  if(!active())return;CardView card=panel.getCard();
  if(card==null||card.getZone()!=ZoneType.Hand||!(panel.getDisplayArea() instanceof VZoneDisplay hand)||hand.getZoneType()!=ZoneType.Hand)return;
  Long arrival=arrivals.remove(card.getId());if(arrival==null||System.nanoTime()-arrival>850_000_000L)return;
  var ui=forge.screens.match.MatchController.instance;
  if(ui==null||card.getController()==null||!ui.getLocalPlayers().contains(card.getController())||!visible(card)||panel.screenPos.width<=0)return;
  VPlayerPanel owner=MatchScreen.getPlayerPanel(card.getController());if(owner==null||owner.getSelectedTab()==null||owner.getSelectedTab().getDisplayArea()!=hand)return;
  if(!CardFlightOverlay.isEnabled()||field(panel,"flight")!=null)return;
  Rectangle source=null;
  for(var tab:owner.getTabs())if(tab.getDisplayArea() instanceof VZoneDisplay zone&&zone.getZoneType()==ZoneType.Library){
   float h=Math.max(24,panel.screenPos.height*.35f);source=new Rectangle(tab.screenPos.x+tab.screenPos.width/2-h/2.8f,tab.screenPos.y-h/2,h/1.4f,h);break;
  }
  if(source==null)return;
  var flight=CardFlightOverlay.start(card,source,new Rectangle(panel.screenPos),0,false,true);
  if(flight!=null)try{fieldOf(panel,"flight").set(panel,flight);draws.add(flight);setFloat(flight,"durationNanos",420_000_000f);}
  catch(ReflectiveOperationException e){throw new IllegalStateException("Hand flight",e);}
 }
 public static Pose drawPose(float x0,float y0,float h0,float x1,float y1,float h1,float progress){
  float t=Math.max(0,Math.min(1,progress)),s=smooth(t),lift=(float)Math.sin(Math.PI*t);
  return new Pose(x0+(x1-x0)*s,y0+(y1-y0)*s-Math.min(55,h1*.30f)*lift,h0+(h1-h0)*s,-7*lift,1,lift*.6f);
 }
 private static void draw(Graphics g,Object f,float p,boolean leave){
  CardView card=(CardView)field(f,"card");Object style=field(f,"style");
  boolean popup=!leave&&style!=null&&style.toString().equalsIgnoreCase("POPUP");
  // "moved" = a card already on the battlefield slides to a new slot (row reflow): no entrance, voice or impact
  boolean moved=Boolean.TRUE.equals(field(f,"moved"));
  boolean toField=!leave&&!moved&&card!=null&&card.getZone()==ZoneType.Battlefield;
  float x0=number(f,"fromCx"),y0=number(f,"fromCy"),h0=number(f,"fromH"),x1=number(f,"toCx"),y1=number(f,"toCy"),h1=number(f,"toH"),angle=number(f,"tappedAngle");
  if(!leave&&draws.contains(f)){
   Pose v=drawPose(x0,y0,h0,x1,y1,h1,p);
   float a=(float)Math.sin(Math.PI*Math.max(0,Math.min(1,p)));
   for(int n=3;n>=1;n--){float q=Math.max(0,p-n*.025f);Pose tail=drawPose(x0,y0,h0,x1,y1,h1,q);
    g.drawLine(Math.max(1,h1*.008f*n),new Color(.56f,.76f,1,.10f*a),tail.cx,tail.cy,v.cx,v.cy);}
   drawCard(g,card,v,false);return;
  }
  if(toField&&card.isCommander()){commander(g,f,card,p,x0,y0,h0,x1,y1,h1,angle);return;}
  Pose v=toField&&!popup?place(x0,y0,h0,x1,y1,h1,angle,p):pose(x0,y0,h0,x1,y1,h1,angle,p,leave,popup);
  drawCard(g,card,v,leave);
  if(toField&&p>=(popup?1f:LAND)&&landed.put(f,Boolean.TRUE)==null&&!BasicCardEffectsMod.add(card,x1,y1,h1,angle))impacts.add(new Impact(x1,y1,h1,angle,System.nanoTime(),false));
 }
 private static boolean visible(CardView card){
  try{var ui=forge.screens.match.MatchController.instance;return card!=null&&ui!=null&&ui.mayView(card);}catch(RuntimeException e){return false;}
 }
 private static void drawCard(Graphics g,CardView card,Pose v,boolean leave){
  float w=v.height/1.4f,oldAlpha=g.getfloatAlphaComposite();
  if(v.lift>.001f&&!leave){float o=v.height*.07f*v.lift;g.fillRoundRect(new Color(0,0,0,.30f*v.lift),v.cx-w/2+o*.5f,v.cy-v.height/2+o,w,v.height,6);}
  g.setAlphaComposite(oldAlpha*v.alpha);g.startRotateTransform(v.cx,v.cy,v.angle);
  try{CardRenderer.drawCard(g,card,v.cx-w/2,v.cy-v.height/2,w,v.height,CardRenderer.CardStackPosition.Top,false,false,false,true);}
  catch(RuntimeException e){   // no card face available (e.g. image still loading): plain card instead of a broken frame
   g.fillRoundRect(Color.valueOf("d9c8a0"),v.cx-w/2,v.cy-v.height/2,w,v.height,6);g.drawRoundRect(2,Color.valueOf("3a2a14"),v.cx-w/2,v.cy-v.height/2,w,v.height,6);
   if(visible(card)&&card.getName()!=null)g.drawText(card.getName(),fit(card.getName(),w-8,v.height*.08f),INK,v.cx-w/2+4,v.cy-v.height/2+6,w-8,v.height*.1f,false,1,true);
  }
  finally{g.endTransform();g.setAlphaComposite(oldAlpha);}
 }
 // ------------------------------------------------------------------ commander entrance
 public static final float COMMANDER_NANOS=1_800_000_000f;
 static final Color GOLD=new Color(1f,.84f,.45f,1),LIGHT=new Color(1f,.95f,.78f,1),INK=Color.valueOf("1a120a");
 private static Color alpha(Color c,float a){return new Color(c.r,c.g,c.b,c.a*Math.max(0,Math.min(1,a)));}
 /** Rogue Commanders voice line when the player's own commander enters (looked up by reflection: the Rogue mod is
  *  installed after this one) */
 private static void rogueVoice(CardView card){
  try{
   if(card.getController()!=null&&card.getController().isAI())return;
   Class.forName("forge.adventure.scene.RogueCommandersScene").getMethod("commanderCast",String.class).invoke(null,card.getName());
  }catch(Throwable ignored){}
 }
 /** the commander rises to the middle of the board in a burst of golden light, then dives onto its place */
 private static void commander(Graphics g,Object f,CardView card,float p,float x0,float y0,float h0,float x1,float y1,float h1,float angle){
  if(extended.put(f,Boolean.TRUE)==null){setFloat(f,"durationNanos",COMMANDER_NANOS);rogueVoice(card);}
  float sw=forge.Forge.getScreenWidth(),sh=forge.Forge.getScreenHeight();
  float t=Math.max(0,Math.min(1,p)),cx=sw/2,cy=sh*.45f,big=Math.max(h1*1.6f,Math.min(sh*.46f,h1*3f));
  float x,y,h,a=0,lift=0;
  if(t<.28f){float s=smooth(t/.28f);x=x0+(cx-x0)*s;y=y0+(cy-y0)*s;h=h0+(big-h0)*s;lift=s;}
  else if(t<.72f){float k=(t-.28f)/.44f;x=cx;y=cy+(float)Math.sin(k*Math.PI*2)*big*.012f;h=big*(1+.025f*(float)Math.sin(k*Math.PI));lift=1;}
  else{float k=(t-.72f)/.28f,s=k*k;x=cx+(x1-cx)*s;y=cy+(y1-cy)*s;h=big+(h1-big)*s;a=angle*s;lift=1-s;}
  float env=t<.12f?t/.12f:t<.72f?1:Math.max(0,1-(t-.72f)/.2f);
  g.fillRect(new Color(0,0,0,.45f*env),0,0,sw,sh);
  if(env>0){
   double spin=System.nanoTime()/1e9*.3;int rays=24;
   for(int i=0;i<rays;i++){double ang=spin+i*2*Math.PI/rays;float len=big*(i%2==0?1.35f:.95f);
    for(int layer=0;layer<3;layer++){double half=new double[]{.07,.04,.018}[layer];float r=len*new float[]{1f,.8f,.6f}[layer];   // brighter towards the centre
     g.fillTriangle(alpha(GOLD,.06f*env),x,y,x+(float)Math.cos(ang-half)*r,y+(float)Math.sin(ang-half)*r,x+(float)Math.cos(ang+half)*r,y+(float)Math.sin(ang+half)*r);}}
   for(int i=3;i>=1;i--)g.fillCircle(alpha(GOLD,.07f*env),x,y,big*.32f*i);
  }
  float w=h/1.4f;
  for(int i=4;i>=1;i--)g.fillRoundRect(alpha(LIGHT,.10f*lift),x-w/2-i*5,y-h/2-i*5,w+i*10,h+i*10,8+i*4);
  drawCard(g,card,new Pose(x,y,h,a,1,0),false);
  float label=t<.2f?0:t<.3f?(t-.2f)/.1f:t<.66f?1:Math.max(0,1-(t-.66f)/.08f);
  if(label>0){
   float ty=y+h/2+big*.05f,lw=Math.max(w*1.6f,sw*.25f);
   g.drawOutlinedText("COMMANDER",fit("COMMANDER",lw,big*.12f),alpha(GOLD,label),alpha(INK,label),x-lw/2,ty,lw,big*.12f,false,1,true);
   String name=card.getName();
   if(name!=null)g.drawOutlinedText(name,fit(name,lw,big*.075f),alpha(LIGHT,label),alpha(INK,label),x-lw/2,ty+big*.12f,lw,big*.075f,false,1,true);
  }
  if(t>=1&&landed.put(f,Boolean.TRUE)==null)impacts.add(new Impact(x1,y1,h1,angle,System.nanoTime(),true));
 }
 private static final Map<String,FSkinFont> fonts=new HashMap<>();
 private static FSkinFont fit(String text,float maxW,float maxH){
  if(fonts.size()>200)fonts.clear();
  return fonts.computeIfAbsent(text+"|"+Math.round(maxW)+"|"+Math.round(maxH),k->{
   int size=Math.max(6,Math.min(80,Math.round(maxH)));FSkinFont f=FSkinFont.get(size);
   while(size>6&&(f.getLineHeight()>maxH||f.getBounds(text).width>maxW))f=FSkinFont.get(--size);
   return f;
  });
 }
 // ------------------------------------------------------------------ landing impacts (drawn above the board)
 public record Impact(float cx,float cy,float h,float angle,long start,boolean commander){}
 private static final List<Impact> impacts=new ArrayList<>();
 public static void addImpact(Impact i){impacts.add(i);}
 private static float hash(int n){n=n*374761393+668265263;n=(n^(n>>>13))*1274126177;return ((n^(n>>>16))&0xffff)/65535f;}
 /** called at the start of CardFlightOverlay.draw, every frame of a match */
 public static synchronized void effects(Graphics g){
  session();if(!active())return;
  BasicCardEffectsMod.effects(g);
  if(impacts.isEmpty())return;
  long now=System.nanoTime();
  for(Iterator<Impact> it=impacts.iterator();it.hasNext();){
   Impact i=it.next();float e=(now-i.start)/1e9f/(i.commander?1.2f:.62f);
   if(e>=1){it.remove();continue;}
   try{impact(g,i,Math.max(0,e));}catch(RuntimeException ex){it.remove();}
  }
  if(Gdx.graphics!=null)Gdx.graphics.requestRendering();
 }
 public static void impact(Graphics g,Impact i,float e){
  boolean sideways=Math.abs(i.angle%180)>45;float k=i.h/260f;
  float w=sideways?i.h:i.h/1.4f,h=sideways?i.h/1.4f:i.h,out=1-(1-e)*(1-e)*(1-e);
  if(i.commander){
   float sw=forge.Forge.getScreenWidth(),sh=forge.Forge.getScreenHeight(),a=Math.max(0,1-e*1.5f);
   g.fillRect(new Color(1,.9f,.6f,.18f*Math.max(0,1-e*4)),0,0,sw,sh);
   for(float part:new float[]{.9f,.55f,.25f})g.fillGradientRect(alpha(LIGHT,0),alpha(LIGHT,.2f*a),true,i.cx-w*part/2,0,w*part,i.cy);
  }
  if(e<.35f)g.fillRoundRect(alpha(LIGHT,(i.commander?.55f:.38f)*(1-e/.35f)),i.cx-w/2,i.cy-h/2,w,h,6*k);
  int rings=i.commander?3:2;
  for(int r=0;r<rings;r++){
   float d=(e-r*.13f)/(1-r*.13f);if(d<=0||d>=1)continue;
   float o=1-(1-d)*(1-d)*(1-d),grow=(i.commander?110:48)*k*o,lw=Math.max(1,(i.commander?6:3.5f)*k*(1-d));
   for(int layer=2;layer>=0;layer--){float gl=grow+layer*lw*1.2f;   // soft glow around a bright core
    g.drawRoundRect(lw,alpha(r==0?LIGHT:GOLD,.9f*(1-d)*new float[]{1f,.38f,.14f}[layer]),i.cx-w/2-gl,i.cy-h/2-gl,w+2*gl,h+2*gl,6*k+gl*.3f);}
  }
  int n=i.commander?36:16;
  for(int s=0;s<n;s++){
   double ang=s*2*Math.PI/n+hash(s)*.6;float speed=.55f+.9f*hash(s+7),dist=(i.commander?170:72)*k*speed*(1-(1-e)*(1-e));
   float ex=(float)Math.cos(ang),ey=(float)Math.sin(ang);
   float size=(i.commander?8f:5.5f)*k*(1-e)*(.6f+.7f*hash(s+3)),px=i.cx+ex*(w/2+dist),py=i.cy+ey*(h/2+dist);
   g.fillCircle(alpha(GOLD,.25f*(1-e)),px,py,Math.max(1,size*2.2f));
   g.fillCircle(alpha(s%3==0?Color.WHITE:LIGHT,1-e),px,py,Math.max(.6f,size));
  }
  if(!i.commander)for(int d=0;d<6;d++){
   float side=d<3?-1:1,o=1-(1-e)*(1-e);
   g.fillCircle(new Color(.86f,.8f,.7f,.20f*(1-e)),i.cx+side*(w*.22f+(d%3)*w*.13f+30*k*o),i.cy+h/2-6*k-12*k*o*hash(d+11),(9+15*o)*k);
  }
 }

public static final class BasicCardEffectsMod {
 public enum Kind { LAND, ARTIFACT, EQUIPMENT, CREATURE, OTHER }
 public record Cue(float cx,float cy,float h,float angle,long start,Kind kind,Color color) {}
 private static final List<Cue> cues=new ArrayList<>();
 public static Kind kind(CardTypeView type){
  if(type==null)return Kind.OTHER;
  if(type.isBasicLand())return Kind.LAND;
  if(type.isEquipment())return Kind.EQUIPMENT;
  if(type.isCreature())return Kind.CREATURE;
  if(type.isArtifact())return Kind.ARTIFACT;
  return Kind.OTHER;
 }
 public static Color color(CardTypeView type){
  if(type!=null&&type.isBasicLand()){
   
   if(type.hasSubtype("Plains"))return Color.valueOf("f8e9a5");
   if(type.hasSubtype("Island"))return Color.valueOf("78caff");
   if(type.hasSubtype("Swamp"))return Color.valueOf("bd91dc");
   if(type.hasSubtype("Mountain"))return Color.valueOf("f59b70");
   if(type.hasSubtype("Forest"))return Color.valueOf("91d89c");
   return Color.valueOf("d3d7df");
  }
  return switch(kind(type)){
   case ARTIFACT -> Color.valueOf("b9dbe4");
   case EQUIPMENT -> Color.valueOf("ebd599");
   case CREATURE -> Color.valueOf("d7c1a0");
   default -> Color.valueOf("eee3c9");
  };
 }
 /** Returns false for cards whose original landing effect should be retained. */
 public static boolean add(CardView card,float x,float y,float h,float angle){
  CardTypeView type=card==null||card.getCurrentState()==null?null:card.getCurrentState().getType();
  Kind kind=kind(type);if(kind==Kind.OTHER)return false;
  if(cues.size()>=32)cues.remove(0);
  cues.add(new Cue(x,y,h,angle,System.nanoTime(),kind,color(type)));return true;
 }
 public static float lifetime(Kind kind){return kind==Kind.CREATURE?.40f:.48f;}
 public static void effects(Graphics g){
  if(!ArenaMotionMod.active()){cues.clear();return;}
  long now=System.nanoTime();
  for(Iterator<Cue> it=cues.iterator();it.hasNext();){
   Cue cue=it.next();float e=(now-cue.start())/1e9f/lifetime(cue.kind());
   if(e>=1){it.remove();continue;}draw(g,cue,Math.max(0,e));
  }
  if(!cues.isEmpty()&&Gdx.graphics!=null)Gdx.graphics.requestRendering();
 }
 private static Color fade(Color c,float a){return new Color(c.r,c.g,c.b,Math.max(0,Math.min(1,a)));}
 public static void draw(Graphics g,Cue c,float progress){
  float e=Math.max(0,Math.min(1,progress));if(e>=1)return;
  float k=Math.max(.25f,c.h()/260f),out=1-(1-e)*(1-e),a=1-e;
  boolean side=Math.abs(c.angle()%180)>45;
  float w=side?c.h():c.h()/1.4f,h=side?c.h()/1.4f:c.h();
  float x=c.cx(),y=c.cy();
  if(c.kind()==Kind.CREATURE){
   // Small ground puff, no screen shake or full-screen flash.
   for(int n=0;n<5;n++){
    float dx=(n-2)*(w*.13f+13*k*out);
    g.fillCircle(fade(c.color(),.18f*a),x+dx,y+h/2-5*k-12*k*out,(5+9*out)*k);
   }
   return;
  }
  float grow=(c.kind()==Kind.LAND?22:12)*k*out;
  g.drawRoundRect(Math.max(1,2*k*a),fade(c.color(),.65f*a),x-w/2-grow,y-h/2-grow,w+2*grow,h+2*grow,5*k+grow*.2f);
  int count=c.kind()==Kind.LAND?6:4;
  for(int n=0;n<count;n++){
   double ang=n*2*Math.PI/count+.35;
   float px=x+(float)Math.cos(ang)*(w/2+grow),py=y+(float)Math.sin(ang)*(h/2+grow);
   float r=(2+2*(1-e))*k;
   if(c.kind()==Kind.LAND)g.fillCircle(fade(c.color(),.75f*a),px,py,r);
   else {
    g.drawLine(Math.max(1,k),fade(c.color(),.8f*a),px-r,py,px+r,py);
    g.drawLine(Math.max(1,k),fade(c.color(),.8f*a),px,py-r,px,py+r);
   }
  }
  if(c.kind()==Kind.EQUIPMENT){
   float yy=y-h/2+h*out;
   g.drawLine(Math.max(1,2*k),fade(c.color(),.30f*a),x-w*.42f,yy,x+w*.42f,yy);
  }
 }
}

 // ---- planeswalker ready glow (user 2026-10-16): light blue frame while one of your planeswalkers can still use a loyalty ability now
 private static final Color PW_READY=Color.valueOf("8fd3ff");
 private static final Map<Integer,long[]> pwReady=new HashMap<>();   // card id -> {checked at, 1/0}
 /** called after every battlefield card panel is drawn (PatchArenaMotion, before each return of CardAreaPanel.draw) */
 public static void panelDrawn(CardAreaPanel panel,Graphics g){
  try{
   CardView card=panel.getCard();
   if(card==null||card.getZone()!=ZoneType.Battlefield||!card.getCurrentState().isPlaneswalker()||!pwCanActivate(card))return;
   float pad=pwPadding(panel),x=pad,y=pad,w=panel.getWidth()-2*pad,h=panel.getHeight()-2*pad,r=Math.max(3,w*.05f);
   float pulse=.65f+.35f*(float)Math.sin(System.currentTimeMillis()/420.0);
   for(int i=3;i>=1;i--)g.drawRoundRect(i*2f,new Color(PW_READY.r,PW_READY.g,PW_READY.b,.10f*pulse),x-i,y-i,w+2*i,h+2*i,r+i);
   g.drawRoundRect(1.6f,new Color(PW_READY.r,PW_READY.g,PW_READY.b,.75f*pulse),x,y,w,h,r);
   Gdx.graphics.requestRendering();
  }catch(Throwable t){/* never break card drawing */}
 }
 private static Method paddingGetter;
 private static float pwPadding(CardAreaPanel panel)throws ReflectiveOperationException{
  if(paddingGetter==null){paddingGetter=forge.toolbox.FCardPanel.class.getDeclaredMethod("getPadding");paddingGetter.setAccessible(true);}
  return (Float)paddingGetter.invoke(panel);
 }
 private static boolean pwCanActivate(CardView view){
  long now=System.currentTimeMillis();long[] c=pwReady.get(view.getId());
  if(c!=null&&now-c[0]<250)return c[1]==1;
  boolean ready=false;
  var ui=forge.screens.match.MatchController.instance;
  if(ui!=null&&ui.getGameController() instanceof forge.player.PlayerControllerHuman h&&h.getPlayer()!=null){
   var me=h.getPlayer();var card=me.getGame().findById(view.getId());
   if(card!=null&&card.getController()==me&&!card.planeswalkerActivationLimitUsed())
    for(var sa:card.getSpellAbilities())if(sa.isPwAbility()&&sa.canPlay()){ready=true;break;}
  }
  pwReady.put(view.getId(),new long[]{now,ready?1:0});
  return ready;
 }
}
