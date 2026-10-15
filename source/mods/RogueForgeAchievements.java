package forge.localinstance.achievements;

import forge.game.Game;
import forge.game.player.Player;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Reuse Forge's evaluators and thresholds, with Rogue-only saved state and no Adventure booster side effects. */
public final class RogueForgeAchievements extends AchievementCollection {
 private static Set<String> availableCards=Set.of();private static String forgeRoot="D:/forge/";
 public static void configure(String root,Set<String> cards){forgeRoot=root;availableCards=Set.copyOf(cards);}
 public RogueForgeAchievements(){super("lblAchievements","",false);}
 @Override public void load(){} // Do not import or write another mode's achievements.
 @Override protected void save(){}
 @Override protected void addAchievements(){
  add(new Poisoned(15,25,40));add(new DeckedOut(8,4,2));add(new Blackjack(30,50,100));
  addCardGoals("AltWinAchievements","AltWinAchievement","altwin-achievements.txt");
  addCardGoals("PlaneswalkerAchievements","PlaneswalkerUltimate","planeswalker-achievements.txt");
  addCardGoals("CardActivationAchievements","CardAbilityUltimate","special-card-achievements.txt");
 }
 private void addCardGoals(String collection,String type,String file){
  if(availableCards.isEmpty())return;
  try{
   Class<?> outer=Class.forName("forge.localinstance.achievements."+collection);
   var constructor=Class.forName(outer.getName()+"$"+type).getDeclaredConstructor(outer,String.class,String.class,String.class);constructor.setAccessible(true);
   // The native outer instance is read only. Every trophy we evaluate below is a new, Rogue-owned object.
   Object owner=null;
   for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of(forgeRoot,"res/lists",file))){
    String[] fields=line.split("\\|",-1);if(fields.length<3||!availableCards.contains(fields[0]))continue;
    if(owner==null)owner=outer.getField("instance").get(null);
    add((Achievement)constructor.newInstance(owner,fields[0],fields[1],fields[2]));
   }
  }catch(Exception e){throw new IllegalStateException("Cannot port native "+collection,e);}
 }
 @Override protected void addSharedAchievements(){
  super.addSharedAchievements();
  // Arena/Quest/limited-event objectives are never imported: this is the shared duel collection only.
 }
 static Element element(Map<String,String> attributes)throws Exception{
  Element e=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().createElement("a");
  if(attributes!=null)attributes.forEach(e::setAttribute);return e;
 }
 static Map<String,String> attributes(Element e){
  Map<String,String> out=new HashMap<>();var a=e.getAttributes();for(int i=0;i<a.getLength();i++)out.put(a.item(i).getNodeName(),a.item(i).getNodeValue());return out;
 }
 public static void evaluate(Player p,Game game,Map<String,Map<String,String>> state,List<String> completed)throws Exception{
  var higher=Achievement.class.getDeclaredField("checkGreaterThan");higher.setAccessible(true);
  for(Achievement a:new RogueForgeAchievements()){
   String key=a.getKey();Map<String,String> prior=state.get(key);
   if(prior!=null)a.loadFromXml(element(prior));
   int best=a.getBest(),value=a.evaluate(p,game);
   Element e=element(null);a.saveToXml(e); // Streak evaluators update their current count as well.
   boolean improved=higher.getBoolean(a)?value>best:value<best;
   if(improved){e.setAttribute("best",Integer.toString(value));e.setAttribute("time",Long.toString(System.currentTimeMillis()));}
   a.loadFromXml(e);state.put(key,attributes(e));
   boolean special=a.isSpecial()&&a.getBest()>0;
   if(special&&!completed.contains("forge:"+key+":special"))completed.add("forge:"+key+":special");
   boolean[] earned={a.earnedCommon(),a.earnedUncommon(),a.earnedRare(),a.earnedMythic()};
   for(int i=0;i<earned.length;i++){String id="forge:"+key+":"+i;if(earned[i]&&!completed.contains(id))completed.add(id);}
  }
 }
 public static List<String[]> describe(Map<String,Map<String,String>> state)throws Exception{
  List<String[]> rows=new ArrayList<>();
  for(Achievement a:new RogueForgeAchievements()){
   Map<String,String> prior=state.get(a.getKey());if(prior!=null)a.loadFromXml(element(prior));
   String goal=a.earnedMythic()?a.getMythicDesc():a.earnedRare()?a.getMythicDesc():a.earnedUncommon()?a.getRareDesc():a.earnedCommon()?a.getUncommonDesc():a.getCommonDesc();
   String desc=(a.getSharedDesc()==null?"":a.getSharedDesc()+" ")+(goal==null?"":goal);
   rows.add(new String[]{a.getKey(),a.getDisplayName(),desc,a.getSubTitle(false),(a.earnedCommon()||(a.isSpecial()&&a.getBest()>0))?"true":"false"});
  }
  return rows;
 }
}

