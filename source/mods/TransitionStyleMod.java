package forge.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Align;
import forge.Graphics;
import forge.assets.FImage;
import forge.assets.FSkinFont;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FProgressBar;
import java.lang.reflect.Field;

/**
 * Loading screen (TransitionScreen$BGAnimation.drawBackground): the old Forge logo becomes a modern gold spinner and the
 * pixel progress bar a slim gold bar with its text (PatchTransitionStyle routes the three draw calls here).
 */
public final class TransitionStyleMod {
 private TransitionStyleMod(){}
 static final Color GOLD=Color.valueOf("e0b24f"),TRACK=Color.valueOf("1a2130"),TEXT=Color.valueOf("ece6d8");
 private static Field value,maximum,desc;

 /** replaces FSkinImage.LOGO */
 public static void logo(Graphics g,FImage image,float x,float y,float w,float h){spinner(g,x,y,w,h);}
 /** replaces the skin logo texture (FSkin.getLogo()) */
 public static void logoTexture(Graphics g,Texture image,float x,float y,float w,float h){spinner(g,x,y,w,h);}

 static void spinner(Graphics g,float x,float y,float w,float h){
  float cx=x+w/2,cy=y+h/2,r=Math.max(18,Math.min(w,h)*.16f),dot=Math.max(2.5f,r*.12f);
  int n=12;float head=(System.currentTimeMillis()%1100)/1100f*n;
  for(int i=0;i<n;i++){
   double a=2*Math.PI*i/n-Math.PI/2;float behind=((head-i)%n+n)%n;
   float alpha=Math.max(.14f,1f-behind/n*1.15f);
   g.fillCircle(new Color(GOLD.r,GOLD.g,GOLD.b,alpha),cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r,dot*(.7f+.3f*alpha));
  }
  Gdx.graphics.requestRendering();
 }

 /** replaces Graphics.draw(progressBar) */
 public static void progress(Graphics g,FDisplayObject bar){
  try{
   if(!(bar instanceof FProgressBar)){g.draw(bar);return;}
   if(value==null){value=field("value");maximum=field("maximum");desc=field("desc");}
   int v=value.getInt(bar),max=Math.max(1,maximum.getInt(bar));String text=(String)desc.get(bar);
   float w=bar.getWidth()*.8f,x=bar.getLeft()+(bar.getWidth()-w)/2,th=Math.max(3,bar.getHeight()*.16f),y=bar.getTop()+bar.getHeight()-th;
   g.fillRoundRect(TRACK,x,y,w,th,th/2);
   float fw=w*Math.max(0,Math.min(1,v/(float)max));
   if(fw>th)g.fillRoundRect(GOLD,x,y,fw,th,th/2);
   if(text!=null&&!text.isEmpty())
    g.drawText(text,FSkinFont.forHeight(bar.getHeight()*.5f),TEXT,x,bar.getTop(),w,bar.getHeight()-th*2,false,Align.center,true);
  }catch(Throwable t){g.draw(bar);}
 }
 private static Field field(String name)throws NoSuchFieldException{Field f=FProgressBar.class.getDeclaredField(name);f.setAccessible(true);return f;}
}
