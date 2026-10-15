package forge.screens.match.views;

import forge.Forge;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FScrollPane;
import java.util.List;

/**
 * Mana pool (W U B R G C) layout. Forge puts the six labels into a 2 x 3 grid in landscape, made for a tall side panel.
 * The Arena-style battle HUD gives the pool a wide, flat strip instead: the cells became huge and flat, symbols and counts
 * were clipped and overlapped, and taps on the symbols missed. Wide areas now get one centred row of compact cells;
 * everything else keeps Forge's layout. VManaPool.layoutAndGetScrollBounds is routed here by PatchManaPoolLayout.
 */
public final class ManaPoolLayoutMod {
 private ManaPoolLayoutMod(){}

 public static FScrollPane.ScrollBounds layout(List<? extends FDisplayObject> labels,float w,float h){
  int n=labels.size();
  if(n==0)return new FScrollPane.ScrollBounds(w,h);
  boolean horizontal=!Forge.isLandscapeMode()||Forge.altZoneTabs&&"Horizontal".equalsIgnoreCase(Forge.altZoneTabMode);
  if(horizontal||w>h*2.2f){
   float cell=horizontal?w/n:Math.min(w/n,Math.max(56,h*1.15f));   // compact cells, centred
   float x=(w-cell*n)/2;
   for(FDisplayObject l:labels){l.setBounds(x,0,cell,h);x+=cell;}
  }else{                                                            // Forge's original 2 x 3 grid
   float lw=w/2,lh=h/3,x=0,y=0;int count=0;
   for(FDisplayObject l:labels){l.setBounds(x,y,lw,lh);if(++count%2==0){x=0;y+=lh;}else x+=lw;}
  }
  return new FScrollPane.ScrollBounds(w,h);
 }
}
