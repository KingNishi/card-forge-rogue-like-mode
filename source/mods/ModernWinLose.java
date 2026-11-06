package forge.screens.match.winlose;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.Graphics;
import forge.assets.FSkinFont;
import forge.game.GameLogEntry;
import forge.game.GameLogEntryType;
import forge.game.GameType;
import forge.game.GameView;
import forge.game.player.PlayerView;
import forge.toolbox.FButton;
import forge.toolbox.FContainer;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FLabel;
import forge.toolbox.FTextArea;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern match result screen (ViewWinLose.doLayout/drawBackground are routed here by PatchModernWinLose):
 * big VICTORY/DEFEAT banner, commander illustration in Rogue Commanders, stat tiles, collapsed game log.
 * Also: every match started from Adventure mode gets the Adventure control, so "quit" returns to the scene
 * (Rogue Commanders duels run as GameType.Commander, which Forge answered with the generic "Quit Match" control).
 */
public final class ModernWinLose {
 private ModernWinLose(){}
 static final Color GOLD=new Color(.93f,.77f,.42f,1),RED=new Color(.88f,.33f,.29f,1),SILVER=new Color(.78f,.82f,.88f,1),
  PANEL=new Color(.06f,.075f,.105f,.97f),TEXT=new Color(.94f,.92f,.88f,1),DIM=new Color(.6f,.64f,.7f,1),
  SHADE=new Color(.012f,.016f,.03f,.84f),TILE=new Color(1,1,1,.045f),OUTLINE=new Color(0,0,0,.85f);

 /** ViewWinLose constructor: the switch that picks the control reads this instead of GameView.getGameType() */
 public static GameType controlType(GameType t){
  try{
   if(Forge.isMobileAdventureMode&&t!=GameType.Adventure&&t!=GameType.AdventureEvent
      &&Forge.getCurrentScene() instanceof forge.adventure.scene.DuelScene)return GameType.AdventureEvent;
  }catch(Throwable e){System.err.println("[ModernWinLose] "+e);}
  return t;
 }

 // ------------------------------------------------------------------ state per result screen
 static final class State{
  boolean ready,logOpen,rogue,won,draw;String result,kicker,subtitle,reason;Color accent;
  final List<String[]> tiles=new ArrayList<>();TextureRegion art;FLabel logToggle;
  float px,py,pw,ph,u,artH,kickerY,titleY,titleH,subY,reasonY,tilesY,tilesH,logY,logH;
 }
 static final Map<ViewWinLose,State> STATES=new WeakHashMap<>();
 static final Map<String,Field> FIELDS=new java.util.HashMap<>();

