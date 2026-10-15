package forge.screens.match.views;

import com.badlogic.gdx.math.Rectangle;
import forge.Forge;
import forge.screens.match.MatchScreen;

/**
 * The stack list hangs from the "Stack" tab at the top right. In the Arena-style battle layout the enemy portrait sits
 * exactly there, so it could not be chosen as a target (e.g. for a copied Lightning Strike) while the stack was open.
 * After Forge has placed the list (VStack.updateSizeAndPosition, hook by PatchStackClearance) it is moved left of every
 * player portrait it would cover.
 */
public final class StackClearanceMod {
 private StackClearanceMod(){}

 public static void after(VStack stack){
  try{
   if(!Forge.isLandscapeMode())return;
   float x=stack.getLeft(),y=stack.getTop(),w=stack.getWidth(),h=stack.getHeight();
   for(VPlayerPanel p:MatchScreen.getPlayerPanels().values()){
    Rectangle r=p.getAvatar().screenPos;
    if(r==null||r.width<=0)continue;
    float pad=Math.max(6,r.width*.12f);
    float left=r.x-pad,right=r.x+r.width+pad,top=r.y-pad,bottom=r.y+r.height*1.4f+pad;   // portrait + life below it
    if(x<right&&x+w>left&&y<bottom&&y+h>top)x=Math.max(0,left-w);
   }
   if(x!=stack.getLeft())stack.setLeft(Math.round(x));
  }catch(Throwable t){System.err.println("[StackClearance] "+t);}
 }
}
