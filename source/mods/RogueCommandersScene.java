package forge.adventure.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import forge.Forge;
import forge.adventure.character.EnemySprite;
import forge.adventure.data.AdventureEventData;
import forge.adventure.data.EffectData;
import forge.adventure.data.EnemyData;
import forge.adventure.data.WorldData;
import forge.adventure.stage.IAfterMatch;
import forge.adventure.util.AdventureEventController;
import forge.adventure.util.Config;
import forge.adventure.util.Current;
import forge.adventure.world.WorldSave;
import forge.assets.ImageCache;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.gui.FThreads;
import forge.gui.GuiBase;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.screens.TransitionScreen;
import forge.util.ScreenUtil;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Rogue Commanders: an endless roguelite mode opened from the Adventure main menu.
 * Pick any commander of the roster, optionally improve its deck with permanent unlocks, then fight opponents of
 * rising strength until the first loss. After every win one of three 6-card packages (commander themes from EDHREC
 * or generic packages, always inside the colour identity) upgrades the deck for this run only. At the end of a run
 * the renown earned (round r gives r points, so deep runs snowball) unlocks cards permanently: three general
 * unlocks (popular EDHREC staples, tiered Solid/Good/Strong) per signature unlock of the played commander. Unlocks go
 * into the pre-run deck ("Prepare deck"). Pacing aims at 80 % of the general unlocks after 40 h of active play.
 * Content: _mods/rogue-commanders/content.json (build_content.py). Profile: rogue_commanders.json in the save folder.
 */
public class RogueCommandersScene extends UIScene implements IAfterMatch {
 /** the Forge folder: where the Forge JAR lies (fallback: working directory); -Dforge.root overrides it */
 static String root(){
  String r=System.getProperty("forge.root");
  if(r==null)try{java.io.File jar=new java.io.File(RogueCommandersScene.class.getProtectionDomain().getCodeSource().getLocation().toURI());
   if(jar.isFile()&&new java.io.File(jar.getParentFile(),"res").isDirectory())r=jar.getParent();}catch(Exception ignored){}
  if(r==null)r=new java.io.File("").getAbsolutePath();
  r=r.replace('\\','/');return r.endsWith("/")?r:r+"/";
 }
 static final String ROOT=root(),DIR=ROOT+"_mods/rogue-commanders/",BOARDS=ROOT+"_mods/arena-boards/boards/";
 static final int LIFE=30,DECK=59,MAX_DECK=99,MAX_ROUNDS=15;   // Commander decks: 59-99 cards + commander
 static final double COST=3.0,TARGET_HOURS=40,TARGET_SHARE=.8;   // half as many unlocks per run (user, 2026-10-14)
 static final String[] BASIC_NAMES={"Plains","Island","Swamp","Mountain","Forest"};
 static final String COLORS="WUBRG";
 static final Map<String,String> BOARD=Map.of("Y'shtola, Night's Blessed","MID_Mobile","Teval, the Balanced Scale","KHM_Mobile",
  "Fire Lord Azula","TLA","Cloud, Ex-SOLDIER","FIN_Mobile","Ureni of the Unwritten","AKR_Mobile","Karolina Dean, Runaway","ESP_Mobile",
  "Pantlaza, Sun-Favored","XLN_Mobile","Jace, Multiverse Architect","WAR_Mobile","The Wise Mothman","DSK_Mobile","Shang-Chi, Master of Kung Fu","GRN_Mobile");
 static final Set<String> TOO_BIG=Set.of("Emrakul","Kozilek","Elesh Norn","Jin-Gitaxias","Urabrask","Vorinclex","Sheoldred","Valyx","Chandra","Liliana","Nissa","Sorin");

 // ------------------------------------------------------------------ content
 static class CardRef{String n,c="",tier,cat,needLandColors;String[] commanders;int s,price;}
 static class Theme{String key,name;boolean rare,focus,progression;int cost,requiredWins;CardRef[] cards;}   // focus: the commander's key synergy package
 static class Cmd{boolean bonus;String name,slug,colors,blurb,board;String[] lands,starter,alts,protectedStarter;int minimumPackages;Theme[] themes;CardRef[] specific;Map<String,Integer> scores;}
 static class Generic{String key,name;CardRef[] cards;}
 static class Content{int version;Cmd[] commanders;Generic[] generic;CardRef[] unlocks;CardRef[] legendary;}
 // ------------------------------------------------------------------ profile (permanent) and run (temporary)
 static class Offer{String name,kind,rarity;List<String> cards=new ArrayList<>();}
 static class Run{String commander,opponent,phase="fight";long seed,duelStart,lastAchievementDuel;int rewardTier,skippedRewards;int round=1,wins,ruhm,bosses,perfect,fast,close,legendary,epic,lifeSource,marathon,cmdrKill,poison,mill,altWin,overkill,mulligan,storm,army,noCommander,heirlooms;boolean lost,completed,cursed;EffectData savedBlessing;List<String> curses=new ArrayList<>(),curseOptions=new ArrayList<>(),offered=new ArrayList<>();
  List<String> deck=new ArrayList<>(),picked=new ArrayList<>(),added=new ArrayList<>(),bench=new ArrayList<>(),lastPackage=new ArrayList<>();List<Offer> offers=new ArrayList<>();List<String> selectedPackages,chosenPackages,shopBought;List<CardRef> shopStock;int shopRound,shopRerolls,shopCatalogVersion,creditedBossGold,goldEarned;}
 /** cards of this run that are not in the run deck right now (old saves have no bench yet) */
 static List<String> bench(Run r){if(r.bench==null)r.bench=new ArrayList<>();return r.bench;}
 static class ScoreLine{String label,detail;int points;ScoreLine(){}ScoreLine(String label,String detail,int points){this.label=label;this.detail=detail;this.points=points;}}
 static class Result{String commander,rank;int wins,ruhm,score,previousBest,progressionEarned;boolean record;List<String> unlocked=new ArrayList<>(),signature=new ArrayList<>(),skins=new ArrayList<>();List<ScoreLine> lines=new ArrayList<>();}
 static class Profile{Map<String,Map<String,String>> forgeAchievements=new HashMap<>();int version=4,counter,runs,totalWins,progressionPoints,goldBalance;long sacrificedPermanents,artifactCreaturesCast;boolean bossOneDefeated,bossTenDefeated,bossDrainKill;List<String> packageUnlocks=new ArrayList<>(),achievements=new ArrayList<>();Map<String,List<String>> packageChoices=new HashMap<>();long activeMs;double carry;
  List<String> general=new ArrayList<>();Map<String,List<String>> signature=new HashMap<>(),loadout=new HashMap<>();
  Map<String,Integer> best=new HashMap<>(),bestScore=new HashMap<>();Map<String,String> commanderArt=new HashMap<>();Run run;Result result;String selected;boolean cursesUnlocked,cursesOn;List<String> recentOffers=new ArrayList<>();int bestScoreAll;}

