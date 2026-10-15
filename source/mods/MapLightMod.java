package forge.adventure.util;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.WeakHashMap;
/**
 * Light and atmosphere for town and dungeon maps (PointOfInterestMapRenderer.render is wrapped by begin/end):
 * the map and its sprites are drawn into an off-screen buffer, then composited with a shader -
 * soft shadows along walls and void edges, world-anchored surface variation, light cast by bright saturated pixels
 * (torches, lava, crystals, magic), a warm flickering light around the player in dark places, drifting dust/fog,
 * cloud shadows in towns, colour grading and a vignette. The HUD is drawn afterwards and stays untouched.
 */
public final class MapLightMod {
 private static FrameBuffer scene,small;private static ShaderProgram shader;private static SpriteBatch batch,copy;
 private static boolean failed,active;private static final IntBuffer VP=BufferUtils.newIntBuffer(16);
 private static final Map<TiledMap,Float> voids=new WeakHashMap<>();
 private static float level=-1;private static TiledMap lastMap;private static final long START=System.nanoTime();
 private static Field stageField;
 static final String VERTEX="attribute vec4 a_position;attribute vec4 a_color;attribute vec2 a_texCoord0;uniform mat4 u_projTrans;varying vec4 v_color;varying vec2 v_texCoords;void main(){v_color=a_color;v_texCoords=a_texCoord0;gl_Position=u_projTrans*a_position;}";
 static final String FRAGMENT=String.join("\n",
  "#ifdef GL_ES","precision highp float;","#endif",
  "varying vec4 v_color;varying vec2 v_texCoords;",
  "uniform sampler2D u_texture;uniform sampler2D u_small;",
  "uniform vec2 u_res;uniform vec2 u_player;uniform float u_tile;uniform vec2 u_world0;uniform float u_dark;uniform float u_time;",
  "float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}",
  "float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1.0,0.0)),f.x),mix(hash(i+vec2(0.0,1.0)),hash(i+vec2(1.0,1.0)),f.x),f.y);}",
  "float fbm(vec2 p){float v=0.0;float a=0.5;for(int i=0;i<3;i++){v+=a*noise(p);p*=2.07;a*=0.5;}return v;}",
  "float luma(vec3 c){return dot(c,vec3(0.299,0.587,0.114));}",
  "float emis(vec3 s){float mx=max(s.r,max(s.g,s.b));float mn=min(s.r,min(s.g,s.b));return smoothstep(0.6,0.92,mx)*smoothstep(0.25,0.6,mx-mn);}",
  "vec3 tap(vec2 px){return texture2D(u_small,px/u_res).rgb;}",
  "void main(){",
  " vec2 px=gl_FragCoord.xy;",
  " vec3 c=texture2D(u_texture,v_texCoords).rgb;",
  " vec2 world=u_world0+px/u_tile;",
  // soft shadows where floor meets void/walls (stronger underground)
  " float ao=0.0;float avg=0.0;",
  " for(int i=0;i<8;i++){float a=float(i)*0.7854;vec2 d=vec2(cos(a),sin(a));float l1=luma(tap(px+d*u_tile*0.55));float l2=luma(tap(px+d*u_tile*1.1));",
  "  ao+=(1.0-smoothstep(0.02,0.10,l1))*0.6+(1.0-smoothstep(0.02,0.10,l2))*0.4;avg+=l1+l2;}",
  " ao=clamp(ao/8.0,0.0,1.0);avg/=16.0;float voidPx=1.0-smoothstep(0.015,0.05,luma(c));",
  " c*=mix(1.0,0.45,ao*(1.0-voidPx)*mix(0.25,1.0,u_dark));",
  // world-anchored surface variation: floors and grass stop looking flat
  " c*=0.93+0.15*fbm(world*0.42)+0.04*(noise(world*3.1)-0.5);",
  // light cast by bright saturated things nearby
  " vec3 glow=vec3(0.0);",
  " for(int i=0;i<10;i++){float a=float(i)*0.6283+0.2;vec2 d=vec2(cos(a),sin(a));vec3 s1=tap(px+d*u_tile*0.9);vec3 s2=tap(px+d*u_tile*2.0);glow+=s1*emis(s1)*smoothstep(0.08,0.28,luma(s1)-avg)+s2*emis(s2)*smoothstep(0.08,0.28,luma(s2)-avg)*0.7;}",
  " glow/=10.0;",
  // player light in dark places
  " float dist=length(px-u_player)/u_tile;",
  " float flick=0.95+0.035*sin(u_time*7.3)+0.02*sin(u_time*13.7+1.3);",
  " float torch=exp(-dist*dist/30.0)*flick;",
  " vec3 ambient=mix(vec3(1.0),vec3(0.19,0.19,0.25),u_dark);",
  " vec3 light=ambient+vec3(1.0,0.80,0.56)*torch*1.05*u_dark+glow*(0.6+2.4*u_dark);",
  " c*=light;",
  // drifting dust in the torch light, fog underground
  " float fog=fbm(world*0.22+vec2(u_time*0.045,u_time*0.018));",
  " c+=vec3(0.09,0.085,0.10)*smoothstep(0.5,0.9,fog)*(0.25+torch)*u_dark;",
  " float mote=step(0.9985,hash(floor(world*7.0+vec2(0.0,u_time*0.6))))*torch*u_dark;c+=vec3(1.0,0.85,0.6)*mote*0.5;",
  // cloud shadows drifting over towns
  " float cloud=fbm(world*0.07+vec2(u_time*0.018,u_time*0.007));",
  " c*=mix(1.0,mix(1.0,0.84,smoothstep(0.48,0.78,cloud)),1.0-u_dark);",
  // grading + vignette
  " float l=luma(c);c=mix(vec3(l),c,mix(0.96,1.03,u_dark));c=c*vec3(1.03,1.0,0.96);",
  " vec2 q=px/u_res-0.5;c*=mix(0.74,1.0,clamp(1.0-dot(q*vec2(1.0,1.25),q*vec2(1.0,1.25))*1.15,0.0,1.0));",
  " gl_FragColor=vec4(c,1.0);",
  "}");

