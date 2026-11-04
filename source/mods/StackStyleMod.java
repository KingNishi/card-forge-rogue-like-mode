package forge.screens.match.views;

import com.badlogic.gdx.graphics.Color;
import forge.Graphics;
import forge.assets.FSkinColor;

/** Stack popup semi-transparent (user 2026-10-17: "den Stack bitte semitransparent darstellen"): the panel and the
 *  row backgrounds are drawn with reduced alpha, card images and text stay fully opaque. Called from VStack and
 *  VStack$StackInstanceDisplay through PatchStackStyle (every fillRect there is redirected here). */
public final class StackStyleMod {
 private StackStyleMod(){}
 static final float ALPHA=.62f;
 public static void fillRect(Graphics g,Color c,float x,float y,float w,float h){g.fillRect(new Color(c.r,c.g,c.b,c.a*ALPHA),x,y,w,h);}
 /** replaces FDropDown's opaque background texture for the stack popup only: translucent dark glass + thin rim */
 public static void drawBackground(VStack v,Graphics g){
  float w=v.getWidth(),h=v.getHeight();
  g.fillRect(new Color(.05f,.07f,.11f,.55f),0,0,w,h);
  g.drawRect(1f,new Color(.55f,.62f,.78f,.55f),0,0,w,h);
 }
 public static void fillRect(Graphics g,FSkinColor c,float x,float y,float w,float h){Color k=c.getColor();g.fillRect(new Color(k.r,k.g,k.b,k.a*ALPHA),x,y,w,h);}
}