 @SuppressWarnings("unchecked")
 static <T> T get(ViewWinLose v,String name){
  try{Field f=FIELDS.get(name);if(f==null){f=ViewWinLose.class.getDeclaredField(name);f.setAccessible(true);FIELDS.put(name,f);}
   return (T)f.get(v);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 static Object rogue(String method){
  try{Method m=Class.forName("forge.adventure.scene.RogueCommandersScene").getMethod(method);return m.invoke(null);}catch(Throwable e){return null;}
 }

 static State state(ViewWinLose v){
  State s=STATES.get(v);if(s!=null)return s;
  STATES.clear();                      // only one result screen exists at a time
  s=new State();STATES.put(v,s);
  GameView game=get(v,"game");
  PlayerView me=null;List<String> others=new ArrayList<>();
  for(PlayerView p:game.getPlayers()){if(me==null&&!p.isAI())me=p;else others.add(p.getName());}
  String winner=game.getWinningPlayerName();
  String meName=me==null?"":me.getName();
  s.draw=winner==null;s.won=!s.draw&&winner.equals(meName);
  s.result=s.draw?"DRAW":s.won?"VICTORY":"DEFEAT";
  s.accent=s.draw?SILVER:s.won?GOLD:RED;
  String loser=s.draw?null:s.won?String.join(", ",others):meName;
  s.subtitle=s.draw?"Nobody wins this game":(s.won?meName:winner)+"  defeats  "+loser;
  String turn=null,duration=null;
  for(GameLogEntry e:game.getGameLog().getLogEntriesExact(GameLogEntryType.GAME_OUTCOME)){
   String m=e.message();
   Matcher d=Pattern.compile("(\\d+)\\s*m\\s*:\\s*(\\d+)\\s*s").matcher(m);
   if(d.find()){duration=d.group(1)+":"+(d.group(2).length()<2?"0":"")+d.group(2);continue;}
   Matcher t=Pattern.compile("^\\D*(\\d+)\\D*$").matcher(m);
   if(t.matches()&&m.length()<24){turn=t.group(1);continue;}
   if(s.reason==null&&(loser==null||m.contains(loser.split(",")[0])))s.reason=m.replaceAll("\\[[^\\]]*\\]","").trim();
  }
  String label=(String)rogue("duelLabel");
  s.rogue=label!=null;
  int games=game.getNumGamesInMatch();
  int gameNo=game.getNumPlayedGamesInMatch()+1;   // played games before this one (it showed "GAME 0" after the first)
  s.kicker=label!=null?label+(games>1?"  ·  GAME "+gameNo+(game.isMatchOver()?"":"  ·  NEXT GAME FOLLOWS"):""):games>1?"GAME "+gameNo+" OF "+games:"";
  // third field "*" = record star on a win: under 3 turns / under 3 or over 30 minutes / under 3 or over 100 life
  if(turn!=null)s.tiles.add(new String[]{turn,"TURNS",s.won&&num(turn)>=0&&num(turn)<3?"*":""});
  if(duration!=null){int sec=seconds(duration);s.tiles.add(new String[]{duration,"DURATION",s.won&&sec>=0&&(sec<180||sec>1800)?"*":""});}
  if(me!=null){int life=me.getLife();s.tiles.add(new String[]{String.valueOf(life),"LIFE",s.won&&(life<3||life>100)?"*":""});}
  if(games>1){
   String stats=((FLabel)get(v,"lblStats")).getText();
   Matcher sc=Pattern.compile(":\\s*(\\d+)").matcher(stats==null?"":stats);List<String> n=new ArrayList<>();
   while(sc.find())n.add(sc.group(1));
   if(n.size()==2)s.tiles.add(new String[]{n.get(0)+" : "+n.get(1),"SCORE",""});
  }
  if(s.rogue){
   Object tex=rogue("duelArt");
   if(tex instanceof Texture t){int w=t.getWidth(),h=t.getHeight();   // art box of a full-border card (744 x 1040)
    s.art=new TextureRegion(t,Math.round(w*.078f),Math.round(h*.1125f),Math.round(w*.844f),Math.round(h*.4425f));}
  }
  s.ready=true;
  return s;
 }

 // ------------------------------------------------------------------ layout (replaces ViewWinLose.doLayout)
 public static void layout(ViewWinLose v,float width,float height){
  try{modern(v,width,height);}
  catch(Throwable e){System.err.println("[ModernWinLose] layout: "+e);e.printStackTrace();try{plain(v,width,height);}catch(Throwable ignored){}}
 }

 static void modern(ViewWinLose v,float width,float height){
  State s=state(v);
  FLabel title=get(v,"lblTitle"),stats=get(v,"lblStats"),lblLog=get(v,"lblLog"),copy=get(v,"btnCopyLog"),battlefield=get(v,"btnShowBattlefield");
  FContainer outcomes=get(v,"pnlOutcomes");FTextArea log=get(v,"txtLog");
  FButton cont=get(v,"btnContinue"),restart=get(v,"btnRestart"),quit=get(v,"btnQuit");
  title.setVisible(false);stats.setVisible(false);outcomes.setVisible(false);lblLog.setVisible(false);
  GameView game=get(v,"game");
  // buttons: Adventure shows "next game" only in longer matches, never "restart"
  List<FButton> buttons=new ArrayList<>();
  if(Forge.isMobileAdventureMode){
   if(game.getNumGamesInMatch()>1&&cont.isVisible()&&cont.isEnabled())buttons.add(cont);else cont.setVisible(false);
   restart.setVisible(false);
   if(s.rogue&&!game.isMatchOver()&&game.getNumGamesInMatch()>1)quit.setVisible(false);   // best of 3: only "next game" between games (quitting would lose the match)
   else buttons.add(quit);
   if(s.rogue&&game.isMatchOver())quit.setText("Continue");
  }else{
   for(FButton b:new FButton[]{cont,restart,quit}){if(b.isEnabled()&&b.isVisible())buttons.add(b);else b.setBounds(-10,-10,0,0);}
  }
  if(s.logToggle==null){
   final State st=s;
   s.logToggle=v.add(new FLabel.ButtonBuilder().text("Show game log").font(FSkinFont.get(13)).command(e->{
    st.logOpen=!st.logOpen;st.logToggle.setText(st.logOpen?"Hide game log":"Show game log");v.revalidate();}).build());
  }
  boolean land=width>=height;
  float pw=land?Math.min(width*.5f,height*1.2f):width*.92f;
  float u=pw/100f;
  s.u=u;s.pw=pw;
  s.artH=s.art!=null?(s.logOpen?pw*.2f:pw*.34f):u*4;
  float y=s.artH-(s.art!=null?u*7:0);
  s.kickerY=y;if(!s.kicker.isEmpty())y+=u*4;
  s.titleY=y;s.titleH=u*12;y+=s.titleH;
  y+=u*3;                              // ornament
  s.subY=y;y+=u*5;
  s.reasonY=y;if(s.reason!=null)y+=u*4;
  y+=u*3;
  s.tilesY=y;s.tilesH=s.tiles.isEmpty()?0:u*13;y+=s.tilesH+(s.tiles.isEmpty()?0:u*4);
  float bw=pw*.62f;
  float[] by=new float[buttons.size()];
  for(int i=0;i<buttons.size();i++){by[i]=y;y+=(i==0?u*8.5f:u*7)+u*2;}
  float toggleY=y;y+=u*5;
  s.logY=y;s.logH=s.logOpen?Math.max(u*16,Math.min(u*30,height*.97f-y-u*4)):0;if(s.logOpen)y+=s.logH+u*2;
  y+=u*2;
  s.ph=Math.min(y,height*.98f);
  s.px=(width-pw)/2;s.py=Math.max(height*.01f,(height-s.ph)/2);
  float px=s.px,py=s.py;
  for(int i=0;i<buttons.size();i++){FButton b=buttons.get(i);float bh=i==0?u*8.5f:u*7;b.setBounds(px+(pw-bw)/2,py+by[i],bw,bh);}
  boolean showBf=battlefield.isVisible();
  float tw=pw*.4f,th=u*4.5f;
  if(showBf){s.logToggle.setBounds(px+pw/2-tw-u,py+toggleY,tw,th);battlefield.setBounds(px+pw/2+u,py+toggleY,tw,th);}
  else s.logToggle.setBounds(px+(pw-tw)/2,py+toggleY,tw,th);
  log.setVisible(s.logOpen);copy.setVisible(s.logOpen);
  if(s.logOpen){
   copy.setBounds(px+pw-u*4-pw*.3f,py+s.logY-u*0.5f,pw*.3f,u*4);
   log.setFont(FSkinFont.forHeight(u*2.7f));
   log.setBounds(px+u*4,py+s.logY+u*4,pw-u*8,s.logH-u*4);
  }
 }

 /** the old stacked layout, only used if the modern one fails */
 static void plain(ViewWinLose v,float width,float height){
  float x=width*.25f,w=width*.5f,y=height*.05f,h=height/10;
  for(String n:new String[]{"lblTitle","lblStats"}){FDisplayObject o=get(v,n);o.setVisible(true);o.setBounds(x,y,w,h);y+=h;}
  FButton quit=get(v,"btnQuit");quit.setBounds(x,y,w,h);y+=h*1.2f;
  FTextArea log=get(v,"txtLog");log.setVisible(true);log.setBounds(x,y,w,height-y-h);
 }

 // ------------------------------------------------------------------ drawing (replaces FOverlay.drawBackground)
 static int num(String t){try{return Integer.parseInt(t.trim());}catch(Exception e){return -1;}}
 static int seconds(String mmss){String[] q=mmss.split(":");return q.length==2&&num(q[0])>=0&&num(q[1])>=0?num(q[0])*60+num(q[1]):-1;}
 static final Color HEART=Color.valueOf("e8434f"),STAR=Color.valueOf("ffd25a");
 static void heart(Graphics g,float x,float y,float s){
  float r=s*.27f;g.fillCircle(HEART,x+r,y+r*1.05f,r);g.fillCircle(HEART,x+s-r,y+r*1.05f,r);
  g.fillTriangle(HEART,x+.02f*s,y+r*1.35f,x+s*.98f,y+r*1.35f,x+s/2,y+s*.98f);
 }
 /** five-pointed gold star around (cx,cy) */
 static void star(Graphics g,float cx,float cy,float r){
  float[] xs=new float[10],ys=new float[10];
  for(int i=0;i<10;i++){double ang=Math.toRadians(-90+i*36);float rr=i%2==0?r:r*.45f;xs[i]=cx+(float)Math.cos(ang)*rr;ys[i]=cy+(float)Math.sin(ang)*rr;}
  for(int i=7;i>=1;i--)g.fillCircle(a(STAR,.035f),cx,cy,r*.32f*i);
  for(int i=0;i<10;i++){int j=(i+1)%10;g.fillTriangle(STAR,cx,cy,xs[i],ys[i],xs[j],ys[j]);}
 }
 public static void paint(ViewWinLose v,Graphics g){
  float w=v.getWidth(),h=v.getHeight();
  g.fillRect(SHADE,0,0,w,h);
  State s=STATES.get(v);
  if(s==null||!s.ready||s.pw<=0)return;
  try{draw(s,g,w,h);}catch(Throwable e){System.err.println("[ModernWinLose] draw: "+e);s.ready=false;}
 }

 static Color a(Color c,float alpha){return new Color(c.r,c.g,c.b,alpha);}

 static void draw(State s,Graphics g,float w,float h){
  float px=s.px,py=s.py,pw=s.pw,ph=s.ph,u=s.u;Color acc=s.accent;
  float cx=px+pw/2;
  // soft light behind the banner
  for(int i=7;i>=1;i--)g.fillCircle(a(acc,.028f),cx,py+s.titleY+s.titleH/2,pw*.11f*i);
  g.fillRect(PANEL,px,py,pw,ph);
  // commander illustration across the top, fading into the panel
  if(s.art!=null){
   float ix=px,iy=py,iw=pw,ih=ph;   // the whole panel (user 2026-10-16: "den ganzen Hintergrund mit dem Commander fuellen")
   TextureRegion r=new TextureRegion(s.art.getTexture(),s.art.getRegionX(),s.art.getRegionY(),s.art.getRegionWidth(),Math.round(s.art.getRegionHeight()*.84f));   // without the bottom edge: full-art frames put the type line there
   float sx=r.getRegionWidth(),sy=r.getRegionHeight();
   float want=iw/ih,have=sx/sy;
   TextureRegion crop=new TextureRegion(r);
   if(have>want){int cw=Math.round(sy*want);crop.setRegion(r.getRegionX()+Math.round((sx-cw)/2),r.getRegionY(),cw,Math.round(sy));}
   else{int ch=Math.round(sx/want);crop.setRegion(r.getRegionX(),r.getRegionY()+Math.round((sy-ch)*.15f),Math.round(sx),ch);}   // faces sit in the upper part
   g.drawImage(crop,ix,iy,iw,ih);
   float fade=Math.min(ih,s.artH);
   g.fillGradientRect(a(PANEL,.05f),a(PANEL,.55f),true,ix,iy,iw,fade);
   g.fillGradientRect(a(PANEL,.55f),a(PANEL,.82f),true,ix,iy+fade,iw,ih-fade+1);
  }else{
   g.fillGradientRect(a(acc,.16f),a(acc,0),true,px+u*.6f,py+u*.6f,pw-u*1.2f,u*16);
  }
  g.drawRect(Math.max(1.5f,u*.2f),a(acc,.75f),px,py,pw,ph);
  g.drawRect(1f,a(acc,.2f),px+u*.9f,py+u*.9f,pw-u*1.8f,ph-u*1.8f);
  float cl=u*5,ct=Math.max(2f,u*.45f);     // gold corner marks
  for(float[] c:new float[][]{{px,py,1,1},{px+pw,py,-1,1},{px,py+ph,1,-1},{px+pw,py+ph,-1,-1}}){
   g.drawLine(ct,acc,c[0],c[1],c[0]+cl*c[2],c[1]);g.drawLine(ct,acc,c[0],c[1],c[0],c[1]+cl*c[3]);}
  // kicker + banner
  if(!s.kicker.isEmpty())
   g.drawOutlinedText(spaced(s.kicker),FSkinFont.forHeight(u*3.2f),a(acc,.95f),OUTLINE,px,py+s.kickerY,pw,u*4,false,Align.center,true);
  FSkinFont big=FSkinFont.forHeight(s.titleH*.95f);
  String banner=spaced(s.result);
  for(float o:new float[]{u*.9f,u*.5f})
   g.drawText(banner,big,a(acc,.12f),px-o,py+s.titleY,pw,s.titleH,false,Align.center,true);
  g.drawOutlinedText(banner,big,acc,OUTLINE,px,py+s.titleY,pw,s.titleH,false,Align.center,true);
  // ornament: line - diamond - line
  float oy=py+s.titleY+s.titleH+u*1.2f,d=u*1.1f;
  g.drawLine(Math.max(1f,u*.18f),a(acc,.6f),px+pw*.22f,oy,cx-d*2.2f,oy);
  g.drawLine(Math.max(1f,u*.18f),a(acc,.6f),cx+d*2.2f,oy,px+pw*.78f,oy);
  g.fillTriangle(acc,cx-d,oy,cx,oy-d,cx+d,oy);g.fillTriangle(acc,cx-d,oy,cx,oy+d,cx+d,oy);
  g.drawText(s.subtitle,FSkinFont.forHeight(u*4.2f),TEXT,px+u*3,py+s.subY,pw-u*6,u*5,false,Align.center,true);
  if(s.reason!=null)g.drawText(s.reason,FSkinFont.forHeight(u*3f),DIM,px+u*3,py+s.reasonY,pw-u*6,u*4,false,Align.center,true);
  // stat tiles
  int n=s.tiles.size();
  if(n>0){
   float gap=u*2.2f,tw=Math.min(u*22,(pw*.86f-gap*(n-1))/n),tx=cx-(tw*n+gap*(n-1))/2,ty=py+s.tilesY;
   FSkinFont value=FSkinFont.forHeight(u*6f),label=FSkinFont.forHeight(u*2.6f);
   for(String[] t:s.tiles){
    g.fillRect(s.art!=null?new Color(.03f,.04f,.07f,.74f):TILE,tx,ty,tw,s.tilesH);   // dark glass over the commander art
    g.drawRect(1f,a(acc,.28f),tx,ty,tw,s.tilesH);
    g.fillRect(a(acc,.7f),tx+tw*.3f,ty,tw*.4f,Math.max(1.5f,u*.3f));
    if("LIFE".equals(t[1])){float hs=u*4.4f,vw=value.getBounds(t[0]).width,x0=tx+(tw-hs-u*1.2f-vw)/2;
     heart(g,x0,ty+u*1.2f+(u*7.2f-hs)/2,hs);g.drawText(t[0],value,TEXT,x0+hs+u*1.2f,ty+u*1.2f,vw+u,u*7.2f,false,Align.left,true);}
    else g.drawText(t[0],value,TEXT,tx,ty+u*1.2f,tw,u*7.2f,false,Align.center,true);
    if(t.length>2&&"*".equals(t[2]))star(g,tx+tw-u*2.8f,ty+u*2.8f,u*2.1f);
    g.drawText(spaced(t[1]),label,DIM,tx,ty+u*8.4f,tw,u*3.4f,false,Align.center,true);
    tx+=tw+gap;
   }
  }
  if(s.logOpen){
   g.drawText(spaced("GAME LOG"),FSkinFont.forHeight(u*2.8f),DIM,px+u*4,py+s.logY-u*.5f,pw*.5f,u*4,false,Align.left,true);
   g.fillRect(new Color(0,0,0,.28f),px+u*3,py+s.logY+u*3.5f,pw-u*6,s.logH-u*3);
  }
 }

 /** caps for banner and labels (Forge's generated fonts have no thin spaces for real letter spacing) */
 static String spaced(String s){return s;}
}
