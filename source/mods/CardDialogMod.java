package forge.toolbox;

import com.badlogic.gdx.graphics.Color;
import forge.Graphics;
import forge.assets.FSkinColor;

/** Card yes/no dialogs (FOptionPane.showCardOptionDialog, e.g. "Fire Lord Azula: ... put it into the command zone?")
 *  show only the card (user 2026-10-17: "diese Box ist auch unnoetig - einfach nur die Karte zeigen"): no opaque
 *  full-width panel, no swipe/title strip, only a faint shade so the board stays visible. Buttons and the question stay
 *  in the dialog's prompt bar. A message text/icon (multi-line confirms) gets a small dark backing to stay readable.
 *  Card choice lists in a match (ListChooser / FChoiceList, e.g. "Rift Bolt / Sorcery") are semi-transparent (user
 *  2026-10-17: "die sollen semi-transparent sein"): translucent dark panel, translucent rows, no swipe-dot strip.
 *  Called from FDialog.drawBackground/drawOverlay and FChoiceList.getItemFillColor through PatchCardDialog; every other
 *  dialog is unchanged. */
public final class CardDialogMod {
 private CardDialogMod(){}
 static final Color SHADE=new Color(0f,0f,0f,.64f),BACK=new Color(.04f,.05f,.08f,.72f),PANEL=new Color(.03f,.04f,.07f,.72f),
  EDGE=new Color(.88f,.72f,.40f,.55f),ORDER_PANEL=new Color(.03f,.04f,.07f,.86f);
 /** the choice dialog currently drawing its children (rows of its list get translucent fills) */
 static FDialog choice;
 /** only FOptionPane with the card display of showCardOptionDialog; 3-button dialogs with a title strip keep it */
 public static boolean cardOnly(FDialog d,FButton middle,String title){
  if(!(d instanceof FOptionPane p)||p.displayObj==null||!p.displayObj.getClass().getName().equals("forge.toolbox.FOptionPane$2"))return false;
  return middle==null||title==null||title.isEmpty();
 }
 static boolean choiceList(FDialog d){
  return (d instanceof FOptionPane   // every option pane in a match: choice lists and text questions like "Concede anyway?" (user 2026-10-17)
    ||d instanceof DualListBox)      // + "Select Order" (trigger order / choose cards in order) - was still the old Forge look (user 2026-10-18)
   &&forge.Forge.getCurrentScreen() instanceof forge.screens.match.MatchScreen;
 }
 /** hook for FDialog.drawBackground (overlay=false) and drawOverlay (overlay=true); true = skip the original */
 public static boolean draw(FDialog d,Graphics g,boolean overlay,FButton middle,String title,FDisplayObject prompt,float total,float reveal){
  if(cardOnly(d,middle,title)){if(!overlay)drawBackground(d,g);return true;}
  if(!choiceList(d)){choice=null;return false;}
  if(reveal==0)return true;
  float top=d.getHeight()-(total+d.getBottomMargin())*reveal,w=d.getWidth();
  if(!overlay){choice=d;g.fillRect(new Color(0f,0f,0f,.66f*reveal),0,0,w,Math.max(0,top));   /* darken the board behind a choice (user 2026-10-17; darker 2026-10-18: "Hintergrund noch mehr dimmen") */
   g.fillRect(d instanceof DualListBox?ORDER_PANEL:PANEL,0,top,w,Math.max(0,total-forge.screens.match.views.VPrompt.HEIGHT));g.drawLine(1.5f,EDGE,0,top,w,top);return true;}   // "Select Order" covers the whole board: darker, the hand behind must not compete with the lists
  choice=null;
  if(middle!=null&&title!=null&&!title.isEmpty()&&prompt!=null){   // title strip of 3-button dialogs, translucent
   float y=prompt.getTop()-FDialog.MSG_HEIGHT;g.fillRect(PANEL,0,y,w,FDialog.MSG_HEIGHT);
   g.drawText(title,forge.screens.match.views.VPrompt.FONT,forge.screens.match.views.VPrompt.getForeColor(),0,y,w,FDialog.MSG_HEIGHT,false,1,true);
  }
  return true;
 }
 /** Choices in a match (user 2026-10-18): Enter / Space = OK with what you selected (no automatic pick - "Auto" is the
  *  button below); OK with nothing selected asks "Really skip?" - Yes skips, No goes back to the choice. Called first in
  *  DualListBox.keyDown / FOptionPane.keyDown (true = key handled) and in DualListBox's OK action (okBlocked). */
 public static boolean enterKey(FDialog d,int key){
  if(key!=com.badlogic.gdx.Input.Keys.ENTER&&key!=com.badlogic.gdx.Input.Keys.NUMPAD_ENTER&&key!=com.badlogic.gdx.Input.Keys.SPACE)return false;
  try{
   if(d instanceof DualListBox<?> box){
    FButton ok=box.getButton(0);if(ok!=null&&ok.isEnabled())ok.trigger();   // OK (asks first when nothing is selected)
    return true;   // never "Auto" by key
   }
   if(d instanceof FOptionPane p&&p.displayObj instanceof FChoiceList<?> list&&list.maxChoices>0){
    if(bypass==d){bypass=null;return false;}
    if(list.getSelectedIndex()>=0||list.getCount()==0)return false;
    askSkip(()->{bypass=d;d.keyDown(key);});return true;
   }
  }catch(Throwable ignored){}
  return false;
 }
 /** DualListBox OK action: nothing selected -> ask first; true = stop the original OK */
 public static boolean okBlocked(DualListBox<?> box){
  try{
   if(bypass==box){bypass=null;return false;}
   FChoiceList<?> src=(FChoiceList<?>)field(DualListBox.class,"sourceList").get(box),dst=(FChoiceList<?>)field(DualListBox.class,"destList").get(box);
   if(dst.getCount()>0||src.getCount()==0)return false;
   askSkip(()->{bypass=box;FButton ok=box.getButton(0);if(ok!=null)ok.trigger();});return true;
  }catch(Throwable t){return false;}
 }
 private static FDialog bypass;
 private static void askSkip(Runnable skip){
  FOptionPane.showConfirmDialog("You did not choose anything. Really skip?","Really skip?","Yes, skip","No, back to the choice",false,
   yes->{if(Boolean.TRUE.equals(yes))skip.run();});
 }
 private static final java.util.Map<String,java.lang.reflect.Field> FIELDS=new java.util.HashMap<>();
 private static java.lang.reflect.Field field(Class<?> c,String name)throws ReflectiveOperationException{
  java.lang.reflect.Field f=FIELDS.get(c.getName()+"."+name);
  if(f==null){f=c.getDeclaredField(name);f.setAccessible(true);FIELDS.put(c.getName()+"."+name,f);}
  return f;
 }
 /** FChoiceList.getItemFillColor: rows of a match choice dialog are translucent (selected row stays clearly marked) */
 public static FSkinColor item(FSkinColor c){
  if(choice==null||c==null||!choice.isVisible())return c;
  return c.getColor().equals(FChoiceList.getSelColor().getColor())?c.alphaColor(.72f):c.alphaColor(.32f);
 }
 public static void drawBackground(FDialog d,Graphics g){
  g.fillRect(SHADE,0,0,d.getWidth(),d.getHeight());
  float pad=FOptionPane.PADDING/2;
  for(FDisplayObject c:d.getChildren())
   if(c.isVisible()&&(c instanceof FTextArea||c instanceof FLabel)&&c.getHeight()>0)
    g.fillRoundRect(BACK,c.getLeft()-pad,c.getTop()-pad,c.getWidth()+2*pad,c.getHeight()+2*pad,pad);
 }
}
