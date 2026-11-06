package forge.adventure.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import forge.Graphics;
import forge.assets.FImage;
import forge.assets.FSkinImage;

/** Shared painted button state sheet. Render thread only; never changes input/layout/game state. */
public final class FantasyButtonArt {
 private FantasyButtonArt(){}
 private static Texture atlas;
 private static boolean tried;
 private static final int CW=1760,CH=208,CAP=310;
 private static final TextureRegion[][] segments=new TextureRegion[4][3];
 public static boolean available(){
  if(!tried){tried=true;var f=Gdx.files.absolute("D:/forge/_mods/rogue-commanders/menu-art/buttons/button-states.png");
   if(f.exists())try{atlas=new Texture(f,true);atlas.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
    if(atlas.getWidth()!=CW||atlas.getHeight()!=CH*4){atlas.dispose();atlas=null;}
    else for(int s=0;s<4;s++){segments[s][0]=new TextureRegion(atlas,0,s*CH,CAP,CH);segments[s][1]=new TextureRegion(atlas,CAP,s*CH,CW-2*CAP,CH);segments[s][2]=new TextureRegion(atlas,CW-CAP,s*CH,CAP,CH);}
   }catch(Exception e){Gdx.app.error("FantasyButtonArt","Cannot load button sheet",e);atlas=null;}
  }return atlas!=null;
 }
 private static int state(int v){return Math.max(0,Math.min(3,v));}
 public static float paintedHeight(float w,float h){return Math.min(h,w*.30f*CH/CAP);}
 public static float capWidth(float w,float h){return paintedHeight(w,h)*CAP/CH;}
 public static float textInset(float w,float h){return capWidth(w,h)*.62f+Math.min(2,h*.08f);}
 private static void parts(Batch b,int v,float x,float y,float w,float h){
  float cap=h*CAP/CH;
  b.draw(segments[v][0],x,y,cap,h);
  float middle=Math.max(0,w-2*cap),tile=h*(CW-2*CAP)/CH;
  for(float offset=0;offset<middle;offset+=tile){float width=Math.min(tile,middle-offset);TextureRegion r=segments[v][1];
   if(width<tile){r=new TextureRegion(r);r.setRegionWidth(Math.max(1,Math.round(r.getRegionWidth()*width/tile)));}
   b.draw(r,x+cap+offset,y,width,h);
  }
  b.draw(segments[v][2],x+w-cap,y,cap,h);
 }
 public static boolean draw(Batch b,int v,boolean primary,float x,float y,float w,float h,float alpha){
  if(!available()||w<=0||h<=0)return false;v=state(v);
  Color old=b.getColor().cpy();int src=b.getBlendSrcFunc(),dst=b.getBlendDstFunc(),sa=b.getBlendSrcFuncAlpha(),da=b.getBlendDstFuncAlpha();
  float dh=paintedHeight(w,h),yy=y+(h-dh)*.5f;
  try{
   if(primary&&v!=3){
    b.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,com.badlogic.gdx.graphics.GL20.GL_ONE);
    float pulse=.85f+.15f*(float)Math.sin(com.badlogic.gdx.utils.TimeUtils.nanoTime()/1e9*1.5);
    for(int ring=3;ring>=1;ring--)for(int k=0;k<8;k++){
     double angle=k*Math.PI/4;float radius=dh*.025f*ring;
     b.setColor(.42f,.22f,1,alpha*.025f*pulse);parts(b,v,x+(float)Math.cos(angle)*radius,yy+(float)Math.sin(angle)*radius,w,dh);
    }
    b.setBlendFunctionSeparate(src,dst,sa,da);
   }
   b.setColor(1,1,1,alpha*(v==3?.70f:1));parts(b,v,x,yy,w,dh);
   if(primary&&v!=3){b.setColor(.78f,.60f,1,alpha*.24f);parts(b,v,x,yy,w,dh);}
  }finally{b.setBlendFunctionSeparate(src,dst,sa,da);b.setColor(old);}return true;
 }
 public static boolean drawGraphics(Graphics g,int v,boolean primary,float x,float y,float w,float h){
  return draw(g.getBatch(),v,primary,g.adjustX(x),g.adjustY(y,h),w,h,g.getfloatAlphaComposite());
 }
 public static boolean drawSegment(Graphics g,FImage image,float x,float y,float w,float h){
  if(!(image instanceof FSkinImage skin)||!available())return false;
  String n=skin.name();if(!n.startsWith("BTN_")||n.contains("TOGGLE"))return false;
  int s=n.contains("DISABLED")?3:n.contains("DOWN")?2:n.contains("OVER")?1:0;
  int part=n.endsWith("LEFT")?0:n.endsWith("RIGHT")?2:n.endsWith("CENTER")?1:-1;
  if(part<0)return false;g.drawImage(segments[s][part],x,y,w,h);return true;
 }
 public static Drawable drawable(Drawable old,int s){return drawable(old,s,false);}
 public static Drawable drawable(Drawable old,int s,boolean primary){
  return new BaseDrawable(old){@Override public void draw(Batch batch,float x,float y,float w,float h){
   Color c=batch.getColor().cpy();if(!FantasyButtonArt.draw(batch,s,primary,x,y,w,h,c.a)&&old!=null)old.draw(batch,x,y,w,h);
  }};
 }
}