 /** called at the start of PointOfInterestMapRenderer.render() */
 public static void begin(OrthogonalTiledMapRenderer renderer){
  active=false;
  if(failed||renderer==null||renderer.getMap()==null)return;
  int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();if(w<32||h<32)return;
  try{
   prepare(w,h);if(failed)return;
   Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,VP);
   scene.begin();Gdx.gl.glViewport(VP.get(0),VP.get(1),VP.get(2),VP.get(3));
   Gdx.gl.glClearColor(0,0,0,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
   active=true;
  }catch(Throwable t){fail(t);}
 }
 /** called right before PointOfInterestMapRenderer.render() returns */
 public static void end(OrthogonalTiledMapRenderer renderer){
  try{signsOnTop(renderer);}catch(Throwable t){System.err.println("[MapLight] shop signs: "+t);}
  if(!active)return;active=false;
  try{
   scene.end();
   Object stage=stage(renderer);
   Camera camera=stage instanceof com.badlogic.gdx.scenes.scene2d.Stage s?s.getCamera():null;
   float px=VP.get(0)+VP.get(2)/2f,py=VP.get(1)+VP.get(3)/2f,tile=48,wx=0,wy=0;
   if(camera!=null){
    Vector3 a=camera.project(new Vector3(0,0,0),VP.get(0),VP.get(1),VP.get(2),VP.get(3)),b=camera.project(new Vector3(16,0,0),VP.get(0),VP.get(1),VP.get(2),VP.get(3));
    tile=Math.max(4,b.x-a.x);wx=-a.x/tile;wy=-a.y/tile;
    if(stage instanceof forge.adventure.stage.GameStage gs&&gs.getPlayerSprite()!=null){
     var p=gs.getPlayerSprite();Vector3 c=camera.project(new Vector3(p.getX()+p.getWidth()/2,p.getY()+p.getHeight()/3,0),VP.get(0),VP.get(1),VP.get(2),VP.get(3));px=c.x;py=c.y;
    }
   }
   float target=darkness(renderer.getMap());
   if(renderer.getMap()!=lastMap||level<0){level=target;lastMap=renderer.getMap();}else level+=(target-level)*Math.min(1,Gdx.graphics.getDeltaTime()*2);
   composite(scene.getColorBufferTexture(),Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight(),px,py,tile,wx,wy,level,(System.nanoTime()-START)/1e9f);
   Gdx.gl.glViewport(VP.get(0),VP.get(1),VP.get(2),VP.get(3));   // the HUD is drawn next with the map's viewport
   Gdx.graphics.requestRendering();
  }catch(Throwable t){fail(t);}
 }
 static void prepare(int w,int h){
  if(scene==null||scene.getWidth()!=w||scene.getHeight()!=h){
   if(scene!=null)scene.dispose();if(small!=null)small.dispose();
   scene=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);scene.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
   small=new FrameBuffer(Pixmap.Format.RGBA8888,Math.max(8,w/4),Math.max(8,h/4),false);small.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
  }
  if(shader==null){
   ShaderProgram.pedantic=false;shader=new ShaderProgram(VERTEX,FRAGMENT);
   if(!shader.isCompiled()){System.err.println("[MapLight shader] "+shader.getLog());shader.dispose();shader=null;failed=true;return;}
   batch=new SpriteBatch(4,shader);copy=new SpriteBatch(4);
  }
 }
 /** draw the off-screen scene to the current framebuffer with lighting; also used by the preview renderer */
 public static void composite(Texture tex,int w,int h,float playerX,float playerY,float tile,float worldX,float worldY,float dark,float time){
  prepare(w,h);
  Matrix4 smallProj=new Matrix4().setToOrtho2D(0,0,small.getWidth(),small.getHeight());
  small.begin();Gdx.gl.glClearColor(0,0,0,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
  copy.setProjectionMatrix(smallProj);copy.disableBlending();copy.begin();copy.draw(tex,0,0,small.getWidth(),small.getHeight(),0,0,tex.getWidth(),tex.getHeight(),false,true);copy.end();
  small.end();
  if(shader==null){copy.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));copy.begin();copy.draw(tex,0,0,w,h,0,0,tex.getWidth(),tex.getHeight(),false,true);copy.end();return;}
  Gdx.gl.glViewport(0,0,w,h);
  small.getColorBufferTexture().bind(1);Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
  batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));batch.disableBlending();batch.begin();
  shader.setUniformi("u_small",1);shader.setUniformf("u_res",w,h);shader.setUniformf("u_player",playerX,playerY);shader.setUniformf("u_tile",tile);
  shader.setUniformf("u_world0",worldX,worldY);shader.setUniformf("u_dark",dark);shader.setUniformf("u_time",time);
  batch.draw(tex,0,0,w,h,0,0,tex.getWidth(),tex.getHeight(),false,true);
  batch.end();batch.enableBlending();
 }
 private static Object stage(OrthogonalTiledMapRenderer r){
  try{if(stageField==null){stageField=r.getClass().getDeclaredField("stage");stageField.setAccessible(true);}return stageField.get(r);}catch(ReflectiveOperationException e){return null;}
 }
 /** 0 = town (bright), 1 = enclosed underground map, in between for outdoor dungeons */
 public static float darkness(TiledMap map){
  String type=pointType();
  if(type!=null&&(type.contains("town")||type.contains("capital")))return 0;
  float v=voids.computeIfAbsent(map,MapLightMod::voidFraction);
  return v>.12f?1f:.42f;
 }
 private static String pointType(){
  try{
   var scene=forge.adventure.scene.TileMapScene.instance();if(scene==null||scene.rootPoint==null)return null;
   Object data=scene.rootPoint.getClass().getMethod("getData").invoke(scene.rootPoint);
   Field f=data.getClass().getField("type");Object t=f.get(data);return t==null?null:t.toString().toLowerCase();
  }catch(Throwable t){return null;}
 }
 /** share of map cells without any tile: caves and crypts are mostly empty black space */
 static float voidFraction(TiledMap map){
  int w=0,h=0;java.util.List<TiledMapTileLayer> layers=new java.util.ArrayList<>();
  var all=map.getLayers();   // index loop: the renderer may be iterating the same layer list right now
  for(int i=0;i<all.getCount();i++)if(all.get(i) instanceof TiledMapTileLayer t){layers.add(t);w=Math.max(w,t.getWidth());h=Math.max(h,t.getHeight());}
  if(w*h==0)return 0;int empty=0;
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){boolean any=false;for(TiledMapTileLayer t:layers){var cell=t.getCell(x,y);if(cell!=null&&cell.getTile()!=null){any=true;break;}}if(!any)empty++;}
  return empty/(float)(w*h);
 }
 // ------------------------------------------------------------------ shop signs above the storefront art
 private static Field regionField;
 /** Storefront art lives in a tile layer drawn after the sprites, so it covered the shop signs: draw them again on top. */
 static void signsOnTop(OrthogonalTiledMapRenderer renderer){
  if(!(stage(renderer) instanceof com.badlogic.gdx.scenes.scene2d.Stage stage))return;
  java.util.List<com.badlogic.gdx.scenes.scene2d.Actor> signs=new java.util.ArrayList<>();collectSigns(stage.getRoot(),signs);
  if(signs.isEmpty())return;
  var b=renderer.getBatch();b.setProjectionMatrix(stage.getCamera().combined);b.begin();
  try{for(var a:signs)a.draw(b,1f);}finally{b.end();}
 }
 private static void collectSigns(com.badlogic.gdx.scenes.scene2d.Group group,java.util.List<com.badlogic.gdx.scenes.scene2d.Actor> out){
  for(var a:group.getChildren()){
   if(!a.isVisible())continue;
   if(a instanceof forge.adventure.character.TextureSprite&&isSign(a))out.add(a);
   else if(a instanceof com.badlogic.gdx.scenes.scene2d.Group g)collectSigns(g,out);
  }
 }
 private static boolean isSign(Object sprite){
  try{
   if(regionField==null){regionField=forge.adventure.character.TextureSprite.class.getDeclaredField("region");regionField.setAccessible(true);}
   var region=(com.badlogic.gdx.graphics.g2d.TextureRegion)regionField.get(sprite);
   return region!=null&&region.getTexture()!=null&&region.getTexture().getTextureData() instanceof com.badlogic.gdx.graphics.glutils.FileTextureData d
    &&d.getFileHandle().name().contains("shop_signs");
  }catch(ReflectiveOperationException e){return false;}
 }
 // ------------------------------------------------------------------ high-resolution ground materials
 private static ShaderProgram ground;private static Texture materials;private static boolean groundFailed,groundOn;
 /** mean luminance of each material cell in materials-atlas.png: grass, path/sand, rock, earth, water, snow, dungeon stone */
 static final float[] MEANS={0.464f,0.546f,0.311f,0.452f,0.416f,0.614f,0.31f,0.452f};
 static final String GROUND_VERTEX="attribute vec4 a_position;attribute vec4 a_color;attribute vec2 a_texCoord0;uniform mat4 u_projTrans;varying vec4 v_color;varying vec2 v_tc;varying vec2 v_world;void main(){v_color=a_color;v_color.a*=255.0/254.0;v_tc=a_texCoord0;v_world=a_position.xy/16.0;gl_Position=u_projTrans*a_position;}";
 /** each ground pixel is classified by colour like CalmTerrainMod, then replaced by a world-anchored material carrying
  *  the tile's own brightness: no 16px tile grid, outlines and dark details stay as drawn */
 static final String GROUND_FRAGMENT=String.join("\n",
  "#ifdef GL_ES","precision mediump float;","#endif",
  "varying vec4 v_color;varying vec2 v_tc;varying vec2 v_world;",
  "uniform sampler2D u_texture;uniform sampler2D u_materials;uniform float u_time;uniform float u_dungeon;uniform float u_strength;uniform float u_mean[8];",
  "vec3 mat(float id,vec2 w){float col=mod(id,4.0);float row=floor(id/4.0);vec2 f=fract(w);return texture2D(u_materials,(vec2(col,row)+vec2(0.006)+f*0.988)/vec2(4.0,2.0)).rgb;}",
  "void main(){",
  " vec4 t=texture2D(u_texture,v_tc);vec3 c=t.rgb*255.0;",
  " float mx=max(c.r,max(c.g,c.b));float mn=min(c.r,min(c.g,c.b));",
  " float id=2.0;float s=u_strength;",
  " if(c.b>c.r*1.18&&c.b>c.g*0.88)id=4.0;",
  " else if(c.g>c.r*1.12&&c.g>c.b*1.12)id=0.0;",
  " else if(c.r>140.0&&c.g>110.0&&c.r>c.b*1.15)id=1.0;",
  " else if(mn>195.0&&mx-mn<40.0)id=5.0;",
  " else if(c.r>c.g*1.1&&c.g>c.b*1.1)id=3.0;",
  " else s*=0.35;",
  " if(u_dungeon>0.5&&(id==2.0||id==3.0)){id=6.0;s=u_strength*0.85;}",
  " s*=smoothstep(60.0,110.0,mx);",
  " vec2 w=v_world/3.0;if(id==4.0)w+=vec2(u_time*0.015,u_time*0.01);",
  " vec3 m=mat(id,w);",
  " float mean=u_mean[0];if(id==1.0)mean=u_mean[1];else if(id==2.0)mean=u_mean[2];else if(id==3.0)mean=u_mean[3];else if(id==4.0)mean=u_mean[4];else if(id==5.0)mean=u_mean[5];else if(id==6.0)mean=u_mean[6];",
  " float lum=dot(t.rgb,vec3(0.299,0.587,0.114));",
  " vec3 d=m*clamp(lum/max(mean,0.05),0.5,1.5);",
  " gl_FragColor=v_color*vec4(mix(t.rgb,d,s),t.a);",
  "}");
 static boolean isGround(String name){
  if(name==null)return false;String n=name.toLowerCase(java.util.Locale.ROOT).trim();
  return n.startsWith("background")||n.startsWith("ground")||n.equals("floor")||n.startsWith("lower background")||n.startsWith("lowest background");
 }
 /** called by PointOfInterestMapRenderer.renderMapLayer before the layer is drawn */
 public static void layerBegin(OrthogonalTiledMapRenderer renderer,MapLayer layer){
  groundOn=false;
  if(groundFailed||!(layer instanceof TiledMapTileLayer)||!isGround(layer.getName())||!(renderer.getBatch() instanceof SpriteBatch sb))return;
  try{
   if(ground==null){
    var file=Gdx.files.absolute("D:/forge/_mods/calm-terrain/materials-atlas.png");if(!file.exists()){groundFailed=true;return;}
    ShaderProgram.pedantic=false;ground=new ShaderProgram(GROUND_VERTEX,GROUND_FRAGMENT);
    if(!ground.isCompiled()){System.err.println("[MapLight ground shader] "+ground.getLog());ground.dispose();ground=null;groundFailed=true;return;}
    materials=new Texture(file);materials.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
   }
   sb.setShader(ground);
   materials.bind(1);Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
   ground.setUniformi("u_materials",1);ground.setUniformf("u_time",(System.nanoTime()-START)/1e9f);
   ground.setUniformf("u_dungeon",darkness(renderer.getMap())>.7f?1f:0f);ground.setUniformf("u_strength",.8f);ground.setUniform1fv("u_mean",MEANS,0,8);
   groundOn=true;
  }catch(Throwable t){groundFailed=true;System.err.println("[MapLight ground] disabled: "+t);try{sb.setShader(null);}catch(Throwable ignored){}}
 }
 /** called after the layer is drawn */
 public static void layerEnd(OrthogonalTiledMapRenderer renderer,MapLayer layer){
  if(!groundOn)return;groundOn=false;
  if(renderer.getBatch() instanceof SpriteBatch sb)sb.setShader(null);
 }
 private static void fail(Throwable t){failed=true;active=false;System.err.println("[MapLight] disabled: "+t);try{if(scene!=null)scene.end();}catch(Throwable ignored){}}
}
