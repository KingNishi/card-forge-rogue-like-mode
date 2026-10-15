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
 public static final float AVATAR=84,BAND=46,LIFE=24;
 public static Layout plan(float w,float h,boolean bottom,boolean selected){
  float scale=Math.max(.6f,Math.min(1.6f,Math.min(w/1600,forge.Forge.getScreenHeight()/900f))),avatar=AVATAR*scale,band=BAND*scale,life=LIFE*scale;
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
  Layout l=plan(w,h,bottom,selected);float scale=l.avatarSize()/AVATAR;
  p.getAvatar().setBounds(l.avatarX(),l.avatarY(),l.avatarSize(),l.avatarSize());
  ((FDisplayObject)member(p,"lblLife")).setBounds(l.avatarX()+l.avatarSize()/2-50*scale,l.lifeY(),100*scale,LIFE*scale-2*scale);
  float manaW=65*scale,manaX=l.avatarX()-manaW-18*scale,phaseW=Math.max(120,Math.min(w*.32f,manaX-12*scale-w*.45f));
  // the per-player phase strips (UP DR M1 ...) are gone: the turn plaque in the middle shows every step
  p.getPhaseIndicator().setBounds(manaX-12*scale-phaseW,l.bandY(),0,0);p.getPhaseIndicator().setVisible(false);
  int count=0;for(var tab:p.getTabs())if(tab!=p.getManaPoolTab())count++;
  float tw=Math.min(78*scale,w*.40f/Math.max(1,count)),th=Math.min(l.bandHeight()-4*scale,42*scale);int index=0;   // bigger zone tiles
  // bottom: the hand button keeps a fixed spot in the bottom-left corner, the tabs follow it;
  // top: the round match menu button sits in the top-left corner
  float toggleSize=Math.min(l.bandHeight()-6*scale,40*scale);
  float zoomW=toggleSize*2.9f;
  float tabsX=12*scale+(bottom&&bars()?toggleSize+8*scale+zoomW+14*scale:!bottom&&bars()?92*scale:0);
  for(var tab:p.getTabs()){
   if(tab==p.getManaPoolTab())tab.setBounds(manaX,l.bandY()+(l.bandHeight()-38*scale)/2,manaW,38*scale);
   else tab.setBounds(tabsX+(index++)*(tw+6*scale),l.bandY()+(l.bandHeight()-th)/2,tw,th);
   tab.setDisplayBounds(w*.12f,l.displayY(),w*.76f,Math.max(1,l.displayHeight()));
  }
  if(bottom){   // Arena-style: fold the (big) hand away to see the whole battlefield, and back
   HandToggle t=toggles.get(p);if(t==null){t=p.add(new HandToggle(p));toggles.put(p,t);}
   // same place whether the hand is shown or folded away: centre of the bottom row above the prompt bar
   float cy=h-promptGap()-l.bandHeight()/2;
   t.setBounds(12*scale,cy-toggleSize/2,toggleSize,toggleSize);
   HandZoom z=zooms.get(p);if(z==null){z=p.add(new HandZoom());zooms.put(p,z);}
   z.setBounds(12*scale+toggleSize+8*scale,cy-toggleSize/2,zoomW,toggleSize);
  }
  // Command zone (commander, item cards like Slobad's Iron Boots / Cursed Treasure) on the left as a grid of
  // separate cards; the same margin on both sides keeps the battlefield rows centred under the hand.
  int cards=p.getCommandZone().getCount();float czh=l.fieldHeight()*.92f,gap=gap(scale);
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
   if(!bars())return original;
   if(avatarPlayer==null){avatarPlayer=VAvatar.class.getDeclaredField("player");avatarPlayer.setAccessible(true);}
   forge.game.player.PlayerView player=(forge.game.player.PlayerView)avatarPlayer.get(avatar);
   if(player==null||player.isAI())return original;                       // opponents keep their portraits
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
  if(bars()&&y>0&&h>0){float nh=h+y,nw=w*nh/h;x-=(nw-w)/2;w=nw;h=nh;y=0;}
  g.drawImage(image,x,y,w,h);
 }
 /** top menu bar (Game / Players / Log / Dev) */
 public static float headerHeight(float original){return bars()?0:original;}
 /** bottom prompt bar (message, OK / End Turn) */
 public static float promptHeight(float original){return bars()?Math.round(original*.52f):original;}
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
  FSkinFont small=null;   // one size that fits every label in the narrowest row
  for(String[] row:STEPS){float bw=(inner-(row.length-1)*gap)/row.length;for(String label:row){FSkinFont f=fit(label,bw-8*s,bh-7*s);if(small==null||f.getLineHeight()<small.getLineHeight())small=f;}}
  for(int r=0;r<STEPS.length;r++){
   String[] row=STEPS[r];float bw=(inner-(row.length-1)*gap)/row.length,by=top+r*(bh+rowGap);
   for(int i=0;i<row.length;i++,index++){
    float bx=x+12*s+i*(bw+gap);
    if(index==active){g.fillRoundRect(accent,bx,by,bw,bh,5*s);g.drawText(row[i],small,INK,bx,by,bw,bh,false,1,true);}
    else if(index<active){g.fillRoundRect(mix(accent,.22f),bx,by,bw,bh,5*s);g.drawText(row[i],small,MUTED,bx,by,bw,bh,false,1,true);}
    else{frame(g,mix(MUTED,.45f),BG,bx,by,bw,bh,5*s,Math.max(1,s*.8f));g.drawText(row[i],small,alpha(MUTED,.85f),bx,by,bw,bh,false,1,true);}
   }
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
  float w=p.getWidth(),s=p.getAvatar().getWidth()/AVATAR;boolean mine=isTurnOf(game,p);
  drawTurnPlaque(g,w-(PLAQUE_W+18)*s,-PLAQUE_H*.28f*s,s,mine,game.getTurn(),game.getPhase());   // lower than centred on the border
  drawTurnBanner(g,w,0,s,mine,game.getTurn(),bannerAlpha(game));
 }
 public static void background(VPlayerPanel p,Graphics g){
  if(!enabled(p,p.getWidth(),p.getHeight())){try{Method m=VPlayerPanel.class.getDeclaredMethod("arenaOriginalBackground",Graphics.class);m.setAccessible(true);m.invoke(p,g);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}return;}
  var a=p.getAvatar();float s=a.getWidth()/AVATAR,bx=a.getLeft()-8*s,by=a.getTop()-6*s,bw=a.getWidth()+16*s,bh=a.getHeight()+(LIFE+12)*s;
  boolean active=isTurnOf(game(),p);Color accent=(Boolean)member(p,"isBottomPlayer")?MINE:ENEMY;
  if(active){   // glowing frame around the portrait of the player whose turn it is
   float pulse=.6f+.4f*(float)Math.sin(System.currentTimeMillis()/350.0);
   for(int i=4;i>=1;i--)g.fillRoundRect(alpha(accent,.10f*pulse),bx-i*3*s,by-i*3*s,bw+i*6*s,bh+i*6*s,(12+i*3)*s);
  }
  g.fillRoundRect(new Color(.055f,.072f,.098f,.78f),bx,by,bw,bh,12*s);
  g.drawRoundRect(active?2*s:1.2f,active?accent:Color.valueOf("8a7650"),bx+3*s,by+2*s,bw-6*s,bh-4*s,10*s);
 }
}
