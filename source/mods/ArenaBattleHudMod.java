package forge.adventure.util;
import forge.Graphics;
import forge.assets.FSkinColor;
import forge.assets.FSkinFont;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.phase.PhaseType;
import forge.game.zone.ZoneType;
import forge.screens.match.views.*;
import forge.toolbox.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import java.lang.reflect.*;
import java.util.*;
public final class ArenaBattleHudMod {
 private static Object member(Object o,String name){try{Field f=VPlayerPanel.class.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 private static boolean enabled(VPlayerPanel p,float w,float h){return ArenaMotionMod.active()&&forge.Forge.isLandscapeMode()&&w>=900&&h>=240&&forge.screens.match.MatchScreen.getPlayerPanels().size()==2;}
 public record Layout(float avatarX,float avatarY,float avatarSize,float lifeY,float fieldY,float fieldHeight,float displayY,float displayHeight,float bandY,float bandHeight){}
 /** portrait size in units of the layout scale (it used to be 54, centred in a tall strip) */
 public static final float AVATAR=84,BAND=46,LIFE=24,BIG=2.5f;   // BIG: portrait size factor (user 2026-10-16)
 /** the layout scale of the last plan() (labels, plaque, buttons); the portrait itself may be smaller */
 static float uiScale=1;
 public static Layout plan(float w,float h,boolean bottom,boolean selected){
  float sh=forge.Forge.getScreenHeight();
  // smaller screens (1080p): zone tabs and portraits down to 80 % so the board keeps room; 1440+ unchanged (user 2026-10-16)
  float res=Math.max(.8f,Math.min(1,.8f+.2f*(sh-1080)/360));
  float scale=res*Math.max(.6f,Math.min(1.6f,Math.min(w/1600,sh/900f))),band=BAND*scale,life=LIFE*scale;
  // portraits at most 15 % of the screen height: at 1080p they were 19 % (user 2026-10-18 "zu gross in 1900er Aufloesung");
  // 4K stays as it was (~15.5 %). Everything else keeps the layout scale (uiScale), only the portrait shrinks.
  float avatar=Math.min(AVATAR*BIG*scale,Math.max(sh*.15f,AVATAR*BIG*scale*.7f));uiScale=scale;
  float gap=bottom?promptGap():0;   // the transparent prompt bar lies over the bottom of the board
  // The hand gets the room of the old menu bar and phase strips: about a quarter of the screen height, bigger cards.
  float display=selected?Math.min(h*.5f,Math.max(70,w*.123f*handScale())):0;
  float bandY=bottom?h-gap-display-band:0,fieldY=bottom?0:band+display,fieldH=Math.max(40,h-gap-band-display);
  // portrait + life in the right-hand column: standing on the bottom player's slim strip, hanging below the opponent's
  float avatarY=bottom?bandY+band-avatar-life-4*scale:bandY+4*scale;
  return new Layout(w-avatar-22*scale,avatarY,avatar,avatarY+avatar+2*scale,fieldY,fieldH,bottom?h-gap-display:band,display,bandY,band);
 }
 /** width kept free on both sides of the battlefield rows: command zone on the left, portrait column on the right */
 static float reserve(Layout l,float w,float commandWidth,float scale){return Math.max(commandWidth>0?commandWidth+10*scale:0,w-l.avatarX()+14*scale);}
 public static void layout(VPlayerPanel p,float w,float h){
  if(!enabled(p,w,h))return;
  boolean bottom=(Boolean)member(p,"isBottomPlayer");boolean selected=p.getSelectedTab()!=null;
  Layout l=plan(w,h,bottom,selected);float scale=uiScale;
  p.getAvatar().setBounds(l.avatarX(),l.avatarY(),l.avatarSize(),l.avatarSize());
  ((FDisplayObject)member(p,"lblLife")).setBounds(l.avatarX()+l.avatarSize()/2-50*scale,l.lifeY(),100*scale,LIFE*scale-2*scale);
  float manaW=65*scale,manaX=l.avatarX()-manaW-18*scale,phaseW=Math.max(120,Math.min(w*.32f,manaX-12*scale-w*.45f));
  // the per-player phase strips (UP DR M1 ...) are gone: the turn plaque in the middle shows every step
  p.getPhaseIndicator().setBounds(manaX-12*scale-phaseW,l.bandY(),0,0);p.getPhaseIndicator().setVisible(false);
  int count=0;for(var tab:p.getTabs())if(tab!=p.getManaPoolTab()&&!(tab instanceof VPlayerPanel.InfoTabExtra))count++;
  float tw=Math.min(78*scale,w*.40f/Math.max(1,count)),th=Math.min(l.bandHeight()-4*scale,42*scale);int index=0;   // bigger zone tiles
  // bottom: the hand button keeps a fixed spot in the bottom-left corner, the tabs follow it;
  // top: the round match menu button sits in the top-left corner
  float side=BattleUiSettings.size(2);   // Settings > Battle UI: side buttons (user 2026-10-17)
  float toggleSize=Math.min(l.bandHeight()-6*scale,40*scale)*side;
  float zoomW=toggleSize*2.9f;
  float tabsX=12*scale+(bottom&&bars()?toggleSize+8*scale+zoomW+14*scale:!bottom&&bars()?92*scale:0);
  for(var tab:p.getTabs()){
   if(tab==p.getManaPoolTab())tab.setBounds(manaX,l.bandY()+(l.bandHeight()-38*scale)/2,manaW,38*scale);
   else if(tab instanceof VPlayerPanel.InfoTabExtra){tab.setVisible(false);tab.setBounds(0,0,0,0);continue;}   // star = sideboard/extra zones: not needed in Rogue (user 2026-10-17)
   else tab.setBounds(tabsX+(index++)*(tw+6*scale),l.bandY()+(l.bandHeight()-th)/2,tw,th);
   tab.setDisplayBounds(w*.12f,l.displayY(),w*.76f,Math.max(1,l.displayHeight()));
  }
  if(!bottom){   // game log behind a scroll icon right under the opponent's portrait + life (user 2026-10-16)
   LogButton lb=logButtons.get(p);if(lb==null){lb=p.add(new LogButton());logButtons.put(p,lb);}
   float lbw=Math.min(l.avatarSize()*.62f,120*scale)*side,lbh=30*scale*side;
   lb.setBounds(l.avatarX()+(l.avatarSize()-lbw)/2,l.lifeY()+LIFE*scale+4*scale,lbw,lbh);
  }
  if(bottom){   // Arena-style: fold the (big) hand away to see the whole battlefield, and back
   HandToggle t=toggles.get(p);if(t==null){t=p.add(new HandToggle(p));toggles.put(p,t);}
   // same place whether the hand is shown or folded away: centre of the bottom row above the prompt bar
   float cy=h-promptGap()-l.bandHeight()/2;
   t.setBounds(12*scale,cy-toggleSize/2,toggleSize,toggleSize);
   BoardZoom bz=boardZooms.get(p);if(bz==null){bz=p.add(new BoardZoom());boardZooms.put(p,bz);}
   bz.setBounds(12*scale+toggleSize+8*scale,cy-toggleSize/2,zoomW,toggleSize);   // board zoom: battlefield + commander
   HandZoom z=zooms.get(p);if(z==null){z=p.add(new HandZoom());zooms.put(p,z);}
   boolean handShown=t.handOpen();z.setVisible(handShown);   // hand zoom only while the hand is open, right of it
   if(handShown)z.setBounds(w*.88f+8*scale,l.displayY()+Math.max(0,(l.displayHeight()-toggleSize)/2),Math.min(zoomW,w*.12f-16*scale),toggleSize);
  }
  // Command zone (commander, item cards like Slobad's Iron Boots / Cursed Treasure) on the left as a grid of
  // separate cards; the same margin on both sides keeps the battlefield rows centred under the hand.
  int cards=p.getCommandZone().getCount();float fieldRef=Math.min(l.fieldHeight(),plan(w,h,true,true).fieldHeight());uiScale=scale;   /* both commanders the same size: the smaller (own) half decides (user 2026-10-18) */
  float czh=fieldRef*.92f*Math.max(.45f,Math.min(1f,forge.adventure.util.BattleZoomMod.scale())),gap=gap(scale);   // board zoom shrinks the commander too
  TopCardSlot top=topCards.get(p);if(top==null){top=p.add(new TopCardSlot(p));topCards.put(p,top);}
  boolean showTop=visibleTopCard(p)!=null;
  top.setVisible(showTop);
  float column=w*.15f-2*gap,commandLimit=showTop?(column-gap)/2:column;
  float cw=cards>0?grid(cards,commandLimit,czh-2*gap,gap)[4]+2*gap:0;
  float topW=showTop?Math.min((column-gap)/2,Math.max(1,(czh-26*scale)/FCardPanel.ASPECT_RATIO)):0,topH=topW*FCardPanel.ASPECT_RATIO+22*scale;
  float leftWidth=cw+(showTop?topW+(cw>0?gap:0):0);
  float reserve=reserve(l,w,leftWidth,scale);
  try{Method m=VField.class.getDeclaredMethod("setCommandZoneWidth",float.class);m.setAccessible(true);m.invoke(p.getField(),0f);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
  p.getField().setBounds(12*scale+reserve,l.fieldY(),w-24*scale-2*reserve,l.fieldHeight());
  p.getCommandZone().setBounds(12*scale,l.fieldY()+(l.fieldHeight()-czh)/2,cw,czh);
  top.setBounds(12*scale+cw+(cw>0?gap:0),l.fieldY()+(l.fieldHeight()-topH)/2,topW,topH);
 }
 /** Only the current top card, and only while the local player's actual visibility permits it.
  * A standalone renderer avoids stealing Forge's globally cached panel from Library/Flashback. */
 private static final Map<VPlayerPanel,TopCardSlot> topCards=new WeakHashMap<>();
 static CardView visibleTopCard(VPlayerPanel p){
  var ui=forge.screens.match.MatchController.instance;
  return visibleTopCard(p.getPlayer(),ui.getLocalPlayers());
 }
 static CardView visibleTopCard(forge.game.player.PlayerView player,Set<forge.game.player.PlayerView> viewers){
  if(!viewers.contains(player))return null;
  var library=player.getLibrary();
  if(library==null||library.isEmpty())return null;
  CardView card=library.get(0);
  return card!=null&&card.getZone()==ZoneType.Library&&card.canBeShownToAny(viewers)?card:null;
 }
 static final class TopCardSlot extends FDisplayObject implements forge.card.CardZoom.ActivateHandler{
  private final VPlayerPanel panel;private CardView zoomed;
  TopCardSlot(VPlayerPanel panel){this.panel=panel;}
  @Override public void draw(Graphics g){
   CardView card=visibleTopCard(panel);if(card==null)return;
   float w=getWidth(),h=getHeight(),label=Math.min(24,h*.18f),pad=Math.max(2,w*.025f);
   frame(g,Color.valueOf("b99650"),Color.valueOf("111722"),0,0,w,h,5,Math.max(1,pad*.5f));
   g.drawText("Top card",fit("Top card",w-2*pad,label-2),Color.valueOf("e0c679"),pad,1,w-2*pad,label,false,com.badlogic.gdx.utils.Align.center,true);
   forge.card.CardRenderer.drawCardWithOverlays(g,card,pad,label,w-2*pad,(w-2*pad)*FCardPanel.ASPECT_RATIO,forge.card.CardRenderer.CardStackPosition.Top);
  }
  private boolean current(CardView c){return c!=null&&c==visibleTopCard(panel);}
  @Override public boolean tap(float x,float y,int count){
   CardView card=visibleTopCard(panel);if(card==null)return false;
   if(count>1){showZoom(card);return true;}
   forge.util.ThreadUtil.invokeInGameThread(()->{
    if(!current(card))return;
    boolean selected=forge.screens.match.MatchController.instance.getGameController().selectCard(card,Collections.emptyList(),null);
    if(!selected)Gdx.app.postRunnable(()->{if(current(card))showZoom(card);});
   });return true;
  }
  @Override public boolean longPress(float x,float y){CardView c=visibleTopCard(panel);if(c==null)return false;showZoom(c);return true;}
  private void showZoom(CardView c){zoomed=c;forge.card.CardZoom.show(Collections.singletonList(c),0,this);}
  @Override public String getActivateAction(int index){return current(zoomed)?forge.screens.match.MatchController.instance.getGameController().getActivateDescription(zoomed):null;}
  @Override public void setSelectedIndex(int index){}
  @Override public void activate(int index){
   CardView card=zoomed;forge.util.ThreadUtil.invokeInGameThread(()->{if(current(card))forge.screens.match.MatchController.instance.getGameController().selectCard(card,Collections.emptyList(),null);});
  }
 }
 private static float gap(float scale){return Math.max(4,6*scale);}
 private static final Map<VPlayerPanel,HandToggle> toggles=new WeakHashMap<>();
 private static final Map<VPlayerPanel,HandZoom> zooms=new WeakHashMap<>();
 /** hand size right next to the hand: [-] magnifier 100 % [+]; tapping the magnifier resets to 100 % */
 private static final Map<VPlayerPanel,BoardZoom> boardZooms=new WeakHashMap<>();
 /** board zoom (user 2026-10-16): battlefield cards + commander; - / + at the ends, tap the middle to reset */
 static final class BoardZoom extends FDisplayObject{
  @Override public boolean tap(float x,float y,int count){
   float w=getWidth(),sc=forge.adventure.util.BattleZoomMod.scale();
   if(x<w*.28f)forge.adventure.util.BattleZoomMod.setScale(sc/1.1f);else if(x>w*.72f)forge.adventure.util.BattleZoomMod.setScale(sc*1.1f);else forge.adventure.util.BattleZoomMod.reset();
   try{var view=forge.screens.match.MatchController.getView();if(view!=null)view.revalidate();}catch(Exception ignored){}
   return true;
  }
  @Override public void draw(Graphics g){
   float w=getWidth(),h=getHeight(),r=h/2,lw=Math.max(1.5f,h*.06f);
   Color gold=Color.valueOf("e0b24f"),text=Color.valueOf("ece6d8"),dark=Color.valueOf("111722");
   g.fillRoundRect(Color.valueOf("8a7346"),0,0,w,h,r);g.fillRoundRect(isHovered()?Color.valueOf("17202e"):dark,lw,lw,w-2*lw,h-2*lw,r-lw);
   float s=h*.18f,cy=h/2;
   g.drawLine(Math.max(1.5f,h*.07f),text,w*.13f-s,cy,w*.13f+s,cy);
   g.drawLine(Math.max(1.5f,h*.07f),text,w*.87f-s,cy,w*.87f+s,cy);g.drawLine(Math.max(1.5f,h*.07f),text,w*.87f,cy-s,w*.87f,cy+s);
   float ix=w*.27f,iy=cy-h*.17f,cw=h*.13f,ch=h*.17f;   // board icon: four small cards
   for(int i=0;i<2;i++)for(int j=0;j<2;j++)g.drawRect(Math.max(1.2f,h*.045f),gold,ix+i*(cw+h*.04f),iy+j*(ch+h*.03f),cw,ch);
   g.drawText(Math.round(forge.adventure.util.BattleZoomMod.scale()*100)+"%",forge.assets.FSkinFont.forHeight(h*.5f),text,w*.42f,0,w*.34f,h,false,com.badlogic.gdx.utils.Align.center,true);
  }
 }
 // stack: semi-transparent dark grey with light text instead of Forge's light grey/black (user 2026-10-16)
 private static Field stackBack,stackFore;private static final Set<Object> styledStack=java.util.Collections.newSetFromMap(new WeakHashMap<>());
 static void styleStack(){
  try{var view=forge.screens.match.MatchController.getView();if(view==null||view.getStack()==null)return;
   if(stackBack==null){stackBack=VStack.StackInstanceDisplay.class.getDeclaredField("backColor");stackBack.setAccessible(true);
    stackFore=VStack.StackInstanceDisplay.class.getDeclaredField("foreColor");stackFore.setAccessible(true);}
   for(var child:view.getStack().getChildren())if(child instanceof VStack.StackInstanceDisplay d&&styledStack.add(d)){
    stackBack.set(d,new Color(.085f,.095f,.125f,.84f));stackFore.set(d,new Color(.95f,.93f,.88f,1f));}   // own Color objects, never shared skin colours
  }catch(Throwable ignored){}
 }
 static final class HandZoom extends FDisplayObject{
  @Override public boolean tap(float x,float y,int count){
   float w=getWidth();scaleHand(x<w*.28f?1/1.1f:x>w*.72f?1.1f:0f);return true;
  }
  @Override public void draw(Graphics g){
   float w=getWidth(),h=getHeight(),r=h/2,lw=Math.max(1.5f,h*.06f);
   Color gold=Color.valueOf("e0b24f"),text=Color.valueOf("ece6d8"),dark=Color.valueOf("111722");
   g.fillRoundRect(Color.valueOf("6a5a34"),0,0,w,h,r);g.fillRoundRect(isHovered()?Color.valueOf("17202e"):dark,lw,lw,w-2*lw,h-2*lw,r-lw);
   float s=h*.18f,cy=h/2;          // - and +
   g.drawLine(Math.max(1.5f,h*.07f),text,w*.13f-s,cy,w*.13f+s,cy);
   g.drawLine(Math.max(1.5f,h*.07f),text,w*.87f-s,cy,w*.87f+s,cy);g.drawLine(Math.max(1.5f,h*.07f),text,w*.87f,cy-s,w*.87f,cy+s);
   float mx=w*.33f,mr=h*.17f;      // magnifier
   g.drawCircle(Math.max(1.5f,h*.06f),gold,mx,cy-mr*.25f,mr);
   g.drawLine(Math.max(2f,h*.08f),gold,mx+mr*.7f,cy+mr*.45f,mx+mr*1.5f,cy+mr*1.25f);
   g.drawText(Math.round(handScale()*100)+"%",forge.assets.FSkinFont.forHeight(h*.5f),text,w*.42f,0,w*.34f,h,false,com.badlogic.gdx.utils.Align.center,true);
  }
 }
 // ------------------------------------------------------------------ hand size, adjustable in the round match menu
 private static final java.nio.file.Path HAND_FILE=java.nio.file.Paths.get("D:/forge/_mods/modern-hud/hand-zoom.txt");
 private static float handScale=-1;
 public static float handScale(){
  if(handScale<0){handScale=1f;
   try{if(java.nio.file.Files.exists(HAND_FILE))handScale=Float.parseFloat(java.nio.file.Files.readString(HAND_FILE).trim());}catch(Exception ignored){}
   handScale=Math.max(.5f,Math.min(1.5f,handScale));}
  return handScale;
 }
 /** MatchMenuMod: "Hand larger" (factor > 1) / "Hand smaller" (factor < 1); remembered in _mods/modern-hud/hand-size.txt */
 public static void scaleHand(float factor){
  handScale=factor<=0?1f:Math.max(.5f,Math.min(1.5f,Math.round(handScale()*factor*10)/10f));   // factor 0: back to 100 %
  try{java.nio.file.Files.createDirectories(HAND_FILE.getParent());java.nio.file.Files.writeString(HAND_FILE,String.valueOf(handScale));}catch(Exception ignored){}
  try{var view=forge.screens.match.MatchController.getView();if(view!=null)view.revalidate();}catch(Exception ignored){}
 }
 // ------------------------------------------------------------------ portrait: the commander instead of the profile picture
 private static Field avatarPlayer;private static final Map<String,forge.assets.FImage> squares=new HashMap<>();
 /** VAvatar.draw reads its image through this: in the Arena layout a human commander player shows the commander's art */
 public static forge.assets.FImage avatarImage(forge.assets.FImage original,VAvatar avatar){
  try{
   if(avatarPlayer==null){avatarPlayer=VAvatar.class.getDeclaredField("player");avatarPlayer.setAccessible(true);}
   forge.game.player.PlayerView player=(forge.game.player.PlayerView)avatarPlayer.get(avatar);
   if(player!=null&&player.isAI()){   // the portrait registered for this opponent (Rogue: our own enemy art, never Forge's avatar)
    Object own=((Map<?,?>)forge.Forge.getAssets().avatarImages()).get(player.getLobbyPlayerName());
    return own instanceof forge.assets.FImage f?f:original;}
   if(!bars())return original;
   if(player==null)return original;
   List<CardView> commanders=player.getCommanders();if(commanders==null||commanders.isEmpty())return original;
   forge.assets.FImageComplex art=forge.card.CardRenderer.getCardArt(commanders.get(0));
   if(art==null||art.getTexture()==null)return original;
   com.badlogic.gdx.graphics.g2d.TextureRegion src=art.getTextureRegion();
   String key=System.identityHashCode(src.getTexture())+":"+src.getRegionX()+","+src.getRegionY()+","+src.getRegionWidth()+","+src.getRegionHeight();
   if(squares.size()>40)squares.clear();
   return squares.computeIfAbsent(key,k->new SquareArt(src));
  }catch(Throwable t){return original;}
 }
 /** square cut from the middle of a card's art (faces sit a little above the centre) */
 static final class SquareArt implements forge.assets.FImage{
  private final com.badlogic.gdx.graphics.g2d.TextureRegion region;
  SquareArt(com.badlogic.gdx.graphics.g2d.TextureRegion src){
   int w=src.getRegionWidth(),h=src.getRegionHeight(),s=Math.min(w,h);
   region=new com.badlogic.gdx.graphics.g2d.TextureRegion(src.getTexture(),src.getRegionX()+(w-s)/2,src.getRegionY()+Math.max(0,(h-s)/3),s,s);
  }
  @Override public float getWidth(){return region.getRegionWidth();}
  @Override public float getHeight(){return region.getRegionHeight();}
  @Override public void draw(Graphics g,float x,float y,float w,float h){g.drawImage(region,x,y,w,h);}
 }
 private static com.badlogic.gdx.graphics.Texture handIcon;private static boolean handIconTried;
 static com.badlogic.gdx.graphics.Texture handIcon(){
  if(!handIconTried){handIconTried=true;
   try{var f=Gdx.files.absolute("D:/forge/_mods/zone-icons/hand-toggle.png");
    if(f.exists()){handIcon=new com.badlogic.gdx.graphics.Texture(f,true);handIcon.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.MipMapLinearLinear,com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);}}
   catch(Exception e){System.err.println("[ArenaBattleHud] hand icon: "+e);}}
  return handIcon;
 }
 /** round button next to the hand tabs: chevron down = fold the hand away (battlefield gets the room), up = show it again */
 static final class HandToggle extends FDisplayObject{
  private final VPlayerPanel panel;
  HandToggle(VPlayerPanel panel){this.panel=panel;}
  boolean handOpen(){var t=panel.getSelectedTab();return t!=null&&t.getDisplayArea() instanceof VZoneDisplay z&&z.getZoneType()==ZoneType.Hand;}
  @Override public boolean tap(float x,float y,int count){
   if(handOpen())panel.setSelectedTab(null);else panel.setSelectedZone(ZoneType.Hand);
   return true;
  }
  @Override public void draw(Graphics g){
   float w=getWidth(),h=getHeight(),r=Math.min(w,h)/2,cx=w/2,cy=h/2;
   boolean open=handOpen();Color gold=Color.valueOf("e0b24f");
   // hand shown: dark button with a light hand; hand folded away: gold button with a dark hand
   g.fillCircle(gold,cx,cy,r);
   if(open)g.fillCircle(isHovered()?Color.valueOf("1c2738"):Color.valueOf("111722"),cx,cy,r-Math.max(1.5f,r*.09f));
   com.badlogic.gdx.graphics.Texture icon=handIcon();
   if(icon!=null){Color c=open?Color.valueOf("ece6d8"):Color.valueOf("111722");float s=r*1.25f;
    g.setColorRGBA(c.r,c.g,c.b,1f);g.drawImage(icon,cx-s/2,cy-s/2,s,s);g.resetColorRGBA(1f);}
  }
 }
 /** best grid for n cards inside maxW x maxH: {cols, rows, cardWidth, cardHeight, gridWidth, gridHeight} */
 public static float[] grid(int n,float maxW,float maxH,float gap){
  n=Math.max(1,n);float[] best={1,n,0,0,0,0};
  for(int cols=1;cols<=n;cols++){
   int rows=(n+cols-1)/cols;
   float cw=Math.min((maxW-(cols-1)*gap)/cols,((maxH-(rows-1)*gap)/rows)/FCardPanel.ASPECT_RATIO);
   if(cw>best[2]+.5f)best=new float[]{cols,rows,cw,cw*FCardPanel.ASPECT_RATIO,cols*cw+(cols-1)*gap,rows*cw*FCardPanel.ASPECT_RATIO+(rows-1)*gap};
  }
  if(best[2]<=0)best=new float[]{1,n,1,FCardPanel.ASPECT_RATIO,1,FCardPanel.ASPECT_RATIO};
  return best;
 }
 /** Lays the command zone out as a grid (called at the end of VZoneDisplay's layout); null for every other zone. */
 public static FScrollPane.ScrollBounds commandGrid(VCardDisplayArea area,float w,float h){
  if(!ArenaMotionMod.active()||!(area instanceof VZoneDisplay zone)||zone.getZoneType()!=ZoneType.Command||w<20||h<20)return null;
  List<FDisplayObject> cards=new ArrayList<>();for(var c:area.getCardPanels())if(c.isVisible())cards.add(c);
  if(!cards.isEmpty()){
   float gap=Math.max(4,Math.min(w,h)*.025f);float[] g=grid(cards.size(),w-2*gap,h-2*gap,gap);
   int cols=(int)g[0];float y0=(h-g[5])/2;
   for(int i=0;i<cards.size();i++){
    int row=i/cols,col=i%cols,inRow=Math.min(cols,cards.size()-row*cols);
    float x0=(w-(inRow*g[2]+(inRow-1)*gap))/2;
    cards.get(i).setBounds(x0+col*(g[2]+gap),y0+row*(g[3]+gap),g[2],g[3]);
   }
  }
  try{Field f=VZoneDisplay.class.getDeclaredField("revealedPanel");f.setAccessible(true);f.set(area,null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
  return new FScrollPane.ScrollBounds(w,h);
 }
 public static final float LAND_SCALE=.65f;
 /** A shared size for both rows; allocating more board height must not enlarge every permanent. */
 public static float fieldCardHeight(float width,float height){
  float scale=Math.max(1,Math.min(1.6f,width/1600));
  float base=Math.min(height*.42f,Math.max(72,Math.min(140*scale,width*.09f)));
  return BattleZoomMod.manual()?zoomTarget(base,height,1):base;
 }
 public static float fieldCardHeight(VField field){
  float w=field.getWidth(),h=field.getHeight(),s=Math.max(1,Math.min(1.6f,w/1600));
  float base=Math.min(h*.42f,Math.max(72,Math.min(140*s,w*.09f)));
  if(!BattleZoomMod.manual())return base;
  float rear=LAND_SCALE;for(var p:field.getRow2().getCardPanels())if(!isLand(p.getCard())){rear=1;break;}
  return zoomTarget(base,h,rear);
 }
 private static float zoomTarget(float base,float height,float rear){
  float target=base*BattleZoomMod.scale(),gap=Math.max(4,Math.min(10,target*.06f));
  // Two margins per row plus the gap between rows. Without the front margins,
  // a card at the zoom limit extends below its row despite fitting mathematically.
  return Math.min(target,Math.max(24,(height-5*gap)/(1+rear)));
 }
 private static boolean isLand(CardView card){
  if(card==null||card.getCurrentState()==null||card.getCurrentState().getType()==null)return false;
  var type=card.getCurrentState().getType();return type.isLand()&&!type.isCreature();
 }
 /** Compact native rows before centering: cap permanent size, make lands 65% of that size,
  *  and reduce the spacing together with the cards. Preserve native stack/attachment ordering. */
 public static void shrinkLands(VCardDisplayArea area){
  if(!ArenaMotionMod.active()||!(area instanceof VField.FieldRow))return;
  List<FDisplayObject> kids=new ArrayList<>();for(FDisplayObject c:area.getChildren())if(c.isVisible())kids.add(c);
  kids.sort(Comparator.comparingDouble(FDisplayObject::getLeft));
  VField field;
  try{Field f=VField.FieldRow.class.getDeclaredField("this$0");f.setAccessible(true);field=(VField)f.get(area);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
  float cardHeight=fieldCardHeight(field);
  float gap=Math.max(4,Math.min(10,cardHeight*.06f));
  float prevOld=0,prevNew=0,prevScale=1;boolean first=true;
  VCardDisplayArea.CardAreaPanel previous=null;
  for(FDisplayObject c:kids){
   if(!(c instanceof VCardDisplayArea.CardAreaPanel panel)||c.getHeight()<=0)continue;
   // Equipment attached to a land follows that land's size, retaining its native stack offsets.
   VCardDisplayArea.CardAreaPanel root=panel;
   Set<VCardDisplayArea.CardAreaPanel> seen=new HashSet<>();
   while(root.getAttachedToPanel()!=null&&seen.add(root))root=root.getAttachedToPanel();
   float target=cardHeight*(isLand(root.getCard())?LAND_SCALE:1);
   float scale=Math.min(1,target/c.getHeight());
   float left=first?c.getLeft():prevNew+(c.getLeft()-prevOld)*prevScale;
   // Native stacks have very narrow edges. Give the small land fans a 32% step
   // (at least 14 layout units) so each card is exposed and can still be selected.
   if(previous!=null&&panel.getPrevPanelInStack()==previous&&isLand(panel.getCard()))
    left=prevNew+Math.max(14,previous.getWidth()*.32f);
   prevOld=c.getLeft();prevNew=left;prevScale=scale;first=false;
   float w=c.getWidth()*scale,h=c.getHeight()*scale;
   // Align our rear row near the hand instead of centring tiny lands in a tall empty row.
   float top=field.isFlipped()?gap:Math.max(gap,area.getHeight()-h-gap);
   c.setBounds(left,top,w,h);
   previous=panel;
  }
 }
 /** The Flashback tab lists everything castable from other zones - including the command zone. Forge has one card
  *  panel per card, so showing those cards in both places made them fight over position and touch handling (the
  *  Cursed Treasure could not be picked). Command cards stay in the always-visible command zone. */
 public static Iterable<CardView> zoneCards(Iterable<CardView> cards,VZoneDisplay zone){
  if(cards==null||!ArenaMotionMod.active()||zone.getZoneType()!=ZoneType.Flashback)return cards;
  List<CardView> keep=new ArrayList<>();for(CardView c:cards)if(c!=null&&c.getZone()!=ZoneType.Command)keep.add(c);
  return keep;
 }
 public static boolean zoneVertical(boolean original,VZoneDisplay zone){return ArenaMotionMod.active()&&zone.getWidth()>zone.getHeight()?false:original;}
 private static Method promptStyle;
 /** height of the prompt bar that lies over the bottom of the board (0 outside the Arena layout) */
 static float promptGap(){return bars()?promptHeight(VPrompt.HEIGHT):0;}
 /** MatchScreen.doLayout: the board reaches down to the screen edge, the transparent prompt bar lies over it */
 public static float scrollerGap(float promptHeight){return bars()?0:promptHeight;}
 public static void prompt(VPrompt p,float w,float h){
  if(!ArenaMotionMod.active()||w<900)return;
  try{if(promptStyle==null)promptStyle=Class.forName("forge.screens.match.views.PromptStyleMod").getMethod("layout",VPrompt.class,float.class,float.class);
   promptStyle.invoke(null,p,w,h);return;}catch(ReflectiveOperationException e){promptStyle=null;}
  float scale=Math.max(.8f,Math.min(1.6f,w/1600)),bw=112*scale,gap=10*scale;
  p.getBtnOk().setBounds(w-2*bw-2*gap,6,bw,h-12);p.getBtnCancel().setBounds(w-bw-gap,6,bw,h-12);
  try{Field f=VPrompt.class.getDeclaredField("lblMessage");f.setAccessible(true);((FDisplayObject)f.get(p)).setBounds(w*.22f,4,w*.52f-2*bw,Math.max(10,h-8));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 // ------------------------------------------------------------------ slimmer bars, more room for the board
 private static boolean bars(){return ArenaMotionMod.active()&&forge.Forge.isLandscapeMode();}
 /** no line between the two player panels in the Arena layout (FieldScroller.drawOverlay) */
 public static void divider(Graphics g,float lineWidth,FSkinColor color,float x1,float y1,float x2,float y2){
  if(!bars())g.drawLine(lineWidth,color,x1,y1,x2,y2);
 }
 /** the board image starts at the screen's left edge, not at the battlefield's (the command-zone column stayed bare) */
 public static float boardLeft(float x){return bars()?0:x;}
 /** FieldScroller.drawBackground's board image: from the very top (Forge started it below the opponent's tab row) */
 public static void boardImage(Graphics g,forge.assets.FImage image,float x,float y,float w,float h){
  // ... and down to the screen edge: the prompt bar floats over the board, no bare backdrop strip under it (user 2026-10-17)
  if(bars()&&h>0){float ny=Math.min(y,0),nh=Math.max(y+h,forge.Forge.getScreenHeight())-ny;if(nh>h){float nw=w*nh/h;x-=(nw-w)/2;w=nw;h=nh;y=ny;}}
  g.drawImage(image,x,y,w,h);
 }
 /** top menu bar (Game / Players / Log / Dev) */
 public static float headerHeight(float original){return bars()?0:original;}
 /** bottom prompt bar (message, OK / End Turn) */
 // from the CURRENT screen height: VPrompt.HEIGHT is fixed at startup and went tiny after a monitor/DPI switch (user 2026-10-17)
 public static float promptHeight(float original){return bars()?Math.round(Math.max(30,Math.min(BattleUiSettings.touch()?150:110,forge.Forge.getScreenHeight()*(BattleUiSettings.touch()?.07f:.045f)))*BattleUiSettings.size(1)):original;}   // x Settings > Battle UI: OK / End Turn
 // ------------------------------------------------------------------ keyboard shortcuts (Settings > Keys, user 2026-10-17)
 /** BattleZoomMod.key calls this first (reflection); true = the key was a bound battle action */
 public static boolean battleKey(int key){
  if(!enabled0()||Gdx.input==null)return false;
  if(Tour.current!=null){Tour.current.key(key);return true;}   // the controls tour takes every key while it is open
  if(Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)||Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT)
   ||Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)||Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT))return false;
  int action=BattleUiSettings.actionFor(key);if(action<0)return false;
  var view=forge.screens.match.MatchController.getView();if(view==null)return false;
  VPlayerPanel bottom=null;for(VPlayerPanel p:forge.screens.match.MatchScreen.getPlayerPanels().values())if((Boolean)member(p,"isBottomPlayer"))bottom=p;
  switch(BattleUiSettings.ACTIONS[action]){
   case "ok"->{var pr=view.getActivePrompt();if(pr!=null&&pr.getBtnOk().isEnabled()&&pr.getBtnOk().isVisible())pr.getBtnOk().trigger();}
   case "cancel"->{var pr=view.getActivePrompt();if(pr!=null&&pr.getBtnCancel().isEnabled()&&pr.getBtnCancel().isVisible())pr.getBtnCancel().trigger();}
   case "skip"->{SkipButton sk=bottom==null?null:skips.get(bottom);if(sk!=null)sk.tap(0,0,1);}
   case "log"->{try{var log=view.getLog();if(log.isVisible())log.hide();else log.show();}catch(Throwable ignored){}}
   case "deck"->forge.screens.match.DeckPeekMod.showDeck();
   case "hand"->{HandToggle t=bottom==null?null:toggles.get(bottom);if(t!=null)t.tap(0,0,1);}
   case "zoomIn"->BattleZoomMod.setScale(BattleZoomMod.scale()*1.1f);
   case "zoomOut"->BattleZoomMod.setScale(BattleZoomMod.scale()/1.1f);
   case "zoomReset"->BattleZoomMod.reset();
   default->{return false;}
  }
  try{view.revalidate();}catch(Exception ignored){}
  Gdx.graphics.requestRendering();
  return true;
 }
 private static boolean enabled0(){return ArenaMotionMod.active()&&forge.Forge.isLandscapeMode();}
 // ------------------------------------------------------------------ controls tour in the first battle (user 2026-10-17)
 // Shows once (Rogue profile flag), step by step: what each button / area does and how to use it - no rules lessons.
 static com.badlogic.gdx.math.Rectangle lastPlaque;
 static com.badlogic.gdx.math.Rectangle rectOf(FDisplayObject o){
  if(o==null||!o.isVisible()||o.getWidth()<=0||o.screenPos==null||o.screenPos.width<=0)return null;return new com.badlogic.gdx.math.Rectangle(o.screenPos);
 }
 static com.badlogic.gdx.math.Rectangle union(com.badlogic.gdx.math.Rectangle a,com.badlogic.gdx.math.Rectangle b){if(a==null)return b;if(b==null)return a;return new com.badlogic.gdx.math.Rectangle(a).merge(b);}
 static final class Tour extends FDisplayObject{
  static Tour current;
  record Step(String title,String text,java.util.function.Supplier<com.badlogic.gdx.math.Rectangle> target){}
  final List<Step> steps=new ArrayList<>();int index;final long start=System.currentTimeMillis();
  Tour(VPlayerPanel me,VPlayerPanel foe){
   var view=forge.screens.match.MatchController.getView();boolean touch=BattleUiSettings.touch();   // phone / tablet: touch wording, no keys (user 2026-11-05)
   steps.add(new Step("Battle controls",touch?"A quick tour of the battle screen. Tap anywhere for the next tip - the Back button skips the tour.":"A quick tour of the battle screen. Tap anywhere (or press a key) for the next tip - Escape skips the tour.",()->null));
   steps.add(new Step("Your hand",touch?"Tap a card to play it. Long-press a card to see it big.":"Tap a card to play it. Hover a card or right-click it to see it big.",()->{var t=me.getSelectedTab();return t==null?null:rectOf(t.getDisplayArea());}));
   steps.add(new Step("Show / hide the hand","Folds your hand away to see the whole battlefield - tap again to bring it back."+(touch?"":" Key: H."),()->rectOf(toggles.get(me))));
   steps.add(new Step("Board zoom","- and + make the cards on the battlefield smaller or bigger, the middle resets."+(touch?"":" Keys: Page Up / Page Down / Home."),()->rectOf(boardZooms.get(me))));
   steps.add(new Step("OK and End Turn","OK confirms what the message on the left asks for (keep the hand, attack, pass...). End Turn passes until your turn ends."+(touch?"":" Keys: Space / E."),()->{var p=view.getActivePrompt();return p==null?null:union(rectOf(p.getBtnOk()),rectOf(p.getBtnCancel()));}));
   steps.add(new Step("The message line","Bottom left: what the game is waiting for right now. Tap it to read a long message in full.",()->{var p=view.getActivePrompt();if(p==null)return null;var r=rectOf(p);if(r==null)return null;r.width*=.55f;return r;}));
   steps.add(new Step("Turn panel","Whose turn it is and which step you are in. The blue steps are the main phases. Drag it anywhere you like - double-tap puts it back.",()->lastPlaque==null?null:new com.badlogic.gdx.math.Rectangle(lastPlaque)));
   steps.add(new Step("Skip","Passes everything until the main phase of your next turn - for when you don't want to respond. Tap again to stop."+(touch?"":" Key: S."),()->rectOf(skips.get(me))));
   steps.add(new Step("Your portrait and life","Your commander and your life total. The glowing frame shows whose turn it is.",()->rectOf(me.getAvatar())));
   steps.add(new Step("Mana and deck","Mana you have floating appears in this tile. Deck shows your deck list."+(touch?"":" Key: D."),()->union(rectOf(me.getManaPoolTab()),rectOf(deckButtons.get(me)))));
   steps.add(new Step("Zones","Library, graveyard, exile and more - tap a tab to look inside, tap it again to close it.",()->{com.badlogic.gdx.math.Rectangle r=null;for(var t:me.getTabs())if(t!=me.getManaPoolTab()&&!(t instanceof VPlayerPanel.InfoTabExtra))r=union(r,rectOf(t));return r;}));
   steps.add(new Step("Command zone","Your commander waits here. Tap it to cast it - also again after it died.",()->rectOf(me.getCommandZone())));
   steps.add(new Step("The enemy","Their portrait and life. Log (the scroll) lists everything that happened."+(touch?"":" Key: L."),()->union(rectOf(foe.getAvatar()),rectOf(logButtons.get(foe)))));
   steps.add(new Step("The stack","Spells and abilities waiting to resolve show up in a list in the middle of the board. While it is open you can still answer with instants before they resolve.",()->rectOf(view.getStack())));
   steps.add(new Step("Battle menu","Top left: concede, zoom, hand size and more.",()->{for(FDisplayObject c:view.getChildren())if(c.getClass().getSimpleName().equals("MenuButton"))return rectOf(c);return null;}));
   steps.add(new Step("Make it yours",touch?"Button sizes and the turn panel: battle menu > Battle settings, or Rogue Commanders > Settings > Battle UI. Good luck!":"Button sizes and keyboard shortcuts: Rogue Commanders > Settings > Battle UI / Keys. Good luck!",()->null));
  }
  void next(){index++;if(index>=steps.size())finish();Gdx.graphics.requestRendering();}
  void finish(){current=null;setVisible(false);try{forge.adventure.scene.RogueCommandersScene.setBattleTourDone();}catch(Throwable ignored){}}
  void key(int k){if(k==com.badlogic.gdx.Input.Keys.ESCAPE||k==com.badlogic.gdx.Input.Keys.BACK)finish();else if(k==com.badlogic.gdx.Input.Keys.LEFT){index=Math.max(0,index-1);Gdx.graphics.requestRendering();}else next();}   // Left = back
  @Override public boolean tap(float x,float y,int count){if(System.currentTimeMillis()-start>400)next();return true;}
  @Override public boolean press(float x,float y){return true;}
  @Override public boolean release(float x,float y){return true;}
  @Override public boolean pan(float x,float y,float dx,float dy,boolean moving){return true;}
  @Override public boolean fling(float vx,float vy){return true;}
  @Override public boolean zoom(float x,float y,float amount){return true;}
  @Override public boolean longPress(float x,float y){return true;}
  @Override public void draw(Graphics g){
   if(index>=steps.size())return;
   float w=getWidth(),h=getHeight(),s=Math.max(.8f,Math.min(2.2f,h/900f));Step st=steps.get(index);
   com.badlogic.gdx.math.Rectangle r;try{r=st.target().get();}catch(Throwable e){r=null;}
   Color shade=new Color(0,0,0,.62f);
   if(r==null)g.fillRect(shade,0,0,w,h);
   else{float pad=6*s,x0=Math.max(0,r.x-pad),y0=Math.max(0,r.y-pad),x1=Math.min(w,r.x+r.width+pad),y1=Math.min(h,r.y+r.height+pad);
    g.fillRect(shade,0,0,w,y0);g.fillRect(shade,0,y1,w,h-y1);g.fillRect(shade,0,y0,x0,y1-y0);g.fillRect(shade,x1,y0,w-x1,y1-y0);
    float pulse=.6f+.4f*(float)Math.sin(System.currentTimeMillis()/250.0);
    g.drawRect(Math.max(2,3*s),new Color(MINE.r,MINE.g,MINE.b,pulse),x0,y0,x1-x0,y1-y0);}
   // text box next to the target (below it if there is room, else above; centred without a target)
   float bw=Math.min(w*.42f,560*s),pad=16*s;FSkinFont tf=FSkinFont.forHeight(26*s),bf=FSkinFont.forHeight(19*s),hf=FSkinFont.forHeight(14*s);
   float th=bf.getWrappedBounds(st.text(),bw-2*pad).height+bf.getLineHeight()*.6f,bh=pad*2+30*s+th+36*s,bx,by;   // room between the text and the footer
   if(r==null){bx=(w-bw)/2;by=(h-bh)/2;}
   else{bx=Math.max(10*s,Math.min(w-bw-10*s,r.x+r.width/2-bw/2));by=r.y+r.height+14*s+bh<h-10*s?r.y+r.height+14*s:Math.max(10*s,r.y-14*s-bh);
    if(by<r.y+r.height&&by+bh>r.y){bx=r.x>w/2?Math.max(10*s,r.x-bw-14*s):Math.min(w-bw-10*s,r.x+r.width+14*s);by=Math.max(10*s,Math.min(h-bh-10*s,r.y));}}
   frame(g,MINE,new Color(.05f,.06f,.10f,1f),bx,by,bw,bh,12*s,Math.max(1.5f,2*s));   // nested fills: no drawRoundRect cross lines
   g.drawText(st.title(),tf,MINE,bx+pad,by+pad,bw-2*pad,30*s,false,com.badlogic.gdx.utils.Align.left,true);
   g.drawText(st.text(),bf,TEXT,bx+pad,by+pad+30*s,bw-2*pad,th+4,true,com.badlogic.gdx.utils.Align.left,false);
   g.drawText((index+1)+" / "+steps.size()+(BattleUiSettings.touch()?"   -   tap: next   -   Back: skip":"   -   tap / any key: next   -   Left: back   -   Escape: skip"),hf,MUTED,bx+pad,by+bh-pad-14*s,bw-2*pad,16*s,false,com.badlogic.gdx.utils.Align.right,true);
   Gdx.graphics.requestRendering();
  }
 }
 /** start the tour once, in the first Rogue battle, as soon as the hand is on screen */
 static void maybeTour(VPlayerPanel me){
  if(Tour.current!=null)return;
  try{if(!forge.adventure.scene.RogueCommandersScene.battleTourDue())return;}catch(Throwable e){return;}
  var view=forge.screens.match.MatchController.getView();if(view==null)return;
  VPlayerPanel foe=null;for(VPlayerPanel p:forge.screens.match.MatchScreen.getPlayerPanels().values())if(p!=me)foe=p;if(foe==null)return;
  Tour t=new Tour(me,foe);t.setBounds(0,0,view.getWidth(),view.getHeight());view.add(t);Tour.current=t;
 }
 /** the idle status (Priority / Turn / Phase / Stack) is four lines - one line fits the slim bar */
 public static String promptText(String message){
  if(!bars()||message==null||message.indexOf(10)<0)return message;
  String[] lines=message.split("\\R");if(lines.length<=2)return message;
  StringBuilder b=new StringBuilder();for(String l:lines){if(l.isBlank())continue;if(b.length()>0)b.append("     |     ");b.append(l.trim());}
  return b.toString();
 }
 private static final Map<String,FSkinFont> promptFonts=new HashMap<>();
 /** prompt font, smaller when a long message would not fit the slim bar */
 public static FSkinFont promptFont(FSkinFont font,FDisplayObject label){
  if(!bars()||font==null)return font;
  String text;try{Field f=label.getClass().getDeclaredField("this$0");f.setAccessible(true);text=promptText(((VPrompt)f.get(label)).getMessage());}catch(ReflectiveOperationException|ClassCastException e){return font;}
  if(text==null||text.isEmpty())return font;
  float w=label.getWidth()-2*VPrompt.PADDING,h=label.getHeight()-2*VPrompt.PADDING;
  if(w<20||h<8)return font;
  if(promptFonts.size()>300)promptFonts.clear();
  return promptFonts.computeIfAbsent(text+"|"+Math.round(w)+"|"+Math.round(h),k->{
   FSkinFont best=font;
   for(int size=14;size>=8;size--){FSkinFont f=FSkinFont.get(size);best=f;if(f.getWrappedBounds(text,w).height<=h)break;}
   return best.getLineHeight()<font.getLineHeight()||best.getWrappedBounds(text,w).height<font.getWrappedBounds(text,w).height?best:font;
  });
 }
 /** Both battlefields get the same height: the hand row is taken from the bottom player's half only, so without
  *  this the opponent had a huge empty field and our cards sat high up, far from the hand. */
 public static void split(float w,float h){
  var panels=forge.screens.match.MatchScreen.getPlayerPanels().values();if(panels.size()!=2)return;
  VPlayerPanel top=null,bottom=null;for(VPlayerPanel p:panels){if((Boolean)member(p,"isBottomPlayer"))bottom=p;else top=p;}
  if(top==null||bottom==null||!enabled(bottom,w,h/2))return;
  float th=h/2,field=0;
  for(int i=0;i<4;i++){   // hand/band sizes depend on the panel height: settle in a few steps
   Layout lt=plan(w,th,false,top.getSelectedTab()!=null),lb=plan(w,h-th,true,bottom.getSelectedTab()!=null);
   field=(h-lt.bandHeight()-lt.displayHeight()-lb.bandHeight()-lb.displayHeight())/2;
   th=lt.bandHeight()+lt.displayHeight()+field;
  }
  if(field<80)return;
  th=Math.round(th);
  top.setBounds(0,0,w,th);bottom.setBounds(0,th,w,h-th);
 }
 // ------------------------------------------------------------------ whose turn / which phase
 static final Color MINE=Color.valueOf("f2cf63"),ENEMY=Color.valueOf("e8705a"),TEXT=Color.valueOf("f3e7c9"),MUTED=Color.valueOf("b9a586"),INK=Color.valueOf("1a120a");
 /** every step of a turn in order, in three rows: beginning + first main phase / combat / second main phase + end */
 private static final String[][] STEPS={{"Untap","Upkeep","Draw","Main 1"},{"Combat","Attack","Block","1st Strike","Damage"},{"End Combat","Main 2","End Step","Cleanup"}};
 private static final String[] ORDER={"UNTAP","UPKEEP","DRAW","MAIN1","COMBAT_BEGIN","COMBAT_DECLARE_ATTACKERS","COMBAT_DECLARE_BLOCKERS","COMBAT_FIRST_STRIKE_DAMAGE","COMBAT_DAMAGE","COMBAT_END","MAIN2","END_OF_TURN","CLEANUP"};
 private static GameView game(){var c=forge.screens.match.MatchController.instance;return c==null?null:c.getGameView();}
 private static boolean isTurnOf(GameView game,VPlayerPanel p){return game!=null&&game.getPlayerTurn()!=null&&p.getPlayer()!=null&&game.getPlayerTurn().getId()==p.getPlayer().getId();}
 /** 0..12 in turn order, -1 if unknown */
 public static int stepIndex(PhaseType phase){if(phase!=null)for(int i=0;i<ORDER.length;i++)if(ORDER[i].equals(phase.name()))return i;return -1;}
 public static String phaseName(PhaseType phase){
  if(phase==null)return "";
  return switch(phase.name()){case "UNTAP"->"Untap";case "UPKEEP"->"Upkeep";case "DRAW"->"Draw Step";case "MAIN1"->"Main Phase 1";
   case "COMBAT_BEGIN"->"Beginning of Combat";case "COMBAT_DECLARE_ATTACKERS"->"Declare Attackers";case "COMBAT_DECLARE_BLOCKERS"->"Declare Blockers";
   case "COMBAT_FIRST_STRIKE_DAMAGE"->"First Strike Damage";case "COMBAT_DAMAGE"->"Combat Damage";case "COMBAT_END"->"End of Combat";
   case "MAIN2"->"Main Phase 2";case "END_OF_TURN"->"End Step";case "CLEANUP"->"Cleanup";default->phase.nameForUi;};
 }
 private static final Map<String,FSkinFont> fitted=new HashMap<>();
 /** largest skin font whose text fits maxW x maxH (cached per text and box) */
 private static final Color MAIN_PHASE=Color.valueOf("6fb7ff");
 private static int fitSize(String text,float maxW,float maxH){
  int size=Math.max(6,Math.min(80,Math.round(maxH)));FSkinFont f=FSkinFont.get(size);
  while(size>6&&(f.getLineHeight()>maxH||f.getBounds(text).width>maxW))f=FSkinFont.get(--size);
  return size;
 }
 private static FSkinFont fit(String text,float maxW,float maxH){
  return fitted.computeIfAbsent(text+"|"+Math.round(maxW)+"|"+Math.round(maxH),k->{
   int size=Math.max(6,Math.min(80,Math.round(maxH)));FSkinFont f=FSkinFont.get(size);
   while(size>6&&(f.getLineHeight()>maxH||f.getBounds(text).width>maxW))f=FSkinFont.get(--size);
   return f;
  });
 }
 private static Color alpha(Color c,float a){return new Color(c.r,c.g,c.b,c.a*a);}
 static final Color BG=new Color(.075f,.058f,.042f,1);
 /** opaque blend of the plaque background towards c (Forge's round rects overlap internally, so translucent fills show seams) */
 private static Color mix(Color c,float t){return new Color(BG.r+(c.r-BG.r)*t,BG.g+(c.g-BG.g)*t,BG.b+(c.b-BG.b)*t,1);}
 /** border without the overlapping corner strokes of Graphics.drawRoundRect: outer fill, inner fill */
 private static void frame(Graphics g,Color border,Color fill,float x,float y,float w,float h,float r,float lw){
  g.fillRoundRect(border,x,y,w,h,r);g.fillRoundRect(fill,x+lw,y+lw,w-2*lw,h-2*lw,Math.max(0,r-lw));
 }
 public static final float PLAQUE_W=340,PLAQUE_H=168;
 /** turn plaque: who is playing, the turn number, the step by name and every step of the turn (current one lit). (x,y)=top-left */
 public static void drawTurnPlaque(Graphics g,float x,float y,float s,boolean mine,int turn,PhaseType phase){
  float pw=PLAQUE_W*s,ph=PLAQUE_H*s;Color accent=mine?MINE:ENEMY;
  g.fillRoundRect(new Color(0,0,0,.35f),x+3*s,y+4*s,pw,ph,10*s);
  frame(g,accent,BG,x,y,pw,ph,10*s,2*s);
  frame(g,mix(accent,.35f),BG,x+5*s,y+5*s,pw-10*s,ph-10*s,7*s,Math.max(1,s*.8f));
  // pointer towards the player whose turn it is
  float cx=x+pw/2,t=10*s;
  if(mine)g.fillTriangle(accent,cx-t,y+ph,cx+t,y+ph,cx,y+ph+t);else g.fillTriangle(accent,cx-t,y,cx+t,y,cx,y-t);
  String title=mine?"YOUR TURN":"ENEMY TURN";
  g.drawText(title,fit(title,pw-24*s,34*s),accent,x+12*s,y+8*s,pw-24*s,34*s,false,1,true);
  String sub="Turn "+Math.max(1,turn)+"  -  "+phaseName(phase);
  g.drawText(sub,fit(sub,pw-24*s,20*s),TEXT,x+12*s,y+44*s,pw-24*s,20*s,false,1,true);
  int active=stepIndex(phase),index=0;float gap=4*s,bh=26*s,rowGap=5*s,top=y+72*s,inner=pw-24*s;
  // one size that fits every label: compare font SIZES - on 1080p several sizes share one line height (user 2026-10-17)
  int minSize=99;
  for(String[] row:STEPS){float bw=(inner-(row.length-1)*gap)/row.length;for(String label:row)minSize=Math.min(minSize,fitSize(label,bw-8*s,bh-7*s));}
  FSkinFont small=FSkinFont.get(minSize);
  for(int r=0;r<STEPS.length;r++){
   String[] row=STEPS[r];float bw=(inner-(row.length-1)*gap)/row.length,by=top+r*(bh+rowGap);
   for(int i=0;i<row.length;i++,index++){
    float bx=x+12*s+i*(bw+gap);
    boolean main=row[i].startsWith("Main");   // main phases (where you cast your cards) in their own colour (user 2026-10-17)
    if(index==active){g.fillRoundRect(main?MAIN_PHASE:accent,bx,by,bw,bh,5*s);g.drawText(row[i],small,INK,bx,by,bw,bh,false,1,true);}
    else if(index<active){g.fillRoundRect(mix(main?MAIN_PHASE:accent,.22f),bx,by,bw,bh,5*s);g.drawText(row[i],small,MUTED,bx,by,bw,bh,false,1,true);}
    else if(main){frame(g,MAIN_PHASE,BG,bx,by,bw,bh,5*s,Math.max(1,s*1.2f));g.drawText(row[i],small,MAIN_PHASE,bx,by,bw,bh,false,1,true);}
    else{frame(g,mix(MUTED,.45f),BG,bx,by,bw,bh,5*s,Math.max(1,s*.8f));g.drawText(row[i],small,alpha(MUTED,.85f),bx,by,bw,bh,false,1,true);}
   }
  }
 }
 /** compact turn panel (Android default, Settings: Compact): one line + a strip of the 13 steps, Skip on the right (user 2026-11-05) */
 public static final float COMPACT_W=400,COMPACT_H=60,COMPACT_SKIP=84;
 public static void drawCompactTurn(Graphics g,float x,float y,float s,boolean mine,int turn,PhaseType phase){
  float pw=COMPACT_W*s,ph=COMPACT_H*s;Color accent=mine?MINE:ENEMY;
  g.fillRoundRect(new Color(0,0,0,.30f),x+2*s,y+3*s,pw,ph,9*s);
  frame(g,accent,BG,x,y,pw,ph,9*s,Math.max(1,1.6f*s));
  float pad=10*s,info=pw-(COMPACT_SKIP+12)*s,th=24*s,ty=y+5*s;
  String title=mine?"YOUR TURN":"ENEMY TURN",sub="Turn "+Math.max(1,turn)+" - "+phaseName(phase);
  FSkinFont tf=fit(title,info*.42f,th);float tw=Math.min(info*.42f,tf.getBounds(title).width+2);
  g.drawText(title,tf,accent,x+pad,ty,tw,th,false,com.badlogic.gdx.utils.Align.left,true);
  float sx=x+pad+tw+8*s,sw=Math.max(10,x+info-pad-sx);
  g.drawText(sub,fit(sub,sw,th*.8f),TEXT,sx,ty,sw,th,false,com.badlogic.gdx.utils.Align.left,true);
  // the 13 steps as ticks in three groups (beginning / combat / end), the current one lit and taller, main phases blue
  int active=stepIndex(phase),index=0;float gap=2.5f*s,groupGap=8*s,avail=info-2*pad-2*groupGap,seg=(avail-10*gap)/13f,bh=7*s,by=y+ph-15*s,bx=x+pad;
  for(String[] row:STEPS){
   for(int i=0;i<row.length;i++,index++){boolean main=row[i].startsWith("Main");Color c=main?MAIN_PHASE:accent;
    if(index==active)g.fillRoundRect(c,bx,by-3*s,seg,bh+6*s,2*s);
    else if(index<active)g.fillRoundRect(mix(c,.35f),bx,by,seg,bh,2*s);
    else if(main)frame(g,MAIN_PHASE,BG,bx,by,seg,bh,2*s,Math.max(1,s));
    else g.fillRoundRect(mix(MUTED,.28f),bx,by,seg,bh,2*s);
    bx+=seg+(i<row.length-1?gap:0);}
   bx+=groupGap;}
 }
 /** the turn panel and its Skip button as one piece on the match screen: drag it anywhere, double-tap puts it back,
  *  the spot is saved (user 2026-11-05: "mach das ding dragable"). Compact or detailed from Settings > Battle UI. */
 static final class TurnPanel extends FContainer{
  static final Map<Object,TurnPanel> byView=new WeakHashMap<>();
  final SkipButton skip=add(new SkipButton());
  boolean compact,mine,dragging;int turn;PhaseType phase;float ps,defX,defY,viewW,viewH,dragX,dragY;long placedAt,lastPan;
  static float pointer(float ps){return 10*ps;}
  static TurnPanel place(VPlayerPanel p,float plaqueX,float ps,boolean mine,GameView game){
   var view=forge.screens.match.MatchController.getView();if(view==null)return null;
   TurnPanel t=byView.get(view);boolean child=false;
   if(t!=null)for(FDisplayObject c:view.getChildren())if(c==t){child=true;break;}
   if(!child){t=new TurnPanel();view.add(t);byView.put(view,t);}
   t.compact=BattleUiSettings.compactTurn();float ps0=ps;if(t.compact&&BattleUiSettings.touch())ps*=1.3f;   /* phone: desktop pixels on a small screen */t.mine=mine;t.turn=game.getTurn();t.phase=game.getPhase();t.ps=ps;t.placedAt=System.currentTimeMillis();
   t.viewW=view.getWidth();t.viewH=view.getHeight();
   float px=p.screenPos.x-view.screenPos.x,py=p.screenPos.y-view.screenPos.y,w,h;
   if(t.compact){w=COMPACT_W*ps;h=COMPACT_H*ps;t.defX=px+plaqueX+PLAQUE_W*ps0-w;t.defY=py-h-6*ps;}   // right edge where the big plaque ended, just above the middle line (not over your creatures)
   else{w=PLAQUE_W*ps;h=pointer(ps)+PLAQUE_H*ps+46*ps;t.defX=px+plaqueX;t.defY=py-PLAQUE_H*.28f*ps-pointer(ps);}
   if(t.dragging&&System.currentTimeMillis()-t.lastPan>300)t.endDrag();   // no panStop reached us: the drag ended anyway
   if(t.dragging){t.setSize(w,h);}
   else{float x=t.defX+BattleUiSettings.turnDx()*t.viewW,y=t.defY+BattleUiSettings.turnDy()*t.viewH;t.setBounds(clamp(x,0,t.viewW-w),clamp(y,0,t.viewH-h),w,h);}
   if(t.compact)t.skip.setBounds(w-(COMPACT_SKIP+7)*ps,7*ps,COMPACT_SKIP*ps,h-14*ps);
   else t.skip.setBounds(w*.25f,pointer(ps)+PLAQUE_H*ps+16*ps,w*.5f,30*ps);
   return t;
  }
  static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(Math.max(lo,hi),v));}
  boolean stale(){return System.currentTimeMillis()-placedAt>400;}   // the HUD stopped placing it (other layout, game over)
  @Override protected void doLayout(float w,float h){}
  @Override public void draw(Graphics g){if(!stale())super.draw(g);}
  @Override public void buildTouchListeners(float x,float y,List<FDisplayObject> listeners){if(!stale())super.buildTouchListeners(x,y,listeners);}
  @Override protected void drawBackground(Graphics g){
   if(compact)drawCompactTurn(g,0,0,ps,mine,turn,phase);else drawTurnPlaque(g,0,pointer(ps),ps,mine,turn,phase);
   lastPlaque=new com.badlogic.gdx.math.Rectangle(screenPos.x,screenPos.y+(compact?0:pointer(ps)),getWidth(),compact?getHeight():PLAQUE_H*ps);   // for the controls tour
  }
  @Override public boolean pan(float x,float y,float dx,float dy,boolean moreVertical){
   if(!dragging){dragging=true;dragX=getLeft();dragY=getTop();}
   lastPan=System.currentTimeMillis();dragX+=dx;dragY+=dy;setPosition(clamp(dragX,0,viewW-getWidth()),clamp(dragY,0,viewH-getHeight()));Gdx.graphics.requestRendering();return true;}
  @Override public boolean panStop(float x,float y){if(!dragging)return false;endDrag();return true;}
  void endDrag(){dragging=false;BattleUiSettings.setTurnOffset(viewW<=0?0:(getLeft()-defX)/viewW,viewH<=0?0:(getTop()-defY)/viewH);BattleUiSettings.save();}
  @Override public boolean tap(float x,float y,int count){
   if(count>=2){BattleUiSettings.resetTurnPosition();BattleUiSettings.save();Gdx.graphics.requestRendering();}return true;}
 }
 private static final Map<VPlayerPanel,SkipButton> skips=new WeakHashMap<>();
 private static final Map<VPlayerPanel,DeckButton> deckButtons=new WeakHashMap<>();
 private static final Map<VPlayerPanel,LogButton> logButtons=new WeakHashMap<>();
 /** opens / closes the game log; a small parchment scroll as icon (user 2026-10-16: "Log Button mit einer Schriftrolle") */
 static final class LogButton extends FDisplayObject{
  @Override public boolean tap(float x,float y,int count){
   try{var log=forge.screens.match.MatchController.getView().getLog();if(log.isVisible())log.hide();else log.show();}catch(Throwable t){}   // its own anchor: full width, not squeezed at the right edge
   return true;}
  @Override public void draw(Graphics g){
   float w=getWidth(),h=getHeight(),r=h/2,lw=Math.max(1.5f,h*.07f);
   g.fillRoundRect(Color.valueOf("8a7346"),0,0,w,h,r);
   g.fillRoundRect(isHovered()?Color.valueOf("17202e"):Color.valueOf("111722"),lw,lw,w-2*lw,h-2*lw,r-lw);
   float ih=h*.64f,iw=ih*.86f,ix=h*.42f,iy=(h-ih)/2;   // scroll: parchment between two wooden rolls, three ink lines
   Color paper=Color.valueOf("efe0b4"),roll=Color.valueOf("b98a4c"),ink=Color.valueOf("6e5130");
   g.fillRect(paper,ix+iw*.1f,iy+ih*.1f,iw*.8f,ih*.8f);
   g.fillRoundRect(roll,ix,iy,iw,ih*.2f,ih*.1f);g.fillRoundRect(roll,ix,iy+ih*.8f,iw,ih*.2f,ih*.1f);
   for(int i=0;i<3;i++){float ly=iy+ih*(.36f+i*.15f);g.drawLine(Math.max(1,ih*.05f),ink,ix+iw*.24f,ly,ix+iw*(i==2?.56f:.76f),ly);}
   float tx=ix+iw+h*.22f,tw=w-tx-h*.35f;
   g.drawText("Log",fit("Log",tw,h*.6f),Color.valueOf("ece6d8"),tx,0,tw,h,false,com.badlogic.gdx.utils.Align.center,true);
  }
 }
 /** visible shortcut to Codex' in-match deck viewer (also in the match menu as "View deck") */
 static final class DeckButton extends FDisplayObject{
  @Override public boolean tap(float x,float y,int count){forge.screens.match.DeckPeekMod.showDeck();return true;}
  @Override public void draw(Graphics g){
   float w=getWidth(),h=getHeight(),r=h/2,lw=Math.max(1.5f,h*.07f);
   g.fillRoundRect(Color.valueOf("8a7346"),0,0,w,h,r);
   g.fillRoundRect(isHovered()?Color.valueOf("17202e"):Color.valueOf("111722"),lw,lw,w-2*lw,h-2*lw,r-lw);
   g.drawText("Deck",fit("Deck",w-12,h*.62f),Color.valueOf("ece6d8"),0,0,w,h,false,com.badlogic.gdx.utils.Align.center,true);
  }
 }
 /** "Skip": passes priority until the start of the local player's next precombat main phase (user 2026-10-16).
  *  For players who don't respond anyway: it passes through everything on the stack, the opponent's spells included
  *  ("der ist fuer ungeduldige Leute ... man kann im Log eh sehen was passiert ist"). The player's own optional triggers
  *  are accepted automatically; real decisions (targets, choices, blockers) wait for the player and the skip continues
  *  afterwards. Tap again to cancel. */
 static final class SkipButton extends FDisplayObject{
  static volatile boolean active;private static long lastPass;private static Field confirmSa;
  // Skip from inside your own main phase runs to the NEXT main phase; after arriving in a main phase the button
  // is locked for one second so a late click does not skip it right away (user 2026-10-17)
  private static String startKey,mainKey;private static long mainSince;
  static String myMainKey(){GameView g=game();var ui=forge.screens.match.MatchController.instance;if(g==null||ui==null)return null;
   forge.game.player.PlayerView t=g.getPlayerTurn();boolean mine=t!=null&&ui.getLocalPlayers().contains(t);
   return mine&&g.getPhase()==PhaseType.MAIN1?g.getTurn()+"/"+g.getPhase():null;}   // Skip runs to your NEXT turn's main phase 1, not to main 2 (user 2026-10-17)
  static boolean locked(){String k=myMainKey();if(k==null)return false;if(!k.equals(mainKey)){mainKey=k;mainSince=System.currentTimeMillis();}return System.currentTimeMillis()-mainSince<1000;}
  /** a yes/no prompt that only asks whether one of the player's triggered abilities should be used */
  static boolean isTriggerConfirm(forge.gamemodes.match.input.InputConfirm ic){
   try{if(confirmSa==null){confirmSa=forge.gamemodes.match.input.InputConfirm.class.getDeclaredField("sa");confirmSa.setAccessible(true);}
    Object sa=confirmSa.get(ic);
    return sa instanceof forge.game.trigger.WrappedAbility||sa instanceof forge.game.spellability.SpellAbility a&&a.isTrigger();
   }catch(ReflectiveOperationException e){return false;}
  }
  @Override public boolean tap(float x,float y,int count){
   if(!active&&locked())return true;
   active=!active;lastPass=0;startKey=active?myMainKey():null;
   tick();return true;}
  @Override public void draw(Graphics g){
   float w=getWidth(),h=getHeight(),r=h/2,lw=Math.max(1.5f,h*.07f);
   Color gold=Color.valueOf("e0b24f"),dark=Color.valueOf("111722");
   g.fillRoundRect(active?gold:Color.valueOf("6a5a34"),0,0,w,h,r);
   g.fillRoundRect(active?Color.valueOf("3a2c10"):isHovered()?Color.valueOf("17202e"):dark,lw,lw,w-2*lw,h-2*lw,r-lw);
   boolean lock=!active&&locked();if(lock)Gdx.graphics.requestRendering();
   if(lock)g.fillRoundRect(new Color(0,0,0,.45f),0,0,w,h,r);
   String label=active?"Skipping...":"Skip";
   g.drawText(label,fit(label,w-12,h*.62f),active?gold:Color.valueOf("ece6d8"),0,0,w,h,false,com.badlogic.gdx.utils.Align.center,true);
   if(active)Gdx.graphics.requestRendering();
  }
  static void tick(){
   if(!active)return;
   GameView game=game();if(game==null||game.isGameOver()){active=false;return;}
   var ui=forge.screens.match.MatchController.instance;
   forge.game.player.PlayerView turn=game.getPlayerTurn();
   boolean myTurn=turn!=null&&ui.getLocalPlayers().contains(turn);
   String key=myMainKey();
   if(key!=null&&!key.equals(startKey)){active=false;locked();return;}   // arrived: the next main phase of yours
   if(!(ui.getGameController() instanceof forge.player.PlayerControllerHuman h))return;
   Object input=h.getInputProxy().getInput();
   boolean trigger=input instanceof forge.gamemodes.match.input.InputConfirm ic&&isTriggerConfirm(ic);   // "use this trigger?" -> Yes
   boolean attack=input instanceof forge.gamemodes.match.input.InputAttack;   // declare attackers: Skip confirms the current choice (none = no attack) (user 2026-10-17)
   if(!trigger&&!attack&&!(input instanceof forge.gamemodes.match.input.InputPassPriority))return;   // targets, choices, blockers: the player decides
   long now=System.currentTimeMillis();if(now-lastPass<150)return;lastPass=now;
   forge.util.ThreadUtil.invokeInGameThread(h::selectButtonOk);
  }
 }
 /** big banner across the middle of the board when a new turn starts; a = 0..1 */
 public static void drawTurnBanner(Graphics g,float w,float centerY,float s,boolean mine,int turn,float a){
  if(a<=0)return;Color accent=mine?MINE:ENEMY;float bh=100*s,y=centerY-bh/2;
  g.fillGradientRect(new Color(0,0,0,0),new Color(.04f,.03f,.02f,.86f*a),false,0,y,w/2,bh);
  g.fillGradientRect(new Color(.04f,.03f,.02f,.86f*a),new Color(0,0,0,0),false,w/2,y,w/2,bh);
  g.fillGradientRect(alpha(accent,0),alpha(accent,a),false,w*.15f,y,w*.35f,2*s);g.fillGradientRect(alpha(accent,a),alpha(accent,0),false,w*.5f,y,w*.35f,2*s);
  g.fillGradientRect(alpha(accent,0),alpha(accent,a),false,w*.15f,y+bh-2*s,w*.35f,2*s);g.fillGradientRect(alpha(accent,a),alpha(accent,0),false,w*.5f,y+bh-2*s,w*.35f,2*s);
  String title=mine?"YOUR TURN":"ENEMY TURN";
  g.drawOutlinedText(title,fit(title,w*.5f,62*s),alpha(accent,a),alpha(INK,a),0,y+8*s,w,62*s,false,1,true);
  String sub="Turn "+Math.max(1,turn);
  g.drawText(sub,fit(sub,w*.3f,22*s),alpha(TEXT,a),0,y+72*s,w,22*s,false,1,true);
 }
 private static int bannerTurn=-1,bannerGame;private static long bannerStart;
 public static float bannerAlpha(GameView game){
  int turn=game.getTurn(),id=System.identityHashCode(game);long now=System.currentTimeMillis();
  if(turn!=bannerTurn||id!=bannerGame){bannerTurn=turn;bannerGame=id;bannerStart=now;}
  float t=(now-bannerStart)/1000f;if(t>2.2f)return 0;
  Gdx.graphics.requestRendering();
  return t<.25f?t/.25f:t<1.7f?1:Math.max(0,1-(t-1.7f)/.5f);
 }
 /** drawn after the panel's children. The bottom panel is drawn last, so it owns the plaque and the banner. */
 public static void overlay(VPlayerPanel p,Graphics g){
  forge.adventure.scene.RogueCommandersScene.trackDuel();
  TopCardSlot top=topCards.get(p);
  if(top!=null){boolean visible=enabled(p,p.getWidth(),p.getHeight())&&visibleTopCard(p)!=null;
   if(top.isVisible()!=visible){top.setVisible(visible);p.revalidate();}}
  if(!enabled(p,p.getWidth(),p.getHeight())||!(Boolean)member(p,"isBottomPlayer"))return;
  GameView game=game();if(game==null||game.getPlayerTurn()==null)return;
  float w=p.getWidth(),s=uiScale;boolean mine=isTurnOf(game,p);
  float ps=s*BattleUiSettings.size(0);   // Settings > Battle UI: turn panel + Skip (user 2026-10-17)
  float plaqueX=Math.min(w-(PLAQUE_W+18)*ps,p.getAvatar().getLeft()-(PLAQUE_W+14)*ps);
  TurnPanel tp=TurnPanel.place(p,plaqueX,ps,mine,game);if(tp!=null)skips.put(p,tp.skip);   // turn panel + Skip: one piece on the match screen, draggable (user 2026-11-05)
  if(game.getTurn()>=1)maybeTour(p);
  // Deck button right ABOVE the mana tile next to the portrait (user 2026-10-16: visible, not in the match menu)
  DeckButton db=deckButtons.get(p);if(db==null){db=p.add(new DeckButton());deckButtons.put(p,db);}
  var mana=p.getManaPoolTab();
  float side=BattleUiSettings.size(2),dbw=mana==null?0:mana.getWidth()*side,dbh=26*s*side;
  if(mana!=null&&mana.getWidth()>0&&mine){db.setVisible(true);db.setBounds(mana.getLeft()+(mana.getWidth()-dbw)/2,mana.getTop()-dbh-4*s,dbw,dbh);}else db.setVisible(false);
  SkipButton.tick();styleStack();
  drawTurnBanner(g,w,0,s,mine,game.getTurn(),bannerAlpha(game));
 }
 public static void background(VPlayerPanel p,Graphics g){
  if(!enabled(p,p.getWidth(),p.getHeight())){try{Method m=VPlayerPanel.class.getDeclaredMethod("arenaOriginalBackground",Graphics.class);m.setAccessible(true);m.invoke(p,g);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}return;}
  var a=p.getAvatar();float s=a.getWidth()/(AVATAR*BIG),bx=a.getLeft()-8*s,by=a.getTop()-6*s,bw=a.getWidth()+16*s,bh=a.getHeight()+(LIFE+12)*s;
  boolean active=isTurnOf(game(),p);Color accent=(Boolean)member(p,"isBottomPlayer")?MINE:ENEMY;
  if(active){   // glowing frame around the portrait of the player whose turn it is
   float pulse=.6f+.4f*(float)Math.sin(System.currentTimeMillis()/350.0);
   for(int i=4;i>=1;i--)g.fillRoundRect(alpha(accent,.10f*pulse),bx-i*3*s,by-i*3*s,bw+i*6*s,bh+i*6*s,(12+i*3)*s);
  }
  g.fillRoundRect(new Color(.055f,.072f,.098f,1f),bx,by,bw,bh,12*s);
  frame(g,active?accent:Color.valueOf("8a7650"),new Color(.055f,.072f,.098f,1f),bx+3*s,by+2*s,bw-6*s,bh-4*s,10*s,active?2*s:1.2f);   // no drawRoundRect cross lines
 }
}
