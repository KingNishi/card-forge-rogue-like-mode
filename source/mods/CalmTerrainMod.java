package forge.adventure.util;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import java.nio.ByteOrder;
import java.util.Locale;
/** Downloaded CC0 surfaces applied only to GPU textures. */
public final class CalmTerrainMod {
 private static int[][] surfaces;
 private static final int SIDE=512;
 public static boolean eligible(String path){
  String p=path.replace('\\','/').toLowerCase(Locale.ROOT);
  return p.contains("/adventure/")&&(p.contains("/world/tilesets/")||p.contains("/maps/tileset/"))
   &&!p.contains("shop_sign")&&!p.endsWith("/items.png")&&!p.contains("_buildings")&&!p.contains("large_buildings");
 }
 private static synchronized void initialize(){
  if(surfaces!=null)return;
  int[][] loaded=new int[7][SIDE*SIDE];
  String[] names={"pack3/hp_03.png","pack3/hp_04.png","pack1/hp_rock.png","pack1/hp_earth_ground.png","water.png","snow.png","dungeon-stone.png"};
  for(int m=0;m<7;m++){
   Pixmap source=new Pixmap(Gdx.files.absolute("D:/forge/_mods/calm-terrain/external/"+names[m]));
   try{for(int y=0;y<SIDE;y++)for(int x=0;x<SIDE;x++){
    int c=source.getPixel(x*source.getWidth()/SIDE,y*source.getHeight()/SIDE);
    float r=c>>>24,g=(c>>>16)&255,b=(c>>>8)&255,lum=r*.25f+g*.6f+b*.15f;
    float sat=m==0?.68f:m==4?.72f:.80f,exposure=m==0?.76f:m==1?.92f:m==5?.95f:.90f;
    r=(lum+(r-lum)*sat)*exposure;g=(lum+(g-lum)*sat)*exposure;b=(lum+(b-lum)*sat)*exposure;
    if(m==0){r*=.79f;g*=1.04f;b*=.83f;}
    if(m==1){r*=1.12f;g*=1.03f;b*=.85f;}
    loaded[m][y*SIDE+x]=(clamp(r)<<24)|(clamp(g)<<16)|(clamp(b)<<8)|255;
   }}finally{source.dispose();}
  }
  surfaces=loaded;
 }
 public static Pixmap refine(Pixmap p){return refine(p,"",true,4);}
 public static Pixmap refine(Pixmap p,String path,boolean world,int scale){
  initialize();if(p.getFormat()!=Pixmap.Format.RGBA8888)return p;
  int w=p.getWidth(),h=p.getHeight();int[] pixels=new int[w*h];
  p.getPixels().duplicate().order(ByteOrder.BIG_ENDIAN).asIntBuffer().get(pixels);
  String file=path.replace('\\','/').toLowerCase(Locale.ROOT);
  boolean atlas=file.endsWith("/main.png"),dungeon=file.endsWith("/dungeon.png");
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int i=y*w+x,c=pixels[i],a=c&255;if(a==0)continue;
   float r=c>>>24,g=(c>>>16)&255,b=(c>>>8)&255;
   float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),lum=r*.25f+g*.6f+b*.15f;
   int material=2;
   if(b>r*1.18f&&b>g*.88f)material=4;
   else if(g>r*1.12f&&g>b*1.12f)material=0;
   else if(r>140&&g>110&&r>b*1.15f)material=1;
   else if(min>195&&max-min<40)material=5;
   else if(r>g*1.1f&&g>b*1.1f)material=3;
   int ox=x/scale,oy=y/scale;
   boolean ground=atlas&&((ox<1280&&oy>=128&&oy<320)
    ||(ox>=416&&ox<1184&&oy>=416&&oy<640));
   // Only known terrain banks: never paste ground material over doors, props or monsters.
   boolean stoneFloor=dungeon&&((ox<48&&oy<48)||(ox<512&&oy>=528&&oy<592));
   boolean dedicatedFloor=file.endsWith("/inn_dungeon_floor.png");
   if(stoneFloor||dedicatedFloor){ground=true;material=6;}
   float strength=!world&&ground&&max>70?(material==6?.38f:.28f):0;
   if(max<120)strength*=Math.max(0,(max-70)/50);
   int period=world?SIDE:16*scale;
   int sx=(x%period)*SIDE/period,sy=(y%period)*SIDE/period;
   int sample=surfaces[material][sy*SIDE+sx];
   float shading=.86f+.22f*Math.min(1,lum/190f);
   float sat=world?.88f:.90f,exposure=world?.98f:1.03f;
   float rr=(lum+(r-lum)*sat)*exposure,gg=(lum+(g-lum)*sat)*exposure,bb=(lum+(b-lum)*sat)*exposure;
   rr=rr*(1-strength)+(sample>>>24)*shading*strength;
   gg=gg*(1-strength)+((sample>>>16)&255)*shading*strength;
   bb=bb*(1-strength)+((sample>>>8)&255)*shading*strength;
   pixels[i]=(clamp(rr)<<24)|(clamp(gg)<<16)|(clamp(bb)<<8)|a;
  }
  p.getPixels().duplicate().order(ByteOrder.BIG_ENDIAN).asIntBuffer().put(pixels);return p;
 }
 private static int clamp(float n){return Math.max(0,Math.min(255,Math.round(n)));}
}
