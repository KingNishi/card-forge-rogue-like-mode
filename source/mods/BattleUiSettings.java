package forge.adventure.util;
import com.badlogic.gdx.Input;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
/** Battle button sizes and keyboard shortcuts, edited in Rogue Commanders > Settings (user 2026-10-17).
 *  Stored next to Forge's preferences (rogue_battle_ui.json), never inside a save. Data only: the HUD reads it. */
public final class BattleUiSettings {
 private BattleUiSettings(){}
 /** size groups: turn panel + Skip, OK / End Turn bar, small side buttons (log, deck, hand, zoom, menu) */
 public static final String[] SIZE_IDS={"turnPanel","prompt","side","trigger"};
 public static final String[] SIZE_LABELS={"Turn panel + Skip button","OK / End Turn / Keep / Mulligan buttons","Side buttons (Log, Deck, Hand, Zoom, Menu)","Trigger / choice text"};   // trigger text: user 2026-10-18
 public static final float MIN=.6f,MAX=1.6f;
 public static final String[] ACTIONS={"ok","cancel","skip","log","deck","hand","zoomIn","zoomOut","zoomReset"};
 public static final String[] ACTION_LABELS={"OK / Confirm","End Turn / Cancel","Skip to my next turn","Game log","View deck","Show / hide hand","Zoom board in","Zoom board out","Reset board zoom"};
 private static final int[] DEFAULT_KEYS={Input.Keys.SPACE,Input.Keys.E,Input.Keys.S,Input.Keys.L,Input.Keys.D,Input.Keys.H,Input.Keys.PAGE_UP,Input.Keys.PAGE_DOWN,Input.Keys.HOME};   // page keys: "+" sits on different keys per keyboard layout
 private static final float[] sizes={1,1,1,1};
 private static final int[] keys=DEFAULT_KEYS.clone();
 private static boolean loaded;
 /** turn panel look: 0 = auto (compact on Android, detailed on PC), 1 = compact, 2 = detailed (user 2026-11-05) */
 public static final String[] TURN_STYLES={"Auto","Compact","Detailed"};
 private static int turnStyle;
 /** where the player dragged the turn panel: offset from its default spot as a fraction of the screen, 0/0 = default */
 private static float turnDx,turnDy;
 private static Path file(){return java.nio.file.Paths.get(forge.localinstance.properties.ForgeConstants.USER_PREFS_DIR,"rogue_battle_ui.json");}
 private static void ensure(){
  if(loaded)return;loaded=true;
  try{
   Path f=file();if(!Files.isRegularFile(f))return;
   JsonObject root=JsonParser.parseString(Files.readString(f)).getAsJsonObject();
   JsonObject s=root.has("sizes")?root.getAsJsonObject("sizes"):new JsonObject();
   for(int i=0;i<SIZE_IDS.length;i++)if(s.has(SIZE_IDS[i]))sizes[i]=clamp(s.get(SIZE_IDS[i]).getAsFloat());
   JsonObject k=root.has("keys")?root.getAsJsonObject("keys"):new JsonObject();
   for(int i=0;i<ACTIONS.length;i++)if(k.has(ACTIONS[i])){
    String name=k.get(ACTIONS[i]).getAsString();keys[i]=name.isEmpty()?-1:Input.Keys.valueOf(name);
   }
   if(root.has("turnPanel")){JsonObject t=root.getAsJsonObject("turnPanel");
    if(t.has("style"))turnStyle=Math.max(0,Math.min(2,t.get("style").getAsInt()));
    if(t.has("dx"))turnDx=frac(t.get("dx").getAsFloat());if(t.has("dy"))turnDy=frac(t.get("dy").getAsFloat());}
  }catch(Exception e){System.err.println("[Rogue] battle UI settings unreadable, using defaults: "+e);}
 }
 public static void save(){
  try{
   JsonObject root=new JsonObject(),s=new JsonObject(),k=new JsonObject();root.addProperty("version",1);
   for(int i=0;i<SIZE_IDS.length;i++)s.addProperty(SIZE_IDS[i],sizes[i]);
   for(int i=0;i<ACTIONS.length;i++)k.addProperty(ACTIONS[i],keys[i]<0?"":Input.Keys.toString(keys[i]));
   root.add("sizes",s);root.add("keys",k);
   JsonObject t=new JsonObject();t.addProperty("style",turnStyle);t.addProperty("dx",turnDx);t.addProperty("dy",turnDy);root.add("turnPanel",t);
   Path f=file();Files.createDirectories(f.getParent());Path tmp=f.resolveSibling(f.getFileName()+".tmp");
   Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(root));
   try{Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING);}
  }catch(Exception e){System.err.println("[Rogue] could not save battle UI settings: "+e);}
 }
 static float clamp(float v){return Float.isFinite(v)?Math.max(MIN,Math.min(MAX,v)):1;}
 /** size factor of a group (0 = turn panel, 1 = OK / End Turn, 2 = side buttons) */
 public static float size(int group){ensure();return sizes[group];}
 public static void setSize(int group,float value){ensure();sizes[group]=clamp(Math.round(value*20)/20f);}
 public static int key(int action){ensure();return keys[action];}
 public static String keyName(int action){int k=key(action);return k<0?"-":Input.Keys.toString(k);}
 /** binds a key; another action that used the same key loses it, so one key never does two things */
 public static void setKey(int action,int key){ensure();for(int i=0;i<keys.length;i++)if(i!=action&&keys[i]==key)keys[i]=-1;keys[action]=key;}
 public static void clearKey(int action){ensure();keys[action]=-1;}
 public static void resetKeys(){ensure();System.arraycopy(DEFAULT_KEYS,0,keys,0,keys.length);}
 public static void resetSizes(){ensure();Arrays.fill(sizes,1);}
 static float frac(float v){return Float.isFinite(v)?Math.max(-1,Math.min(1,v)):0;}
 public static boolean touch(){if(System.getProperty("forge.touchtest")!=null)return true;   /* dev instance: Android look on PC */try{return forge.gui.GuiBase.getInterface()!=null&&forge.gui.GuiBase.isAndroid();}catch(Throwable t){return false;}}
 public static int turnStyle(){ensure();return turnStyle;}
 public static void setTurnStyle(int style){ensure();turnStyle=Math.max(0,Math.min(2,style));}
 /** the slim one-line turn panel: Compact, or Auto on a phone / tablet */
 public static boolean compactTurn(){ensure();return turnStyle==1||turnStyle==0&&touch();}
 public static float turnDx(){ensure();return turnDx;}
 public static float turnDy(){ensure();return turnDy;}
 public static void setTurnOffset(float dx,float dy){ensure();turnDx=frac(dx);turnDy=frac(dy);}
 public static void resetTurnPosition(){ensure();turnDx=0;turnDy=0;}
 /** index of the action bound to this key, -1 if none */
 public static int actionFor(int key){ensure();if(key<0)return -1;for(int i=0;i<keys.length;i++)if(keys[i]==key)return i;return -1;}
}
