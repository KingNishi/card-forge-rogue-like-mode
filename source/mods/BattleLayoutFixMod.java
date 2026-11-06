package forge.adventure.util;
import forge.Graphics;
import forge.assets.*;
import forge.card.*;
import forge.game.card.CardView;
import forge.game.GameView;
import forge.screens.match.MatchScreen;
import forge.screens.match.views.*;
import forge.toolbox.*;
import com.badlogic.gdx.graphics.Color;
import java.lang.reflect.*;
/** Finish native layouts after their card/attachment and scrolling logic has run. */
public final class BattleLayoutFixMod {
 public static void updated(VField field){if(ArenaMotionMod.active())field.revalidate();}
 private static final float MIN_BOARD_ZOOM=.40f;
 private static final class RowGroup {
  final java.util.List<FDisplayObject> cards=new java.util.ArrayList<>();
  float left=Float.POSITIVE_INFINITY,top=Float.POSITIVE_INFINITY,right,bottom;
  void add(FDisplayObject c){cards.add(c);left=Math.min(left,c.getLeft());top=Math.min(top,c.getTop());right=Math.max(right,c.getRight());bottom=Math.max(bottom,c.getBottom());}
  float width(){return right-left;}float height(){return bottom-top;}
 }
 private static VCardDisplayArea.CardAreaPanel root(VCardDisplayArea.CardAreaPanel panel){
  var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<VCardDisplayArea.CardAreaPanel,Boolean>());
  while(seen.add(panel)){
   if(panel.getAttachedToPanel()!=null)panel=panel.getAttachedToPanel();
   else if(panel.getPrevPanelInStack()!=null)panel=panel.getPrevPanelInStack();else break;
  }
  return panel;
 }
 /** Fit only battlefield rows. Keep a fan and all attachments together; never resize the hand or HUD.
  *  A second lane is preferable to unreadably small cards. Extreme boards retain native scrolling. */
 private static void autoFit(VCardDisplayArea area,float w,float h){
  if(!ArenaMotionMod.active()||BattleZoomMod.manual()||!(area instanceof VField.FieldRow)||w<200||h<30)return;
  var map=new java.util.LinkedHashMap<VCardDisplayArea.CardAreaPanel,RowGroup>();
  float left=Float.POSITIVE_INFINITY,right=0;
  for(FDisplayObject c:area.getChildren())if(c.isVisible()&&c instanceof VCardDisplayArea.CardAreaPanel p&&c.getWidth()>0&&c.getHeight()>0){
   map.computeIfAbsent(root(p),k->new RowGroup()).add(c);left=Math.min(left,c.getLeft());right=Math.max(right,c.getRight());
  }
  if(map.isEmpty()||right-left<=w-16)return;
  var groups=new java.util.ArrayList<>(map.values());groups.sort(java.util.Comparator.comparingDouble(g->g.left));
  float width=0,maxH=0;for(RowGroup g:groups){width+=g.width();maxH=Math.max(maxH,g.height());}
  float single=Math.min((w-16-6*(groups.size()-1))/width,(h-8)/maxH);
  float zoom;boolean wrap;
  if(single>=.62f){zoom=Math.min(1,single);wrap=false;}
  else{
   float low=0,high=.80f;
   for(int i=0;i<20;i++){float mid=(low+high)/2;if(packedHeight(groups,mid,w-16)<=h-8)low=mid;else high=mid;}
   zoom=Math.max(MIN_BOARD_ZOOM,low);wrap=packedHeight(groups,zoom,w-16)<=h-8;
  }
  VField field;
  try{Field f=VField.FieldRow.class.getDeclaredField("this$0");f.setAccessible(true);field=(VField)f.get(area);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
  float totalH=wrap?packedHeight(groups,zoom,w-16):maxH*zoom;
  float y=field.isFlipped()?4:Math.max(4,h-totalH-4);
  int start=0;
  while(start<groups.size()){
   int end=start;float used=0,laneH=0;
   while(end<groups.size()){
    RowGroup g=groups.get(end);float next=used+(end>start?6:0)+g.width()*zoom;
    if(wrap&&end>start&&next>w-16)break;
    used=next;laneH=Math.max(laneH,g.height()*zoom);end++;
   }
   float x=used<=w-16?(w-used)/2:8;
   for(int i=start;i<end;i++){
    RowGroup g=groups.get(i);float gy=y+(field.isFlipped()?0:laneH-g.height()*zoom);
    for(FDisplayObject c:g.cards)c.setBounds(x+(c.getLeft()-g.left)*zoom,gy+(c.getTop()-g.top)*zoom,c.getWidth()*zoom,c.getHeight()*zoom);
    x+=g.width()*zoom+6;
   }
   y+=laneH+6;start=end;
  }
 }
 private static float packedHeight(java.util.List<RowGroup> groups,float zoom,float maxW){
  float x=0,height=0,laneH=0;
  for(RowGroup g:groups){float gw=g.width()*zoom,gh=g.height()*zoom;
   if(gw>maxW)return Float.POSITIVE_INFINITY;
   if(x>0&&x+6+gw>maxW){height+=laneH+6;x=0;laneH=0;}
   x+=(x>0?6:0)+gw;laneH=Math.max(laneH,gh);
  }
  return height+laneH;
 }
 /** Keep Forge's native matching and selection, but separate tapped lands and limit each fan
  *  to five cards so individual exposed edges remain useful. Other permanent types use Forge's rules. */
 public static boolean stackCard(VField field,CardView card,java.util.List<CardView> roots){
  java.util.List<CardView> candidates=roots;
  if(ArenaMotionMod.active()&&plainLand(card)){
   candidates=new java.util.ArrayList<>();
   for(CardView root:roots){
    if(!plainLand(root)||root.isTapped()!=card.isTapped())continue;
    int count=1;var panel=VCardDisplayArea.CardAreaPanel.get(root);
    while(panel.getNextPanelInStack()!=null&&count<5){panel=panel.getNextPanelInStack();count++;}
    if(count<5)candidates.add(root);
   }
  }
  try{Method m=VField.class.getDeclaredMethod("layoutOriginalTryStackCard",CardView.class,java.util.List.class);m.setAccessible(true);return (Boolean)m.invoke(field,card,candidates);}
  catch(InvocationTargetException e){throw new IllegalStateException("Native card stacking failed",e.getCause());}
  catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 private static boolean plainLand(CardView card){return card!=null&&card.getCurrentState().isLand()&&!card.getCurrentState().isCreature();}
 /** A card that can be cast from the graveyard sits in two zone rows at once (graveyard + flashback) with ONE shared
  *  panel; the row laid out last moved it, leaving a gap in the other (user 2026-11-05: "Luecken zwischen den Karten").
  *  Every zone row remembers the bounds of its own layout and puts them back right before it draws. */
 private static final java.util.Map<VCardDisplayArea,java.util.Map<FDisplayObject,float[]>> ownBounds=new java.util.WeakHashMap<>();
 private static Field panelArea;
 public static FScrollPane.ScrollBounds center(FScrollPane.ScrollBounds original,VCardDisplayArea area,float w,float h){
  FScrollPane.ScrollBounds b=center0(original,area,w,h);
  if(area instanceof VZoneDisplay){var m=new java.util.IdentityHashMap<FDisplayObject,float[]>();
   for(FDisplayObject c:area.getChildren())m.put(c,new float[]{c.getLeft(),c.getTop(),c.getWidth(),c.getHeight()});ownBounds.put(area,m);}
  return b;
 }
 public static void beforeDraw(VCardDisplayArea area){
  var m=ownBounds.get(area);if(m==null)return;
  for(var e:m.entrySet()){FDisplayObject c=e.getKey();float[] r=e.getValue();
   if(c.getLeft()==r[0]&&c.getTop()==r[1]&&c.getWidth()==r[2]&&c.getHeight()==r[3]||!childOf(area,c))continue;   // left this row since: not ours any more
   c.setBounds(r[0],r[1],r[2],r[3]);
   if(c instanceof VCardDisplayArea.CardAreaPanel p)try{if(panelArea==null){panelArea=VCardDisplayArea.CardAreaPanel.class.getDeclaredField("displayArea");panelArea.setAccessible(true);}panelArea.set(p,area);}catch(ReflectiveOperationException ignored){}
  }
 }
 /** Android: more than BUNDLE attackers against one player draw ONE arrow from the middle of the attackers instead of one
  *  each - every arrow costs extra GPU passes per frame on a phone (user 2026-11-05: "lagt ... alpha strike"). The attacking
  *  cards keep their own attack marking. Runs right before TargetingOverlay draws its prepared arrows. */
 static final int BUNDLE=6;
 private static Field arrowsF,countF,startF,endF,solidF;
 @SuppressWarnings("unchecked") public static void thinArrows(){
  if(!BattleUiSettings.touch())return;
  try{
   if(arrowsF==null){Class<?> t=Class.forName("forge.screens.match.TargetingOverlay"),pa=Class.forName("forge.screens.match.TargetingOverlay$PendingArrow");
    Field a=t.getDeclaredField("pendingArrows"),c=t.getDeclaredField("pendingCount"),s=pa.getDeclaredField("start"),e=pa.getDeclaredField("end"),so=pa.getDeclaredField("solid");
    for(Field f:new Field[]{a,c,s,e,so})f.setAccessible(true);startF=s;endF=e;solidF=so;countF=c;arrowsF=a;}
   java.util.List<Object> list=(java.util.List<Object>)arrowsF.get(null);int n=countF.getInt(null);if(n<=BUNDLE)return;
   var groups=new java.util.LinkedHashMap<Long,java.util.List<Object>>();
   for(int i=0;i<n;i++){Object a=list.get(i);if(!solidF.getBoolean(a))continue;var end=(com.badlogic.gdx.math.Vector2)endF.get(a);if(end==null)continue;
    groups.computeIfAbsent(((long)Math.round(end.x)<<32)^(Math.round(end.y)&0xffffffffL),k->new java.util.ArrayList<>()).add(a);}
   var drop=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
   for(var g:groups.values()){if(g.size()<=BUNDLE)continue;float sx=0,sy=0;
    for(Object a:g){var st=(com.badlogic.gdx.math.Vector2)startF.get(a);sx+=st.x;sy+=st.y;}
    startF.set(g.get(0),new com.badlogic.gdx.math.Vector2(sx/g.size(),sy/g.size()));for(int i=1;i<g.size();i++)drop.add(g.get(i));}
   if(drop.isEmpty())return;
   var kept=new java.util.ArrayList<Object>(n);for(int i=0;i<n;i++)if(!drop.contains(list.get(i)))kept.add(list.get(i));
   var tail=new java.util.ArrayList<Object>(list.subList(0,n));tail.removeAll(kept);   // dropped entries stay in the pool for reuse
   for(int i=0;i<kept.size();i++)list.set(i,kept.get(i));for(int i=0;i<tail.size();i++)list.set(kept.size()+i,tail.get(i));
   countF.setInt(null,kept.size());
  }catch(ReflectiveOperationException|RuntimeException e){/* never break arrow drawing */}
 }
 private static boolean childOf(VCardDisplayArea area,FDisplayObject c){for(FDisplayObject x:area.getChildren())if(x==c)return true;return false;}
 private static FScrollPane.ScrollBounds center0(FScrollPane.ScrollBounds original,VCardDisplayArea area,float w,float h){
  FScrollPane.ScrollBounds grid=ArenaBattleHudMod.commandGrid(area,w,h);if(grid!=null)return grid;   // command zone: card grid
  ArenaBattleHudMod.shrinkLands(area);   // battlefield rows: lands a little smaller
  autoFit(area,w,h);
  if(!ArenaMotionMod.active()||w<h||w<200)return original;
  float left=Float.POSITIVE_INFINITY,right=0;
  for(FDisplayObject child:area.getChildren())if(child.isVisible()){left=Math.min(left,child.getLeft());right=Math.max(right,child.getRight());}
  if(!Float.isFinite(left))return original;
  float used=right-left,offset=used<=w-16?(w-used)/2-left:Math.max(0,8-left);
  for(FDisplayObject child:area.getChildren())if(child.isVisible())child.setPosition(child.getLeft()+offset,child.getTop());
  // Native bounds describe the cards before compaction. Keeping them leaves a blank scroll area
  // and can preserve a scroll offset that moves centered lands off the visible battlefield.
  return new FScrollPane.ScrollBounds(Math.max(w,right+offset+8),h);
 }
 /** front (creature) row height a field gets on its own: {front, rear, gap, usable} */
 private static float[] split(VField field,float w,float h){
  try{Field f=VField.class.getDeclaredField("commandZoneWidth");f.setAccessible(true);float reserve=f.getFloat(field);
   float usable=Math.max(200,w-reserve),card=ArenaBattleHudMod.fieldCardHeight(field);
   float rearRatio=ArenaBattleHudMod.LAND_SCALE;for(var p:field.getRow2().getCardPanels())if(!plainLand(p.getCard())){rearRatio=1;break;}
   float gap=Math.max(4,Math.min(10,card*.06f)),rear=BattleZoomMod.manual()?card*rearRatio+2*gap:Math.min(h*.48f,card+2*gap);
   // Let a crowded rear row borrow height before making its lands/artifacts too small.
   float rearWidth=0,rearCard=0;int roots=0;
   for(var panel:field.getRow2().getCardPanels()){
    float size=card*(plainLand(panel.getCard())?ArenaBattleHudMod.LAND_SCALE:1),fan=1;
    var p=panel;var seen=new java.util.HashSet<VCardDisplayArea.CardAreaPanel>();
    while(p.getNextPanelInStack()!=null&&seen.add(p)){p=p.getNextPanelInStack();fan+=.32f;}
    rearWidth+=size*(fan+panel.getCard().getAttachedCards().size()*.20f);rearCard=Math.max(rearCard,size);roots++;
   }
   if(!BattleZoomMod.manual()&&rearWidth+Math.max(0,roots-1)*6>usable-16){
    float lanes=(float)Math.ceil((rearWidth+Math.max(0,roots-1)*6)/(usable-16));
    rear=Math.min(h*.48f,Math.max(rear,lanes*rearCard*.80f+(lanes-1)*6+2*gap));
   }
   return new float[]{h-rear-gap,rear,gap,usable};
  }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 private static final java.util.Map<VField,Float> APPLIED_FRONT=new java.util.WeakHashMap<>();
 private static boolean evening;
 public static void field(VField field,float w,float h){
  if(!ArenaMotionMod.active()||w<600)return;
  float[] own=split(field,w,h);float front=own[0],rear=own[1],gap=own[2],usable=own[3];
  // both players' creatures at the same size (user 2026-10-18: "wieso sind beim Zoom die Karten vom Gegner groesser als meine"):
  // a field whose rear row holds only lands left its creature row taller - every field takes the smaller creature row
  VField other=null;
  for(VPlayerPanel p:MatchScreen.getPlayerPanels().values())if(p.getField()!=field)other=p.getField();
  if(other!=null&&other.getWidth()>=600&&other.getHeight()>0&&MatchScreen.getPlayerPanels().size()==2)front=Math.min(front,split(other,other.getWidth(),other.getHeight())[0]);
  front=Math.max(24,front);
  // the creature row stays next to the centre line, the rear row at the outer edge; the spare height lies between them
  field.getRow1().setBounds(0,field.isFlipped()?h-front:0,usable,front);
  field.getRow2().setBounds(0,field.isFlipped()?0:h-rear,usable,rear);
  APPLIED_FRONT.put(field,front);
  if(other!=null&&!evening){Float was=APPLIED_FRONT.get(other);if(was==null||Math.abs(was-front)>.5f){evening=true;try{other.revalidate();}finally{evening=false;}}}
 }
 public static float previewX(float x,float width,float screenWidth){return Math.max(12,Math.min(x,Math.max(12,screenWidth-width-12)));}
 public static void preview(Graphics g,CardView card,float x,float y,float w,float h,CardRenderer.CardStackPosition stack,boolean rotated,boolean a,boolean b,boolean c,MatchScreen screen){
  if(ArenaMotionMod.active()){x=previewX(x,rotated?Math.max(w,h):w,screen.getWidth());y=Math.max(64,Math.min(y,Math.max(64,screen.getHeight()-h-80)));}
  CardRenderer.drawCard(g,card,x,y,w,h,stack,rotated,a,b,c);
 }
 public static void details(Graphics g,CardView card,GameView view,boolean a,float x,float y,float w,float h,MatchScreen screen){
  if(ArenaMotionMod.active()){x=previewX(x,w,screen.getWidth());y=Math.max(64,Math.min(y,Math.max(64,screen.getHeight()-h-80)));}
  CardImageRenderer.drawDetails(g,card,view,a,x,y,w,h);
 }
 private static void original(Object o,Class<?> type,String name,Graphics g){try{Method m=type.getDeclaredMethod(name,Graphics.class);m.setAccessible(true);m.invoke(o,g);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 public static void button(FButton button,Graphics g){
  String text=button.getText();if(!ArenaMotionMod.active()||text==null||text.isBlank()){original(button,FButton.class,"layoutOriginalDraw",g);return;}
  float w=button.getWidth(),h=button.getHeight();boolean disabled=!button.isEnabled();
  int art=disabled?3:button.isToggled()?2:button.isHovered()?1:0;   // painted fantasy button sheet (user concept 2026-10-17)
  if(FantasyButtonArt.drawGraphics(g,art,false,2,2,Math.max(0,w-4),Math.max(0,h-4))){
   float pad=FantasyButtonArt.textInset(Math.max(0,w-4),Math.max(0,h-4));
   int size=Math.max(9,Math.min(26,Math.round(h*.36f)));FSkinFont font=FSkinFont.get(size);
   while(size>9&&(font.getBounds(text).width>w-4-2*pad||font.getLineHeight()>h-10))font=FSkinFont.get(--size);
   g.drawText(text,font,disabled?Color.valueOf("82919e"):Color.valueOf("f0e8d8"),2+pad,4,Math.max(0,w-4-2*pad),Math.max(0,h-8),false,1,true);
   return;
  }
  g.fillRoundRect(Color.valueOf(disabled?"202b35":button.isHovered()?"345b6b":"1b303e"),2,2,Math.max(0,w-4),Math.max(0,h-4),6);
  g.drawRoundRect(1,Color.valueOf(disabled?"46525b":"a38b61"),2,2,Math.max(0,w-4),Math.max(0,h-4),6);
  int size=Math.max(9,Math.min(26,Math.round(h*.36f)));FSkinFont font=FSkinFont.get(size);
  while(size>9&&(font.getBounds(text).width>w-16||font.getLineHeight()>h-10))font=FSkinFont.get(--size);
  g.drawText(text,font,disabled?Color.valueOf("82919e"):Color.valueOf("f0e8d8"),8,4,Math.max(0,w-16),Math.max(0,h-8),false,1,true);
 }
 public static void list(FList<?> list,Graphics g){
  if(!ArenaMotionMod.active()){original(list,FList.class,"layoutOriginalBackground",g);return;}
  g.fillRect(Color.valueOf("15212c"),0,0,list.getWidth(),list.getHeight());
 }
}