 // Real alternate illustrations, not extended-art reprints of the same picture.
 // The first entry is the free standard print; unlocks use this commander's best completed run.
 static class Skin{final String edition,number,artist;final int wins;
  Skin(String edition,String number,String artist,int wins){this.edition=edition;this.number=number;this.artist=artist;this.wins=wins;}
  String id(){return edition+":"+number;}
  boolean custom(){return "CUSTOM".equals(edition);}
 }
 static final Map<String,List<Skin>> SKINS=Map.of(
  "Y'shtola, Night's Blessed",List.of(new Skin("FIC","7","Magali Villeneuve",0),new Skin("FIC","207","Nakamura8",3)),
  "Teval, the Balanced Scale",List.of(new Skin("TDC","8","Chris Rahn",0)),
  "Fire Lord Azula",List.of(new Skin("TLA","220","Fahmi Fauzi",0),new Skin("TLA","313","Dominik Mayer",3),new Skin("TLA","334","JungShan",6)),
  "Cloud, Ex-SOLDIER",List.of(new Skin("FIC","2","Justyna Dura",0),new Skin("FIC","202","S. Makimura",3)),
  "Ureni of the Unwritten",List.of(new Skin("TDC","9","Valera Lutfullina",0)),
  "Karolina Dean, Runaway",List.of(new Skin("CUSTOM","karolina-fan.jpg","AaronTigerTear (Fan-Art)",0),new Skin("MSC","694","Jeff Dekal",0)),
  "Pantlaza, Sun-Favored",List.of(new Skin("LCC","4","Sam Burley",0),new Skin("LCC","20","Sidharth Chaturvedi",3)),
  "Jace, Multiverse Architect",List.of(new Skin("FRC","1","Volkan Baǵa",0)),
  "The Wise Mothman",List.of(new Skin("PIP","4","Sergei Leoluch Panin",0),new Skin("PIP","343","David Gaillet",3),new Skin("SLD","2455","Chris Seaman",6)),
  "Shang-Chi, Master of Kung Fu",List.of(new Skin("MSH","187","Lee Woo-chul",0),new Skin("CUSTOM","shang-chi-fan.jpg","Fan-Art",0)));
 static List<Skin> skins(String commander){return SKINS.getOrDefault(commander,List.of());}
 static boolean skinUnlocked(Profile p,String commander,Skin skin){return skin.wins==0||p.best.getOrDefault(commander,0)>=skin.wins;}
 static Skin selectedSkin(Profile p,String commander){
  List<Skin> list=skins(commander);if(list.isEmpty())return null;
  String id=p.commanderArt.get(commander);
  for(Skin s:list)if(s.id().equals(id)&&skinUnlocked(p,commander,s))return s;
  return list.get(0);
 }
 private static final Map<String,PaperCard> commanderPrints=new HashMap<>();
 static PaperCard skinPrinting(forge.card.CardDb db,String name,Skin skin){
  if(skin==null)return null;
  for(PaperCard p:db.getAllCards(name))if(!p.isFoil()&&skin.edition.equals(p.getEdition())&&skin.number.equals(p.getCollectorNumber()))return p;
  return null;
 }
 static PaperCard skinPaper(String name,Skin skin){
  if(skin==null)return paper(name);
  String key=name+"|"+skin.id();PaperCard cached=commanderPrints.get(key);if(cached!=null)return cached;
  PaperCard fallback=paper(name); // ensure lazy card rules have been loaded first
  try{PaperCard p=skinPrinting(FModel.getMagicDb().getCommonCards(),name,skin);if(p!=null){commanderPrints.put(key,p);return p;}}catch(Exception e){log(e);}
  return fallback;
 }
 static PaperCard commanderPaper(String name){return skinPaper(name,selectedSkin(profile(),name));}

 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static Content content;private static Profile profile;private static Map<String,CardRef> refs;
 static Content content(){
  if(content==null){
   try{content=GSON.fromJson(Files.readString(new File(DIR+"content.json").toPath(),StandardCharsets.UTF_8),Content.class);}
   catch(Exception e){System.err.println("[Rogue] content: "+e);content=null;}
   if(content==null)content=new Content();
   if(content.commanders==null)content.commanders=new Cmd[0];if(content.generic==null)content.generic=new Generic[0];
   if(content.unlocks==null)content.unlocks=new CardRef[0];if(content.legendary==null)content.legendary=new CardRef[0];
   refs=new HashMap<>();
   for(CardRef r:content.unlocks){r.s=switch(r.tier==null?"":r.tier){case "stark"->75;case "gut"->65;default->55;};refs.put(r.n,r);}
   for(CardRef r:content.legendary)refs.putIfAbsent(r.n,r);
   for(Generic g:content.generic)for(CardRef r:g.cards)refs.putIfAbsent(r.n,r);
   Set<String> available=new HashSet<>(refs.keySet());for(Cmd c:content.commanders){available.add(c.name);available.addAll(Arrays.asList(c.starter));available.addAll(Arrays.asList(c.lands));for(CardRef x:c.specific)available.add(x.n);for(Theme t:c.themes)for(CardRef x:t.cards)available.add(x.n);}
   forge.localinstance.achievements.RogueForgeAchievements.configure(root(),available);
  }
  return content;
 }
 private static Map<String,String> intros;
 static String intro(String enemy){
  if(intros==null){
   intros=new HashMap<>();
   try{File f=new File(DIR+"enemy_intros.json");if(f.exists()){Map<String,String> m=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,String>>(){}.getType());if(m!=null)intros=m;}}
   catch(Exception e){log(e);}
  }
  return intros.get(enemy);
 }
 static Cmd cmd(String name){for(Cmd c:content().commanders)if(c.name.equals(name))return c;return null;}
 private static File profileFile(){return new File(WorldSave.getSaveDir(),"rogue_commanders.json");}
 static Profile profile(){
  if(profile==null){
   profile=new Profile();
   File f=profileFile();
   try{if(f.exists()){Profile loaded=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),Profile.class);if(loaded!=null)profile=loaded;else keepBroken(f);}}
   catch(Exception e){log(e);keepBroken(f);}
   if(profile.recentOffers==null)profile.recentOffers=new ArrayList<>();if(profile.bestScore==null)profile.bestScore=new HashMap<>();
   if(profile.general==null)profile.general=new ArrayList<>();if(profile.signature==null)profile.signature=new HashMap<>();
   if(profile.loadout==null)profile.loadout=new HashMap<>();if(profile.best==null)profile.best=new HashMap<>();
   if(profile.commanderArt==null)profile.commanderArt=new HashMap<>();
   initProgression(profile);if(profile.run!=null)profile.run.rewardTier=rewardTier(profile.run);
   if(profile.result!=null&&profile.result.skins==null)profile.result.skins=new ArrayList<>();
  }
  return profile;
 }
 static void log(Throwable t){
  System.err.println("[Rogue] "+t);
  try(var w=new java.io.PrintWriter(new java.io.FileWriter(DIR+"rogue.log",StandardCharsets.UTF_8,true))){w.println(new Date()+"  "+t);t.printStackTrace(w);}catch(Exception ignored){}
 }
 /** an unreadable profile is kept next to the new one instead of being overwritten */
 static void keepBroken(File f){try{Files.copy(f.toPath(),new File(f.getParentFile(),"rogue_commanders.broken-"+System.currentTimeMillis()+".json").toPath());}catch(Exception e){log(e);}}
 static void save(){if(profile==null)return;   // never write "null" over the profile
  try{File f=profileFile();f.getParentFile().mkdirs();File tmp=new File(f.getParentFile(),f.getName()+".tmp");
   Files.writeString(tmp.toPath(),GSON.toJson(profile),StandardCharsets.UTF_8);
   try{Files.move(tmp.toPath(),f.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
   catch(Exception e){Files.move(tmp.toPath(),f.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
  }catch(Exception e){log(e);}}

 // ------------------------------------------------------------------ card helpers
 /** the printing used everywhere in Rogue (offers, deck, duels): Forge's preferred art unless that is a digital-only
  *  (MTG Arena / Online) edition - then the newest paper printing (e.g. Armor of Shadows: CLB instead of Alchemy HBG) */
 private static final Map<String,PaperCard> papers=new HashMap<>();
 static PaperCard paper(String name){
  if(name==null)return null;
  PaperCard cached=papers.get(name);if(cached!=null||papers.containsKey(name))return cached;
  PaperCard best=null;
  try{
   var db=FModel.getMagicDb();best=db.getCommonCards().getCard(name);
   if(best!=null&&digital(db,best)){
    PaperCard paper=null;java.util.Date newest=null;
    for(PaperCard p:db.getCommonCards().getAllCards(name)){
     if(digital(db,p))continue;
     forge.card.CardEdition ed=db.getEditions().get(p.getEdition());java.util.Date d=ed==null?null:ed.getDate();
     if(paper==null||(d!=null&&(newest==null||d.after(newest)))){paper=p;newest=d;}
    }
    if(paper!=null)best=paper;
   }
  }catch(Exception e){best=null;}
  papers.put(name,best);return best;
 }
 static boolean digital(forge.StaticData db,PaperCard p){
  forge.card.CardEdition ed=db.getEditions().get(p.getEdition());
  return ed!=null&&ed.getType()==forge.card.CardEdition.Type.ONLINE;
 }
 static String wins(int n){return n+(n==1?" win":" wins");}
 static boolean isLand(String name){PaperCard p=paper(name);return p!=null&&p.getRules().getType().isLand();}
 static boolean isBasic(String name){return Arrays.asList(BASIC_NAMES).contains(name)||name.startsWith("Snow-Covered ")||name.equals("Wastes");}
 static boolean fits(String identity,String colors){if(identity==null)return true;for(char ch:identity.toCharArray())if(colors.indexOf(ch)<0)return false;return true;}
 static int score(Cmd c,String name){
  if(isBasic(name))return 2;
  if(c!=null&&c.scores!=null&&c.scores.containsKey(name))return c.scores.get(name);
  if(c!=null){for(CardRef r:c.specific)if(r.n.equals(name))return r.s;for(Theme t:c.themes)for(CardRef r:t.cards)if(r.n.equals(name))return r.s;}
  CardRef r=refs==null?null:refs.get(name);return r!=null?r.s:30;
 }
 static List<String> defaultDeck(Cmd c){List<String> d=new ArrayList<>(Arrays.asList(c.lands));d.addAll(Arrays.asList(c.starter));return d;}
 static List<String> loadout(Cmd c){List<String> l=profile().loadout.get(c.name);return l==null||l.isEmpty()?defaultDeck(c):new ArrayList<>(l);}
 static List<String> unlockedFor(Cmd c){
  List<String> out=new ArrayList<>();
  for(CardRef r:content().unlocks)if(profile().general.contains(r.n)&&fits(r.c,c.colors))out.add(r.n);
  out.addAll(profile().signature.getOrDefault(c.name,List.of()));
  return out;
 }
 static Deck deckOf(String commander,List<String> cards,String title){
  Deck d=new Deck(title);PaperCard leader=commanderPaper(commander);
  if(leader!=null)d.getOrCreate(DeckSection.Commander).add(leader,1);else d.getOrCreate(DeckSection.Commander).add(commander,1);
  for(String n:cards){PaperCard p=paper(n);if(p!=null)d.getMain().add(p,1);else d.getMain().add(n,1);}   // same printing as shown in Rogue
  return d;
 }
 /** keep a legal singleton list of 59-99 cards: drop duplicates of non-basics, trim the weakest above 99, fill basics below 59 */
 static List<String> normalize(Cmd c,List<String> cards){
  List<String> out=new ArrayList<>();Set<String> seen=new HashSet<>();
  for(String n:cards){if(n.equals(c.name))continue;if(!isBasic(n)&&!seen.add(n))continue;out.add(n);}
  while(out.size()>MAX_DECK){String worst=null;for(String n:out)if(worst==null||score(c,n)<score(c,worst))worst=n;out.remove(worst);}
  int i=0;while(out.size()<DECK){char ch=c.colors.isEmpty()?'C':c.colors.charAt(i++%Math.max(1,c.colors.length()));out.add(ch=='C'?"Wastes":BASIC_NAMES[COLORS.indexOf(ch)]);}
  return out;
 }

  // Commander achievements are independent goals; their identifiers are permanent save keys.
 static final String TEYSA="Teysa Karlov",BREYA="Breya, Etherium Shaper",ERIETTE="Eriette of the Charmed Apple",SHANG="Shang-Chi, Master of Kung Fu";
 static final String SAC_GOAL="commander:teysa",ART_GOAL="commander:breya",DRAIN_GOAL="commander:eriette",BOSS_GOAL="commander:shang-chi";
 static class RogueGoal{
  final String id,name,description,reward;final long current,target;final boolean complete;
  RogueGoal(Profile p,String id,String name,String description,String reward,long current,long target){
   this.id=id;this.name=name;this.description=description;this.reward=reward;this.current=current;this.target=target;complete=p.achievements.contains(id);
  }
 }
 static void updateCommanderAchievements(Profile p){
  if(p.achievements==null)p.achievements=new ArrayList<>();
  if(p.forgeAchievements==null)p.forgeAchievements=new HashMap<>();
  int best=0;if(p.best!=null)for(int w:p.best.values())best=Math.max(best,w);
  if(p.run!=null)best=Math.max(best,p.run.wins);
  p.bossOneDefeated|=best>=5;p.bossTenDefeated|=best>=10;
  if(p.sacrificedPermanents>=20)award(p,SAC_GOAL);
  if(p.artifactCreaturesCast>=20&&p.bossTenDefeated)award(p,ART_GOAL);
  if(p.bossDrainKill)award(p,DRAIN_GOAL);
  if(p.bossOneDefeated)award(p,BOSS_GOAL);
  for(Cmd c:content().commanders){int wins=p.best.getOrDefault(c.name,0);if(p.run!=null&&c.name.equals(p.run.commander))wins=Math.max(wins,p.run.wins);
   for(Theme t:c.themes)if(t.progression&&t.cost>0&&(wins>=t.requiredWins||p.packageUnlocks.contains(packageId(c,t))))award(p,"package-ready:"+packageId(c,t));
   for(Skin skin:skins(c.name))if(skin.wins>0&&wins>=skin.wins)award(p,"artwork:"+c.name+":"+skin.id());
  }
  for(String n:p.general)award(p,"card:"+n);
  for(var e:p.signature.entrySet())for(String n:e.getValue())award(p,"signature:"+e.getKey()+":"+n);
 }
 static void award(Profile p,String id){if(!p.achievements.contains(id))p.achievements.add(id);}
 static String commanderGoal(String name){return switch(name){case TEYSA->SAC_GOAL;case BREYA->ART_GOAL;case ERIETTE->DRAIN_GOAL;case SHANG->BOSS_GOAL;default->null;};}
 static boolean commanderOpen(Profile p,Cmd c){String id=commanderGoal(c.name);return id==null||p.achievements.contains(id);}
 static List<RogueGoal> commanderGoals(Profile p){return List.of(
  new RogueGoal(p,SAC_GOAL,"Nothing Goes to Waste","Sacrifice 20 permanents across any number of runs.",TEYSA,p.sacrificedPermanents,20),
  new RogueGoal(p,ART_GOAL,"Etherium Engine","Cast 20 artifact creatures across runs AND defeat the level 10 boss. Boss: "+(p.bossTenDefeated?"defeated":"pending"),BREYA,p.artifactCreaturesCast,20),
  new RogueGoal(p,DRAIN_GOAL,"A Charmed Ending","Defeat a boss with lethal life drain or noncombat burn.",ERIETTE,p.bossDrainKill?1:0,1),
  new RogueGoal(p,BOSS_GOAL,"First Boss Down","Defeat the first boss (level 5).",SHANG,p.bossOneDefeated?1:0,1));}
 static String commanderRequirement(Profile p,Cmd c){
  for(RogueGoal g:commanderGoals(p))if(g.id.equals(commanderGoal(c.name)))return g.name+": "+g.description+" ["+Math.min(g.current,g.target)+"/"+g.target+"]";
  return "Available";
 }
 static List<RogueGoal> allGoals(Profile p){
  List<RogueGoal> out=new ArrayList<>(commanderGoals(p));
  for(Cmd c:content().commanders){int wins=p.best.getOrDefault(c.name,0);if(p.run!=null&&c.name.equals(p.run.commander))wins=Math.max(wins,p.run.wins);
   for(Theme t:c.themes)if(t.progression&&t.cost>0)out.add(new RogueGoal(p,"package-ready:"+packageId(c,t),c.name.split(",")[0]+" · Package mastery","Win "+t.requiredWins+" duels in one run with this commander.",t.name+" ("+t.cost+" points)",wins,t.requiredWins));
  }
  try{for(String[] row:forge.localinstance.achievements.RogueForgeAchievements.describe(p.forgeAchievements)){
   String key="forge:"+row[0]+":display";if("true".equals(row[4]))award(p,key);
   out.add(new RogueGoal(p,key,row[1],row[2]+" · "+row[3],"Forge trophy", "true".equals(row[4])?1:0,1));
  }}catch(Exception e){log(e);}
  return out;
 }
 private boolean achievementScreen;private int achievementPage;
 private void buildAchievements(Profile p){
  background(p.selected,.80f);title("ACHIEVEMENTS","Commander unlocks Â· progress persists across runs");
  button("Back",W()-66,H()-22,56,16,()->{achievementScreen=false;rebuild();});
  List<RogueGoal> goals=allGoals(p);int pages=Math.max(1,(goals.size()+3)/4);achievementPage=Math.max(0,Math.min(achievementPage,pages-1));
  if(pages>1){button("<",12,8,30,16,()->{achievementPage=Math.max(0,achievementPage-1);rebuild();});button(">",W()-42,8,30,16,()->{achievementPage=Math.min(pages-1,achievementPage+1);rebuild();});}
  root.addActor(new Painter((b,a)->text(b,"Page "+(achievementPage+1)+" / "+pages,W()/2-40,18,6,DIM,a,80,Align.center,false)));
  goals=goals.subList(achievementPage*4,Math.min(goals.size(),achievementPage*4+4));float width=W()-24,height=Math.min(48,(H()-72)/4f);
  for(int i=0;i<goals.size();i++){RogueGoal g=goals.get(i);float y=H()-43-(i+1)*height;
   root.addActor(new Painter((b,a)->{
    rect(b,12,y,width,height-3,g.complete?Color.valueOf("183b2a"):DARK,a*.95f);frame(b,12,y,width,height-3,.7f,g.complete?GREEN:DIM,a);
    text(b,(g.complete?"[COMPLETE] ":"[LOCKED] ")+g.name+"  "+Math.min(g.current,g.target)+"/"+g.target,18,y+height-9,7,g.complete?GREEN:GOLD,a,width-12,Align.left,false);
    text(b,g.description,18,y+height-21,5.6f,TEXT,a,width-12,Align.left,false);
    text(b,"Unlock: "+g.reward,18,y+9,6,DIM,a,width-12,Align.left,false);
   }));
  }
 }
 // Observe native events only while an actual Rogue duel is active. Weak keys avoid retaining finished games.
 static final Map<forge.game.Game,DuelProgress> duelProgress=Collections.synchronizedMap(new WeakHashMap<>());
 public static void trackDuel(){
  Profile p=profile;if(p==null||p.run==null||p.run.duelStart<=0||!"fight".equals(p.run.phase))return;
  try{var view=forge.screens.match.MatchController.instance.getGameView();if(view==null)return;var game=view.getGame();
   if(game==null||game.isGameOver())return;
   synchronized(duelProgress){if(!duelProgress.containsKey(game)){DuelProgress d=new DuelProgress(p.run.duelStart);duelProgress.put(game,d);game.subscribeToEvents(d);}}
  }catch(Exception e){log(e);}
 }
 static final class DuelProgress{
  final long duel;long sacrifices,artifacts;final Set<Integer> lethalDrain=new HashSet<>();final Map<Integer,long[]> damageBatches=new HashMap<>();
  DuelProgress(long duel){this.duel=duel;}
  @com.google.common.eventbus.Subscribe public synchronized void sacrificed(forge.game.event.GameEventCardSacrificed e){
   var c=e.card();if(c!=null&&c.getController()!=null&&!c.getController().isAI()&&c.getCurrentState().getType().isPermanent())sacrifices++;
  }
  @com.google.common.eventbus.Subscribe public synchronized void cast(forge.game.event.GameEventSpellAbilityCast e){
   if(e.sa()==null||!e.sa().isSpell()||e.si()==null||e.si().getActivatingPlayer()==null||e.si().getActivatingPlayer().isAI())return;
   var c=e.sa().getHostCard();if(c==null||c.isToken()||c.isTokenCard()||c.getGamePieceType()==forge.card.GamePieceType.COPIED_SPELL)return;
   var type=c.getCurrentState().getType();if(type.isArtifact()&&type.isCreature())artifacts++;
  }
  @com.google.common.eventbus.Subscribe public synchronized void lifeChanged(forge.game.event.GameEventPlayerLivesChanged e){
   // Every new life change invalidates the earlier lethal cause; the precise loss/damage hook restores it if applicable.
   if(e.player()!=null)lethalDrain.remove(e.player().getId());
  }
  @com.google.common.eventbus.Subscribe public synchronized void damaged(forge.game.event.GameEventPlayerDamaged e){
   if(e.target()==null||!e.target().isAI()||e.amount()<=0)return;
   if(e.infect())return;
   long[] batch=damageBatches.computeIfAbsent(e.target().getId(),k->new long[2]);
   boolean ours=!e.combat()&&e.source()!=null&&e.source().getController()!=null&&!e.source().getController().isAI();
   batch[ours?0:1]+=e.amount();
  }
 }
 /** Called with the actual LoseLife SpellAbility, after prevention/replacement. Payments and mana burn never qualify. */
 public static void recordLifeLoss(int lost,forge.game.player.Player target,boolean damage,boolean manaBurn,forge.game.spellability.SpellAbility cause){
  if(target==null||!target.isAI())return;
  DuelProgress d=duelProgress.get(target.getGame());if(d==null)return;
  synchronized(d){
   if(damage){long[] batch=d.damageBatches.remove(target.getId());
    if(lost>0&&target.getLife()<=0&&batch!=null&&batch[0]>0&&batch[1]==0)d.lethalDrain.add(target.getId());return;}
   if(lost<=0||manaBurn)return;
   d.lethalDrain.remove(target.getId());
   if(target.getLife()<=0&&cause!=null&&cause.getApi()==forge.game.ability.ApiType.LoseLife&&cause.getActivatingPlayer()!=null&&!cause.getActivatingPlayer().isAI())d.lethalDrain.add(target.getId());
  }
 }
 static boolean drainBossWin(boolean won,int round,boolean lifeLoss,boolean lethal){return won&&round%5==0&&lifeLoss&&lethal;}
 static void mergeDuelProgress(Profile p,Run r,long duel,long sacrifices,long artifacts,boolean bossBurn,boolean winner){
  if(duel<=0||r.lastAchievementDuel==duel)return;
  r.lastAchievementDuel=duel;p.sacrificedPermanents+=Math.max(0,sacrifices);p.artifactCreaturesCast+=Math.max(0,artifacts);
  if(winner){p.bossOneDefeated|=r.round>=5;p.bossTenDefeated|=r.round>=10;p.bossDrainKill|=bossBurn;}
  updateCommanderAchievements(p);
 }
 static void finishDuelAchievements(Profile p,Run r,boolean winner){
  if(r.duelStart<=0||r.lastAchievementDuel==r.duelStart)return;
  try{var view=forge.screens.match.MatchController.instance.getGameView();if(view==null)return;var game=view.getGame();if(game==null)return;
   DuelProgress d=duelProgress.get(game);boolean burn=false;long sacs=0,arts=0;
   if(d!=null&&d.duel==r.duelStart){synchronized(d){sacs=d.sacrifices;arts=d.artifacts;
    for(var opponent:game.getRegisteredPlayers())if(opponent.isAI()){
     var outcome=opponent.getOutcome();burn|=drainBossWin(winner,r.round,outcome!=null&&outcome.lossState==forge.game.player.GameLossReason.LifeReachedZero,d.lethalDrain.contains(opponent.getId()));
    }
   }}
   mergeDuelProgress(p,r,r.duelStart,sacs,arts,burn,winner);
   for(var human:game.getRegisteredPlayers())if(!human.isAI())forge.localinstance.achievements.RogueForgeAchievements.evaluate(human,game,p.forgeAchievements,p.achievements);
  }catch(Exception e){log(e);}
 }

 // ------------------------------------------------------------------ package progression and the run-only shop
 static void initProgression(Profile p){
  if(p.packageUnlocks==null)p.packageUnlocks=new ArrayList<>();
  if(p.packageChoices==null)p.packageChoices=new HashMap<>();
  if(p.achievements==null)p.achievements=new ArrayList<>();
  p.version=Math.max(4,p.version);updateCommanderAchievements(p);
  if(p.run!=null){initRunEconomy(p.run);creditBossGold(p,p.run,p.run.wins);}
 }
 static void initRunEconomy(Run r){
  if(r.shopStock==null)r.shopStock=new ArrayList<>();
  if(r.shopBought==null)r.shopBought=new ArrayList<>();
 }
 /** Boss victories are credited atomically with the permanent balance. Legacy temporary gold is not converted. */
 static int creditBossGold(Profile p,Run r,int wins){
  int earned=0;int[] rounds={5,10,15},amounts={6,10,15};
  for(int i=0;i<rounds.length;i++){int bit=1<<i;if(wins>=rounds[i]&&(r.creditedBossGold&bit)==0){r.creditedBossGold|=bit;earned+=amounts[i];}}
  p.goldBalance+=earned;r.goldEarned+=earned;return earned;
 }
 /** run rule (user, 2026-10-15): pick 2 packages; 3 more of your open packages join each run at random */
 static final int RUN_PICKS=2,RUN_RANDOM=3;
 static int minimumPackages(Cmd c){return RUN_PICKS;}
 static String packageId(Cmd c,Theme t){return c.slug+":"+t.key;}
 static boolean packageOpen(Profile p,Cmd c,Theme t){return !t.progression||t.cost==0||p.packageUnlocks.contains(packageId(c,t));}
 static boolean unlockPackage(Profile p,Cmd c,Theme t){
  initProgression(p);
  if(packageOpen(p,c,t)||p.progressionPoints<t.cost||!p.achievements.contains("package-ready:"+packageId(c,t)))return false;
  p.progressionPoints-=t.cost;p.packageUnlocks.add(packageId(c,t));return true;
 }
 /** Unknown, duplicated and newly locked choices are removed; missing baseline slots are restored. */
 static List<String> packageSelection(Profile p,Cmd c){
  LinkedHashSet<String> out=new LinkedHashSet<>();List<String> saved=p.packageChoices.get(c.name);
  if(saved!=null)for(String k:saved)for(Theme t:c.themes)if(t.key.equals(k)&&packageOpen(p,c,t)&&out.size()<RUN_PICKS)out.add(t.key);
  if(saved==null)for(Theme t:c.themes)if(out.size()<RUN_PICKS&&!t.progression)out.add(t.key);   // first visit: a default pair
  return new ArrayList<>(out);
 }
 static boolean togglePackage(Profile p,Cmd c,Theme t){
  if(!packageOpen(p,c,t))return false;List<String> selected=packageSelection(p,c);
  if(selected.contains(t.key))selected.remove(t.key);
  else{if(selected.size()>=RUN_PICKS)selected.remove(0);selected.add(t.key);}   // a third pick replaces the older one
  p.packageChoices.put(c.name,selected);return true;
 }
 /** the run's 5 packages: the 2 picks plus 3 random open packages (seeded by the run) */
 static List<String> runPackages(Profile p,Cmd c,List<String> picks,long seed){
  List<String> out=new ArrayList<>(picks),pool=new ArrayList<>();
  for(Theme t:c.themes)if(!picks.contains(t.key)&&packageOpen(p,c,t)&&t.cards!=null&&t.cards.length>=6)pool.add(t.key);
  Collections.shuffle(pool,new Random(seed^0x5ac4L));
  for(int i=0;i<Math.min(RUN_RANDOM,pool.size());i++)out.add(pool.get(i));
  return out;
 }
 /** Legacy runs retain their original packages. Unlocking or changing a future loadout never mutates a current run. */
 static List<Theme> activeThemes(Cmd c,Run r){
  List<Theme> out=new ArrayList<>();
  for(Theme t:c.themes)if(r.selectedPackages==null?!t.progression:r.selectedPackages.contains(t.key))out.add(t);
  return out;
 }
 static void progressionAfterRun(Profile p,Run r){
  initProgression(p);int earned=r.wins*5;
  String[] ids={"first-victory","boss-five","boss-ten","complete-run","three-wins:"+r.commander};
  boolean[] done={r.wins>=1,r.wins>=5,r.wins>=10,r.completed,r.wins>=3};int[] bonus={5,10,15,25,5};
  for(int i=0;i<ids.length;i++)if(done[i]&&!p.achievements.contains(ids[i])){p.achievements.add(ids[i]);earned+=bonus[i];}
  p.progressionPoints+=earned;
 }
 /** Selection is drawn directly, with a persistent filled checkbox, coloured surface and status text. */
 class PackageTile extends Actor{
  final Cmd commander;final Theme theme;
  PackageTile(Cmd c,Theme t,float x,float y,float w,float h){commander=c;theme=t;setBounds(x,y,w,h);
   addListener(new ClickListener(){@Override public void clicked(InputEvent e,float xx,float yy){
    if(blocked(PackageTile.this))return;
    if(!packageOpen(profile(),c,t)){toast("Unlock with "+t.cost+" points after "+t.requiredWins+" wins in one run.");return;}
    togglePackage(profile(),c,t);
    save();rebuild();
   }});
  }
  @Override public void draw(Batch b,float a){
   Profile p=profile();boolean open=packageOpen(p,commander,theme),selected=packageSelection(p,commander).contains(theme.key);
   float x=getX(),y=getY(),w=getWidth(),h=getHeight();
   rect(b,x,y,w,h,selected?Color.valueOf("304c36"):open?Color.valueOf("17232f"):Color.valueOf("17191f"),a*.98f);
   frame(b,x,y,w,h,selected?1.6f:.65f,selected?GOLD:open?DIM:Color.valueOf("57525a"),a);
   float bx=x+7,by=y+h/2-6;rect(b,bx,by,12,12,selected?GOLD:Color.valueOf("10151c"),a);frame(b,bx,by,12,12,1,selected?GOLD:DIM,a);
   if(selected){rect(b,bx+2,by+5,2,2,Color.BLACK,a);rect(b,bx+4,by+3,2,2,Color.BLACK,a);rect(b,bx+6,by+5,2,2,Color.BLACK,a);rect(b,bx+8,by+7,2,3,Color.BLACK,a);}
   else if(!open){frame(b,bx+3,by+3,6,5,1,DIM,a);frame(b,bx+4,by+7,4,3,1,DIM,a);}
   text(b,theme.name,x+26,y+h-5,6.5f,open?TEXT:DIM,a,w-32,Align.left,true);
   String state=selected?"SELECTED":open?"NOT SELECTED":"LOCKED · "+theme.requiredWins+" wins + "+theme.cost+" points";
   text(b,state,x+26,y+10,5.2f,selected?GOLD:open?DIM:Color.valueOf("c8a893"),a,w-75,Align.left,selected);
  }
 }
 static int packageColumns(float width){return width>=400?2:1;}
 static int packageRows(float height){return Math.max(1,(int)((height-120)/34));}
 private void buildPackages(Cmd c){
  if(!commanderOpen(profile(),c)){packageConfig=null;buildSelect(profile());return;}
  Profile p=profile();List<String> selected=packageSelection(p,c);background(c.name,.86f);
  long available=Arrays.stream(c.themes).filter(t->packageOpen(p,c,t)).count();
  title("CHOOSE 2 PACKAGES",selected.size()+" / "+RUN_PICKS+" SELECTED  ·  "+available+" available now");
  root.addActor(new Painter((b,a)->{text(b,p.progressionPoints+" unlock points",W()-140,H()-12,6,GOLD,a,128,Align.right,false);
   text(b,"Pick 2 packages: one of them is offered in every reward. 3 more of your open packages join the run at random.",12,H()-43,5.6f,TEXT,a,W()-24,Align.left,false);}));
  List<Theme> ordered=new ArrayList<>(Arrays.asList(c.themes));ordered.sort(Comparator.comparing(t->!packageOpen(p,c,t)));
  int cols=packageColumns(W()),rows=packageRows(H()),per=cols*rows,pages=Math.max(1,(ordered.size()+per-1)/per);
  packagePage=Math.max(0,Math.min(packagePage,pages-1));float gap=5,tw=(W()-24-(cols-1)*gap)/cols,top=H()-64;
  float th=Math.min(38,(top-52-(rows-1)*gap)/rows);
  for(int i=packagePage*per;i<Math.min(ordered.size(),(packagePage+1)*per);i++){
   Theme t=ordered.get(i);int pos=i-packagePage*per;float x=12+(pos%cols)*(tw+gap),y=top-(pos/cols+1)*th-(pos/cols)*gap;
   root.addActor(new PackageTile(c,t,x,y,tw,th));
   boolean open=packageOpen(p,c,t);
   if(open)button("Cards",x+tw-40,y+3,35,11,()->{List<String> ns=new ArrayList<>();for(CardRef card:t.cards)ns.add(card.n);showDeck(c.name,ns,t.name);});
   else button("Unlock "+t.cost+" pts",x+tw-73,y+3,68,11,()->{
    if(unlockPackage(p,c,t)){save();toast("Package unlocked. You can now select it.");rebuild();}
    else toast("Needs "+t.cost+" points and "+t.requiredWins+" wins in one run with this commander.");
   });
  }
  root.addActor(new Painter((b,a)->text(b,"MORE PACKAGES unlock with points + achievements. A third pick replaces your older one.",12,46,5.8f,GOLD,a,W()-24,Align.left,false)));
  button("Back",12,10,60,22,()->{packageConfig=null;rebuild();});
  if(pages>1){button("<",84,10,26,22,()->{packagePage--;rebuild();});button(">",114,10,26,22,()->{packagePage++;rebuild();});}
  Btn start=button("START RUN",W()-130,10,118,22,()->{packageConfig=null;startRun(c);});start.gold=true;start.disabled=selected.size()!=RUN_PICKS;
 }
 static CardRef[] shopCatalog;
 static CardRef[] shopCatalog(){
  if(shopCatalog==null)try{shopCatalog=GSON.fromJson(Files.readString(new File(DIR+"shop.json").toPath(),StandardCharsets.UTF_8),CardRef[].class);}
  catch(Exception e){log(e);shopCatalog=new CardRef[0];}
  return shopCatalog==null?new CardRef[0]:shopCatalog;
 }
 static boolean shopEligible(CardRef x,Cmd c,Run r){
  if(!fits(x.c,c.colors)||x.n.equals(c.name)||!rewardCompatible(x,c)||paper(x.n)==null)return false;
  if(x.needLandColors!=null){boolean target=false;for(char col:x.needLandColors.toCharArray()){
   int i=COLORS.indexOf(col);if(i>=0&&r.deck.contains(BASIC_NAMES[i]))target=true;
  }if(!target)return false;}
  if("Mox Opal".equals(x.n)){int count=0;for(String n:r.deck){PaperCard pc=paper(n);if(pc!=null&&pc.getRules().getType().isArtifact())count++;}if(count<8)return false;}
  if("Crucible of Worlds".equals(x.n)&&!c.slug.startsWith("teval")&&r.deck.stream().noneMatch(n->n.equals("Strip Mine")||n.equals("Wasteland")||n.equals("Prismatic Vista")||n.equals("Fabled Passage")))return false;
  if(("Allosaurus Shepherd".equals(x.n)||"Collector Ouphe".equals(x.n))&&!c.slug.startsWith("shang-chi"))return false;
  if("Urborg, Tomb of Yawgmoth".equals(x.n)&&c.colors.indexOf('B')<0)return false;
  if("Yavimaya, Cradle of Growth".equals(x.n)&&c.colors.indexOf('G')<0)return false;
  if("Cavern of Souls".equals(x.n)&&c.slug.startsWith("jace-"))return false;
  return true;
 }
 static final String[] SHOP_CATEGORIES={"Artifacts","Lands","Removal / Control"};
 static final int SHOP_VERSION=2,REROLL_COST=2;
 static List<CardRef> rollShop(Run r,Cmd c,int rerolls,Set<String> previous){
  List<CardRef> stock=new ArrayList<>();Random rng=new Random(r.seed^((long)r.round*0x5a09L)^((long)rerolls*0x731f95L));
  for(String cat:SHOP_CATEGORIES){List<CardRef> pool=new ArrayList<>();Set<String> seen=new HashSet<>();
   for(CardRef x:shopCatalog())if(cat.equals(x.cat)&&seen.add(x.n)&&shopEligible(x,c,r))pool.add(x);
   Collections.shuffle(pool,rng);
   pool.sort(Comparator.comparingInt(x->r.deck.contains(x.n)||bench(r).contains(x.n)?2:previous.contains(x.n)?1:0));
   stock.addAll(pool.subList(0,Math.min(6,pool.size())));
  }return stock;
 }
 /** Reopening/reloading is free and stable; a deliberate reroll of all 18 cards costs two banked gold. */
 static void stockShop(Run r,Cmd c){
  initRunEconomy(r);if(r.shopRound==r.round&&r.shopCatalogVersion==SHOP_VERSION&&!r.shopStock.isEmpty())return;
  r.shopRound=r.round;r.shopRerolls=0;r.shopCatalogVersion=SHOP_VERSION;r.shopStock=rollShop(r,c,0,Set.of());
 }
 static boolean rerollShop(Profile p,Run r,Cmd c){
  if(r==null||!"fight".equals(r.phase)||r.duelStart>0||p.goldBalance<REROLL_COST)return false;
  stockShop(r,c);Set<String> previous=new HashSet<>();for(CardRef x:r.shopStock)previous.add(x.n);
  List<CardRef> next=rollShop(r,c,r.shopRerolls+1,previous);if(next.size()!=18)return false;
  p.goldBalance-=REROLL_COST;r.shopRerolls++;r.shopStock=next;return true;
 }
 static boolean buyShop(Profile p,Run r,CardRef x){
  if(r==null||!"fight".equals(r.phase)||x==null||r.shopStock==null||!r.shopStock.contains(x)||r.shopBought.contains(x.n)
   ||r.duelStart>0||r.deck.contains(x.n)||bench(r).contains(x.n)||x.price<=0||p.goldBalance<x.price)return false;
  p.goldBalance-=x.price;r.shopBought.add(x.n);
  if(r.deck.size()<MAX_DECK)r.deck.add(x.n);else bench(r).add(x.n);
  if(r.added==null)r.added=new ArrayList<>();if(!r.added.contains(x.n))r.added.add(x.n);return true;
 }
 private void buildShop(Run r){
  Cmd c=cmd(r.commander);stockShop(r,c);background(r.commander,.8f);
  Profile p=profile();title("RUN SHOP · "+p.goldBalance+" GOLD","Gold carries between runs. Bought cards last only this run.");
  float gap=7,pw=(W()-24-gap*2)/3,top=H()-49,bottom=43;
  float cw=Math.min((pw-15)/2,(top-bottom-30)/3*.716f),ch=cw/.716f;
  for(int cat=0;cat<3;cat++){
   String category=SHOP_CATEGORIES[cat];float px=12+cat*(pw+gap);
   root.addActor(new Painter((b,a)->text(b,category,px,top+8,7,GOLD,a,pw,Align.center,true)));
   int slot=0;for(CardRef x:r.shopStock)if(category.equals(x.cat)){
    int col=slot%2,row=slot/2;slot++;float xx=px+(pw-2*cw-5)/2+col*(cw+5),yy=top-15-ch-row*(ch+10);
    CardView v=new CardView(x.n,xx,yy,cw,ch);root.addActor(v);
    boolean owned=r.deck.contains(x.n)||bench(r).contains(x.n),bought=r.shopBought.contains(x.n);
    Btn buy=button(bought?"Bought":owned?"Owned":x.price+" gold",xx,yy-9,cw,9,()->{
     if(buyShop(p,r,x)){save();toast("Bought "+x.n+(r.deck.contains(x.n)?" — added to deck.":" — on your bench (deck full)."));rebuild();}
    });buy.disabled=owned||bought||p.goldBalance<x.price;buy.gold=!buy.disabled;
   }
   if(slot<6)root.addActor(new Painter((b,a)->text(b,"Not enough compatible cards in this catalogue.",px,35,5,DIM,a,pw,Align.center,false)));
  }
  button("Back",12,10,65,21,()->{shopOpen=false;save();rebuild();});
  Btn reroll=button(W()<400?"Reroll · 2g":"Reroll all 18 · 2 gold",88,10,W()-212,21,()->{if(rerollShop(p,r,c)){save();rebuild();}});reroll.disabled=p.goldBalance<REROLL_COST;
  root.addActor(new Painter((b,a)->text(b,"Boss rewards: 6 / 10 / 15 gold. All lands: 6 gold.",12,35,5.5f,GOLD,a,W()-24,Align.left,false)));
  button("Edit deck",W()-112,10,100,21,()->showRunDeck(r));
 }

 // ------------------------------------------------------------------ card images (Forge downloads missing art on demand)
 private static final Map<String,String> imageKeys=new HashMap<>();private static final Set<String> requested=new HashSet<>();
 private static volatile boolean fetchedSomething;private static long lastClear;
 static Texture cardTexture(String name){
  if(SKINS.containsKey(name)){Skin sk=selectedSkin(profile(),name);if(sk!=null&&sk.custom())return customCard(name,sk);return cardTexture(commanderPaper(name));}
  String key=imageKeys.computeIfAbsent(name,n->{PaperCard p=paper(n);return p==null?"":p.getImageKey(false);});
  Texture sh=sharpTexture(key,()->cardPictureFile(paper(name)));if(sh!=null)return sh;
  return textureForKey(key);
 }
 // sharp card pictures: Forge's cached textures have no mipmaps and flicker/blur when drawn small, so the picture file is
 // loaded once more with mipmaps (a few per frame; pictures not drawn for a while are released above 150)
 private static final LinkedHashMap<String,Texture> sharp=new LinkedHashMap<>(64,.75f,true);
 private static final Map<String,Long> sharpUsed=new HashMap<>(),sharpMissing=new HashMap<>();
 private static long sharpFrame=-1;private static int sharpLoads;
 static Texture sharpTexture(String key,java.util.function.Supplier<File> file){
  if(key==null||key.isEmpty())return null;
  long now=System.currentTimeMillis();
  Texture t=sharp.get(key);if(t!=null){sharpUsed.put(key,now);return t;}
  if(now-sharpMissing.getOrDefault(key,0L)<2000)return null;
  long frame=Gdx.graphics.getFrameId();if(frame!=sharpFrame){sharpFrame=frame;sharpLoads=0;}
  if(sharpLoads>=3)return null;
  sharpLoads++;
  File f=null;try{f=file.get();}catch(Throwable ignored){}
  if(f==null||!f.exists()){sharpMissing.put(key,now);return null;}
  try{t=new Texture(Gdx.files.absolute(f.getPath()),true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);}
  catch(Throwable e){log(e);sharpMissing.put(key,now+60000);return null;}
  sharp.put(key,t);sharpUsed.put(key,now);sharpMissing.remove(key);
  if(sharp.size()>150){var it=sharp.entrySet().iterator();
   while(it.hasNext()&&sharp.size()>150){var e=it.next();if(now-sharpUsed.getOrDefault(e.getKey(),0L)<3000)break;e.getValue().dispose();sharpUsed.remove(e.getKey());it.remove();}}
  return t;
 }
 private static final Map<String,Texture> files=new HashMap<>();
 /** custom card images (fan-made illustrations) straight from disk */
 static Texture fileTexture(String path){
  return files.computeIfAbsent(path,p->{try{var f=Gdx.files.absolute(p);if(!f.exists())return null;Texture t=new Texture(f,true);
   t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return t;}catch(Exception e){log(e);return null;}});
 }
 /** fan-art skin: the finished card image from skins/, else built once on this PC from the official card picture in Forge's
  *  picture cache with the illustration skins/<name>-art.png painted into its art box (the original credit line is covered) */
 private static final Set<String> customFailed=new HashSet<>();
 static Texture customCard(String commander,Skin sk){
  String base=DIR+"skins/"+sk.number.replaceAll("\\.[A-Za-z]+$","");
  if(Gdx.files.absolute(DIR+"skins/"+sk.number).exists())return fileTexture(DIR+"skins/"+sk.number);
  if(files.get(base+"-card.png")!=null||Gdx.files.absolute(base+"-card.png").exists())return fileTexture(base+"-card.png");
  PaperCard p=skinPaper(commander,skins(commander).get(0));
  Texture official=cardTexture(p);       // also makes Forge download the picture
  File src=cardPictureFile(p);
  if(customFailed.contains(base)||src==null||!Gdx.files.absolute(base+"-art.png").exists())return official;
  Pixmap card=null,art=null;
  try{
   card=new Pixmap(Gdx.files.absolute(src.getPath()));art=new Pixmap(Gdx.files.absolute(base+"-art.png"));
   float sx=card.getWidth()/744f,sy=card.getHeight()/1040f;
   int x0=Math.round(58*sx),y0=Math.round(117*sy),w=Math.round(628*sx),h=Math.round(460*sy);
   float scale=Math.max(w/(float)art.getWidth(),h/(float)art.getHeight());int cw=Math.round(w/scale),ch=Math.round(h/scale);
   card.setFilter(Pixmap.Filter.BiLinear);
   card.drawPixmap(art,(art.getWidth()-cw)/2,(art.getHeight()-ch)/2,cw,ch,x0,y0,w,h);
   card.setColor(card.getPixel(Math.round(332*sx),Math.round(983*sy)));
   card.fillRectangle(Math.round(137*sx),Math.round(986*sy),Math.round(193*sx),Math.round(22*sy));
   try{com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(base+"-card.png"),card);}catch(Throwable t){log(t);}   // e.g. no write access
   Texture t=new Texture(card,true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
   files.put(base+"-card.png",t);return t;
  }catch(Throwable t){log(t);customFailed.add(base);return official;}
  finally{if(card!=null)card.dispose();if(art!=null)art.dispose();}
 }
 /** the downloaded picture of a printing in Forge's picture cache, or null while it is not there yet */
 static File cardPictureFile(PaperCard p){
  if(p==null)return null;
  for(String key:new String[]{forge.util.ImageUtil.getImageKey(p,"",true),p.getImageKey(false)}){
   try{File f=forge.ImageKeys.getImageFile(key);if(f!=null&&f.exists())return f;}catch(Throwable ignored){}
  }
  try{String base=forge.localinstance.properties.ForgeConstants.CACHE_CARD_PICS_DIR+p.getEdition()+"/"+p.getName()+Math.max(1,p.getArtIndex());
   for(String ext:new String[]{".fullborder.jpg",".full.jpg",".fullborder.png",".full.png"}){File f=new File(base+ext);if(f.exists())return f;}
  }catch(Throwable ignored){}
  return null;
 }
 static Texture cardTexture(PaperCard card){
  if(card==null)return null;String key=card.getImageKey(false);
  Texture sh=sharpTexture(key,()->cardPictureFile(card));return sh!=null?sh:textureForKey(key);
 }
 static Texture textureForKey(String key){
  if(key.isEmpty())return null;
  if(fetchedSomething&&System.currentTimeMillis()-lastClear>1500){fetchedSomething=false;lastClear=System.currentTimeMillis();try{ImageCache.getInstance().clear();}catch(Exception ignored){}}
  Texture t=null;try{t=ImageCache.getInstance().getImage(key,false);}catch(Exception ignored){}
  if(t==null&&requested.add(key)){try{GuiBase.getInterface().getImageFetcher().fetchImage(key,()->fetchedSomething=true);}catch(Exception ignored){}}
  return t;
 }

 // ------------------------------------------------------------------ fonts, pixels, boards
 private static BitmapFont titleFont,bodyFont;private static Texture white;private static final Map<String,Texture> boards=new HashMap<>();
 static final float FONT_PX=96;
 static BitmapFont font(boolean title){
  if(titleFont==null){
   String t=fontFile(true),bo=fontFile(false);
   titleFont=t!=null?gen(t):bo!=null?gen(bo):new BitmapFont();bodyFont=bo!=null?gen(bo):titleFont;
  }
  return title?titleFont:bodyFont;
 }
 /** mod fonts if present, else the fonts that come with Windows (nothing is shipped) */
 static String fontFile(boolean title){
  String w=System.getenv("WINDIR");if(w==null)w="C:/Windows";w=w.replace('\\','/')+"/Fonts/";
  String[] c=title?new String[]{ROOT+"_mods/modern-hud/fantasy.ttf",w+"ANTQUAB.TTF",w+"BKANT.TTF",w+"georgiab.ttf",w+"segoeuib.ttf",w+"arialbd.ttf"}
   :new String[]{ROOT+"_mods/modern-hud/ui.ttf",w+"segoeui.ttf",w+"arial.ttf"};
  for(String p:c)if(new File(p).exists())return p;
  return null;
 }
 private static BitmapFont gen(String path){
  var g=new FreeTypeFontGenerator(Gdx.files.absolute(path));var p=new FreeTypeFontGenerator.FreeTypeFontParameter();
  p.size=(int)FONT_PX;p.minFilter=Texture.TextureFilter.Linear;p.magFilter=Texture.TextureFilter.Linear;
  p.characters=FreeTypeFontGenerator.DEFAULT_CHARS+"äöüÄÖÜßǵ–—·…’‘“”";
  BitmapFont f=g.generateFont(p);g.dispose();f.setUseIntegerPositions(false);return f;
 }
 // warm dark leather with a double gold rim and corner rivets (same look as the author's RPG menu theme).
 // variant: 0 normal, 1 hover, 2 pressed, 3 disabled
 private static final NinePatch[] frames=new NinePatch[4];
 static NinePatch panel(){return panel(0);}
 static NinePatch panel(int variant){
  if(frames[variant]!=null)return frames[variant];
  int s=48;Pixmap p=new Pixmap(s,s,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  float lift=variant==1?1.22f:variant==2?.78f:1f;
  for(int y=0;y<s;y++)for(int x=0;x<s;x++){
   float t=y/(float)(s-1),grain=(hash(x,y)-.5f)*7f;
   float r=(60-25*t+grain)*lift,g=(43-19*t+grain*.8f)*lift,b=(29-14*t+grain*.6f)*lift;
   if(variant==3){float l=(r+g+b)/3;r=g=b=l*.85f;}
   p.setColor(r/255f,g/255f,b/255f,1);p.drawPixel(x,y);
  }
  Color dark=Color.valueOf("120b06"),base=Color.valueOf(variant==3?"7d7465":"b08a3e"),light=Color.valueOf(variant==3?"a59a86":"ead08a"),shade=Color.valueOf(variant==3?"4f4a40":"6e5020");
  p.setColor(dark);p.drawRectangle(0,0,s,s);
  p.setColor(base);p.drawRectangle(1,1,s-2,s-2);
  p.setColor(light);p.drawLine(2,1,s-3,1);p.drawLine(1,2,1,s-3);
  p.setColor(shade);p.drawLine(2,s-2,s-3,s-2);p.drawLine(s-2,2,s-2,s-3);
  p.setColor(dark);p.drawRectangle(2,2,s-4,s-4);
  p.setColor(Color.valueOf(variant==3?"59524a":"775530"));p.drawRectangle(3,3,s-6,s-6);
  for(int[] c:new int[][]{{4,4},{s-6,4},{4,s-6},{s-6,s-6}}){p.setColor(dark);p.fillRectangle(c[0]-1,c[1]-1,4,4);p.setColor(light);p.fillRectangle(c[0],c[1],2,2);}
  Texture tex=new Texture(p);p.dispose();tex.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
  NinePatch n=new NinePatch(tex,8,8,8,8);n.scale(.5f,.5f);frames[variant]=n;return n;
 }
 private static float hash(int x,int y){int n=x*374761393+y*668265263;n=(n^(n>>>13))*1274126177;return ((n^(n>>>16))&0xffff)/65535f;}
 static Texture white(){if(white==null){Pixmap p=new Pixmap(1,1,Pixmap.Format.RGBA8888);p.setColor(Color.WHITE);p.fill();white=new Texture(p);p.dispose();}return white;}
 static void rect(Batch b,float x,float y,float w,float h,Color c,float a){Color o=b.getColor().cpy();b.setColor(c.r,c.g,c.b,c.a*a);b.draw(white(),x,y,w,h);b.setColor(o);}
 static void frame(Batch b,float x,float y,float w,float h,float t,Color c,float a){rect(b,x,y,w,t,c,a);rect(b,x,y+h-t,w,t,c,a);rect(b,x,y,t,h,c,a);rect(b,x+w-t,y,t,h,c,a);}
 static float text(Batch b,String s,float x,float y,float size,Color c,float a,float width,int align,boolean title){
  BitmapFont f=font(title);f.getData().setScale(size/FONT_PX);f.setColor(c.r,c.g,c.b,c.a*a);
  return f.draw(b,s,x,y,width,align,true).height;
 }
 static Texture board(String commander){
  Cmd definition=cmd(commander);String name=definition!=null&&definition.board!=null?definition.board:commander==null?"Fallback":BOARD.getOrDefault(commander,"Fallback");
  return boards.computeIfAbsent(commander==null?"":commander,k->{
   Cmd c=commander==null?null:cmd(commander);
   String hd=BOARDS.replace("/boards/","/boards-hd/");   // reworked 4K boards first
   for(String path:new String[]{hd+name+".jpg",BOARDS+name+".jpg",hd+"Fallback.jpg",BOARDS+"Fallback.jpg",DIR+"backgrounds/"+(c==null?"default":c.slug)+".jpg",DIR+"backgrounds/default.jpg"}){
    var f=Gdx.files.absolute(path);if(!f.exists())continue;
    Texture t=new Texture(f);t.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);return t;}
   return null;});
 }
 static final Color GOLD=Color.valueOf("e8b44c"),TEXT=Color.valueOf("efe6d2"),DIM=Color.valueOf("a69c8a"),DARK=Color.valueOf("0b0c12"),
  GREEN=Color.valueOf("6fd08f"),RED=Color.valueOf("ef6b6b");
 static Color pip(char ch){return switch(ch){case 'W'->Color.valueOf("f4efd2");case 'U'->Color.valueOf("3d8fd6");case 'B'->Color.valueOf("4a3f45");case 'R'->Color.valueOf("e0483c");case 'G'->Color.valueOf("2f9a5a");default->Color.GRAY;};}
 static Color rarityColor(String r){return switch(r==null?"":r){case "Selten"->Color.valueOf("5aa7ff");case "Episch"->Color.valueOf("b46cff");case "Legendär"->Color.valueOf("ff9a2e");default->Color.valueOf("c8c8c8");};}
 static Color tierColor(String t){return switch(t==null?"":t){case "stark"->Color.valueOf("ffb43c");case "gut"->Color.valueOf("5aa7ff");case "signatur"->Color.valueOf("c77dff");default->Color.valueOf("cfcfcf");};}

 // Translate only display text; machine keys and saved profile text keep their original values.
 static String displayText(String value){
  if(value==null)return null;
  return switch(value){
   case "signatur"->"Signature";
   case "stark"->"Strong";
   case "gut"->"Good";
   case "mittel"->"Solid";
   case "Selten"->"Rare";
   case "Episch"->"Epic";
   case "Legendär"->"Legendary";
   case "Thema"->"Theme";
   case "Allgemein"->"General";
   case "Legendärer Fund"->"Legendary Find";
   case "Alle"->"All";
   case "Kreaturen"->"Creatures";
   case "Zauber"->"Spells";
   case "Artefakte"->"Artifacts";
   case "Verzauberungen"->"Enchantments";
   case "Länder"->"Lands";
   case "Neu im Run"->"New this run";
   case "Erbstücke"->"Heirlooms";
   case "Freigeschaltet"->"Unlocked";
   case "Gesperrt"->"Locked";
   case "Signatur"->"Signature";
   case "Typ"->"Type";
   case "Farbe"->"Color";
   case "Neu"->"New";
   case "Stufe"->"Tier";
   case "Letztes Paket"->"Last package";
   case "groß"->"major";
   case "klein"->"minor";
   case "Dünne Hand"->"Thin Hand";
   case "Du startest jedes Duell mit einer Karte weniger."->"You start each duel with one fewer card.";
   case "Zähe Gegner"->"Tough Foes";
   case "Gegner starten mit 8 Leben mehr."->"Opponents start with 8 extra life.";
   case "Lähmende Präsenz"->"Crippling Presence";
   case "Gegner starten mit Authority of the Consuls: deine Kreaturen kommen getappt ins Spiel."->"Opponents start with Authority of the Consuls: your creatures enter tapped.";
   case "Verdorbene Heilung"->"Tainted Healing";
   case "Gegner starten mit Tainted Remedy: dein Lebensgewinn wird zu Lebensverlust."->"Opponents start with Tainted Remedy: your life gain becomes life loss.";
   case "Dornenlast"->"Thorn Burden";
   case "Gegner starten mit Thorn of Amethyst: Nichtkreatur-Zauber kosten 1 mehr."->"Opponents start with Thorn of Amethyst: noncreature spells cost 1 more.";
   case "Geteilte Gaben"->"Shared Gifts";
   case "Gegner starten mit Howling Mine: jeder zieht eine Karte mehr."->"Opponents start with Howling Mine: each player draws an extra card.";
   case "Halbes Leben"->"Half Life";
   case "Du startest jedes Duell mit halbem Leben."->"You start each duel at half life.";
   case "Seelenverrat"->"Soul Betrayal";
   case "Gegner starten mit Night of Souls' Betrayal: alle Kreaturen erhalten -1/-1."->"Opponents start with Night of Souls' Betrayal: all creatures get -1/-1.";
   case "Schwefelsturm"->"Sulfur Storm";
   case "Gegner starten mit Sulfuric Vortex: 2 Schaden pro Zug, kein Lebensgewinn."->"Opponents start with Sulfuric Vortex: 2 damage each turn, no life gain.";
   case "Ewige Ruhe"->"Eternal Rest";
   case "Gegner starten mit Rest in Peace: Friedhöfe werden ins Exil gelegt."->"Opponents start with Rest in Peace: graveyards are exiled.";
   case "Leere Hände"->"Empty Hands";
   case "Du startest jedes Duell mit zwei Karten weniger."->"You start each duel with two fewer cards.";
   case "Sphäre des Widerstands"->"Sphere of Resistance";
   case "Gegner starten mit Sphere of Resistance: alle Zauber kosten 1 mehr."->"Opponents start with Sphere of Resistance: all spells cost 1 more.";
   case "Runden gewonnen"->"Rounds won";
   case "Verfluchter Sieg"->"Cursed victory";
   case "Bosse besiegt"->"Bosses defeated";
   case "Makellos (volles Leben)"->"Flawless (full life)";
   case "Lebensquelle (50+ Leben)"->"Life Source (50+ life)";
   case "Blitzsieg (bis Zug 7)"->"Blitz win (by turn 7)";
   case "Marathon (20+ Züge)"->"Marathon (20+ turns)";
   case "Am seidenen Faden (≤ 5 Leben)"->"Close Call (≤ 5 life)";
   case "Commander-Schaden-Sieg"->"Commander damage win";
   case "Giftsieg"->"Poison win";
   case "Mill-Sieg"->"Mill win";
   case "Alternativer Sieg"->"Alternate win";
   case "Überwältigend (−10 Leben)"->"Overkill (−10 life)";
   case "Gegen alle Widrigkeiten (2+ Mulligans)"->"Against the Odds (2+ mulligans)";
   case "Zaubersturm (20+ Zauber)"->"Spellstorm (20+ spells)";
   case "Armee (10+ Kreaturen)"->"Army (10+ creatures)";
   case "Ohne Commander gewonnen"->"Won without commander";
   case "Legendäre Pakete"->"Legendary packages";
   case "Epische Pakete"->"Epic packages";
   case "Erbstücke im Startdeck"->"Heirlooms in starting deck";
   case "Meilenstein (neuer Siegrekord)"->"Milestone (new win record)";
   case "Tiefenbonus"->"Depth bonus";
   default->value;
  };
 }
 static String scoreLabel(String value){
  if(value==null)return null;
  if(value.startsWith("Fluch: "))return "Curse: "+displayText(value.substring(7));
  if(value.startsWith("Run abgeschlossen ("))return value.replace("Run abgeschlossen (","Run completed (").replace(" Runden)"," rounds)");
  if(value.startsWith("Spezialist ("))return value.replace("Spezialist (","Specialist (").replace("× gleiches Paket)","× same package)");
  if(value.startsWith("Vielseitig ("))return value.replace("Vielseitig (","Versatile (").replace(" Paketarten)"," package types)");
  return displayText(value);
 }
 static String pickedLabel(String value){
  int suffix=value.lastIndexOf(" (");
  if(suffix<0||!value.endsWith(")"))return value;
  String name=value.substring(0,suffix),rarity=value.substring(suffix+2,value.length()-1);
  return ("Legendärer Fund".equals(name)?"Legendary Find":name)+" ("+displayText(rarity)+")";
 }

 // ------------------------------------------------------------------ actors
 /** wooden RPG button with centered caption */
 class Btn extends Actor{
  String label;Runnable action;boolean gold,disabled;final ClickListener click;
  Btn(String label,float x,float y,float w,float h,Runnable action){this.label=label;this.action=action;setBounds(x,y,w,h);
   click=new ClickListener(){@Override public void clicked(InputEvent e,float cx,float cy){if(!disabled&&!blocked(Btn.this))try{action.run();}catch(Throwable t){log(t);toast("Error: "+t);}}};addListener(click);}
  @Override public void draw(Batch b,float a){
   int v=disabled?3:click.isPressed()?2:click.isOver()?1:0;Color o=b.getColor().cpy();b.setColor(1,1,1,a);panel(v).draw(b,getX(),getY(),getWidth(),getHeight());b.setColor(o);
   if(gold&&!disabled)frame(b,getX()+1.5f,getY()+1.5f,getWidth()-3,getHeight()-3,.8f,GOLD,a*(.55f+.35f*(float)Math.sin(time*3)));
   float size=Math.min(getHeight()*.48f,9.5f);
   BitmapFont face=font(true);face.getData().setScale(size/FONT_PX);float measured=new com.badlogic.gdx.graphics.g2d.GlyphLayout(face,label).width;if(measured>getWidth()-8&&measured>0)size*=Math.max(1,getWidth()-8)/measured;
   text(b,label,getX(),getY()+getHeight()/2+size*.42f,size,gold&&!disabled?GOLD:disabled?DIM:TEXT,a,getWidth(),Align.center,true);
  }
 }
 /** card image (or a framed placeholder while the art downloads); tap opens the big preview unless an action is set */
 class CardView extends Actor{
  String name,tag;Skin customArt;Color border;boolean selected,dim;Runnable action;PaperCard illustration;int rating=-1;
  CardView(String name,float x,float y,float w,float h){this.name=name;setBounds(x,y,w,h);
   addListener(new ClickListener(){@Override public void clicked(InputEvent e,float cx,float cy){if(blocked(CardView.this))return;try{if(action!=null)action.run();else preview(CardView.this.name,illustration);}catch(Throwable t){log(t);}}});}
  @Override public void draw(Batch b,float a){
   float x=getX(),y=getY(),w=getWidth(),h=getHeight();
   if(selected)rect(b,x-2.5f,y-2.5f,w+5,h+5,GOLD,a*(.55f+.3f*(float)Math.sin(time*4)));
   rect(b,x+1.2f,y-1.5f,w,h,Color.BLACK,a*.45f);
   Texture t=customArt!=null?customCard(name,customArt):illustration==null?cardTexture(name):cardTexture(illustration);
   if(t!=null){Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(t,x,y,w,h);b.setColor(o);}
   else{rect(b,x,y,w,h,Color.valueOf("1c1a22"),a);frame(b,x,y,w,h,.8f,DIM,a);text(b,name,x+2,y+h-3,Math.max(3.2f,w/9),TEXT,a,w-4,Align.center,false);}
   if(dim)rect(b,x,y,w,h,Color.BLACK,a*.62f);
   if(rating>=0){float s=Math.max(7,w*.26f),bx=x+w-s-w*.05f,by=y+h*.80f-s;Color rc=ratingColor(rating);
    rect(b,bx-.7f,by-.7f,s+1.4f,s+1.4f,Color.BLACK,a*.85f);rect(b,bx,by,s,s,rc,a*.95f);
    text(b,String.valueOf(rating),bx,by+s*.74f,s*.56f,Color.BLACK,a,s,Align.center,true);}
   if(border!=null)frame(b,x-.6f,y-.6f,w+1.2f,h+1.2f,1.1f,border,a);
   if(tag!=null){float th=Math.max(4.5f,h*.11f);rect(b,x,y,w,th,Color.BLACK,a*.72f);text(b,tag,x,y+th*.78f,th*.72f,border!=null?border:TEXT,a,w,Align.center,false);}
  }
 }
 class Painter extends Actor{
  final java.util.function.BiConsumer<Batch,Float> paint;
  Painter(java.util.function.BiConsumer<Batch,Float> paint){this.paint=paint;setTouchable(Touchable.disabled);}
  boolean failed;
  @Override public void draw(Batch b,float a){if(failed)return;try{paint.accept(b,a);}catch(Throwable t){failed=true;log(t);}}
 }

 // ------------------------------------------------------------------ scene
 private static RogueCommandersScene object;
 public static RogueCommandersScene instance(){if(object==null)object=new RogueCommandersScene();return object;}
 private final Group root=new Group();private final List<Group> overlays=new ArrayList<>();private float time;private String message;private float messageTime;
 private long lastInput=System.currentTimeMillis(),lastTick=System.currentTimeMillis();
 private AdventureEventData editing;private String editingCommander;private boolean editingRun;private AdventureEventData.AdventureEventParticipant opponent;
 private Cmd wardrobe,packageConfig;private int packagePage;private boolean shopOpen;
 private RogueCommandersScene(){
  super(Forge.isLandscapeMode()?"ui/rogue_commanders.json":"ui/rogue_commanders_portrait.json");
  ui.addActor(root);
 }
 float W(){return getIntendedWidth();}
 float H(){return getIntendedHeight();}

 /** StartScene constructor hook: wires the "Rogue" button of the main menu */
 public static void hookStart(StartScene start){try{start.getUI().onButtonPress("Rogue",RogueCommandersScene::openFromMenu);}catch(Exception e){System.err.println("[Rogue] menu: "+e);}}
 /** duels need a loaded character (avatar, statistics, music), so the last save is loaded quietly first */
 public static void openFromMenu(){
  if(WorldSave.getCurrentSave().getWorld().getData()!=null){try{Forge.switchScene(instance());}catch(Throwable t){log(t);}return;}
  String last=Config.instance().getSettingData().lastActiveSave;
  if(last!=null&&WorldSave.isSafeFile(last)){
   Forge.setTransitionScreen(new TransitionScreen(()->{
    boolean loaded=false;
    try{loaded=WorldSave.load(WorldSave.filenameToSlot(last));}catch(Throwable t){log(t);}
    try{if(!loaded)createCharacter();profile=null;profile();Forge.switchScene(instance());}
    catch(Throwable t){log(t);StartScene s=StartScene.instance();
     s.showDialog(s.createGenericDialog("Rogue Commanders","Start a new game first","OK",null,s::removeDialog,null,false,"",true));}
    Forge.clearTransitionScreen();
   },null,false,true,"Loading Rogue Commanders ..."));
  }else{
   // new player: Rogue Commanders only needs a character and its save folder, so one is created quietly
   Forge.setTransitionScreen(new TransitionScreen(()->{
    try{createCharacter();profile=null;profile();Forge.switchScene(instance());}
    catch(Throwable t){log(t);StartScene s=StartScene.instance();
     s.showDialog(s.createGenericDialog("Rogue Commanders","Start a new game first","OK",null,s::removeDialog,null,false,"",true));}
    Forge.clearTransitionScreen();
   },null,false,true,"Setting up Rogue Commanders ..."));
  }
 }
 /** default Adventure character (starting difficulty, standard mode) in the first free save slot */
 static void createCharacter(){
  Config cfg=Config.instance();
  forge.adventure.data.DifficultyData[] diffs=cfg.getConfigData().difficulties;
  if(diffs==null||diffs.length==0)throw new IllegalStateException("Adventure config has no difficulties");
  forge.adventure.data.DifficultyData diff=diffs[0];
  for(forge.adventure.data.DifficultyData d:diffs)if(d.startingDifficulty)diff=d;
  String[] colors=cfg.colorIds();
  forge.card.ColorSet color=forge.card.ColorSet.fromNames(colors.length>0?colors[0].toCharArray():new char[]{'W'});
  String[] editions=cfg.starterEditions();
  forge.card.CardEdition edition=editions.length>0?FModel.getMagicDb().getEditions().get(editions[0]):null;
  WorldSave.generateNewWorld("Rogue",true,0,0,color,diff,forge.adventure.util.AdventureModes.Standard,0,edition,0L);
  forge.player.GamePlayerUtil.getGuiPlayer().setName("Rogue");
  for(int slot=1;slot<=20;slot++)if(!new File(WorldSave.getSaveFile(slot)).exists()){WorldSave.getCurrentSave().save("Rogue Commanders",slot);return;}
  // all slots taken: keep the character for this session only, never overwrite a save
 }

 @Override public void enter(){
  super.enter();restoreSavedDisplay();
  try{profile();content();if(editing!=null)finishEditing();rebuild();}catch(Throwable t){log(t);}
 }
 @Override public boolean leave(){save();return super.leave();}
 @Override public boolean back(){if(displayScreen){displayScreen=false;rebuild();return true;}if(achievementScreen){achievementScreen=false;rebuild();return true;}if(!overlays.isEmpty()){closeOverlay();return true;}if(packageConfig!=null){packageConfig=null;rebuild();return true;}if(shopOpen){shopOpen=false;rebuild();return true;}if(wardrobe!=null){wardrobe=null;rebuild();return true;}Forge.switchScene(StartScene.instance());return true;}
 @Override public void act(float delta){
  super.act(delta);time+=delta;if(messageTime>0)messageTime-=delta;
  long now=System.currentTimeMillis();
  if(Gdx.input.isTouched()||Gdx.input.justTouched())lastInput=now;
  if(now-lastInput<120_000)profile().activeMs+=Math.min(250,now-lastTick);   // idle menus do not count
  lastTick=now;
 }
 private void toast(String m){message=m;messageTime=4f;}

 private void rebuild(){
  while(!overlays.isEmpty())closeOverlay();
  root.clearChildren();root.setBounds(0,0,W(),H());
  Profile p=profile();
  if(displayScreen)buildDisplay();
  else if(achievementScreen)buildAchievements(p);
  else if(packageConfig!=null)buildPackages(packageConfig);
  else if(shopOpen&&p.run!=null&&"fight".equals(p.run.phase))buildShop(p.run);
  else if(wardrobe!=null)buildSkins(wardrobe);
  else if(p.result!=null)buildResult(p);
  else if(p.run!=null&&"curse".equals(p.run.phase))buildCurse(p.run);
  else if(p.run!=null&&"package".equals(p.run.phase))buildOffers(p.run);
  else if(p.run!=null)buildRun(p.run);
  else buildSelect(p);
  root.addActor(new Painter((b,a)->{if(messageTime>0&&message!=null){float w=Math.min(W()-40,260);float x=(W()-w)/2,ty=40;rect(b,x,ty,w,16,Color.BLACK,a*.85f*Math.min(1,messageTime));frame(b,x,ty,w,16,.6f,GOLD,a*Math.min(1,messageTime));text(b,message,x,ty+11,6,TEXT,a*Math.min(1,messageTime),w,Align.center,false);}}));
 }
 private void background(String commander,float dim){
  root.addActor(new Painter((b,a)->{
   float w=W(),h=H();Texture t=board(commander);
   if(t!=null){float ta=t.getWidth()/(float)t.getHeight(),sa=w/h;int sw=t.getWidth(),sh=t.getHeight(),sx=0,sy=0;
    if(ta>sa){sw=Math.round(t.getHeight()*sa);sx=(t.getWidth()-sw)/2;}else{sh=Math.round(t.getWidth()/sa);sy=(t.getHeight()-sh)/2;}
    float drift=(float)Math.sin(time*.07f)*12;Color o=b.getColor().cpy();b.setColor(1,1,1,a);
    b.draw(t,-6+drift*.3f,-4,w+12,h+8,sx,sy,sw,sh,false,false);b.setColor(o);}
   else rect(b,0,0,w,h,DARK,a);
   rect(b,0,0,w,h,DARK,a*dim);
   for(int i=0;i<12;i++){rect(b,0,0,w,h*.05f*(12-i)/12f,Color.BLACK,a*.05f);rect(b,0,h-h*.04f*(12-i)/12f,w,h*.04f*(12-i)/12f,Color.BLACK,a*.05f);}
  }));
 }
 private void title(String big,String small){
  root.addActor(new Painter((b,a)->{
   text(b,big,13,H()-7,15,Color.BLACK,a*.6f,W()-160,Align.left,true);
   text(b,big,12,H()-6,15,GOLD,a,W()-160,Align.left,true);
   if(small!=null)text(b,small,13,H()-25,6.5f,DIM,a,W()-160,Align.left,false);
  }));
 }
 private Btn button(String label,float x,float y,float w,float h,Runnable r){Btn b=new Btn(label,x,y,w,h,r);root.addActor(b);return b;}
 private void pips(Batch b,String colors,float x,float y,float r,float a){
  for(int i=0;i<colors.length();i++){char ch=colors.charAt(i);float cx=x+i*(r*2+2);
   rect(b,cx-.6f,y-.6f,r*2+1.2f,r*2+1.2f,Color.BLACK,a*.7f);rect(b,cx,y,r*2,r*2,pip(ch),a);
   text(b,String.valueOf(ch),cx,y+r*1.55f,r*1.3f,ch=='W'?Color.DARK_GRAY:Color.WHITE,a,r*2,Align.center,true);}
 }
 private void bar(Batch b,float x,float y,float w,float h,float f,Color c,float a){rect(b,x,y,w,h,Color.BLACK,a*.6f);rect(b,x,y,w*Math.max(0,Math.min(1,f)),h,c,a);frame(b,x,y,w,h,.5f,DIM,a*.6f);}
 static String hours(long ms){long m=ms/60000;return (m/60)+":"+String.format("%02d",m%60)+" h";}
 float generalShare(){return content().unlocks.length==0?0:profile().general.size()/(float)content().unlocks.length;}

 // Common desktop presets use Forge's native persisted video mode names.
 static final int[][] DISPLAY_SIZES={{1280,720},{1600,900},{1920,1080},{2560,1440},{3840,2160}};
 static final String[] DISPLAY_MODES={"720p","900p","1080p","1440p","2160p"};
 private boolean displayScreen,displayFullscreen;private int displaySelection=2;
 static int displayIndex(int width,int height){for(int i=0;i<DISPLAY_SIZES.length;i++)if(DISPLAY_SIZES[i][0]==width&&DISPLAY_SIZES[i][1]==height)return i;return -1;}
 static boolean windowFits(int index,int desktopWidth,int desktopHeight){return index>=0&&index<DISPLAY_SIZES.length&&DISPLAY_SIZES[index][0]<=desktopWidth&&DISPLAY_SIZES[index][1]<=desktopHeight;}
 private void openDisplay(){
  int index=displayIndex(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
  displaySelection=index<0?Math.max(0,displayIndex(Config.instance().getSettingData().width,Config.instance().getSettingData().height)):index;
  displayFullscreen=Gdx.graphics.isFullscreen();displayScreen=true;rebuild();
 }
 static com.badlogic.gdx.Graphics.DisplayMode displayMode(int index){
  if(index<0||index>=DISPLAY_SIZES.length)return null;
  var monitor=Gdx.graphics.getMonitor();var current=Gdx.graphics.getDisplayMode(monitor);com.badlogic.gdx.Graphics.DisplayMode best=null;
  for(var mode:Gdx.graphics.getDisplayModes(monitor))if(mode.width==DISPLAY_SIZES[index][0]&&mode.height==DISPLAY_SIZES[index][1]){
   if(best==null||Math.abs(mode.refreshRate-current.refreshRate)<Math.abs(best.refreshRate-current.refreshRate))best=mode;
  }
  return best;
 }
 private void applyDisplay(){
  final int index=displaySelection;final boolean fullscreen=displayFullscreen;
  Gdx.app.postRunnable(()->{
   try{
    boolean accepted;
    if(fullscreen){var mode=displayMode(index);if(mode==null){toast("This fullscreen resolution is not supported by your monitor.");return;}accepted=Gdx.graphics.setFullscreenMode(mode);}
    else{var desktop=Gdx.graphics.getDisplayMode(Gdx.graphics.getMonitor());if(!windowFits(index,desktop.width,desktop.height)){toast("Choose a resolution that fits your monitor.");return;}accepted=Gdx.graphics.setWindowedMode(DISPLAY_SIZES[index][0],DISPLAY_SIZES[index][1]);}
    if(!accepted){toast("The display could not apply that mode.");return;}
    var settings=Config.instance().getSettingData();settings.fullScreen=fullscreen;settings.width=DISPLAY_SIZES[index][0];settings.height=DISPLAY_SIZES[index][1];settings.videomode=DISPLAY_MODES[index];Config.instance().saveSettings();
    FModel.getPreferences().setPref(forge.localinstance.properties.ForgePreferences.FPref.UI_VIDEO_MODE,DISPLAY_MODES[index]);FModel.getPreferences().save();
    rebuild();toast("Applied "+DISPLAY_SIZES[index][0]+" x "+DISPLAY_SIZES[index][1]+(fullscreen?" fullscreen":" windowed"));
   }catch(Exception e){log(e);toast("Display change failed. Your saved mode was kept.");}
  });
 }
 private void buildDisplay(){
  background(profile().selected,.8f);title("DISPLAY",null);
  button("Back",W()-66,H()-22,56,16,()->{displayScreen=false;rebuild();});
  float width=Math.min(330,W()-24),x=(W()-width)/2;
  Btn window=button(displayFullscreen?"Windowed":"[ Windowed ]",x,H()-55,(width-6)/2,20,()->{displayFullscreen=false;rebuild();});window.gold=!displayFullscreen;
  Btn full=button(displayFullscreen?"[ Fullscreen ]":"Fullscreen",x+(width+6)/2,H()-55,(width-6)/2,20,()->{displayFullscreen=true;rebuild();});full.gold=displayFullscreen;
  var desktop=Gdx.graphics.getDisplayMode(Gdx.graphics.getMonitor());
  for(int i=0;i<DISPLAY_SIZES.length;i++){
   final int index=i;boolean supported=displayFullscreen?displayMode(i)!=null:windowFits(i,desktop.width,desktop.height);
   String label=DISPLAY_SIZES[i][0]+" x "+DISPLAY_SIZES[i][1]+(displaySelection==i?"   [ SELECTED ]":"")+(supported?"":"   (unavailable)");
   Btn preset=button(label,x,H()-82-i*24,width,20,()->{displaySelection=index;rebuild();});preset.gold=displaySelection==i;preset.disabled=!supported;
  }
  Btn apply=button("APPLY",x,35,width,22,this::applyDisplay);apply.gold=true;apply.disabled=displayFullscreen?displayMode(displaySelection)==null:!windowFits(displaySelection,desktop.width,desktop.height);
  root.addActor(new Painter((b,a)->text(b,"Current: "+Gdx.graphics.getWidth()+" x "+Gdx.graphics.getHeight()+" · "+(Gdx.graphics.isFullscreen()?"fullscreen":"windowed")+" · saved for next start",12,22,5.6f,DIM,a,W()-24,Align.center,false)));
 }
 // Three distinct header bands; button positions are shared with the layout regression check.
 static float[] selectHeaderButton(float width,int index){float gap=4,w=(width-24-3*gap)/4;return new float[]{12+index*(w+gap),w};}
 private void selectHeader(Profile p){
  root.addActor(new Painter((b,a)->{
   text(b,"ROGUE COMMANDERS",12,H()-6,12,GOLD,a,W()-24,Align.left,true);
   String status="General unlocks "+p.general.size()+"/"+content().unlocks.length+"   ·   Playtime "+hours(p.activeMs)+"   ·   Gold "+p.goldBalance;
   text(b,status,12,H()-24,5.5f,DIM,a,W()-24,Align.left,false);bar(b,12,H()-36,W()-24,3,generalShare(),GOLD,a);
  }));
  String[] labels={"Achievements","Collection","Display","Back"};
  Runnable[] actions={()->{achievementScreen=true;rebuild();},()->showCollection(cmd(p.selected)),this::openDisplay,this::back};
  for(int i=0;i<labels.length;i++){float[] position=selectHeaderButton(W(),i);button(labels[i],position[0],H()-57,position[1],16,actions[i]);}
 }

 private static boolean restoredDisplay;
 private static void restoreSavedDisplay(){
  if(restoredDisplay||Gdx.graphics==null||!Gdx.graphics.supportsDisplayModeChange())return;restoredDisplay=true;
  var settings=Config.instance().getSettingData();int index=displayIndex(settings.width,settings.height);
  // Forge's launcher starts fullscreen at desktop resolution; restore the explicitly saved preset once Rogue is ready.
  if(settings.fullScreen&&index>=0&&(Gdx.graphics.getWidth()!=settings.width||Gdx.graphics.getHeight()!=settings.height))Gdx.app.postRunnable(()->{try{var mode=displayMode(index);if(mode!=null)Gdx.graphics.setFullscreenMode(mode);}catch(Exception e){log(e);}});
 }

 // ------------------------------------------------------------------ commander select
 private void buildSelect(Profile p){
  Cmd[] all=content().commanders;if(all.length==0){background(null,.6f);title("Rogue Commanders","content.json is missing");button("Back",W()-62,H()-22,52,16,this::back);return;}
  Cmd sel=cmd(p.selected);if(sel==null){sel=all[0];p.selected=sel.name;}
  final Cmd c=sel;
  background(c.name,.55f);
  selectHeader(p);
  float W=W(),H=H();boolean portrait=H>W;
  float cw=portrait?96:96,ch=cw/.716f,cx=12,cy=H-68-ch;
  CardView big=new CardView(c.name,cx,cy,cw,ch);big.selected=true;root.addActor(big);
  float ix=portrait?cx:cx+cw+12,iw=portrait?W-24:W-ix-12,iy=portrait?cy-6:H-68;
  List<String> unl=unlockedFor(c);int sig=p.signature.getOrDefault(c.name,List.of()).size();
  int best=p.best.getOrDefault(c.name,0);List<String> deckNow=loadout(c);long built=unl.stream().filter(deckNow::contains).count();
  root.addActor(new Painter((b,a)->{
   float top=portrait?iy:iy;float px=portrait?ix:ix,pw=iw;
   if(!portrait){Color o=b.getColor().cpy();b.setColor(1,1,1,a*.92f);panel().draw(b,px-4,cy-2,pw+8,top-cy+6);b.setColor(o);}
   float y=top-4;
   y-=text(b,c.name,px+4,y,11,GOLD,a,pw-8,Align.left,true)+4;
   pips(b,c.colors,px+4,y-8,4.2f,a);y-=14;
   y-=text(b,c.blurb,px+4,y,6.2f,TEXT,a,pw-8,Align.left,false)+5;
   if(!commanderOpen(p,c)){text(b,commanderRequirement(p,c),px+4,y,6,GOLD,a,pw-8,Align.left,false);return;}
   StringBuilder th=new StringBuilder();for(Theme t:c.themes){if(th.length()>0)th.append("  ·  ");th.append(t.name);}
   y-=text(b,"Themes: "+th,px+4,y,6,DIM,a,pw-8,Align.left,false)+6;
   text(b,"Signature unlocks  "+sig+"/"+c.specific.length,px+4,y,6,TEXT,a,pw-8,Align.left,false);
   bar(b,px+pw*.48f,y-5.5f,pw*.48f,5,c.specific.length==0?0:sig/(float)c.specific.length,tierColor("signatur"),a);y-=11;
   text(b,"Best run  "+wins(best)+"   ·   Deck: "+unl.stream().filter(loadout(c)::contains).count()+" unlocks added",px+4,y,6,TEXT,a,pw-8,Align.left,false);
  }));
  // roster strip
  int n=all.length;float gap=3,tw=portrait?Math.min(26,(W-24-(n-1)*gap)/n):Math.min(30,(W-150-(n-1)*gap)/n),th=tw/.716f;
  float sx=12,sy=8;
  for(int i=0;i<n;i++){Cmd o=all[i];CardView v=new CardView(o.name,sx+i*(tw+gap),sy,tw,th);v.selected=o==c;
   if(!commanderOpen(p,o)){v.tag="Locked";v.dim=true;v.border=DIM;}else if(o.bonus){v.tag="Bonus";v.border=Color.valueOf("ffb43c");}
   v.action=()->{profile().selected=o.name;save();rebuild();voice(o.name,"select");};root.addActor(v);}
  float bx=portrait?12:W-128,bw=portrait?(W-30)/2:116;
  if(portrait){button("Prepare deck",12,sy+th+6,bw,18,()->openLoadoutEditor(c));Btn s=button("START RUN",18+bw,sy+th+6,bw,18,()->{packageConfig=c;packagePage=0;rebuild();});s.gold=true;s.disabled=!commanderOpen(p,c);}
  else{button("Prepare deck",bx,sy+26,bw,18,()->openLoadoutEditor(c));Btn s=button("START RUN",bx,sy,bw,22,()->{packageConfig=c;packagePage=0;rebuild();});s.gold=true;s.disabled=!commanderOpen(p,c);}
  int available=(int)skins(c.name).stream().filter(s->skinUnlocked(p,c.name,s)).count();
  if(skins(c.name).size()>1)button("Artwork "+available+"/"+skins(c.name).size(),portrait?12:bx,portrait?sy+th+30:sy+50,portrait?112:bw,16,()->{wardrobe=c;rebuild();});
  if(p.cursesUnlocked){Btn cb=button("Curses: "+(p.cursesOn?"On":"Off"),portrait?130:bx,portrait?sy+th+50:sy+70,portrait?112:bw,16,()->{profile().cursesOn=!profile().cursesOn;save();rebuild();});cb.gold=p.cursesOn;}

 }

 // ------------------------------------------------------------------ commander illustration collection
 private void buildSkins(Cmd c){
  Profile p=profile();List<Skin> list=skins(c.name);background(c.name,.7f);
  title("Artwork",null);
  root.addActor(new Painter((b,a)->text(b,c.name+"  ·  Best run: "+wins(p.best.getOrDefault(c.name,0)),12,H()-30,6,DIM,a,W()-24,Align.left,false)));
  button("Back",W()-66,H()-22,56,16,this::back);
  float gap=12,cw=Math.min(95,Math.min((W()-28-(list.size()-1)*gap)/Math.max(1,list.size()),(H()-115)*.716f)),ch=cw/.716f;
  float sx=(W()-(list.size()*cw+(list.size()-1)*gap))/2,cy=H()-48-ch;
  Skin equipped=selectedSkin(p,c.name);
  for(int i=0;i<list.size();i++){
   final Skin skin=list.get(i);float x=sx+i*(cw+gap);boolean unlocked=skinUnlocked(p,c.name,skin),active=skin==equipped;
   CardView v=new CardView(c.name,x,cy,cw,ch);v.illustration=skinPaper(c.name,skin);v.selected=active;
   if(skin.custom()){v.customArt=skin;v.illustration=null;}
   v.border=unlocked?GOLD:DIM;v.tag=skin.custom()?"Fan-Art":skin.wins==0&&i==0?"Standard":skin.edition+" #"+skin.number;root.addActor(v);
   root.addActor(new Painter((b,a)->{
    float h=text(b,skin.artist,x,cy-6,5.5f,TEXT,a,cw,Align.center,false);
    text(b,unlocked?active?"Equipped":"Unlocked":wins(skin.wins)+" in one run",x,cy-9-h,5.5f,unlocked?GREEN:DIM,a,cw,Align.center,false);
   }));
   Btn equip=button(active?"Active":unlocked?"Equip":"Locked",x,30,cw,18,()->{
    if(!skinUnlocked(profile(),c.name,skin))return;
    profile().commanderArt.put(c.name,skin.id());save();rebuild();
   });equip.disabled=!unlocked||active;equip.gold=active;
  }
  root.addActor(new Painter((b,a)->text(b,list.size()==1?"Only the default artwork is currently available for this commander.":"3 / 6 wins with this commander in one run. Tap artwork to preview.",12,20,5.5f,DIM,a,W()-24,Align.center,false)));
 }

 // ------------------------------------------------------------------ pre-run deck (permanent unlocks)
 private void openLoadoutEditor(Cmd c){
  List<String> main=loadout(c);Deck d=deckOf(c.name,main,c.name+" (Rogue)");
  List<String> pool=unlockedFor(c);pool.removeAll(main);
  for(String n:pool)d.getOrCreate(DeckSection.Sideboard).add(n,1);
  AdventureEventData ev=new AdventureEventData(new Random().nextLong(),AdventureEventController.EventFormat.Sealed,null,null);
  ev.registeredDeck=d;ev.eventStatus=AdventureEventController.EventStatus.Ready;
  editing=ev;editingCommander=c.name;
  if(pool.isEmpty())toast("No unlocks yet for "+c.name.split(",")[0]+" – play runs to unlock cards.");
  DeckEditScene.getInstance(getUIBackground()).loadEvent(ev);Forge.switchScene(DeckEditScene.getInstance(getUIBackground()));
 }
 /** run deck in Forge's deck editor: main deck = run deck, sideboard = the bench (cards from packages and cards taken out) */
 private void openRunEditor(){
  Run r=profile().run;if(r==null)return;Cmd c=cmd(r.commander);if(c==null)return;
  Deck d=deckOf(r.commander,r.deck,r.commander.split(",")[0]+" (run deck)");
  for(String n:bench(r))if(!r.deck.contains(n))d.getOrCreate(DeckSection.Sideboard).add(n,1);
  AdventureEventData ev=new AdventureEventData(new Random().nextLong(),AdventureEventController.EventFormat.Sealed,null,null);
  ev.registeredDeck=d;ev.eventStatus=AdventureEventController.EventStatus.Ready;
  editing=ev;editingCommander=r.commander;editingRun=true;
  toast(bench(r).isEmpty()?"Cards you take out go to the bench (sideboard) and can come back later.":bench(r).size()+" card(s) on the bench (sideboard).");
  DeckEditScene.getInstance(getUIBackground()).loadEvent(ev);Forge.switchScene(DeckEditScene.getInstance(getUIBackground()));
 }
 private void finishRunEditing(Cmd c,Deck d){
  Run r=profile().run;if(r==null)return;
  Set<String> pool=new LinkedHashSet<>(r.deck);pool.addAll(bench(r));
  List<String> main=new ArrayList<>();
  for(var e:d.getMain())for(int i=0;i<e.getValue();i++){String n=e.getKey().getName();if(!n.equals(c.name)&&(pool.contains(n)||isBasic(n)))main.add(n);}
  int before=main.size();List<String> fixed=normalize(c,main);
  List<String> bench=new ArrayList<>();for(String n:pool)if(!isBasic(n)&&!fixed.contains(n))bench.add(n);
  r.deck=fixed;r.bench=bench;save();
  toast(before>MAX_DECK?"Deck had "+before+" cards - the "+(before-MAX_DECK)+" weakest went to the bench (max 99)."
   :before<DECK?(DECK-before)+" basic land(s) added to reach the minimum of 59 cards.":"Run deck saved: "+fixed.size()+" cards + commander.");
 }
 private void finishEditing(){
  Cmd c=cmd(editingCommander);Deck d=editing.registeredDeck;editing=null;if(c==null||d==null)return;
  if(editingRun){editingRun=false;finishRunEditing(c,d);return;}
  Set<String> allowed=new HashSet<>(defaultDeck(c));allowed.addAll(unlockedFor(c));
  List<String> main=new ArrayList<>();
  for(var e:d.getMain())for(int i=0;i<e.getValue();i++){String n=e.getKey().getName();if(allowed.contains(n)||isBasic(n))main.add(n);}
  int before=main.size();List<String> fixed=normalize(c,main);
  profile().loadout.put(c.name,fixed);save();
  toast(before>MAX_DECK?"Deck had "+before+" cards - the weakest were removed (max 99).":before<DECK?"Deck filled with basic lands to the minimum of 59 ("+(DECK-before)+" added).":"Deck saved: "+fixed.size()+" cards + commander.");
 }

 // ------------------------------------------------------------------ run
 private void startRun(Cmd c){
  if(!commanderOpen(profile(),c)){toast(commanderRequirement(profile(),c));return;}
  Run r=new Run();r.commander=c.name;r.seed=new Random().nextLong();r.deck=normalize(c,loadout(c));
  r.chosenPackages=packageSelection(profile(),c);r.selectedPackages=runPackages(profile(),c,r.chosenPackages,r.seed);initRunEconomy(r);shopOpen=false;
  r.cursed=profile().cursesUnlocked&&profile().cursesOn;
  // every run swaps up to eight starter cards for other cards this commander's players like, so early rounds differ
  if(c.alts!=null&&c.alts.length>0){
   Random rv=new Random(r.seed^0xa17L);Set<String> starter=new HashSet<>(Arrays.asList(c.starter));starter.removeAll(List.of("Sol Ring","Arcane Signet"));if(c.protectedStarter!=null)starter.removeAll(Arrays.asList(c.protectedStarter));
   List<String> swappable=new ArrayList<>();for(String n:r.deck)if(starter.contains(n))swappable.add(n);
   List<String> alts=new ArrayList<>(Arrays.asList(c.alts));alts.removeAll(r.deck);Collections.shuffle(alts,rv);Collections.shuffle(swappable,rv);
   for(int i=0;i<Math.min(8,Math.min(alts.size(),swappable.size()));i++){if(paper(alts.get(i))==null)continue;r.deck.remove(swappable.get(i));r.deck.add(alts.get(i));}
  }
  {List<String> unl=unlockedFor(c);for(String n:r.deck)if(unl.contains(n))r.heirlooms++;}r.opponent=pickOpponent(r);
  profile().run=r;save();rebuild();voice(c.name,"run_start");
 }
 private void buildRun(Run r){
  Cmd c=cmd(r.commander);background(r.commander,.6f);
  title(r.commander.split(",")[0]+"  ·  Round "+r.round+" / "+MAX_ROUNDS,"Wins "+r.wins+"   ·   Glory "+r.ruhm+"   ·   one loss ends the run");
  float W=W(),H=H();float cw=100,ch=cw/.716f;
  CardView big=new CardView(r.commander,12,H-38-ch,cw,ch);root.addActor(big);
  EnemyData e=WorldData.getEnemy(r.opponent);boolean boss=r.round%5==0;
  TextureRegion avatar=null;try{if(e!=null)avatar=new EnemySprite(e).getAvatar();}catch(Exception ignored){}
  final TextureRegion av=avatar;
  float px=124,pw=W-136,py=H-38;
  root.addActor(new Painter((b,a)->{
   String story=intro(e!=null?e.getName():r.opponent);
   Color o=b.getColor().cpy();b.setColor(1,1,1,a*.94f);panel().draw(b,px,py-164,pw,164);b.setColor(o);
   if(story!=null)text(b,story,px+10,py-96,6,TEXT,a*.92f,pw-20,Align.left,false);
   text(b,boss?"BOSS ROUND":"Next opponent",px+8,py-6,6.5f,boss?RED:DIM,a,pw-16,Align.left,true);
   if(av!=null){rect(b,px+8,py-90,72,72,Color.BLACK,a*.5f);b.setColor(1,1,1,a);b.draw(av,px+10,py-88,68,68);b.setColor(o);}
   float tx=px+90;
   text(b,e!=null?e.getName():r.opponent,tx,py-18,11,GOLD,a,pw-100,Align.left,true);
   text(b,"Starting life "+enemyLife(r.round)+"   ·   Win grants +"+r.round+" Glory",tx,py-36,6.2f,TEXT,a,pw-100,Align.left,false);
   List<String> bn=boons(r);
   text(b,!bn.isEmpty()?"Starts with: "+String.join(", ",bn):boss?"Every fifth round has a boss with extra life.":r.round>=7?"From round 10, opponents start with bonus cards.":"Opponents get stronger each round.",tx,py-48,5.8f,bn.isEmpty()?DIM:Color.valueOf("ffb43c"),a,pw-100,Align.left,false);
   float y=py-172;text(b,"Packages this run:",px,y,6,DIM,a,pw,Align.left,false);
   String list=r.picked.isEmpty()?"–":r.picked.stream().map(RogueCommandersScene::pickedLabel).collect(java.util.stream.Collectors.joining("   ·   "));text(b,list,px,y-9,6,TEXT,a,pw,Align.left,false);
   if(!r.curses.isEmpty()){List<String> names=new ArrayList<>();for(String id:r.curses){Curse cu=curse(id);if(cu!=null)names.add(displayText(cu.name()));}
    text(b,"Curses: "+String.join("  ·  ",names),px,y-20,6,RED,a,pw,Align.left,false);}
  }));
  Btn fight=button("FIGHT",W-130,10,118,24,this::startRound);fight.gold=true;
  button("Edit deck",W-252,10,116,24,()->showRunDeck(r));
  button("Shop · "+profile().goldBalance+" gold",W-252,38,240,18,()->{shopOpen=true;rebuild();});
  button("Abandon run",12,10,90,18,()->confirm("Abandon run?","The run ends with "+r.wins+(r.wins==1?" win":" wins")+" and "+r.ruhm+" Glory. Unlocks and points are awarded as for a loss.","Abandon","Keep playing",this::endRun));
 }
 // from round 10 opponents start with helpful permanents on the battlefield (like Adventure's enemy effects)
 static final String[] BOON_LIGHT={"Mind Stone","Bonesplitter","Loxodon Warhammer","Heart-Piercer Bow","The Blackstaff of Waterdeep","Fervor","Glorious Anthem"};
 static final String[] BOON_MID={"Sol Ring","Thran Dynamo","Staff of Nin","Ghostly Prison","Propaganda","Phyrexian Arena","Leyline of Sanctity","Mind's Eye"};
 static final String[] BOON_STRONG={"Sword of Fire and Ice","Mirari's Wake","Smothering Tithe","Rhystic Study","Hedron Archive"};
 static final Map<Character,String> MAGUS_STAFF=Map.of('W',"Staff of the Sun Magus",'U',"Staff of the Mind Magus",'B',"Staff of the Death Magus",
  'R',"Staff of the Flame Magus",'G',"Staff of the Wild Magus");
 /** bosses (every fifth round) start with the Magus staff of their colour(s): life for every spell/land of that colour;
  *  from round 10 opponents also get boon permanents */
 static List<String> boons(Run r){
  List<String> out=new ArrayList<>();
  if(r.round%5==0){
   EnemyData e=WorldData.getEnemy(r.opponent);String colors=e==null||e.colors==null?"":e.colors;
   for(char ch:colors.toCharArray()){String staff=MAGUS_STAFF.get(Character.toUpperCase(ch));if(staff!=null&&!out.contains(staff)&&out.size()<2)out.add(staff);}
  }
  if(r.round<10)return out;
  // Guaranteed extra starting permanent for the level-10 boss, not a random deck draw.
  // Add before the random pools so Mind's Eye can never be duplicated as a boon.
  if(r.round==10)out.add("Mind's Eye");
  Random rnd=new Random(r.seed*131+r.round);
  String[][] plan=r.round>=MAX_ROUNDS?new String[][]{BOON_STRONG,BOON_MID,BOON_MID}:r.round>=13?new String[][]{BOON_MID,BOON_LIGHT}:new String[][]{rnd.nextBoolean()?BOON_MID:BOON_LIGHT};
  for(String[] pool:plan){
   List<String> left=new ArrayList<>();for(String n:pool)if(!out.contains(n)&&paper(n)!=null)left.add(n);
   if(!left.isEmpty())out.add(left.get(rnd.nextInt(left.size())));
  }
  return out;
 }
 // curses (after completing all 15 rounds once): first boss a minor curse, second boss a major one, chosen from three
 record Curse(String id,String name,String desc,boolean major,int playerLife,int playerCards,int enemyLife,String card){}
 static final List<Curse> CURSES=List.of(
  new Curse("thin_hand","Dünne Hand","Du startest jedes Duell mit einer Karte weniger.",false,0,-1,0,null),
  new Curse("tough_foes","Zähe Gegner","Gegner starten mit 8 Leben mehr.",false,0,0,8,null),
  new Curse("consuls","Lähmende Präsenz","Gegner starten mit Authority of the Consuls: deine Kreaturen kommen getappt ins Spiel.",false,0,0,0,"Authority of the Consuls"),
  new Curse("tainted","Verdorbene Heilung","Gegner starten mit Tainted Remedy: dein Lebensgewinn wird zu Lebensverlust.",false,0,0,0,"Tainted Remedy"),
  new Curse("thorn","Dornenlast","Gegner starten mit Thorn of Amethyst: Nichtkreatur-Zauber kosten 1 mehr.",false,0,0,0,"Thorn of Amethyst"),
  new Curse("mine","Geteilte Gaben","Gegner starten mit Howling Mine: jeder zieht eine Karte mehr.",false,0,0,0,"Howling Mine"),
  new Curse("half_life","Halbes Leben","Du startest jedes Duell mit halbem Leben.",true,-LIFE/2,0,0,null),
  new Curse("betrayal","Seelenverrat","Gegner starten mit Night of Souls' Betrayal: alle Kreaturen erhalten -1/-1.",true,0,0,0,"Night of Souls' Betrayal"),
  new Curse("vortex","Schwefelsturm","Gegner starten mit Sulfuric Vortex: 2 Schaden pro Zug, kein Lebensgewinn.",true,0,0,0,"Sulfuric Vortex"),
  new Curse("rest","Ewige Ruhe","Gegner starten mit Rest in Peace: Friedhöfe werden ins Exil gelegt.",true,0,0,0,"Rest in Peace"),
  new Curse("empty_hand","Leere Hände","Du startest jedes Duell mit zwei Karten weniger.",true,0,-2,0,null),
  new Curse("sphere","Sphäre des Widerstands","Gegner starten mit Sphere of Resistance: alle Zauber kosten 1 mehr.",true,0,0,0,"Sphere of Resistance"));
 static Curse curse(String id){for(Curse c:CURSES)if(c.id().equals(id))return c;return null;}
 static List<String> curseOffer(Run r,boolean major){
  List<String> pool=new ArrayList<>();for(Curse c:CURSES)if(c.major()==major&&!r.curses.contains(c.id()))pool.add(c.id());
  Collections.shuffle(pool,new Random(r.seed*71+r.round));return new ArrayList<>(pool.subList(0,Math.min(3,pool.size())));
 }
 private void buildCurse(Run r){
  background(r.commander,.78f);
  boolean major=r.round>10;
  title(major?"Major Curse":"Minor Curse","The boss is defeated, but curses you. Choose a curse that lasts until the run ends.");
  float W=W(),H=H();int n=r.curseOptions.size();float gap=10,pw=(W-24-gap*(n-1))/Math.max(1,n),ph=H-90,py=40;
  for(int i=0;i<n;i++){
   Curse c=curse(r.curseOptions.get(i));if(c==null)continue;float px=12+i*(pw+gap);
   root.addActor(new Painter((b,a)->{Color o=b.getColor().cpy();b.setColor(1,1,1,a*.95f);panel().draw(b,px,py,pw,ph);b.setColor(o);
    frame(b,px+2,py+2,pw-4,ph-4,.8f,RED,a*(.45f+.3f*(float)Math.sin(time*2.2f)));
    text(b,displayText(c.name()),px,py+ph-10,10,RED,a,pw,Align.center,true);
    text(b,c.major()?"major curse":"minor curse",px,py+ph-25,6,DIM,a,pw,Align.center,false);
    text(b,displayText(c.desc()),px+10,py+ph-42,6.6f,TEXT,a,pw-20,Align.center,false);}));
   if(c.card()!=null){float cw=Math.min(pw*.45f,58),ch=cw/.716f;CardView v=new CardView(c.card(),px+(pw-cw)/2,py+30,cw,ch);root.addActor(v);}
   Btn take=button("Accept",px+pw/2-40,py+8,80,17,()->chooseCurse(c));take.gold=true;
  }
 }
 private void chooseCurse(Curse c){
  Run r=profile().run;r.curses.add(c.id());r.curseOptions.clear();
  r.phase=r.offers.isEmpty()?"fight":"package";if(r.offers.isEmpty())r.opponent=pickOpponent(r);save();rebuild();
 }
 /** opponents' starting life rises from 15 (round 1) to 60 (round 15); bosses get 5 more */
 static int enemyLife(int round){return 15+Math.round(45f*(Math.max(1,round)-1)/(MAX_ROUNDS-1))+(round%5==0?5:0);}
 static int enemyBonus(int round){return enemyLife(round)-LIFE;}
 /** opponents get stronger with the round (by adventure life), every fifth round is a boss */
 static final java.util.regex.Pattern WEAK_FOES=java.util.regex.Pattern.compile(
  "(?i)apprentice|novice|initiate|acolyte|squire|recruit|peasant|farmer|villager|beggar|urchin|pickpocket|cutpurse|young|student|trainee|militia");
 static String pickOpponent(Run r){
  Random rnd=new Random(r.seed*31+r.round);
  List<EnemyData> normal=new ArrayList<>(),bosses=new ArrayList<>();
  var all=WorldData.getAllEnemies();
  for(int i=0;i<all.size;i++){EnemyData e=all.get(i);
   if(e==null||e.copyPlayerDeck||e.nextEnemy!=null||e.deck==null||e.deck.length==0)continue;
   if(e.boss){if(e.life<=40+r.round*3&&(r.round>=20||TOO_BIG.stream().noneMatch(n->e.getName().startsWith(n))))bosses.add(e);}
   else normal.add(e);
  }
  // only opponents with a flavor intro (enemy_intros.json) as long as that leaves enough of each kind
  intro("");
  if(intros!=null&&!intros.isEmpty()){
   List<EnemyData> n2=new ArrayList<>(),b2=new ArrayList<>();
   for(EnemyData e:normal)if(intros.containsKey(e.getName()))n2.add(e);
   for(EnemyData e:bosses)if(intros.containsKey(e.getName()))b2.add(e);
   if(n2.size()>=30)normal=n2;
   if(b2.size()>=3)bosses=b2;
  }
  normal.sort(Comparator.comparingInt(e->e.life));bosses.sort(Comparator.comparingInt(e->e.life));
  if(r.round%5==0&&!bosses.isEmpty()){int k=bosses.size();int center=Math.min(k-1,(r.round/5-1)*k/6);return bosses.get(Math.min(k-1,center+rnd.nextInt(Math.max(1,k/6)))).getName();}
  if(normal.isEmpty())return "";
  // after the first boss the opponents jump up: no apprentices, masters instead (user, 2026-10-15)
  if(r.round>5){
   List<EnemyData> veteran=new ArrayList<>();
   for(EnemyData e:normal)if(!WEAK_FOES.matcher(e.getName()).find())veteran.add(e);
   if(veteran.size()>=20)normal=veteran;
  }
  int k=normal.size();
  double q=r.round<=5?.4*(r.round-1)/4.0:Math.min(1,.55+.45*(r.round-6)/8.0);   // rounds 1-5: lower 40 %, 6-14: upper half
  int center=(int)(q*(k-1)),win=Math.max(1,k/12);
  return normal.get(Math.max(0,Math.min(k-1,center-win+rnd.nextInt(2*win+1)))).getName();
 }
 private void startRound(){
  Run r=profile().run;if(r==null)return;
  EnemyData data=WorldData.getEnemy(r.opponent);if(data==null){r.opponent=pickOpponent(r);data=WorldData.getEnemy(r.opponent);}
  if(data==null)data=WorldData.getAllEnemies().first();
  try{
   Constructor<AdventureEventData.AdventureEventParticipant> ctor=AdventureEventData.AdventureEventParticipant.class.getDeclaredConstructor();ctor.setAccessible(true);
   opponent=ctor.newInstance().generate(data);
  }catch(ReflectiveOperationException e){System.err.println("[Rogue] opponent: "+e);return;}
  opponent.setDeck(data.generateDeck(Current.player().isFantasyMode(),false));
  AdventureEventData event=new AdventureEventData(r.seed,AdventureEventController.EventFormat.Constructed,AdventureEventController.EventStyle.Bracket,null);
  event.eventRules=new AdventureEventData.AdventureEventRules(AdventureEventController.EventFormat.Constructed,AdventureEventData.PairingStyle.SingleElimination,1f);
  event.eventRules.gameType=GameType.Commander;event.eventRules.startingLife=LIFE;event.eventRules.gamesPerMatch=1;
  event.eventRules.allowsShards=false;event.eventRules.allowsItems=false;event.eventRules.allowsBlessings=true;   // blessings carry the enemy life bonus
  event.registeredDeck=deckOf(r.commander,r.deck,"Rogue - "+r.commander);event.rounds=Math.max(r.round,1);event.currentRound=r.round;event.nextOpponent=opponent;
  event.eventStatus=AdventureEventController.EventStatus.Started;
  EnemySprite enemy=opponent.getSprite();
  EffectData bonus=new EffectData();bonus.name="Rogue Level "+r.round;bonus.lifeModifier=enemyBonus(r.round);
  List<String> boons=boons(r);if(!boons.isEmpty())bonus.startBattleWithCard=boons.toArray(new String[0]);
  EffectData cursed=new EffectData();cursed.name="Curse";List<String> curseCards=new ArrayList<>();
  if(bonus.startBattleWithCard!=null)curseCards.addAll(Arrays.asList(bonus.startBattleWithCard));
  for(String id:r.curses){Curse c=curse(id);if(c==null)continue;
   bonus.lifeModifier+=c.enemyLife();cursed.lifeModifier+=c.playerLife();cursed.changeStartCards+=c.playerCards();
   if(c.card()!=null&&paper(c.card())!=null&&!curseCards.contains(c.card()))curseCards.add(c.card());}
  if(!curseCards.isEmpty())bonus.startBattleWithCard=curseCards.toArray(new String[0]);
  if(cursed.lifeModifier!=0||cursed.changeStartCards!=0)bonus.opponent=cursed;
  enemy.effect=bonus;
  if(r.savedBlessing==null)r.savedBlessing=Current.player().getBlessing();   // the Adventure blessing is kept for the overworld
  Current.player().clearBlessing();
  r.duelStart=System.currentTimeMillis();save();
  DuelScene duel=DuelScene.instance();
  showVersus(r,enemy,r.round%5==0,()->{
   TransitionScreen t=new TransitionScreen(()->{duel.initDuels(null,enemy,false,event);Forge.switchScene(duel);},ScreenUtil.getInstance().takeScreenshot(),false,false);
   FThreads.invokeInEdtNowOrLater(()->Forge.setTransitionScreen(t));
  });
 }
 /** for the match result screen (ModernWinLose): label while a Rogue duel runs, else null */
 public static String duelLabel(){Profile p=profile;if(p==null||p.run==null||!"fight".equals(p.run.phase)||p.run.duelStart<=0)return null;
  return "ROGUE COMMANDERS  ·  ROUND "+p.run.round+"/"+MAX_ROUNDS+(p.run.round%5==0?"  ·  BOSS":"");}
 /** the commander's card image (selected illustration) for the result screen */
 public static Texture duelArt(){Profile p=profile;if(p==null||p.run==null)return null;try{return cardTexture(p.run.commander);}catch(Throwable t){return null;}}
 @Override public void setWinner(boolean winner,boolean isArena){
  Run r=profile().run;if(r==null||!"fight".equals(r.phase))return;
  finishDuelAchievements(profile(),r,winner);
  if(r.savedBlessing!=null){try{Current.player().clearBlessing();Current.player().addBlessing(r.savedBlessing);}catch(Exception e){log(e);}r.savedBlessing=null;}
  if(r.duelStart>0){profile().activeMs+=Math.min(45*60_000L,Math.max(0,System.currentTimeMillis()-r.duelStart));r.duelStart=0;}
  lastInput=System.currentTimeMillis();
  if(winner){
   if(r.round%5==0)r.bosses++;
   try{matchStats(r);}catch(Throwable t){log(t);}
   voice(r.commander,"victory");
   int won=r.round;
   creditBossGold(profile(),r,r.round);
   r.wins++;r.ruhm+=r.round;r.round++;updateCommanderAchievements(profile());
   if(r.wins>=MAX_ROUNDS){r.completed=true;save();endRun();return;}r.phase="package";r.offers=makeOffers(r);if(r.offers.isEmpty()){r.phase="fight";r.opponent=pickOpponent(r);}
   if(r.cursed&&(won==5||won==10)){r.curseOptions=curseOffer(r,won==10);if(!r.curseOptions.isEmpty())r.phase="curse";}
   save();rebuild();}
  else{r.lost=true;voice(r.commander,"defeat");endRun();}
 }

 // ------------------------------------------------------------------ packages (run-only)
 /** reads the finished game for the score categories (life, turns, how the opponent lost, mulligans, ...) */
 static void matchStats(Run r){
  var gv=forge.screens.match.MatchController.instance.getGameView();if(gv==null)return;
  forge.game.Game game=gv.getGame();
  forge.game.player.Player me=null,opp=null;
  if(game!=null)for(forge.game.player.Player pl:game.getRegisteredPlayers()){if(pl.isAI())opp=pl;else me=pl;}
  int life=-1;for(var pv:gv.getPlayers())if(!pv.isAI())life=pv.getLife();
  if(life>=LIFE)r.perfect++;else if(life>0&&life<=5)r.close++;
  if(life>=50)r.lifeSource++;
  int turns=gv.getTurn();
  if(game!=null&&game.getOutcome()!=null&&game.getOutcome().getLastTurnNumber()>0)turns=game.getOutcome().getLastTurnNumber();
  if(turns<=14)r.fast++;
  if(turns>=40)r.marathon++;
  if(game!=null&&game.getOutcome()!=null&&game.getOutcome().getWinCondition()==forge.game.GameEndReason.WinsGameSpellEffect)r.altWin++;
  if(opp!=null){
   var out=opp.getOutcome();var loss=out==null?null:out.lossState;
   if(loss==forge.game.player.GameLossReason.CommanderDamage)r.cmdrKill++;
   else if(loss==forge.game.player.GameLossReason.Poisoned)r.poison++;
   else if(loss==forge.game.player.GameLossReason.Milled)r.mill++;
   else if(loss==forge.game.player.GameLossReason.SpellEffect)r.altWin++;
   if(opp.getLife()<=-10)r.overkill++;
  }
  if(me!=null){
   if(me.getStats()!=null&&me.getStats().getMulliganCount()>=2)r.mulligan++;
   if(me.getSpellsCastThisGame()>=20)r.storm++;
   if(me.getCreaturesInPlay().size()>=10)r.army++;
   if(me.getTotalCommanderCast()==0)r.noCommander++;
  }
 }
 static String rarity(int wins,Random r){
  float x=r.nextFloat();
  if(wins>=8&&x<.22f)return "Legendär";
  if(wins>=6)return x<.55f?"Episch":"Selten";
  if(wins>=3)return x<.2f?"Episch":x<.7f?"Selten":"Normal";
  return x<.25f?"Selten":"Normal";
 }
 static Set<String> avoid=new HashSet<>(),seenThisRun=new HashSet<>();
 /** variety: cards already offered in this run come back rarely, cards from the last runs less often */
 static double variety(String n){return seenThisRun.contains(n)?.12:avoid.contains(n)?.5:1;}
 static List<String> pick(List<CardRef> pool,Set<String> exclude,String colors,String rarity,Random r,int n){
  List<CardRef> c=new ArrayList<>();for(CardRef x:pool)if(!exclude.contains(x.n)&&fits(x.c,colors)&&paper(x.n)!=null)c.add(x);
  c.sort((a,b)->b.s-a.s);
  if("Episch".equals(rarity))c=c.subList(0,Math.min(c.size(),Math.max(n+2,c.size()*2/5)));
  if("Legendär".equals(rarity))c=c.subList(0,Math.min(c.size(),Math.max(n+1,c.size()/4)));
  double k="Normal".equals(rarity)?1:"Selten".equals(rarity)?2.5:3.5;
  List<String> out=new ArrayList<>();
  while(out.size()<n&&!c.isEmpty()){
   double sum=0;for(CardRef x:c)sum+=Math.pow(Math.max(1,x.s),k)*variety(x.n);
   double t=r.nextDouble()*sum;CardRef chosen=c.get(c.size()-1);
   for(CardRef x:c){t-=Math.pow(Math.max(1,x.s),k)*variety(x.n);if(t<=0){chosen=x;break;}}
   out.add(chosen.n);c.remove(chosen);
  }
  return out;
 }
 // Saved separately from package rarity: old runs default to Normal, including already-saved offers.
 static int rewardTier(Run run){return Math.max(0,Math.min(3,run.rewardTier));}
 static String rewardLabel(int tier){return switch(tier){case 1->"Rare";case 2->"Epic";case 3->"Legendary";default->"Normal";};}
 static Color rewardColor(int tier){return Color.valueOf(switch(tier){case 1->"5aa7ff";case 2->"b07cf0";case 3->"ffb43c";default->"c8c8c8";});}
 /** reward level = wins so far: later rewards get better cards and guaranteed legendaries (3 at the round-14 reward) */
 static int guaranteedLegendaries(int level,int tier){
  if(level<10)return 0;                                   // legendaries only from round 10 on
  return Math.min(4,(level>=14?3:level>=12?2:1)+(tier==3?1:0));
 }
 /** highest tier at a reward level: Legendary only from round 10 on */
 static int maxTier(int level){return level>=10?3:2;}
 /** tier of the reward after winning round "level": skip upgrades, capped; the first boss (round 5) is at least Epic */
 static int offerTier(Run run){return tierAt(run.wins,rewardTier(run));}
 /** skip bonus after skipping the current reward; at the boss (round 5) it builds on the carried bonus, not on Epic */
 static int nextSkipTier(Run r){int base=r.wins==5?rewardTier(r):offerTier(r);return Math.min(maxTier(r.wins+1),base+1);}
 static int tierAt(int level,int skipTier){int t=Math.min(maxTier(level),skipTier);if(level==5)t=Math.max(t,2);return t;}
 /** share of each pool (best cards first) the package draws from: the smaller of the tier share and the round share */
 static double qualityShare(int level,int tier){
  double byTier=tier==1?.60:tier==2?.35:tier==3?.15:1.0,byLevel=1.0-.55*Math.max(0,Math.min(13,level-1))/13.0;
  return Math.min(byTier,byLevel);
 }
 static String rewardRule(int tier,int level){
  int legend=guaranteedLegendaries(level,tier),size=6,top=(int)Math.round(qualityShare(level,tier)*100);
  return size+" cards"+(top<100?" from the best "+top+"%":"")+(legend>0?"  -  "+legend+" guaranteed legendar"+(legend==1?"y":"ies"):"")
   +(level==5?".  Boss reward: always Epic, and taking it keeps your skip bonus for round 6."
     :".  Rewards get better every round; skipping upgrades the next one, taking resets it to Normal.");
 }
 static boolean rewardCompatible(CardRef card,Cmd commander){
  if(card.commanders!=null&&!Arrays.asList(card.commanders).contains(commander.slug))return false;
  if(card.needLandColors!=null){
   boolean match=false;
   for(char color:card.needLandColors.toCharArray())if(commander.colors.indexOf(color)>=0){match=true;break;}
   if(!match)return false;
  }
  return true;
 }
 static List<CardRef> rewardPool(List<CardRef> pool,Set<String> exclude,Cmd commander){
  Map<String,CardRef> unique=new LinkedHashMap<>();
  for(CardRef x:pool)if(x!=null&&!exclude.contains(x.n)&&fits(x.c,commander.colors)&&rewardCompatible(x,commander)&&paper(x.n)!=null){
   CardRef previous=unique.get(x.n);if(previous==null||x.s>previous.s)unique.put(x.n,x);
  }
  List<CardRef> out=new ArrayList<>(unique.values());out.sort((a,b)->Integer.compare(b.s,a.s));return out;
 }
 /** Score-weighted draw with the same recent-offer penalty as the original packages. */
 static CardRef drawReward(List<CardRef> pool,Random r){
  double sum=0;for(CardRef x:pool)sum+=Math.pow(Math.max(1,x.s),1.5)*variety(x.n);
  double t=r.nextDouble()*sum;int index=pool.size()-1;
  for(int i=0;i<pool.size();i++){CardRef x=pool.get(i);t-=Math.pow(Math.max(1,x.s),1.5)*variety(x.n);if(t<=0){index=i;break;}}
  return pool.remove(index);
 }
 /** Ranked eligible pool; widen the preferred slice only enough to fill a singleton package. */
 static List<String> pickReward(List<CardRef> ranked,double share,int strong,Random r,int count){
  int limit=Math.min(ranked.size(),Math.max(count,(int)Math.ceil(ranked.size()*share)));
  List<CardRef> candidates=new ArrayList<>(ranked.subList(0,limit));List<String> out=new ArrayList<>();
  strong=Math.min(count,strong);
  List<CardRef> best=new ArrayList<>(candidates.subList(0,Math.min(limit,Math.max(strong,(int)Math.ceil(ranked.size()*.15)))));
  while(out.size()<strong&&!best.isEmpty()){CardRef x=drawReward(best,r);out.add(x.n);candidates.remove(x);}
  while(out.size()<count&&!candidates.isEmpty())out.add(drawReward(candidates,r).n);
  return out;
 }
 static Offer scaledOffer(Object source,Cmd c,Run run,Set<String> used,int tier,int level,Random r){
  Offer of=new Offer();of.rarity=switch(tier){case 1->"Selten";case 2->"Episch";case 3->"Legendär";default->"Normal";};
  List<CardRef> pool;
  if(source instanceof Theme t){of.kind="Thema";of.name=t.name;pool=Arrays.asList(t.cards);}
  else{Generic g=(Generic)source;of.kind="Allgemein";of.name=g.name;pool=Arrays.asList(g.cards);}
  Set<String> exclude=new HashSet<>(used);int size=6;
  int legend=Math.min(size/2,guaranteedLegendaries(level,tier));double share=qualityShare(level,tier);
  int strong=tier==1?1:tier>=2?2:level>=8?1:0;
  List<CardRef> fallback=new ArrayList<>();
  for(Theme t:activeThemes(c,run))if(t.cards!=null)fallback.addAll(Arrays.asList(t.cards));
  if(c.specific!=null)fallback.addAll(Arrays.asList(c.specific));
  for(Generic g:content().generic)if(g.cards!=null)fallback.addAll(Arrays.asList(g.cards));
  if(legend>0){   // guaranteed legendaries: the legendary list, topped up with the strongest fitting non-land cards
   List<CardRef> bombs=rewardPool(Arrays.asList(content().legendary),exclude,c);
   if(bombs.size()<legend){List<CardRef> more=rewardPool(fallback,exclude,c);more.removeIf(x->isLand(x.n));
    Set<String> have=new HashSet<>();for(CardRef x:bombs)have.add(x.n);for(CardRef x:more)if(have.add(x.n))bombs.add(x);}
   for(String b:pickReward(bombs,.15,legend,r,legend)){of.cards.add(b);exclude.add(b);}
  }
  of.cards.addAll(pickReward(rewardPool(pool,exclude,c),share,strong,r,size-of.cards.size()));exclude.addAll(of.cards);
  // only themes are topped up, from this commander's own themes (no lands unless it is a land theme); a general package
  // stays on topic and is simply not offered when too few of its cards fit the colours
  if(of.cards.size()<size&&source instanceof Theme t){
   List<CardRef> own=new ArrayList<>();for(Theme x:activeThemes(c,run))if(x.cards!=null)own.addAll(Arrays.asList(x.cards));
   if(c.specific!=null)own.addAll(Arrays.asList(c.specific));
   List<CardRef> more=rewardPool(own,exclude,c);
   if(!t.name.toLowerCase().contains("land"))more.removeIf(x->isLand(x.n));
   of.cards.addAll(pickReward(more,share,strong,r,size-of.cards.size()));
  }
  return of;
 }
 static List<Offer> makeOffers(Run run){
  Cmd c=cmd(run.commander);List<Offer> out=new ArrayList<>();if(c==null)return out;
  Random r=new Random(run.seed*977+run.round*13);Set<String> used=new HashSet<>(run.deck);used.add(run.commander);used.addAll(bench(run));
  List<Theme> main=new ArrayList<>(),rareThemes=new ArrayList<>();
  for(Theme t:activeThemes(c,run)){if(t.cards==null||t.cards.length<6)continue;(t.rare?rareThemes:main).add(t);}
  Collections.shuffle(main,r);Collections.shuffle(rareThemes,r);
  // focus packages (e.g. Azula's combat tricks) come first in every reward until taken once, afterwards in every second one
  for(Theme t:new ArrayList<>(main))if(t.focus){
   boolean taken=run.picked!=null&&run.picked.stream().anyMatch(x->x.startsWith(t.name+" ("));
   if(!taken||r.nextBoolean()){main.remove(t);main.add(0,t);}
  }
  List<Theme> themes=new ArrayList<>(main);
  if(!rareThemes.isEmpty()&&(themes.size()<2||r.nextFloat()<.3f))themes.add(Math.min(1,themes.size()),rareThemes.get(0));
  if(run.chosenPackages!=null){   // 2 picks + 3 random: one pick is guaranteed, the other offers come from the rest of the run
   List<Theme> picks=new ArrayList<>(),rest=new ArrayList<>();
   for(Theme t:activeThemes(c,run)){if(t.cards==null||t.cards.length<6)continue;(run.chosenPackages.contains(t.key)?picks:rest).add(t);}
   Collections.shuffle(picks,r);Collections.shuffle(rest,r);
   themes=new ArrayList<>();if(!picks.isEmpty())themes.add(picks.get(0));
   themes.addAll(rest);if(picks.size()>1)themes.add(picks.get(1));
   if(themes.isEmpty())return out;
   avoid=new HashSet<>(profile().recentOffers==null?List.of():profile().recentOffers);
   seenThisRun=new HashSet<>(run.offered==null?List.of():run.offered);
   List<Object> plan=new ArrayList<>(themes);List<Generic> gens=new ArrayList<>(Arrays.asList(content().generic));Collections.shuffle(gens,r);
   plan.addAll(gens);   // generic packages only fill in when run packages are too thin
   for(Object o:plan){
    if(out.size()>=3)break;
    int tier=offerTier(run);
    Offer of=scaledOffer(o,c,run,used,tier,run.wins,r);
    int need=o instanceof Generic||tier>0?6:4;
    if(of.cards.size()>=need){used.addAll(of.cards);out.add(of);if(run.offered==null)run.offered=new ArrayList<>();run.offered.addAll(of.cards);}
   }
   return out;
  }
  if(themes.isEmpty())return out;
  avoid=new HashSet<>(profile().recentOffers==null?List.of():profile().recentOffers);
  seenThisRun=new HashSet<>(run.offered==null?List.of():run.offered);
  List<Generic> gens=new ArrayList<>(Arrays.asList(content().generic));Collections.shuffle(gens,r);
  List<Object> plan=new ArrayList<>();plan.add(themes.get(0));
  if(r.nextBoolean()&&themes.size()>1)plan.add(themes.get(1));else plan.add(gens.get(0));
  for(Generic g:gens)if(!plan.contains(g)){plan.add(g);break;}
  if(themes.size()>1&&!plan.contains(themes.get(1)))plan.add(themes.get(1));   // fallback if a generic pool is empty
  for(Generic g:gens)if(!plan.contains(g))plan.add(g);                           // more fallbacks: packages too thin in these colours are skipped
  for(Object o:plan){
   if(out.size()>=3)break;
   int tier=offerTier(run);
   Offer of=scaledOffer(o,c,run,used,tier,run.wins,r);   // better every round (run.wins = the round just won), skip tiers on top
   int need=o instanceof Generic||tier>0?6:4;   // every tier has 6 cards; higher tiers only get better cards
   if(of.cards.size()>=need){used.addAll(of.cards);out.add(of);if(run.offered==null)run.offered=new ArrayList<>();run.offered.addAll(of.cards);}
  }
  return out;
 }
 private void buildOffers(Run r){
  background(r.commander,.68f);
  int tier=offerTier(r);Color color=rewardColor(tier);
  root.addActor(new Painter((b,a)->{
   text(b,rewardLabel(tier).toUpperCase(Locale.ROOT)+" REWARD",13,H()-7,15,Color.BLACK,a*.6f,W()-120,Align.left,true);
   text(b,rewardLabel(tier).toUpperCase(Locale.ROOT)+" REWARD",12,H()-6,15,color,a,W()-120,Align.left,true);
   text(b,"Victory in round "+(r.round-1)+"! Choose one run-only package.",12,H()-24,6.5f,DIM,a,W()-24,Align.left,false);
   text(b,rewardRule(tier,r.wins),12,H()-34,5.8f,color,a,W()-24,Align.left,false);
  }));
  float W=W(),H=H();int n=r.offers.size();float gap=8,pw=(W-24-gap*(n-1))/Math.max(1,n),ph=H-86,py=40;
  for(int i=0;i<n;i++){
   Offer o=r.offers.get(i);float px=12+i*(pw+gap);Color rc=color;
   root.addActor(new Painter((b,a)->{Color old=b.getColor().cpy();b.setColor(1,1,1,a*.95f);panel().draw(b,px,py,pw,ph);b.setColor(old);
    frame(b,px+2,py+2,pw-4,ph-4,.8f,rc,a*(.5f+.3f*(float)Math.sin(time*2.5f)));
    text(b,"Legendär".equals(o.kind)?displayText(o.name):o.name,px,py+ph-6,9,GOLD,a,pw,Align.center,true);
    text(b,rewardLabel(tier).toUpperCase(Locale.ROOT)+"  ·  "+o.cards.size()+" CARDS",px,py+ph-18,6,rc,a,pw,Align.center,true);
    text(b,(tier==0?displayText(o.rarity)+" · ":"")+displayText(o.kind),px,py+ph-27,5.5f,DIM,a,pw,Align.center,false);}));
   int rows=(o.cards.size()+2)/3;
   float cw=Math.min((pw-16)/3,(ph-64-(rows-1)*4)/rows*.716f),chh=cw/.716f;
   float gx=px+(pw-(cw*3+8))/2,gy=py+26;
   for(int k=0;k<o.cards.size();k++){int col=k%3,row=k/3;CardView v=new CardView(o.cards.get(k),gx+col*(cw+4),gy+(rows-1-row)*(chh+4),cw,chh);root.addActor(v);}
   Btn pickB=button("Choose",px+pw/2-36,py+6,72,16,()->choose(o));pickB.gold=true;
  }
  button("Edit deck",W-100,H-21,90,15,()->showRunDeck(r));
  String skipText=r.wins+1==5?"Skip  -  boss reward stays Epic, round 6: "+rewardLabel(nextSkipTier(r))
   :"Skip  -  next reward: "+rewardLabel(tierAt(r.wins+1,nextSkipTier(r)));
  float sw=Math.min(W-24,r.wins+1==5?300:240);button(skipText,(W-sw)/2,12,sw,19,this::skipReward);
 }
 private void skipReward(){
  Run r=profile().run;if(r==null||!"package".equals(r.phase))return;
  r.rewardTier=nextSkipTier(r);r.skippedRewards++;
  r.offers.clear();r.phase="fight";r.opponent=pickOpponent(r);save();rebuild();
 }
 private void choose(Offer o){
  Run r=profile().run;if(r==null||!"package".equals(r.phase)||!r.offers.contains(o))return;Cmd c=cmd(r.commander);
  // package cards join the deck (59-99 cards are legal); unwanted cards go to the bench with "Edit deck"
  int addedNow=0;
  for(String card:o.cards){
   if(r.deck.contains(card))continue;
   if(r.deck.size()>=MAX_DECK){if(!bench(r).contains(card))bench(r).add(card);continue;}
   r.deck.add(card);bench(r).remove(card);addedNow++;if(r.added==null)r.added=new ArrayList<>();r.added.add(card);
  }
  toast(addedNow+" card(s) added - deck now "+r.deck.size()+". Use \"Edit deck\" to bench cards you do not want.");
  voice(r.commander,"package");
  if("Legendär".equals(o.rarity))r.legendary++;else if("Episch".equals(o.rarity))r.epic++;
  r.lastPackage=new ArrayList<>(o.cards);   // "Last package" sort in the deck editor shows these first
  r.picked.add(o.name+" ("+o.rarity+")");
  if(r.wins!=5)r.rewardTier=0;   // the fixed boss reward keeps the skip bonus
  r.offers.clear();r.phase="fight";r.opponent=pickOpponent(r);save();rebuild();
 }

 // ------------------------------------------------------------------ end of run: permanent unlocks
 private void endRun(){
  Profile p=profile();Run r=p.run;if(r==null)return;
  Cmd c=cmd(r.commander);Result res=new Result();res.commander=r.commander;res.wins=r.wins;res.ruhm=r.ruhm;
  double hours=p.activeMs/3_600_000.0,target=TARGET_SHARE*Math.pow(Math.min(1,hours/TARGET_HOURS),1.4),actual=generalShare();
  double pace=hours<.5?1:Math.max(.75,Math.min(1.33,(target+.03)/(actual+.03)));
  double points=p.carry+r.ruhm*pace;int count=(int)Math.floor(points/COST);p.carry=points-count*COST;
  Random rnd=new Random(r.seed^0x5eedL);
  for(int i=0;i<count;i++){
   p.counter++;
   if(p.counter%4==0&&c!=null){                     // every fourth unlock: a signature card of this commander
    List<String> have=p.signature.computeIfAbsent(c.name,k->new ArrayList<>());
    List<String> left=new ArrayList<>();for(CardRef x:c.specific)if(!have.contains(x.n))left.add(x.n);
    if(!left.isEmpty()){String s=left.get(rnd.nextInt(left.size()));have.add(s);award(p,"signature:"+c.name+":"+s);res.signature.add(s);continue;}
   }
   String g=generalUnlock(c,r.wins,rnd);if(g==null)break;p.general.add(g);award(p,"card:"+g);res.unlocked.add(g);
  }
  int beforeProgression=p.progressionPoints;progressionAfterRun(p,r);res.progressionEarned=p.progressionPoints-beforeProgression;
  scoreRun(p,r,res);
  int previousBest=p.best.getOrDefault(r.commander,0);
  if(r.completed)p.cursesUnlocked=true;
  if(r.offered!=null){p.recentOffers=new ArrayList<>(r.offered.subList(Math.max(0,r.offered.size()-300),r.offered.size()));}
  p.runs++;p.totalWins+=r.wins;p.best.merge(r.commander,r.wins,Math::max);updateCommanderAchievements(p);
  for(Skin s:skins(r.commander))if(s.wins>previousBest&&s.wins<=r.wins)res.skins.add(s.artist+" ("+s.edition+" #"+s.number+")");
  p.run=null;p.result=res;save();rebuild();
  if(res.record||r.completed)voice(r.commander,"record");
 }
 /** better tiers for deeper runs; mostly cards the played commander can use */
 String generalUnlock(Cmd c,int wins,Random rnd){
  int[] w=wins<=2?new int[]{65,30,5}:wins<=5?new int[]{50,35,15}:wins<=8?new int[]{35,40,25}:new int[]{25,40,35};
  float x=rnd.nextFloat()*100;String tier=x<w[0]?"mittel":x<w[0]+w[1]?"gut":"stark";
  boolean own=c!=null&&rnd.nextFloat()<.7f;
  for(int attempt=0;attempt<4;attempt++){
   List<String> cand=new ArrayList<>();
   for(CardRef r:content().unlocks){
    if(profile().general.contains(r.n))continue;
    if(attempt<2&&!tier.equals(r.tier))continue;
    if((attempt==0||attempt==2)&&own&&!fits(r.c,c.colors))continue;
    cand.add(r.n);
   }
   if(!cand.isEmpty())return cand.get(rnd.nextInt(cand.size()));
  }
  return null;
 }
 /** Slay the Spire style score: every category is listed with its points, the total gets a rank S-D */
 static void scoreRun(Profile p,Run r,Result res){
  if(p.bestScore==null)p.bestScore=new HashMap<>();
  List<ScoreLine> l=res.lines;
  line(l,"Runden gewonnen",r.wins,10,true);
  if(r.completed)l.add(new ScoreLine("Run abgeschlossen ("+MAX_ROUNDS+" Runden)","",150));
  for(String id:r.curses){Curse c=curse(id);if(c!=null)l.add(new ScoreLine("Fluch: "+c.name(),c.major()?"groß":"klein",c.major()?80:40));}
  if(r.completed&&!r.curses.isEmpty())l.add(new ScoreLine("Verfluchter Sieg","",100));
  line(l,"Bosse besiegt",r.bosses,40,false);
  line(l,"Makellos (volles Leben)",r.perfect,15,false);
  line(l,"Lebensquelle (50+ Leben)",r.lifeSource,10,false);
  line(l,"Blitzsieg (bis Zug 7)",r.fast,15,false);
  line(l,"Marathon (20+ Züge)",r.marathon,10,false);
  line(l,"Am seidenen Faden (≤ 5 Leben)",r.close,10,false);
  line(l,"Commander-Schaden-Sieg",r.cmdrKill,25,false);
  line(l,"Giftsieg",r.poison,25,false);
  line(l,"Mill-Sieg",r.mill,25,false);
  line(l,"Alternativer Sieg",r.altWin,30,false);
  line(l,"Überwältigend (−10 Leben)",r.overkill,10,false);
  line(l,"Gegen alle Widrigkeiten (2+ Mulligans)",r.mulligan,15,false);
  line(l,"Zaubersturm (20+ Zauber)",r.storm,10,false);
  line(l,"Armee (10+ Kreaturen)",r.army,10,false);
  line(l,"Ohne Commander gewonnen",r.noCommander,15,false);
  line(l,"Legendäre Pakete",r.legendary,20,false);
  line(l,"Epische Pakete",r.epic,8,false);
  line(l,"Patience: skipped rewards",r.skippedRewards,3,false);
  Map<String,Integer> themes=new HashMap<>();for(String pk:r.picked)themes.merge(pk.replaceAll(" \\(.*\\)$",""),1,Integer::sum);
  int most=themes.values().stream().max(Integer::compare).orElse(0);
  if(most>=3)l.add(new ScoreLine("Spezialist ("+most+"× gleiches Paket)","",20));
  if(themes.size()>=4)l.add(new ScoreLine("Vielseitig ("+themes.size()+" Paketarten)","",15));
  line(l,"Erbstücke im Startdeck",r.heirlooms,2,false);
  if(r.wins>p.best.getOrDefault(r.commander,0)&&r.wins>0)l.add(new ScoreLine("Meilenstein (neuer Siegrekord)","",25));
  if(r.wins>1)l.add(new ScoreLine("Tiefenbonus",r.wins+"²",r.wins*r.wins));
  int total=0;for(ScoreLine x:l)total+=x.points;
  res.score=total;res.rank=total>=800?"S":total>=450?"A":total>=220?"B":total>=90?"C":"D";
  res.previousBest=p.bestScore.getOrDefault(r.commander,0);res.record=total>res.previousBest&&total>0;
  p.bestScore.merge(r.commander,total,Math::max);p.bestScoreAll=Math.max(p.bestScoreAll,total);
 }
 static void line(List<ScoreLine> l,String label,int count,int each,boolean always){if(count>0||always)l.add(new ScoreLine(label,count+" × "+each,count*each));}
 static Color rankColor(String r){return switch(r==null?"":r){case "S"->Color.valueOf("ffb43c");case "A"->Color.valueOf("c77dff");case "B"->Color.valueOf("5aa7ff");case "C"->Color.valueOf("6fd08f");default->Color.valueOf("b0b0b0");};}
 private void buildResult(Profile p){
  Result res=p.result;Cmd c=cmd(res.commander);
  background(res.commander,.74f);
  int total=res.unlocked.size()+res.signature.size();
  title((res.wins>=MAX_ROUNDS?"Run completed!":"Run ended")+"  ·  "+wins(res.wins),res.ruhm+" Glory   ·   "+total+" new cards   ·   +"+res.progressionEarned+" progression points"+(res.skins.isEmpty()?"":"   ·   "+res.skins.size()+" new artwork")+"   ·   General "+p.general.size()+"/"+content().unlocks.length);
  float W=W(),H=H();
  // score panel (left)
  float px=12,py=36,pw=Math.min(206,W*.43f),ph=H-36-40;
  if(res.lines==null)res.lines=new ArrayList<>();
  root.addActor(new Painter((b,a)->{
   Color o=b.getColor().cpy();b.setColor(1,1,1,a*.95f);panel().draw(b,px,py,pw,ph);b.setColor(o);
   text(b,"SCORE",px+8,py+ph-6,8,GOLD,a,pw-16,Align.left,true);
   float y=py+ph-22,step=Math.min(9.5f,(ph-74)/Math.max(1,res.lines.size())),fs=Math.min(5.6f,step*.62f);
   for(ScoreLine l:res.lines){
    text(b,scoreLabel(l.label),px+8,y,fs,TEXT,a,pw*.62f,Align.left,false);
    text(b,displayText(l.detail),px+pw*.6f,y,fs,DIM,a,pw*.2f,Align.right,false);
    text(b,String.valueOf(l.points),px+pw*.8f,y,fs,TEXT,a,pw*.2f-8,Align.right,false);
    y-=step;
   }
   rect(b,px+8,y+2,pw-16,.6f,GOLD,a*.6f);
   text(b,"TOTAL",px+8,y-6,7,DIM,a,pw*.5f,Align.left,true);
   text(b,String.valueOf(res.score),px+8,y-4,16,GOLD,a,pw-56,Align.right,true);
   float rs=30,rx=px+pw-rs-8,ry=y-rs-2;Color rc=rankColor(res.rank);
   rect(b,rx-1,ry-1,rs+2,rs+2,Color.BLACK,a*.7f);rect(b,rx,ry,rs,rs,rc,a*(.82f+.18f*(float)Math.sin(time*3)));
   text(b,res.rank==null?"-":res.rank,rx,ry+rs*.8f,rs*.62f,Color.BLACK,a,rs,Align.center,true);
   text(b,res.record?"New record!":"Record: "+res.previousBest,px+8,ry+8,6.2f,res.record?GREEN:DIM,a,pw-rs-24,Align.left,true);
  }));
  // unlocked cards (right)
  List<String[]> cards=new ArrayList<>();
  for(String s:res.signature)cards.add(new String[]{s,"signatur"});
  for(String u:res.unlocked){CardRef r=refs.get(u);cards.add(new String[]{u,r==null?"mittel":r.tier});}
  float ax=px+pw+10,aw=W-ax-12;
  if(cards.isEmpty()){
   root.addActor(new Painter((b,a)->text(b,res.wins==0?"No wins, no Glory. Better luck next run!":"Not enough Glory for a card yet – the rest carries over.",ax,H/2+16,8,TEXT,a,aw,Align.center,true)));
  }else{
   int per=Math.max(4,Math.min(8,cards.size()));float gap=4,cw=Math.min(40,(aw-(per-1)*gap)/per),ch=cw/.716f;
   int rows=Math.max(1,Math.min((int)((H-90)/(ch+12)),(cards.size()+per-1)/per));int shown=Math.min(cards.size(),rows*per);
   float top=H-40;
   root.addActor(new Painter((b,a)->text(b,"UNLOCKED",ax,top+2,7,GOLD,a,aw,Align.center,true)));
   for(int i=0;i<shown;i++){
    String[] e=cards.get(i);int row=i/per,col=i%per;int inRow=Math.min(per,shown-row*per);
    float x=ax+(aw-(inRow*cw+(inRow-1)*gap))/2+col*(cw+gap),y=top-10-(row+1)*(ch+12)+12;
    CardView v=new CardView(e[0],x,y,cw,ch);v.border=tierColor(e[1]);v.tag=displayText(e[1]);root.addActor(v);
   }
   if(cards.size()>shown){int more=cards.size()-shown;root.addActor(new Painter((b,a)->text(b,"+ "+more+" more",ax,46,6.5f,DIM,a,aw,Align.center,false)));}
  }
  if(c!=null){int sig=p.signature.getOrDefault(c.name,List.of()).size();
   root.addActor(new Painter((b,a)->{text(b,"General",14,32,6,DIM,a,80,Align.left,false);bar(b,62,26,90,4,generalShare(),GOLD,a);
    text(b,"Signature",160,32,6,DIM,a,60,Align.left,false);bar(b,196,26,60,4,sig/(float)Math.max(1,c.specific.length),tierColor("signatur"),a);}));}
  Btn next=button("Continue",W-110,8,98,20,()->{profile().result=null;save();rebuild();});next.gold=true;
  button("Prepare deck",W-222,8,106,20,()->{profile().result=null;save();if(c!=null)openLoadoutEditor(c);});
  if(c!=null&&!res.skins.isEmpty()){Btn art=button("Artwork",W-334,8,106,20,()->{wardrobe=c;rebuild();});art.gold=true;}
 }

 // ------------------------------------------------------------------ overlays: modern dialog and deck view
 Group pushOverlay(){
  Group g=new Group();g.setBounds(0,0,W(),H());
  Actor blocker=new Actor();blocker.setBounds(0,0,W(),H());blocker.addListener(new ClickListener(){});g.addActor(blocker);
  ui.addActor(g);overlays.add(g);return g;
 }
 boolean blocked(Actor a){return !overlays.isEmpty()&&!a.isDescendantOf(overlays.get(overlays.size()-1));}
 private void confirm(String title,String body,String yes,String no,Runnable onYes){
  Group g=pushOverlay();
  float w=Math.min(W()-40,260),h=104,x=(W()-w)/2,y=(H()-h)/2;
  g.addActor(new Painter((b,a)->{
   rect(b,0,0,W(),H(),Color.BLACK,a*.62f);
   Color o=b.getColor().cpy();b.setColor(1,1,1,a);panel().draw(b,x,y,w,h);b.setColor(o);
   frame(b,x+2,y+2,w-4,h-4,.7f,GOLD,a*.5f);
   text(b,title,x,y+h-9,11,GOLD,a,w,Align.center,true);
   text(b,body,x+14,y+h-30,6.4f,TEXT,a,w-28,Align.center,false);
  }));
  Btn ok=new Btn(yes,x+w/2-104,y+10,98,20,()->{closeOverlay();onYes.run();});ok.gold=true;g.addActor(ok);
  g.addActor(new Btn(no,x+w/2+6,y+10,98,20,this::closeOverlay));
 }
 // ------------------------------------------------------------------ card browser: run deck and unlock collection
 static class Item{String name,tag,tier;Color tagColor;int count=1,recent=-1;boolean locked,added,heirloom;PaperCard illustration;}
 static int typeOrder(PaperCard p){
  if(p==null)return 9;var t=p.getRules().getType();
  return t.isLand()?8:t.isCreature()?0:t.isPlaneswalker()?1:t.isInstant()?2:t.isSorcery()?3:t.isArtifact()?4:t.isEnchantment()?5:6;
 }
 static int colorOrder(PaperCard p){
  if(p==null)return 9;var c=p.getRules().getColor();
  if(c.isColorless())return p.getRules().getType().isLand()?8:7;
  if(c.isMulticolor())return 6;
  if(c.hasWhite())return 0;if(c.hasBlue())return 1;if(c.hasBlack())return 2;if(c.hasRed())return 3;return 4;
 }
 static int tierOrder(String t){return switch(t==null?"":t){case "signatur"->0;case "stark"->1;case "gut"->2;case "mittel"->3;default->4;};}
 static int rating(String name){return (int)Math.round(forge.adventure.util.CommanderRatingMod.score(paper(name)));}
 static Color ratingColor(int r){return Color.valueOf(r>=90?"ffb43c":r>=80?"c77dff":r>=60?"5aa7ff":r>=25?"6fd08f":"9a9a9a");}
 static boolean matches(Item i,String filter){
  PaperCard p=paper(i.name);var t=p==null?null:p.getRules().getType();
  return switch(filter){
   case "Kreaturen"->t!=null&&t.isCreature()&&!t.isLand();
   case "Zauber"->t!=null&&(t.isInstant()||t.isSorcery());
   case "Artefakte"->t!=null&&t.isArtifact()&&!t.isCreature()&&!t.isLand();
   case "Verzauberungen"->t!=null&&t.isEnchantment()&&!t.isCreature();
   case "Planeswalker"->t!=null&&t.isPlaneswalker();
   case "Länder"->t!=null&&t.isLand();
   case "Neu im Run"->i.added;
   case "Erbstücke"->i.heirloom;
   case "Freigeschaltet"->!i.locked;
   case "Gesperrt"->i.locked;
   case "Signatur"->"signatur".equals(i.tier);
   default->true;
  };
 }
 static Comparator<Item> sorter(String sort){
  Comparator<Item> byName=Comparator.comparing(i->i.name);
  Comparator<Item> byCmc=Comparator.comparingInt((Item i)->{PaperCard p=paper(i.name);return p==null||p.getRules().getType().isLand()?99:p.getRules().getManaCost().getCMC();});
  Comparator<Item> c=switch(sort){
   case "Typ"->Comparator.comparingInt((Item i)->typeOrder(paper(i.name))).thenComparing(byCmc);
   case "Farbe"->Comparator.comparingInt((Item i)->colorOrder(paper(i.name))).thenComparing(byCmc);
   case "Name"->byName;
   case "Rating"->Comparator.comparingInt((Item i)->-rating(i.name));
   case "Neu"->Comparator.comparing((Item i)->!(i.added||!i.locked&&i.tier!=null)).thenComparing(byCmc);
   case "Stufe"->Comparator.comparingInt((Item i)->tierOrder(i.tier)).thenComparing((Item i)->i.locked);
   case "Letztes Paket"->Comparator.comparingInt((Item i)->i.recent<0?9999:i.recent).thenComparing(byCmc);
   default->byCmc;
  };
  return c.thenComparing(byName);
 }
 /** grid of cards: filter chips that combine (row 1), sort chips with reverse (row 2), pages; every card shows its
  *  Commander rating; tap a card for the big preview */
 /** editing inside the card browser: tabs (Deck / Bench); right-click on a card moves it */
 interface BrowserEdit{String[] tabs();int tab();void tab(int t);void act(Item i);void closed();}
 private void showBrowser(String title,List<Item> items,String[] filters,String[] sorts,java.util.function.Function<List<Item>,String> info,Runnable extra,String extraLabel){
  showBrowser(title,()->items,filters,sorts,info,extra,extraLabel,null);
 }
 private void showBrowser(String title,java.util.function.Supplier<List<Item>> items,String[] filters,String[] sorts,java.util.function.Function<List<Item>,String> info,Runnable extra,String extraLabel,BrowserEdit edit){
  Group g=pushOverlay();
  if(edit!=null)overlayClosed.put(g,edit::closed);
  float W=W(),H=H();
  Set<String> active=new LinkedHashSet<>();String[] sort={sorts[0]};boolean[] reverse={false};int[] page={0};
  Group content=new Group();
  g.addActor(new Painter((b,a)->{rect(b,0,0,W,H,DARK,a);text(b,title,12,H-8,12,GOLD,a,W-24,Align.left,true);}));
  g.addActor(content);
  int cols=12,rows=3;float gap=3,top=H-72,bottom=32;
  float cw=Math.min((W-24-(cols-1)*gap)/cols,((top-bottom)-(rows-1)*(gap+5))/rows*.716f),ch=cw/.716f;
  int per=cols*rows;
  Runnable[] refresh=new Runnable[1];
  refresh[0]=()->{
   content.clearChildren();
   List<Item> list=new ArrayList<>();
   for(Item i:items.get()){boolean ok=active.isEmpty();for(String f:active)if(matches(i,f)){ok=true;break;}if(ok)list.add(i);}
   Comparator<Item> cmp=sorter(sort[0]);list.sort(reverse[0]?cmp.reversed():cmp);
   int pages=Math.max(1,(list.size()+per-1)/per);if(page[0]>=pages)page[0]=pages-1;
   String sub=info.apply(list)+(pages>1?"   ·   Page "+(page[0]+1)+"/"+pages:"");
   content.addActor(new Painter((b,a)->{text(b,sub,12,H-26,6.2f,DIM,a,W-24,Align.left,false);
    text(b,"Filters (combine):",12,H-35,5,DIM,a,120,Align.left,false);text(b,"Sort:",12,H-52,5,DIM,a,60,Align.left,false);}));
   float x=12;
   Btn all=new Btn("All",x,H-50,28,12,()->{active.clear();page[0]=0;refresh[0].run();});all.gold=active.isEmpty();content.addActor(all);x+=30;
   for(String f:filters){
    if(f.equals("Alle"))continue;
    float w=Math.max(28,displayText(f).length()*3.4f+10);
    Btn c=new Btn(displayText(f),x,H-50,w,12,()->{if(!active.remove(f))active.add(f);page[0]=0;refresh[0].run();});c.gold=active.contains(f);content.addActor(c);x+=w+2;
   }
   x=48;
   for(String s:sorts){
    float w=Math.max(26,displayText(s).length()*3.4f+10);
    Btn c=new Btn(displayText(s),x,H-67,w,12,()->{if(sort[0].equals(s))reverse[0]=!reverse[0];else{sort[0]=s;reverse[0]=false;}refresh[0].run();});c.gold=s.equals(sort[0]);content.addActor(c);x+=w+2;
   }
   Btn rev=new Btn(reverse[0]?"Descending":"Ascending",x+6,H-67,52,12,()->{reverse[0]=!reverse[0];refresh[0].run();});content.addActor(rev);
   if(edit!=null){String[] tabs=edit.tabs();float tw=104;
    for(int k=0;k<tabs.length;k++){final int t=k;Btn tb=new Btn(tabs[k],W-12-(tabs.length-k)*(tw+4)+4,H-25,tw,17,()->{edit.tab(t);page[0]=0;refresh[0].run();});tb.gold=edit.tab()==k;content.addActor(tb);}}
   for(int i=0;i<per;i++){
    int k=page[0]*per+i;if(k>=list.size())break;Item it=list.get(k);
    float cx=12+(i%cols)*(cw+gap),cy=top-ch-(i/cols)*(ch+gap+5);
    CardView v=new CardView(it.name,cx,cy,cw,ch);v.illustration=it.illustration;v.dim=it.locked;v.rating=rating(it.name);
    v.tag=it.count>1?"× "+it.count:it.tag;v.border=it.tagColor;content.addActor(v);
    if(edit!=null)v.addListener(new ClickListener(com.badlogic.gdx.Input.Buttons.RIGHT){   // right-click moves the card
     @Override public void clicked(InputEvent e,float x,float y){if(blocked(v))return;try{edit.act(it);refresh[0].run();}catch(Throwable t){log(t);}}});
   }
   if(list.isEmpty())content.addActor(new Painter((b,a)->text(b,"No cards match these filters.",0,H/2,8,DIM,a,W,Align.center,true)));
   if(pages>1){
    content.addActor(new Btn("Previous page",12,8,84,18,()->{page[0]=(page[0]+pages-1)%pages;refresh[0].run();}));
    content.addActor(new Btn("Next page",100,8,84,18,()->{page[0]=(page[0]+1)%pages;refresh[0].run();}));
   }
  };
  refresh[0].run();
  if(extra!=null)g.addActor(new Btn(extraLabel,W-222,8,106,20,extra));
  Btn close=new Btn("Close",W-110,8,98,20,this::closeOverlay);close.gold=true;g.addActor(close);
 }
 /** run deck, editable: tab Deck (a card goes to the bench, basic lands are just removed) and tab Bench (cards from
  *  packages and cards taken out, plus the basic lands of the commander's colours; + puts a card into the deck) */
 private void showRunDeck(Run r){
  Cmd c=cmd(r.commander);
  Set<String> heir=new HashSet<>(c==null?List.of():unlockedFor(c));
  int[] tab={0};
  java.util.function.Supplier<List<String>> benchNow=()->{List<String> l=new ArrayList<>();for(String n:bench(r))if(!r.deck.contains(n)&&!isBasic(n))l.add(n);return l;};
  java.util.function.Supplier<List<Item>> items=()->{
   Set<String> added=new HashSet<>(r.added!=null?r.added:List.of());
   Map<String,Integer> counts=new LinkedHashMap<>();
   if(tab[0]==0){for(String n:r.deck)if(!n.equals(r.commander))counts.merge(n,1,Integer::sum);}
   else{
    for(String n:benchNow.get())counts.merge(n,1,Integer::sum);
    String colors=c==null||c.colors==null?"":c.colors;
    for(char ch:colors.toCharArray()){int k=COLORS.indexOf(ch);if(k>=0)counts.putIfAbsent(BASIC_NAMES[k],0);}
    if(colors.isEmpty())counts.putIfAbsent("Wastes",0);
   }
   List<Item> out=new ArrayList<>();
   for(var e:counts.entrySet()){
    Item i=new Item();i.name=e.getKey();i.count=Math.max(1,e.getValue());
    i.recent=r.lastPackage==null?-1:r.lastPackage.indexOf(i.name);
    if(tab[0]==1&&isBasic(i.name)){i.tag="Basic land";i.tagColor=DIM;}
    else if(i.recent>=0){i.added=true;i.tag="Last package";i.tagColor=Color.valueOf("ffd166");}
    else if(added.contains(i.name)){i.added=true;i.tag="Package";i.tagColor=Color.valueOf("ff9a2e");}
    else if(heir.contains(i.name)){i.heirloom=true;i.tag="Heirloom";i.tagColor=tierColor("signatur");}
    out.add(i);
   }
   return out;
  };
  BrowserEdit edit=new BrowserEdit(){
   public String[] tabs(){return new String[]{"Deck  ("+r.deck.size()+")","Bench  ("+benchNow.get().size()+")"};}
   public int tab(){return tab[0];}
   public void tab(int t){tab[0]=t;}
   public void act(Item i){
    if(tab[0]==0){
     r.deck.remove(i.name);if(!isBasic(i.name)&&!bench(r).contains(i.name))bench(r).add(i.name);
     toast(i.name+(isBasic(i.name)?" removed":" moved to the bench")+"  -  deck: "+r.deck.size());
    }else{
     if(r.deck.size()>=MAX_DECK){toast("The deck is full ("+MAX_DECK+" cards + commander). Take a card out first.");return;}
     r.deck.add(i.name);if(!isBasic(i.name))bench(r).remove(i.name);
     toast(i.name+" added to the deck  -  deck: "+r.deck.size());
    }
    save();
   }
   public void closed(){
    if(r.deck.size()<DECK&&c!=null){int miss=DECK-r.deck.size();r.deck=normalize(c,r.deck);save();
     toast(miss+" basic land(s) added to reach the minimum of "+DECK+" cards.");}
   }
  };
  showBrowser("Run deck  ·  "+r.commander.split(",")[0],items,
   new String[]{"Alle","Kreaturen","Zauber","Artefakte","Verzauberungen","Planeswalker","Länder","Neu im Run","Erbstücke"},
   new String[]{"Letztes Paket","Mana","Typ","Farbe","Rating","Name","Neu"},
   list->{int lands=0;for(String n:r.deck)if(isLand(n))lands++;int n=r.deck.size();
    return n+" cards + commander   ·   "+lands+" lands   ·   "+(n-lands)+" others"+(n<DECK?"   ·   needs at least "+DECK:n>MAX_DECK?"   ·   max "+MAX_DECK:"   ·   allowed "+DECK+"-"+MAX_DECK)
     +"   ·   right-click a card: "+(tab[0]==0?"remove":"add");},
   c==null?null:()->showCollection(c),"Unlocked",edit);
 }
 private void showDeck(String commander,List<String> cards,String title){
  Run r=profile().run;Cmd c=cmd(commander);
  Set<String> added=new HashSet<>(r!=null&&r.added!=null?r.added:List.of());
  Set<String> heir=new HashSet<>(c==null?List.of():unlockedFor(c));
  Map<String,Integer> counts=new LinkedHashMap<>();for(String n:cards)if(!n.equals(commander))counts.merge(n,1,Integer::sum);
  List<Item> items=new ArrayList<>();
  for(var e:counts.entrySet()){
   Item i=new Item();i.name=e.getKey();i.count=e.getValue();
   if(added.contains(i.name)){i.added=true;i.tag="Package";i.tagColor=Color.valueOf("ff9a2e");}
   else if(heir.contains(i.name)){i.heirloom=true;i.tag="Heirloom";i.tagColor=tierColor("signatur");}
   items.add(i);
  }
  int lands=0;for(String n:cards)if(isLand(n))lands++;
  final int landCount=lands;
  showBrowser(title+"  ·  "+commander.split(",")[0],items,new String[]{"Alle","Kreaturen","Zauber","Artefakte","Verzauberungen","Planeswalker","Länder","Neu im Run","Erbstücke"},
   new String[]{"Mana","Typ","Farbe","Rating","Name","Neu"},
   list->cards.size()+" cards + commander   ·   "+landCount+" lands   ·   "+(cards.size()-landCount)+" others   ·   shown: "+list.size(),
   c==null?null:()->showCollection(c),"Unlocked");
 }
 /** every unlockable card: signature cards of this commander and the general pool, locked ones dimmed */
 private void showCollection(Cmd c){
  Profile p=profile();
  List<String> sig=p.signature.getOrDefault(c.name,List.of());
  List<Item> items=new ArrayList<>();
  for(CardRef r:c.specific){Item i=new Item();i.name=r.n;i.tier="signatur";i.locked=!sig.contains(r.n);i.tagColor=tierColor("signatur");i.tag=i.locked?"locked":"Signature";items.add(i);}
  int fitting=0,fittingOpen=0;
  for(CardRef r:content().unlocks){
   if(!fits(r.c,c.colors))continue;
   Item i=new Item();i.name=r.n;i.tier=r.tier;i.locked=!p.general.contains(r.n);i.tagColor=tierColor(r.tier);i.tag=i.locked?"locked":displayText(r.tier);items.add(i);
   fitting++;if(!i.locked)fittingOpen++;
  }
  final int f=fitting,fo=fittingOpen;
  showBrowser("Unlocks  ·  "+c.name.split(",")[0],items,new String[]{"Alle","Freigeschaltet","Gesperrt","Signatur","Kreaturen","Zauber","Artefakte","Verzauberungen","Länder"},
   new String[]{"Stufe","Rating","Mana","Typ","Farbe","Name"},
   list->"Signature "+sig.size()+"/"+c.specific.length+"   ·   General (compatible) "+fo+"/"+f+"   ·   General total "+p.general.size()+"/"+content().unlocks.length+"   ·   shown: "+list.size(),
   null,null);
 }

 // ------------------------------------------------------------------ voice lines (voices/<slug>/<event>_*.wav|ogg|mp3)
 private static final Map<String,com.badlogic.gdx.audio.Sound> voiceCache=new HashMap<>();
 private static com.badlogic.gdx.audio.Sound currentVoice;private static long lastVoice;
 /** plays a random clip for the event; own clips can simply be added to the commander's voice folder */
 static void voice(String commander,String event){
  try{
   Cmd c=cmd(commander);if(c==null)return;
   File[] files=new File(DIR+"voices/"+c.slug).listFiles((d,n)->n.startsWith(event+"_")&&(n.endsWith(".wav")||n.endsWith(".ogg")||n.endsWith(".mp3")));
   if(files==null||files.length==0)return;
   File f=files[new Random().nextInt(files.length)];
   String said=lineText(c.slug,f.getName());
   if(said!=null){subSpeaker=c.name.split(",")[0];subText=said;subStart=System.currentTimeMillis();subMs=Math.max(2400,Math.min(8000,wavMs(f)+900));}
   if(currentVoice!=null)currentVoice.stop();
   com.badlogic.gdx.audio.Sound s=voiceCache.computeIfAbsent(f.getPath(),p->Gdx.audio.newSound(Gdx.files.absolute(p)));
   float vol=1f;try{vol=FModel.getPreferences().getPrefInt(forge.localinstance.properties.ForgePreferences.FPref.UI_VOL_SOUNDS)/100f;}catch(Exception ignored){}
   if(vol<=0)return;
   s.play(Math.min(1f,vol));currentVoice=s;lastVoice=System.currentTimeMillis();
  }catch(Throwable t){log(t);}
 }
 // ------------------------------------------------------------------ subtitles for the voice lines
 private static volatile String subSpeaker,subText;private static volatile long subStart,subMs;
 private static com.badlogic.gdx.graphics.g2d.SpriteBatch subBatch;
 private static Map<String,Map<String,List<String>>> lineTexts;
 /** the text of a rendered clip <event>_<n>.wav from lines.json (rogue-commanders/voices/lines.json or _mods/voices/lines.json) */
 static String lineText(String slug,String file){
  if("pantlaza-sun-favored".equals(slug))return file.startsWith("defeat_")?"[pained dinosaur growl]":file.startsWith("package_")?"[low dinosaur rumble]":"[dinosaur roar]";
  if(lineTexts==null){
   lineTexts=new HashMap<>();
   try{
    File f=new File(DIR+"voices/lines.json");if(!f.exists())f=new File(ROOT+"_mods/voices/lines.json");
    if(f.exists()){
     Map<String,Object> all=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,Object>>(){}.getType());
     for(Map.Entry<String,Object> e:all.entrySet())if(e.getValue() instanceof Map<?,?> events){
      Map<String,List<String>> per=new HashMap<>();
      for(Map.Entry<?,?> x:events.entrySet())if(x.getValue() instanceof List<?> l){List<String> t=new ArrayList<>();for(Object o:l)t.add(String.valueOf(o));per.put(String.valueOf(x.getKey()),t);}
      lineTexts.put(e.getKey(),per);
     }
    }
   }catch(Exception e){log(e);}
  }
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(.+)_(\\d+)\\.[A-Za-z0-9]+$").matcher(file);
  if(!m.matches())return null;
  List<String> l=lineTexts.getOrDefault(slug,Map.of()).get(m.group(1));int i=Integer.parseInt(m.group(2))-1;
  return l!=null&&i>=0&&i<l.size()?l.get(i):null;
 }
 /** length of a PCM WAV clip in ms (estimate for other formats) */
 static long wavMs(File f){
  try(var in=new java.io.RandomAccessFile(f,"r")){
   if(!f.getName().endsWith(".wav"))return 3000;
   in.seek(22);int ch=Short.reverseBytes(in.readShort());int rate=Integer.reverseBytes(in.readInt());in.seek(34);int bits=Short.reverseBytes(in.readShort());
   return (long)((f.length()-44)*1000L/(Math.max(1,ch)*Math.max(1,rate)*(Math.max(8,bits)/8)));
  }catch(Exception e){return 3000;}
 }
 /** drawn on top of every screen (also during matches) by DevModeMod.frame() at the end of Forge.render */
 public static void drawSubtitle(){
  String text=subText,speaker=subSpeaker;if(text==null)return;
  long t=System.currentTimeMillis()-subStart;
  if(t>subMs+400){subText=null;return;}
  try{
   float a=Math.max(0,Math.min(1,Math.min(t/180f,(subMs+400-t)/400f)));
   int w=Gdx.graphics.getWidth(),h=Gdx.graphics.getHeight();if(w<=0||h<=0)return;
   if(subBatch==null)subBatch=new com.badlogic.gdx.graphics.g2d.SpriteBatch();
   subBatch.getProjectionMatrix().setToOrtho2D(0,0,w,h);
   float size=Math.max(16,Math.min(w,h)*.034f),maxW=Math.min(w*.62f,size*30);
   BitmapFont body=font(false),title=font(true);
   body.getData().setScale(size/FONT_PX);
   com.badlogic.gdx.graphics.g2d.GlyphLayout said=new com.badlogic.gdx.graphics.g2d.GlyphLayout(body,"\u201C"+text+"\u201D",new Color(TEXT.r,TEXT.g,TEXT.b,a),maxW,Align.center,true);
   title.getData().setScale(size*.72f/FONT_PX);
   com.badlogic.gdx.graphics.g2d.GlyphLayout who=new com.badlogic.gdx.graphics.g2d.GlyphLayout(title,speaker.toUpperCase(),new Color(GOLD.r,GOLD.g,GOLD.b,a),maxW,Align.center,false);
   float padX=size*1.1f,padY=size*.55f,gap=size*.35f;
   float boxW=Math.max(said.width,who.width)+2*padX,boxH=who.height+gap+said.height+2*padY,bx=(w-boxW)/2,by=h*.2f;
   subBatch.begin();
   subBatch.setColor(.03f,.04f,.07f,.8f*a);subBatch.draw(white(),bx,by,boxW,boxH);
   subBatch.setColor(GOLD.r,GOLD.g,GOLD.b,.85f*a);subBatch.draw(white(),bx,by+boxH-Math.max(2,size*.08f),boxW,Math.max(2,size*.08f));
   subBatch.setColor(Color.WHITE);
   title.draw(subBatch,who,(w-maxW)/2,by+boxH-padY);
   body.draw(subBatch,said,(w-maxW)/2,by+padY+said.height);
   subBatch.end();
  }catch(Throwable e){subText=null;log(e);}
 }
 /** called by ArenaMotionMod when the player's commander enters the battlefield */
 public static void commanderCast(String name){
  Profile p=profile;if(p==null||p.run==null||name==null)return;
  if(name.equals(p.run.commander)||name.startsWith(p.run.commander.split(",")[0]))voice(p.run.commander,"commander");
 }

 /** enemy artwork from the enemy-intro manifest (sprite atlas -> art file in _mods/art_cache), or null */
 private static Map<String,String> enemyArt;private static final Map<String,String> enemyArtUrl=new HashMap<>();
 /** background download of missing enemy artwork from Scryfall (same source Forge uses for card pictures) */
 static final class EnemyArtFetch{
  private static final Set<String> FETCHING=Collections.synchronizedSet(new HashSet<>());
  static void fetch(String path,String url){
   if(url==null||!url.startsWith("https://cards.scryfall.io/")||!FETCHING.add(path))return;
   Thread t=new Thread(()->{
    try{
     java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();
     c.setRequestProperty("User-Agent","ForgeRogueCommanders/1.0");c.setConnectTimeout(15000);c.setReadTimeout(30000);
     if(c.getResponseCode()!=200)return;
     java.nio.file.Path target=java.nio.file.Path.of(path),tmp=java.nio.file.Path.of(path+".part");
     Files.createDirectories(target.getParent());
     try(java.io.InputStream in=c.getInputStream()){Files.copy(in,tmp,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
     Files.move(tmp,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }catch(Exception ex){log(ex);}
   },"enemy-art-download");
   t.setDaemon(true);t.start();
  }
 }
 static Texture enemyArtTexture(EnemySprite e){
  try{
   if(e==null||e.getData()==null)return null;
   if(enemyArt==null){
    enemyArt=new HashMap<>();File f=new File(ROOT+"_mods/enemy-intro/portraits.json");
    if(f.exists()){
     List<Map<String,Object>> list=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<List<Map<String,Object>>>(){}.getType());
     for(Map<String,Object> m:list)if(m.get("sprite")!=null&&m.get("path")!=null){
      enemyArt.put(String.valueOf(m.get("sprite")),String.valueOf(m.get("path")));
      if(m.get("url")!=null)enemyArtUrl.put(String.valueOf(m.get("path")),String.valueOf(m.get("url")));}
    }
   }
   String p=enemyArt.get(e.getData().sprite);
   if(p!=null&&!new File(p).exists()){EnemyArtFetch.fetch(p,enemyArtUrl.get(p));return null;}   // public package: downloaded on first use
   return p==null?null:fileTexture(p);
  }catch(Exception ex){log(ex);return null;}
 }
 // ------------------------------------------------------------------ versus screen before every duel
 /** commander art (selected illustration) against the opponent, round, life and boss badge; tap or wait to fight */
 private void showVersus(Run r,EnemySprite enemy,boolean boss,Runnable fight){
  Group g=pushOverlay();
  float W=W(),H=H();long start=System.currentTimeMillis();boolean[] done={false};
  PaperCard art=commanderPaper(r.commander);
  TextureRegion foe=null;try{foe=enemy.getAvatar();}catch(Exception ignored){}
  final TextureRegion enemyAvatar=foe;List<String> startCards=boons(r);
  final Texture foeArt=enemyArtTexture(enemy);   // full artwork (upscaled) instead of the small sprite avatar
  Cmd c=cmd(r.commander);String colors=c==null?"":c.colors;
  Color mine=colors.isEmpty()?GOLD:pip(colors.charAt(0)),theirs=boss?Color.valueOf("e0483c"):Color.valueOf("9aa3b5");
  Runnable go=()->{if(done[0])return;done[0]=true;closeOverlay();fight.run();};
  g.addActor(new Painter((b,a)->{
   float t=(System.currentTimeMillis()-start)/1000f;
   float in=Math.min(1,t/.45f),ease=1-(1-in)*(1-in)*(1-in);
   rect(b,0,0,W,H,DARK,a);
   // diagonal halves in commander / enemy colours
   int n=90;float strip=H/n;
   for(int i=0;i<n;i++){float f=(i+.5f)/n,split=W*.5f+(f-.5f)*40;rect(b,0,i*strip,split,strip,mine,a*.16f*(1-f*.4f));rect(b,split,i*strip,W-split,strip,theirs,a*.13f*(.6f+f*.4f));}
   // commander art (left)
   Texture tex=cardTexture(r.commander);
   float aw=W*.44f,ah=H*.62f,ax=-aw+(aw+18)*ease,ay=H*.22f;
   if(tex!=null){int tw=tex.getWidth(),th=tex.getHeight();int sx=Math.round(tw*.08f),sy=Math.round(th*.115f),sw=Math.round(tw*.84f),sh=Math.round(th*.43f);
    float scale=Math.max(aw/sw,ah/sh);int cw=Math.round(aw/scale),chh=Math.round(ah/scale);
    rect(b,ax-2,ay-2,aw+4,ah+4,mine,a*.9f);Color o=b.getColor().cpy();b.setColor(1,1,1,a);
    b.draw(tex,ax,ay,aw,ah,sx+(sw-cw)/2,sy+(sh-chh)/2,cw,chh,false,false);b.setColor(o);}
   else rect(b,ax,ay,aw,ah,Color.valueOf("1c1a22"),a);
   for(int i=0;i<10;i++)rect(b,ax,ay+i*2,aw,2,Color.BLACK,a*.06f*(10-i));
   // enemy (right)
   float ew=H*.5f,ex=W+ew-(ew+W*.12f+ew)*ease+ew*.0f,ey=H*.28f;
   ex=W-ew-W*.07f+(1-ease)*(ew+40);
   if(foeArt!=null){rect(b,ex-6,ey-6,ew+12,ew+12,theirs,a*.35f);Color o=b.getColor().cpy();b.setColor(1,1,1,a);
    int fw=foeArt.getWidth(),fh=foeArt.getHeight(),side=Math.min(fw,fh);
    b.draw(foeArt,ex,ey,ew,ew,(fw-side)/2,Math.round((fh-side)*.35f),side,side,false,false);b.setColor(o);}
   else if(enemyAvatar!=null){rect(b,ex-6,ey-6,ew+12,ew+12,theirs,a*.35f);Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(enemyAvatar,ex,ey,ew,ew);b.setColor(o);}
   // names
   text(b,r.commander.split(",")[0],ax+6,ay-6,11,GOLD,a,aw,Align.left,true);
   text(b,"Round "+r.round+"   ·   "+wins(r.wins)+"   ·   Glory "+r.ruhm,ax+6,ay-21,6.2f,TEXT,a,aw,Align.left,false);
   String foeName=enemy.getName();
   text(b,foeName,ex-W*.25f,ey-6,11,boss?RED:TEXT,a,ew+W*.25f,Align.right,true);
   text(b,"Starting life "+enemyLife(r.round),ex-W*.25f,ey-21,6.2f,DIM,a,ew+W*.25f,Align.right,false);
   if(!startCards.isEmpty())text(b,"Starts with: "+String.join(", ",startCards),ex-W*.3f,ey-31,6,Color.valueOf("ffb43c"),a,ew+W*.3f,Align.right,false);
   if(boss){float bw=60,bx=ex+ew-bw,by=ey+ew+8;rect(b,bx,by,bw,14,RED,a*(.75f+.25f*(float)Math.sin(t*6)));text(b,"BOSS",bx,by+11,8,Color.WHITE,a,bw,Align.center,true);}
   // VS
   float vs=Math.min(1,Math.max(0,(t-.3f)/.35f)),pulse=1+.06f*(float)Math.sin(t*5);
   float size=34*pulse*(2-vs);
   text(b,"VS",0,H*.62f+size*.35f,size,Color.BLACK,a*vs*.7f,W+3,Align.center,true);
   text(b,"VS",0,H*.62f+size*.35f+1,size,GOLD,a*vs,W,Align.center,true);
   rect(b,W*.5f-1,H*.1f,2,H*.3f*vs,GOLD,a*vs*.6f);
   text(b,"Tap to fight",0,14,6,DIM,a*Math.min(1,t/1.2f),W,Align.center,false);
   if(t>3.2f)Gdx.app.postRunnable(go);
  }));
  Actor tap=new Actor();tap.setBounds(0,0,W,H);
  tap.addListener(new ClickListener(){@Override public void clicked(InputEvent e,float x,float y){if(System.currentTimeMillis()-start>500)go.run();}});
  g.addActor(tap);
  voice(r.commander,boss?"boss":"battle");
 }

 // ------------------------------------------------------------------ big card preview
 private void preview(String name){preview(name,null);}
 private void preview(String name,PaperCard illustration){
  Group g=pushOverlay();
  float ch=H()-30,cw=ch*.716f;
  g.addActor(new Painter((b,a)->{rect(b,0,0,W(),H(),Color.BLACK,a*.78f);Texture t=illustration==null?cardTexture(name):cardTexture(illustration);float x=(W()-cw)/2,y=15;
   if(t!=null){b.setColor(1,1,1,a);b.draw(t,x,y,cw,ch);}else{rect(b,x,y,cw,ch,Color.valueOf("1c1a22"),a);text(b,name,x+6,y+ch/2+10,10,TEXT,a,cw-12,Align.center,true);}
   text(b,"Tap to close",0,10,5.5f,DIM,a,W(),Align.center,false);}));
  Actor catcher=new Actor();catcher.setBounds(0,0,W(),H());
  catcher.addListener(new ClickListener(){@Override public void clicked(InputEvent e,float x,float y){closeOverlay();}});
  g.addActor(catcher);
 }
 private final Map<Group,Runnable> overlayClosed=new HashMap<>();
 private void closeOverlay(){
  if(overlays.isEmpty())return;
  Group g=overlays.remove(overlays.size()-1);g.remove();
  Runnable done=overlayClosed.remove(g);if(done!=null)try{done.run();}catch(Throwable t){log(t);}
 }
}
