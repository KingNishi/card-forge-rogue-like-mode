package forge.adventure.util;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
/** Normalized positions survive window size changes; preferences never touch saves. */
public final class HudCustomization {
 public float opacity=1;
 public final Map<String,float[]> widgets=new LinkedHashMap<>();
 public static float clamp(float v,float lo,float hi){return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):lo;}
 public float[] bounds(String id,float x,float y,float w,float h,float vw,float vh){
  float[] p=widgets.get(id);
  return new float[]{clamp(p==null?x:p[0]*vw,0,Math.max(0,vw-w)),clamp(p==null?y:p[1]*vh,0,Math.max(0,vh-h)),w,h};
 }
 public float alpha(String id){float[] p=widgets.get(id);return opacity*(p==null?1:p[2]);}
 public void move(String id,float x,float y,float w,float h,float vw,float vh){
  float[] old=widgets.get(id);widgets.put(id,new float[]{clamp(x,0,Math.max(0,vw-w))/vw,clamp(y,0,Math.max(0,vh-h))/vh,old==null?1:old[2]});
 }
 public void setOpacity(String id,float v){
  v=clamp(v,.15f,1);if(id==null){opacity=v;return;}
  float[] p=widgets.get(id);if(p==null)throw new IllegalStateException("Position must be initialized first");p[2]=v;
 }
 public void reset(){widgets.clear();opacity=1;}
 public static HudCustomization load(Path path){
  HudCustomization data=new HudCustomization();if(!Files.exists(path))return data;
  try{
   JsonObject root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();data.opacity=clamp(root.get("opacity").getAsFloat(),.15f,1);
   for(Map.Entry<String,JsonElement> e:root.getAsJsonObject("widgets").entrySet()){
    JsonArray a=e.getValue().getAsJsonArray();data.widgets.put(e.getKey(),new float[]{clamp(a.get(0).getAsFloat(),0,1),clamp(a.get(1).getAsFloat(),0,1),clamp(a.get(2).getAsFloat(),.15f,1)});
   }
  }catch(Exception e){System.err.println("[HUD] Could not read layout; using defaults: "+e);data.reset();}
  return data;
 }
 public void save(Path path)throws java.io.IOException{
  JsonObject root=new JsonObject();root.addProperty("version",1);root.addProperty("opacity",opacity);JsonObject entries=new JsonObject();
  widgets.forEach((id,p)->{JsonArray a=new JsonArray();for(float v:p)a.add(v);entries.add(id,a);});root.add("widgets",entries);
  Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(root));
  try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
 }
}
