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
 /** side seat (3 players): the cards of a row stand one below the other, in more columns (and smaller) when they do not fit;
  *  fans and attachments stay together, the whole block centred in the row */
 private static FScrollPane.ScrollBounds upright(VCardDisplayArea area,float w,float h){
  var map=new java.util.LinkedHashMap<VCardDisplayArea.CardAreaPanel,RowGroup>();
  for(FDisplayObject c:area.getChildren())if(c.isVisible()&&c instanceof VCardDisplayArea.CardAreaPanel p&&c.getWidth()>0&&c.getHeight()>0)map.computeIfAbsent(root(p),k->new RowGroup()).add(c);
  if(map.isEmpty())return new FScrollPane.ScrollBounds(w,h);
  var groups=new java.util.ArrayList<>(map.values());groups.sort(java.util.Comparator.comparingDouble(g->g.left));
  float zoom=1;
  if(packedWidth(groups,1,h-8)>w-8){float low=0,high=1;for(int i=0;i<20;i++){float mid=(low+high)/2;if(packedWidth(groups,mid,h-8)<=w-8)low=mid;else high=mid;}zoom=Math.max(MIN_BOARD_ZOOM,low);}
  // columns: {first group, end, width, height}
  var cols=new java.util.ArrayList<float[]>();int start=0;
  while(start<groups.size()){int end=start;float used=0,colW=0;
   while(end<groups.size()){RowGroup g=groups.get(end);float next=used+(end>start?6:0)+g.height()*zoom;if(end>start&&next>h-8)break;used=next;colW=Math.max(colW,g.width()*zoom);end++;}
   cols.add(new float[]{start,end,colW,used});start=end;}
  float total=0,maxH=0;for(float[] c:cols){total+=c[2];maxH=Math.max(maxH,c[3]);}total+=6*(cols.size()-1);
  float x=Math.max(4,(w-total)/2);
  for(float[] c:cols){float y=Math.max(4,(h-c[3])/2);
   for(int i=(int)c[0];i<(int)c[1];i++){RowGroup g=groups.get(i);float gx=x+(c[2]-g.width()*zoom)/2;
    for(FDisplayObject d:g.cards)d.setBounds(gx+(d.getLeft()-g.left)*zoom,y+(d.getTop()-g.top)*zoom,d.getWidth()*zoom,d.getHeight()*zoom);
    y+=g.height()*zoom+6;}
   x+=c[2]+6;}
  return new FScrollPane.ScrollBounds(Math.max(w,x+2),Math.max(h,maxH+8));
 }
 private static float packedWidth(java.util.List<RowGroup> groups,float zoom,float maxH){
  float y=0,width=0,colW=0;
  for(RowGroup g:groups){float gw=g.width()*zoom,gh=g.height()*zoom;
   if(gh>maxH)return Float.POSITIVE_INFINITY;
   if(y>0&&y+6+gh>maxH){width+=colW+6;y=0;colW=0;}
   y+=(y>0?6:0)+gh;colW=Math.max(colW,gw);
  }
  return width+colW;
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
  placeZoneButton(area);
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
  foldZone(area);   // folded artifacts / enchantments: one fan (user 2026-11-13)
  if(area instanceof VField.FieldRow&&ArenaBattleHudMod.sideField(owner(area)))return upright(area,w,h);
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
   float rearRatio=(BattleUiSettings.compactLands()?.48f:ArenaBattleHudMod.LAND_SCALE);for(var p:field.getRow2().getCardPanels())if(!plainLand(p.getCard())){rearRatio=1;break;}
   float gap=Math.max(4,Math.min(10,card*.06f)),rear=BattleZoomMod.manual()?card*rearRatio+2*gap:Math.min(h*.48f,card+2*gap);
   // Let a crowded rear row borrow height before making its lands/artifacts too small.
   float rearWidth=0,rearCard=0;int roots=0;
   for(var panel:field.getRow2().getCardPanels()){
    float size=card*(plainLand(panel.getCard())?(BattleUiSettings.compactLands()?.48f:ArenaBattleHudMod.LAND_SCALE):1),fan=1;
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
  if(ArenaMotionMod.active()&&ArenaBattleHudMod.sideField(field)){   // 3 players, side seat: rows stand upright - lands outside, creatures toward the middle
   float lw=ArenaBattleHudMod.landScale(),n1=field.getRow1().getChildCount(),n2=field.getRow2().getChildCount();
   float share=n1+n2==0?.42f:Math.max(.30f,Math.min(.62f,n2*lw/(n2*lw+n1)));
   float g=Math.max(4,w*.02f),rearW=Math.round((w-g)*share);
   field.getRow2().setBounds(0,0,rearW,h);
   field.getRow1().setBounds(rearW+g,0,w-rearW-g,h);
   zoneButton(field,w);APPLIED_FRONT.remove(field);return;
  }
  if(!ArenaMotionMod.active()||w<(BattleZoomMod.multi()?240:600))return;   // 3-4 players: quadrant fields are narrower
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
  zoneButton(field,usable);
  APPLIED_FRONT.put(field,front);
  if(other!=null&&!evening){Float was=APPLIED_FRONT.get(other);if(was==null||Math.abs(was-front)>.5f){evening=true;try{other.revalidate();}finally{evening=false;}}}
 }
 public static float previewX(float x,float width,float screenWidth){return Math.max(12,Math.min(x,Math.max(12,screenWidth-width-12)));}
 public static void preview(Graphics g,CardView card,float x,float y,float w,float h,CardRenderer.CardStackPosition stack,boolean rotated,boolean a,boolean b,boolean c,MatchScreen screen){
  BattleUiSettings.previewAt=System.currentTimeMillis();
  if(ArenaMotionMod.active()){x=previewX(x,rotated?Math.max(w,h):w,screen.getWidth());y=Math.max(64,Math.min(y,Math.max(64,screen.getHeight()-h-80)));}
  CardRenderer.drawCard(g,card,x,y,w,h,stack,rotated,a,b,c);
 }
 public static void details(Graphics g,CardView card,GameView view,boolean a,float x,float y,float w,float h,MatchScreen screen){
  BattleUiSettings.previewAt=System.currentTimeMillis();
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

 // ------------------------------------------------------------------ compact lands + folding artifact zone (user 2026-11-13)
 // "Lands kompakt wie MTG Arena: kleine Kacheln mit Tap-Symbol statt Drehung" and "Artefakt/Verzauberungs-Zone pro Spieler
 // einklappbar, trotzdem aktivierbar". Battle UI setting each: Auto = Android on, PC off.
 private static boolean onField(CardView c){return c!=null&&c.getZone()==forge.game.zone.ZoneType.Battlefield;}
 /** CardAreaPanel.getTappedAngle: compact lands stay upright when tapped */
 public static float tappedAngle(float angle,VCardDisplayArea.CardAreaPanel panel){
  if(ArenaMotionMod.active()&&BattleUiSettings.compactLands()&&plainLand(panel.getCard())&&onField(panel.getCard())&&panel.getAttachedToPanel()==null)return 0;
  return angle;
 }
 /** FCardPanel.rotateTransform (end): a tapped compact land is darkened and shows the tap symbol */
 public static void tapMark(FCardPanel panel,Graphics g,float x,float y,float w,float h){
  CardView c=panel.getCard();
  if(!panel.isTapped()||!ArenaMotionMod.active()||!BattleUiSettings.compactLands()||!plainLand(c)||!onField(c))return;
  if(panel instanceof VCardDisplayArea.CardAreaPanel p&&p.getAttachedToPanel()!=null)return;
  g.fillRect(new Color(0,0,0,.50f),x,y,w,h);
  float s=Math.min(w,h)*.46f;
  g.fillCircle(new Color(0,0,0,.55f),x+w/2,y+h/2,s*.62f);
  CardFaceSymbols.drawSymbol("T",g,x+(w-s)/2,y+(h-s)/2,s,s);
 }
 /** which fan a card goes into (user 2026-11-15): 0 = artifacts (no creatures, no equipment) AND enchantments
  *  (no creatures, no sagas) in one shared pile ("enchantments und artifacts auf ein stapel"), -1 = stays where it is.
  *  ENCH stays as an index (arrays of 2) but is no longer used. */
 static final int ART=0,ENCH=1;
 private static int stackType(CardView c){
  if(c==null||c.getCurrentState()==null)return -1;var s=c.getCurrentState();
  if(s.isCreature()||s.isLand()||s.isPlaneswalker())return -1;
  var t=s.getType();
  if(s.isArtifact())return t.hasSubtype("Equipment")?-1:ART;
  if(s.isEnchantment())return t.hasSubtype("Saga")?-1:ART;   // same pile as the artifacts
  return -1;
 }
 private static boolean zoneCard(CardView c){return stackType(c)>=0;}
 private static final java.util.Map<VField,boolean[]> OPEN=new java.util.WeakHashMap<>();
 private static boolean open(VField f,int t){boolean[] o=OPEN.get(f);return o!=null&&o[t];}
 private static void toggle(VField f,int t){OPEN.computeIfAbsent(f,k->new boolean[2])[t]^=true;f.revalidate();f.getRow1().revalidate();f.getRow2().revalidate();}
 private static VField owner(VCardDisplayArea area){
  try{Field f=VField.FieldRow.class.getDeclaredField("this$0");f.setAccessible(true);return (VField)f.get(area);}catch(ReflectiveOperationException e){return null;}
 }
 private static boolean foldOn(VField field){return field!=null&&ArenaMotionMod.active()&&BattleUiSettings.collapsibleZone();}
 /** every root card of a row (with what is attached to / stacked on it) as one unit, left to right */
 private static java.util.List<java.util.Map.Entry<VCardDisplayArea.CardAreaPanel,RowGroup>> units(VCardDisplayArea area){
  var map=new java.util.LinkedHashMap<VCardDisplayArea.CardAreaPanel,RowGroup>();
  for(FDisplayObject c:area.getChildren())if(c.isVisible()&&c instanceof VCardDisplayArea.CardAreaPanel p&&c.getWidth()>0)map.computeIfAbsent(root(p),k->new RowGroup()).add(c);
  var list=new java.util.ArrayList<>(map.entrySet());list.sort(java.util.Comparator.comparingDouble(e->e.getValue().left));return list;
 }
 /** folded: artifacts and enchantments each lie in one fan; the more cards, the tighter (the fan stays about 1.6 cards
  *  wide), every card keeps a visible edge. The number on the top card opens / closes the fan (user 2026-11-15). */
 private static void foldZone(VCardDisplayArea area){
  if(!(area instanceof VField.FieldRow))return;VField field=owner(area);if(!foldOn(field))return;
  var units=units(area);if(units.size()<2)return;
  int[] count=new int[2];float cw=0;
  for(var e:units){int t=stackType(e.getKey().getCard());if(t>=0){count[t]++;cw=Math.max(cw,e.getValue().width());}}
  boolean[] fold={count[ART]>=2&&!open(field,ART),count[ENCH]>=2&&!open(field,ENCH)};
  if(!fold[ART]&&!fold[ENCH])return;
  float[] step=new float[2];for(int t=0;t<2;t++)step[t]=count[t]<2?0:Math.max(6,Math.min(cw*.30f,cw*.60f/(count[t]-1)));
  float[] slot={Float.NaN,Float.NaN};int[] k=new int[2];
  float x=0,prevRight=0;boolean first=true;
  for(var e:units){
   RowGroup g=e.getValue();int t=stackType(e.getKey().getCard());
   float gap=first?0:Math.max(0,g.left-prevRight);prevRight=g.right;
   if(t>=0&&fold[t]&&!Float.isNaN(slot[t])){k[t]++;shift(g,slot[t]+k[t]*step[t]);continue;}   // tucked into its fan
   float nx=first?g.left:x+gap;shift(g,nx);x=nx+g.width();first=false;
   if(t>=0&&fold[t]){slot[t]=nx;x+=(count[t]-1)*step[t];}   // room for the rest of the fan
  }
 }
 private static void shift(RowGroup g,float nx){float dx=nx-g.left;if(Math.abs(dx)<.01f)return;for(FDisplayObject c:g.cards)c.setPosition(c.getLeft()+dx,c.getTop());}
 /** the old "Artifacts N" button is gone: the count sits on the fan itself */
 private static final java.util.Map<VField,FButton> BUTTONS=new java.util.WeakHashMap<>();
 private static void zoneButton(VField field,float usable){FButton b=BUTTONS.get(field);if(b!=null)b.setVisible(false);}
 private static final java.util.Map<VCardDisplayArea,StackBadge[]> BADGES=new java.util.WeakHashMap<>();
 /** round count on the top card of a fan; a tap opens / closes that fan */
 static final class StackBadge extends FDisplayObject{
  final VField field;final int type;int n;boolean opened;
  StackBadge(VField field,int type){this.field=field;this.type=type;}
  @Override public boolean tap(float x,float y,int count){toggle(field,type);return true;}
  @Override public void draw(Graphics g){
   float w=getWidth(),h=getHeight(),r=Math.min(w,h)/2,cx=w/2,cy=h/2;
   Color gold=Color.valueOf("e0b24f"),dark=Color.valueOf("111722");
   g.fillCircle(new Color(0,0,0,.45f),cx+r*.08f,cy+r*.1f,r);g.fillCircle(gold,cx,cy,r);
   if(!opened)g.fillCircle(isHovered()?Color.valueOf("1c2738"):dark,cx,cy,r-Math.max(1.2f,r*.12f));
   String t=String.valueOf(n);g.drawText(t,forge.assets.FSkinFont.forHeight(h*.6f),opened?dark:gold,0,0,w,h,false,com.badlogic.gdx.utils.Align.center,true);
  }
 }
 /** after a row layout: the count badge on the top card of each fan (or on the first card while the fan is open) */
 private static void placeZoneButton(VCardDisplayArea area){
  if(!(area instanceof VField.FieldRow))return;VField field=owner(area);if(field==null)return;
  StackBadge[] bs=BADGES.computeIfAbsent(area,a->new StackBadge[2]);
  if(!foldOn(field)){for(StackBadge b:bs)if(b!=null)b.setVisible(false);return;}
  var units=units(area);int[] count=new int[2];RowGroup[] firstU=new RowGroup[2],lastU=new RowGroup[2];
  for(var e:units){int t=stackType(e.getKey().getCard());if(t<0)continue;count[t]++;if(firstU[t]==null)firstU[t]=e.getValue();lastU[t]=e.getValue();}
  for(int t=0;t<2;t++){
   StackBadge b=bs[t];
   if(count[t]<2){if(b!=null)b.setVisible(false);continue;}
   if(b==null){b=new StackBadge(field,t);field.add(b);bs[t]=b;}
   b.n=count[t];b.opened=open(field,t);
   RowGroup u=b.opened?firstU[t]:lastU[t];
   float d=Math.max(18,Math.min(34,u.height()*.24f));
   float x=area.getLeft()+u.left+u.width()*.72f-d/2-area.getScrollLeft(),y=area.getTop()+u.top+u.height()*.07f;   // upper right of the top card
   b.setBounds(x,y,d,d);b.setVisible(true);
  }
 }
}
