package forge.adventure.character;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
public final class FloatingMageMod {
 private static Texture shadow;
 public static boolean selected(CharacterSprite actor,TextureRegion r){
  return actor instanceof PlayerSprite && r.getTexture().getTextureData() instanceof FileTextureData d
   && d.getFileHandle().name().equals("floating-mage@8x.png");
 }
 public static float visualSize(float logical){return logical*1.1f;}
 public static float hover(float time,float height){return height*(.09f+.035f*(float)Math.sin(time*2.4));}
 public static void draw(Batch b,TextureRegion r,float x,float y,float w,float h,CharacterSprite actor){
  if(!selected(actor,r)){b.draw(r,x,y,w,h);return;}
  float t=(System.nanoTime()%600_000_000_000L)/1_000_000_000f;
  if(shadow==null){
   Pixmap p=new Pixmap(64,32,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
   for(int py=0;py<32;py++)for(int px=0;px<64;px++){
    float dx=(px-31.5f)/32,dy=(py-15.5f)/16,d=dx*dx+dy*dy;
    if(d<1)p.drawPixel(px,py,((int)((1-d)*95)));
   }
   shadow=new Texture(p);p.dispose();shadow.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
  }
  Color saved=new Color(b.getColor());b.setColor(1,1,1,saved.a);
  b.draw(shadow,x+w*.2f,y-h*.025f,w*.6f,h*.18f);b.setColor(saved);
  float vw=visualSize(w),vh=visualSize(h),baseX=x-(vw-w)/2,baseY=y+hover(t,h);
  for(int row=0;row<24;row++){
   float top=row/24f,bottom=(row+1)/24f;
   float sway=(float)Math.sin(t*2.8+top*2.5)*w*.016f*top*top;
   float v0=r.getV()+(r.getV2()-r.getV())*top,v1=r.getV()+(r.getV2()-r.getV())*bottom;
   b.draw(r.getTexture(),baseX+sway,baseY+vh*(1-bottom),vw,vh/24,r.getU(),v1,r.getU2(),v0);
  }
 }
}
