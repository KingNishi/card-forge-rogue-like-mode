package forge.adventure.util;
import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import forge.adventure.stage.WorldStage;
import forge.adventure.world.World;
import java.util.*;
/** Bounded, cosmetic overworld weather; no effects on maps, rules or collisions. */
public final class BiomeWeatherMod {
 private static SpriteBatch particles;private static Texture dot,streak;private static ShaderProgram heat;
 private static final Map<Batch,ShaderProgram> previous=new WeakHashMap<>();
 private static float time,transition=1;private static int current=-1,old=-1;private static boolean enabled=true;
 public static int kind(String name){String n=name.toLowerCase(Locale.ROOT);if(n.contains("white")||n.contains("plain")||n.contains("desert"))return 0;if(n.contains("black")||n.contains("swamp"))return 1;if(n.contains("green")||n.contains("forest"))return 2;if(n.contains("blue")||n.contains("island"))return 3;if(n.contains("red")||n.contains("mountain"))return 4;return -1;}
 private static boolean active(){return forge.Forge.getCurrentScene() instanceof forge.adventure.scene.GameScene;}
 private static int biome(){
  World w=Current.world();var p=WorldStage.getInstance().getPlayerSprite();if(p==null)return -1;
  int i=World.highestBiome(w.getBiome((int)p.getX()/w.getTileSize(),(int)p.getY()/w.getTileSize()));
  var biomes=w.getData().GetBiomes();return i>=0&&i<biomes.size()?kind(biomes.get(i).name): -1;
 }
 public static void before(Batch batch){
  if(!active())return;
  if(Gdx.input.isKeyJustPressed(Input.Keys.F7))enabled=!enabled;
  if(!enabled||biome()!=4)return;
  if(heatShader()==null)return;
  previous.put(batch,batch.getShader());batch.setShader(heat);heat.bind();heat.setUniformf("u_time",time);
 }
 public static ShaderProgram heatShader(){
  if(heat==null){
   String vertex="attribute vec4 a_position;attribute vec4 a_color;attribute vec2 a_texCoord0;uniform mat4 u_projTrans;varying vec4 v_color;varying vec2 v_texCoords;void main(){v_color=a_color;v_color.a*=255.0/254.0;v_texCoords=a_texCoord0;gl_Position=u_projTrans*a_position;}";
   String fragment="#ifdef GL_ES\nprecision mediump float;\n#endif\nvarying vec4 v_color;varying vec2 v_texCoords;uniform sampler2D u_texture;uniform float u_time;void main(){vec2 uv=v_texCoords;uv.x+=sin(gl_FragCoord.y*.09+u_time*2.4)*.0007;gl_FragColor=v_color*texture2D(u_texture,uv);}";
   heat=new ShaderProgram(vertex,fragment);if(!heat.isCompiled()){System.err.println("[Weather heat shader] "+heat.getLog());heat.dispose();heat=null;return null;}
  }
  return heat;
 }
 public static void after(Batch batch){if(previous.containsKey(batch))batch.setShader(previous.remove(batch));}
 private static void initialize(){
  if(particles!=null)return;particles=new SpriteBatch();
  Pixmap p=new Pixmap(16,16,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  for(int y=0;y<16;y++)for(int x=0;x<16;x++){float r=(float)Math.hypot(x-7.5,y-7.5)/7.5f;p.setColor(1,1,1,Math.max(0,1-r)*.8f);p.drawPixel(x,y);}dot=new Texture(p);p.dispose();
  p=new Pixmap(2,16,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  for(int y=0;y<16;y++){p.setColor(1,1,1,(float)Math.sin(Math.PI*y/16));p.drawLine(0,y,1,y);}streak=new Texture(p);p.dispose();
  dot.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);streak.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
 }
 private static float fraction(float n){return n-(float)Math.floor(n);}
 public static void draw(WorldStage stage){
  if(!active())return;float dt=Math.min(.05f,Gdx.graphics.getDeltaTime());time+=dt;if(!enabled)return;
  int next=biome();if(next!=current){old=current;current=next;transition=0;}transition=Math.min(1,transition+dt*.7f);
  initialize();var cam=stage.getCamera();float zoom=cam instanceof OrthographicCamera c?c.zoom:1;
  float w=cam.viewportWidth*zoom,h=cam.viewportHeight*zoom,left=cam.position.x-w/2,bottom=cam.position.y-h/2;
  particles.setProjectionMatrix(cam.combined);particles.begin();layer(old,1-transition,left,bottom,w,h);layer(current,transition,left,bottom,w,h);particles.end();
 }
 public static void preview(int biome,float seconds,float w,float h){initialize();time=seconds;particles.getProjectionMatrix().setToOrtho2D(0,0,w,h);particles.begin();layer(biome,1,0,0,w,h);particles.end();}
 private static void layer(int type,float fade,float left,float bottom,float w,float h){
  if(type<0||fade<.01f)return;
  int count=type==3?110:type==4?18:70;
  for(int i=0;i<count;i++){
   float seed=fraction(i*.618034f+.173f),rate=.7f+fraction(i*.371f);
   float x=fraction(seed+time*(type==0?.022f:type==3?-.026f:.009f)*rate)*w+left;
   float y=fraction(i*.437f+time*(type==3?-.24f:type==1?.012f:.008f)*rate)*h+bottom;
   float size=(type==2?1.4f:type==0?.65f:.85f)*Math.max(.7f,w/480);
   if(type==3){particles.setColor(.66f,.80f,.90f,.24f*fade);particles.draw(streak,x,y,.24f*Math.max(.7f,w/480),5*rate*Math.max(.7f,h/270));}
   else{float pulse=.65f+.35f*(float)Math.sin(time*1.8+i);if(type==0)particles.setColor(.88f,.73f,.46f,.34f*fade);if(type==1)particles.setColor(.65f,.61f,.56f,.32f*fade);if(type==2)particles.setColor(.45f,.92f,.39f,.38f*pulse*fade);if(type==4)particles.setColor(1,.54f,.18f,.20f*fade);particles.draw(dot,x+(float)Math.sin(time+i)*size,y,size,size);}
  }
 }
}
