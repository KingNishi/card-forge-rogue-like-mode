package forge.adventure.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import java.util.*;

/** Render-thread-only dungeon art adapter. Pure presentation: no encounter selection, RNG or save writes. */
public final class DungeonMapArt {
 private static final String ROOT="D:/forge/_mods/dungeon-art-20261016/";
 private static final Map<String,Texture> textures=new HashMap<>();
 private static final Set<String> missing=new HashSet<>();
 private static Texture dot;
 private static final Color GOLD=Color.valueOf("d9ad65"),TEAL=Color.valueOf("79b3ab"),RED=Color.valueOf("b87169"),DIM=Color.valueOf("605b59");
 private DungeonMapArt(){}
 static Texture texture(String file){
  if(missing.contains(file))return null;Texture found=textures.get(file);if(found!=null)return found;
  var path=Gdx.files.absolute(ROOT+file);if(!path.exists()){missing.add(file);return null;}
  found=new Texture(path,true);found.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);textures.put(file,found);return found;
 }
 private static Texture dot(){
  if(dot==null){Pixmap p=new Pixmap(1,1,Pixmap.Format.RGBA8888);p.setColor(Color.WHITE);p.fill();dot=new Texture(p);p.dispose();}return dot;
 }
 private static void rect(Batch b,float x,float y,float w,float h,Color c,float a){b.setColor(c.r,c.g,c.b,c.a*a);b.draw(dot(),x,y,w,h);}
 private static void line(Batch b,float x,float y,float xx,float yy,float width,Color c,float a){
  float dx=xx-x,dy=yy-y,len=(float)Math.sqrt(dx*dx+dy*dy);b.setColor(c.r,c.g,c.b,c.a*a);
  b.draw(dot(),x,y-width/2,0,width/2,len,width,1,1,(float)Math.toDegrees(Math.atan2(dy,dx)),0,0,1,1,false,false);
 }
 private static void ring(Batch b,float x,float y,float r,float width,Color c,float a){
  for(int i=0;i<24;i++){double a0=i*Math.PI/12,a1=(i+1)*Math.PI/12;line(b,x+r*(float)Math.cos(a0),y+r*(float)Math.sin(a0),x+r*(float)Math.cos(a1),y+r*(float)Math.sin(a1),width,c,a);}
 }
 /** x/y use libGDX's bottom-left origin. Caller owns timing; pass 0 for a still preview. */
 public static void background(Batch b,float x,float y,float w,float h,float elapsed,float alpha){
  Color old=b.getColor().cpy();try{
   Texture t=texture("dungeon-background.png");b.setColor(1,1,1,alpha);
   if(t!=null)b.draw(t,x,y,w,h);else rect(b,x,y,w,h,Color.valueOf("101315"),alpha);
   // A handful of dim drifting motes along the walls. The map surface stays still and legible.
   for(int i=0;i<18;i++){float p=(elapsed*(.025f+i*.0007f)+i*.618034f)%1;
    float xx=x+w*(i%2==0?.075f:.925f)+w*.016f*(float)Math.sin(elapsed*.31+i),yy=y+h*p;
    float fade=(float)Math.sin(Math.PI*p),s=Math.max(.5f,w*.001f);
    rect(b,xx,yy,s,s,GOLD,alpha*.32f*fade);
   }
  }finally{b.setColor(old);}
 }
 /** Same topology as gameplay: left/right branches merge at floors 5, 10 and 15.
  * The current side is cosmetic only; do not infer a saved choice from the diagram. */
 public static float[] nodePosition(int floor,int side){
  float x=.035f+.93f*(Math.max(1,Math.min(15,floor))-1)/14f;
  float y=floor%5==0?.5f:side==0?.75f:.25f;return new float[]{x,y};
 }
 public static void map(Batch b,float x,float y,float w,float h,int currentFloor,int chosenSide,float elapsed,float alpha){
  Color old=b.getColor().cpy();try{
   float radius=Math.max(2.8f,Math.min(w*.013f,h*.095f)),stroke=Math.max(.55f,radius*.17f);
   for(int floor=1;floor<15;floor++)for(int side=0;side<2;side++){
    float[] p=nodePosition(floor,side),q=nodePosition(floor+1,side);
    boolean past=floor<currentFloor;Color c=past?TEAL:DIM;
    float ax=x+w*p[0],ay=y+h*p[1],bx=x+w*q[0],by=y+h*q[1];
    line(b,ax,ay,bx,by,stroke,c,alpha*(past?.70f:.40f));
    if(floor==currentFloor&&chosenSide==side){float k=(elapsed*.4f)%1;ring(b,ax+(bx-ax)*k,ay+(by-ay)*k,radius*.18f,stroke,GOLD,alpha*.8f);}
   }
   for(int floor=1;floor<=15;floor++)for(int side=0;side<(floor%5==0?1:2);side++){
    float[] p=nodePosition(floor,side);float xx=x+w*p[0],yy=y+h*p[1];boolean boss=floor%5==0,now=floor==currentFloor;
    Color c=floor<currentFloor?TEAL:now?GOLD:boss?RED:DIM;float r=radius*(boss?1.25f:1);
    rect(b,xx-r*.7f,yy-r*.7f,r*1.4f,r*1.4f,Color.valueOf("111416"),alpha);
    ring(b,xx,yy,r,stroke,c,alpha);
    if(now){float pulse=.5f+.5f*(float)Math.sin(elapsed*3);ring(b,xx,yy,r*(1.35f+pulse*.18f),stroke,GOLD,alpha*(.2f+.2f*pulse));}
    if(boss){ // crown: three prongs, distinguished from the ordinary crossed blades
     line(b,xx-r*.55f,yy-r*.3f,xx+r*.55f,yy-r*.3f,stroke,c,alpha);
     for(int n=-1;n<=1;n++)line(b,xx+n*r*.45f,yy-r*.3f,xx+n*r*.55f,yy+r*(n==0?.55f:.25f),stroke,c,alpha);
    }else{line(b,xx-r*.35f,yy-r*.4f,xx+r*.35f,yy+r*.4f,stroke,c,alpha);line(b,xx-r*.35f,yy+r*.4f,xx+r*.35f,yy-r*.4f,stroke,c,alpha);}
   }
  }finally{b.setColor(old);}
 }
 /** Do not call for a merely possible encounter: hidden events must stay hidden until the logic reveals them. */
 public static void encounterNode(Batch b,float x,float y,float radius,float sinceReveal,float alpha){
  Color old=b.getColor().cpy();try{float t=Math.min(1,Math.max(0,sinceReveal/.45f)),ease=1-(1-t)*(1-t)*(1-t),r=radius*(.75f+.25f*ease);
   ring(b,x,y,r,Math.max(.6f,r*.12f),TEAL,alpha*ease);
   line(b,x,y-r*.3f,x,y+r*.3f,Math.max(.8f,r*.15f),TEAL,alpha*ease);
   ring(b,x,y-r*.55f,r*.06f,Math.max(.6f,r*.09f),TEAL,alpha*ease);
  }finally{b.setColor(old);}
 }
 public static String encounterFile(String kind){return switch(kind){
  case "relics","heirloom","heirlooms"->"heirloom-shop.png";
  case "shop","favor","divine-favor"->"divine-favor-shop.png";
  case "offer"->"refuse-offer.png";
  case "merchant"->"far-away-merchants.png";
  default->null;
 };}
 /** Draw behind the encounter panel. Gentle one-time fade; no zoom jitter below text. */
 public static void encounter(Batch b,String kind,float x,float y,float w,float h,float sinceReveal,float alpha){
  String file=encounterFile(kind);if(file==null)return;Texture t=texture(file);if(t==null)return;
  Color old=b.getColor().cpy();try{float a=Math.max(0,Math.min(1,sinceReveal/.45f));b.setColor(1,1,1,alpha*a);b.draw(t,x,y,w,h);}
  finally{b.setColor(old);}
 }
 /** Call on the GL thread when the Rogue scene is permanently disposed. */
 public static void dispose(){for(Texture t:textures.values())t.dispose();textures.clear();missing.clear();if(dot!=null){dot.dispose();dot=null;}}
}
