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
import forge.adventure.util.FantasyButtonArt;
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
  if(r==null)try{String a=forge.localinstance.properties.ForgeConstants.ASSETS_DIR;   /* Android: <obb>/Forge/ (APK 2026-11-04) */
   if(a!=null&&!a.isEmpty()&&new java.io.File(a,"_mods").isDirectory())r=a;}catch(Throwable ignored){}
  if(r==null)r=new java.io.File("").getAbsolutePath();
  r=r.replace('\\','/');return r.endsWith("/")?r:r+"/";
 }
 /** Android APK (user 2026-11-04: "ein paar PC Sachen auf Android aendern - Steuerung, Tooltips, Guides"): touch texts and
  *  gestures instead of mouse and keyboard. On the PC nothing changes. */
 static boolean touch(){try{return forge.gui.GuiBase.getInterface()!=null&&forge.gui.GuiBase.isAndroid();}catch(Throwable t){return false;}}
 static String tx(String pc,String touch){return touch()?touch:pc;}
 /** long press (0.5 s) on touch screens: the right-click actions */
 static void onLongPress(Actor a,Runnable r){if(!touch())return;var gl=new com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener(){
   @Override public boolean longPress(Actor act,float x,float y){var st=act.getStage();if(st!=null)st.cancelTouchFocusExcept(this,act);r.run();return true;}};
  gl.getGestureDetector().setLongPressSeconds(.5f);a.addListener(gl);}
 static final String ROOT=root(),DIR=ROOT+"_mods/rogue-commanders/",BOARDS=ROOT+"_mods/arena-boards/boards/";
 static final int LIFE=30,DECK=70,MAX_DECK=99,MAX_ROUNDS=15;   // Rogue decks: 70-99 cards + commander (user, 2026-10-15)
 static final int UNLOCK_PERCENT=97;   // +10 % faster heirloom / signature unlocks (user 2026-10-17: 80 -> 88; 2026-10-18 again +10 %: 88 -> 97)
 static final double COST=3.0,TARGET_HOURS=20,TARGET_SHARE=1.0;   // all heirlooms in ~20-30 h of play (user 2026-10-18: "20-30 Stunden alle Unlocks ist okay, das Spiel ist in Beta"); was 80 % in 40 h   // half as many unlocks per run (user, 2026-10-14)
 static final String[] BASIC_NAMES={"Plains","Island","Swamp","Mountain","Forest"};
 static final String COLORS="WUBRG";
 static final Map<String,String> BOARD=Map.of("Y'shtola, Night's Blessed","MID_Mobile","Teval, the Balanced Scale","KHM_Mobile",
  "Fire Lord Azula","TLA","Cloud, Ex-SOLDIER","FIN_Mobile","Ureni of the Unwritten","AKR_Mobile","Karolina Dean, Runaway","ESP_Mobile",
  "Pantlaza, Sun-Favored","XLN_Mobile","Jace, Multiverse Architect","WAR_Mobile","The Wise Mothman","DSK_Mobile","Shang-Chi, Master of Kung Fu","GRN_Mobile");
 static final Set<String> TOO_BIG=Set.of("Emrakul","Kozilek","Elesh Norn","Jin-Gitaxias","Urabrask","Vorinclex","Sheoldred","Valyx","Chandra","Liliana","Nissa","Sorin");

 // ------------------------------------------------------------------ content
 static class CardRef{String n,c="",tier,cat,needLandColors;String[] commanders;int s,price;}
 static class Theme{String key,name;boolean rare,focus,progression;int cost,requiredWins;CardRef[] cards;}   // focus: the commander's key synergy package
 static class Cmd{boolean bonus,packageTiers;String name,slug,colors,blurb,board;String[] lands,starter,legacyStarter,legacyLands,alts,protectedStarter;int minimumPackages;Theme[] themes;CardRef[] specific;Map<String,Integer> scores;Map<String,String> packageAliases;}
 static class Generic{String key,name;CardRef[] cards;}
 static class Content{int version,packageSchema;Cmd[] commanders;Generic[] generic;CardRef[] unlocks;CardRef[] legendary;}
 // ------------------------------------------------------------------ profile (permanent) and run (temporary)
 static class Offer{String name,kind,rarity,packageKey;boolean depletedMix;List<String> cards=new ArrayList<>();}
 static class MapNode{int id,floor,lane,count;String type,deck,pre;boolean preDone;List<Integer> next=new ArrayList<>();}   // Spire map room
 /** one card over all runs: taken from rewards, runs + duels in the deck, wins, times played (cast or land drop) */
 static class CardStat{int taken,runs,games,wins,played;}
 static class Run{Set<String> statCards;   /* cards whose run count went up this run (card history) */int storyAct;List<MapNode> map;int mapPos=-1,mapVersion,eliteBonus,extraWins;List<Integer> mapPath=new ArrayList<>();boolean eliteFight,extraFight;String extraDeck,treasureCard;String commander,opponent,phase="fight";long seed,duelStart,lastAchievementDuel;int rewardTier,skippedRewards,rewardSchema;boolean skippedThirteen;int round=1,wins,ruhm,bosses,perfect,fast,close,legendary,epic,lifeSource,marathon,cmdrKill,poison,mill,altWin,overkill,mulligan,storm,army,noCommander,heirlooms,quickWin,longWin;int routeCurseRound;Map<String,String> routeCurses;int routeRound;String routeChoice;List<String> routeOptions;boolean routeConfirmed;boolean lost,completed,cursed,trueRogue;int life,branchSide=-1,branchSegment=-1,encounters,forcedTier=-1;String encounter,nextFightPenalty;boolean bonusEpicPending,bonusEpicReady;List<String> encounterTypes=new ArrayList<>(),lastUsed=new ArrayList<>();EffectData savedBlessing;List<String> curses=new ArrayList<>(),curseOptions=new ArrayList<>(),offered=new ArrayList<>();
  List<String> deck=new ArrayList<>(),picked=new ArrayList<>(),added=new ArrayList<>(),bench=new ArrayList<>(),lastPackage=new ArrayList<>();List<Offer> offers=new ArrayList<>();List<String> selectedPackages,chosenPackages,shopBought;List<CardRef> shopStock;int shopRound,shopRerolls,shopCatalogVersion,creditedBossGold,goldEarned;int packageRerolls,rerollSalt,rewardUpgrades;List<String> rerollAvoid;}
 /** cards of this run that are not in the run deck right now (old saves have no bench yet) */
 static List<String> bench(Run r){if(r.bench==null)r.bench=new ArrayList<>();return r.bench;}
 static class ScoreLine{String label,detail;int points;ScoreLine(){}ScoreLine(String label,String detail,int points){this.label=label;this.detail=detail;this.points=points;}}
 static class Result{String commander,rank,dungeonNotice;boolean dungeonNoticeSeen,storySeen;int wins,ruhm,score,previousBest,progressionEarned;boolean record,completed;List<String> unlocked=new ArrayList<>(),signature=new ArrayList<>(),skins=new ArrayList<>();List<ScoreLine> lines=new ArrayList<>();}
 static class Profile{Set<String> tutorialsSeen,huntSeeded;boolean lastRunFailed,storyOff,packagesExplained,battleTourDone;Map<String,List<String>> neutralFavorites,huntLists;String playerName;Map<String,Map<String,String>> forgeAchievements=new HashMap<>();int voiceVolume=100;String monitorKey;   /* Settings > Display: the monitor to play on (tester 2026-10-19) */Map<String,Integer> packagePicks=new HashMap<>(),packageTaken=new HashMap<>();   /* package statistics over all runs (user 2026-10-18) */Map<String,CardStat> cardStats=new HashMap<>();   /* card history over all runs (user 2026-10-19) */int version=4,counter,runs,totalWins,progressionPoints,goldBalance,packageRerolls;long sacrificedPermanents,artifactCreaturesCast,artifactsCast;boolean artifactCounterMigrated;boolean bossOneDefeated,bossTenDefeated,bossDrainKill;List<String> packageUnlocks=new ArrayList<>(),achievements=new ArrayList<>();Map<String,List<String>> packageChoices=new HashMap<>();Map<String,List<String>> neutralExcluded=new HashMap<>();Map<String,Integer> starterSchema=new HashMap<>();   /* saved decks migrated to this starter version */long activeMs;double carry;
  List<String> general=new ArrayList<>(),unlockOrder=new ArrayList<>();Map<String,List<String>> signature=new HashMap<>(),loadout=new HashMap<>();
  Map<String,Integer> best=new HashMap<>(),bestScore=new HashMap<>();Map<String,String> commanderArt=new HashMap<>();Run run;Result result;String selected;boolean cursesUnlocked,cursesOn,trueRogueOn,trueRogueWarned,reachedFinalBoss,trueRogueBossOne;long elvesRemoved,graveyardPlays,creaturesSacrificed;boolean killedAngel,killedDemon,killedDragon;long landsDestroyed;long castWhite,castBlue,castRed,castGreen;List<String> recentOffers=new ArrayList<>();int bestScoreAll;}

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
  "Fire Lord Azula",List.of(new Skin("TLA","220","Fahmi Fauzi",0),new Skin("TLA","313","Dominik Mayer",3),new Skin("TLA","334","JungShan",6),new Skin("CUSTOM","azula-fullart.png","Fahmi Fauzi (Full Art)",9)),
  "Cloud, Ex-SOLDIER",List.of(new Skin("FIC","2","Justyna Dura",0),new Skin("FIC","202","S. Makimura",3)),
  "Ureni of the Unwritten",List.of(new Skin("TDC","9","Valera Lutfullina",0)),
  "Karolina Dean, Runaway",List.of(new Skin("CUSTOM","karolina-fan.jpg","AaronTigerTear (Fan-Art)",0),new Skin("MSC","694","Jeff Dekal",0)),
  "Pantlaza, Sun-Favored",List.of(new Skin("LCC","4","Sam Burley",0),new Skin("LCC","20","Sidharth Chaturvedi",3)),
  "Jace, Multiverse Architect",List.of(new Skin("FRC","1","Volkan Baǵa",0)),
  "The Wise Mothman",List.of(new Skin("PIP","4","Sergei Leoluch Panin",0),new Skin("PIP","343","David Gaillet",3),new Skin("SLD","2455","Chris Seaman",6)),
  "Shang-Chi, Master of Kung Fu",List.of(new Skin("MSH","187","Lee Woo-chul",0),new Skin("CUSTOM","shang-chi-fan.jpg","Fan-Art",0)));
 /** the commander's artworks; private fan-art skins (CUSTOM) only when their files exist on this PC - the public build
  *  ships none, so there the official prints remain */
 static List<Skin> skins(String commander){
  List<Skin> all=SKINS.getOrDefault(commander,List.of()),out=new ArrayList<>(all.size());
  for(Skin sk:all)if(!sk.custom()||customAvailable(sk))out.add(sk);
  return out;
 }
 private static final Map<String,Boolean> customFiles=new HashMap<>();
 static boolean customAvailable(Skin sk){
  return customFiles.computeIfAbsent(sk.number,n->{String base=DIR+"skins/"+n.replaceAll("\\.[A-Za-z]+$","");
   return new File(DIR+"skins/"+n).exists()||new File(base+"-card.png").exists()||new File(base+"-art.png").exists();});
 }
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
   if(profile.result!=null&&profile.result.dungeonNotice!=null&&!profile.result.dungeonNoticeSeen)profile.result.dungeonNotice=dungeonProgressNotice(profile);
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
   if(best!=null&&(digital(db,best)||foreign(best))){
    PaperCard paper=null;java.util.Date newest=null;
    for(PaperCard p:db.getCommonCards().getAllCards(name)){
     if(digital(db,p)||foreign(p))continue;
     forge.card.CardEdition ed=db.getEditions().get(p.getEdition());java.util.Date d=ed==null?null:ed.getDate();
     if(paper==null||(d!=null&&(newest==null||d.after(newest)))){paper=p;newest=d;}
    }
    if(paper!=null)best=paper;
   }
  }catch(Exception e){best=null;}
  papers.put(name,best);return best;
 }
 /** a foreign-language print (e.g. PRM "0001★JP"): its picture has no English text (tester 2026-10-19) */
 static boolean foreign(PaperCard p){String cn=p.getCollectorNumber();return cn!=null&&cn.toUpperCase(Locale.ROOT).matches(".*[^A-Z](JP|JA|KO|ZH|ZHS|ZHT|RU|PH)$");}
 static boolean digital(forge.StaticData db,PaperCard p){
  forge.card.CardEdition ed=db.getEditions().get(p.getEdition());
  return ed!=null&&ed.getType()==forge.card.CardEdition.Type.ONLINE;
 }
 static String wins(int n){return n+(n==1?" win":" wins");}
 static boolean isLand(String name){PaperCard p=paper(name);return p!=null&&p.getRules().getType().isLand();}
 /** full-art basic land printings per commander (basics.json, built by build_basics.py from Scryfall + Forge editions) */
 static class BasicSet{String set,name;boolean own;Map<String,List<String>> cards;}
 private static Map<String,BasicSet> basicSets;private static final Map<String,PaperCard> basicPrints=new HashMap<>();
 static PaperCard basicPaper(String commander,String land,int index){
  Cmd c=commander==null?null:cmd(commander);if(c==null)return null;
  if(basicSets==null){
   try{basicSets=GSON.fromJson(Files.readString(java.nio.file.Path.of(DIR+"basics.json")),new com.google.gson.reflect.TypeToken<Map<String,BasicSet>>(){}.getType());}
   catch(Exception e){basicSets=new HashMap<>();}
   if(basicSets==null)basicSets=new HashMap<>();}
  BasicSet b=basicSets.get(c.slug);List<String> nums=b==null||b.cards==null?null:b.cards.get(land);
  if(nums==null||nums.isEmpty())return null;
  String num=nums.get(Math.floorMod(index,nums.size())),key=land+"|"+b.set+"|"+num;
  if(basicPrints.containsKey(key))return basicPrints.get(key);
  PaperCard p=null;try{p=FModel.getMagicDb().getCommonCards().getCard(land,b.set,num);}catch(Exception e){log(e);}
  basicPrints.put(key,p);return p;
 }
 /** commander whose basics the Rogue screens show: the running run's, otherwise the selected one */
 static String activeCommander(){Profile p=profile();return p.run!=null&&p.run.commander!=null?p.run.commander:p.selected;}
 static boolean isBasic(String name){return Arrays.asList(BASIC_NAMES).contains(name)||name.startsWith("Snow-Covered ")||name.equals("Wastes");}
 static boolean fits(String identity,String colors){if(identity==null)return true;for(char ch:identity.toCharArray())if(colors.indexOf(ch)<0)return false;return true;}
 static int score(Cmd c,String name){
  if(isBasic(name))return 2;
  if(c!=null&&c.scores!=null&&c.scores.containsKey(name))return c.scores.get(name);
  if(c!=null){for(CardRef r:c.specific)if(r.n.equals(name))return r.s;for(Theme t:c.themes)for(CardRef r:t.cards)if(r.n.equals(name))return r.s;}
  CardRef r=refs==null?null:refs.get(name);return r!=null?r.s:30;
 }
 static List<String> defaultDeck(Cmd c){List<String> d=new ArrayList<>(Arrays.asList(c.lands));d.addAll(Arrays.asList(c.starter));return d;}
 static final int STARTER_SCHEMA=2;   // 2: synergy starters (user 2026-11-03: weaker starters) - saved decks swap retired cards once
 static List<String> loadout(Cmd c){Profile p=profile();List<String> l=p.loadout.get(c.name);if(l==null||l.isEmpty())return defaultDeck(c);
  if(p.starterSchema==null)p.starterSchema=new HashMap<>();
  if(p.starterSchema.getOrDefault(c.name,0)<STARTER_SCHEMA){   /* once: later unlocks of former starter cards stay in the deck */
   l=migrateStarter(c,l);p.loadout.put(c.name,new ArrayList<>(l));p.starterSchema.put(c.name,STARTER_SCHEMA);save();}
  return growToDefault(c,l,Set.of());}
 /** Refresh retired starter slots for future runs, retaining user lands and added unlocks; never mutates an active run. */
 static List<String> migrateStarter(Cmd c,List<String> saved){
  List<String> out=new ArrayList<>(saved);
  if(c.legacyStarter!=null){
   Set<String> retired=new HashSet<>(Arrays.asList(c.legacyStarter));retired.removeAll(Arrays.asList(c.starter));
   List<String> available=new ArrayList<>(Arrays.asList(c.starter));available.removeAll(out);
   for(int i=0;i<out.size()&&!available.isEmpty();i++)if(retired.contains(out.get(i)))out.set(i,available.remove(0));}
  if(c.legacyLands!=null){   /* former starter duals make way for the new starter lands (Lands & Fixing package earns them back) */
   Set<String> retired=new HashSet<>(Arrays.asList(c.legacyLands));retired.removeAll(Arrays.asList(c.lands));
   List<String> free=new ArrayList<>(Arrays.asList(c.lands));for(String n:out)free.remove(n);
   String basic=c.lands[c.lands.length-1];
   for(int i=0;i<out.size();i++)if(retired.contains(out.get(i)))out.set(i,free.isEmpty()?basic:free.remove(0));}
  return out;
 }
 /** decks saved before the 70-card minimum: first add the starter cards they lack (the 11 new ones), then basics */
 static List<String> growToDefault(Cmd c,List<String> cards,Collection<String> skip){
  List<String> out=new ArrayList<>(cards);if(out.size()>=DECK)return out;
  Map<String,Integer> have=new HashMap<>();for(String n:out)have.merge(n,1,Integer::sum);
  List<String> order=new ArrayList<>(Arrays.asList(c.starter));Collections.reverse(order);   // newest starter cards first
  List<String> lands=new ArrayList<>(Arrays.asList(c.lands));Collections.reverse(lands);order.addAll(lands);
  for(String n:order){if(out.size()>=DECK)break;if(skip.contains(n))continue;
   if(have.getOrDefault(n,0)>0){have.merge(n,-1,Integer::sum);continue;}out.add(n);}
  return out;
 }
 /** unlock history (newest last) so heirlooms can be shown newest first (user 2026-10-17); older saves fall back to
  *  the order of the general / signature lists, which are also chronological */
 static void noteUnlock(Profile p,String card){if(p.unlockOrder==null)p.unlockOrder=new ArrayList<>();p.unlockOrder.remove(card);p.unlockOrder.add(card);}
 static int unlockRank(Profile p,String card){
  int i=p.unlockOrder==null?-1:p.unlockOrder.indexOf(card);if(i>=0)return 1_000_000+i;
  i=p.general.indexOf(card);if(i>=0)return 1000+i;
  for(List<String> l:p.signature.values()){i=l.indexOf(card);if(i>=0)return 1000+i;}
  return -1;
 }
 static List<String> unlockedFor(Cmd c){List<String> out=heirloomsFor(c);for(String s:signaturesFor(c))if(!out.contains(s))out.add(s);return out;}
 /** general heirlooms (shared pool) that fit this commander's colours */
 static List<String> heirloomsFor(Cmd c){List<String> out=new ArrayList<>();for(CardRef r:content().unlocks)if(profile().general.contains(r.n)&&fits(r.c,c.colors))out.add(r.n);return out;}
 /** every signature card already unlocked anywhere - overlap counts: a card unlocked for Azula is also unlocked for
  *  Shang-Chi when it is one of his signature cards too (user 2026-10-17) */
 static Set<String> unlockedSignatures(Profile p){Set<String> all=new HashSet<>(p.general);for(List<String> l:p.signature.values())all.addAll(l);return all;}
 static List<String> signaturesFor(Cmd c){
  Profile p=profile();Set<String> any=unlockedSignatures(p);List<String> out=new ArrayList<>(p.signature.getOrDefault(c.name,List.of()));
  if(c.specific!=null)for(CardRef s:c.specific)if(s!=null&&any.contains(s.n)&&!out.contains(s.n))out.add(s.n);
  return out;
 }
 /** This viewer uses only the known run list; it never reads Game, Library, hand, exile or hidden zones. */
 public static boolean canViewRunDeck(){return profile!=null&&profile.run!=null&&"fight".equals(profile.run.phase)&&profile.run.duelStart>0;}
 static List<String> randomRunDeckNames(Run r,Random random){List<String> names=new ArrayList<>(r.deck);Collections.shuffle(names,random);return names;}
 public static void viewRunDeckRandom(){
  if(!canViewRunDeck())return;Run r=profile.run;List<PaperCard> cards=new ArrayList<>();
  for(String name:randomRunDeckNames(r,new Random())){PaperCard card=paper(name);if(card!=null)cards.add(card);}
  FThreads.invokeInEdtNowOrLater(()->new forge.toolbox.ListChooser<PaperCard>("Run deck · "+cards.size()+" cards · random display order (not library order)",0,1,cards,PaperCard::getName,ignored->{}).show());
 }
 static Deck deckOf(String commander,List<String> cards,String title){
  Deck d=new Deck(title);PaperCard leader=commanderPaper(commander);
  if(leader!=null)d.getOrCreate(DeckSection.Commander).add(leader,1);else d.getOrCreate(DeckSection.Commander).add(commander,1);
  Map<String,Integer> nth=new HashMap<>();
  for(String n:cards){
   PaperCard p=isBasic(n)?basicPaper(commander,n,nth.merge(n,1,Integer::sum)-1):null;   // full-art basics of the commander's set
   if(p==null)p=paper(n);
   if(p!=null)d.getMain().add(p,1);else d.getMain().add(n,1);}   // same printing as shown in Rogue
  return d;
 }
 /** keep a legal singleton list of 70-99 cards: drop duplicates of non-basics, trim the weakest above 99, fill basics below 70 */
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
  migrateArtifactCounter(p);if(p.artifactsCast>=30&&p.bossOneDefeated)award(p,ART_GOAL);   // eased 2026-10-17: 50 + level-10 boss -> 30 + level-5 boss
  if(p.bossDrainKill)award(p,DRAIN_GOAL);
  if(p.bossOneDefeated)award(p,BOSS_GOAL);
  if(p.reachedFinalBoss)award(p,ATRAXA_GOAL);if(p.elvesRemoved>=20)award(p,LATHRIL_GOAL);
  if(p.trueRogueBossOne&&p.creaturesSacrificed>=15)award(p,SEPHIROTH_GOAL);   // eased: 30 -> 15 sacrificesif(p.graveyardPlays>=30)award(p,MULDROTHA_GOAL);
  if(p.killedAngel||p.killedDemon||p.killedDragon)award(p,KAALIA_GOAL);if(p.landsDestroyed>=15)award(p,HEARTHHULL_GOAL);   // eased: any 2 of 3 (Demons only appear very late) / 35 -> 15 lands
  if(p.castWhite>=10&&p.castBlue>=10&&p.castRed>=10&&p.castGreen>=10)award(p,ARAGORN_GOAL);   // eased: 15 -> 10 per colour
  for(Cmd c:content().commanders){int wins=p.best.getOrDefault(c.name,0);if(p.run!=null&&c.name.equals(p.run.commander))wins=Math.max(wins,p.run.wins);
   for(Theme t:c.themes)if(t.progression&&t.cost>0&&(wins>=t.requiredWins||p.packageUnlocks.contains(packageId(c,t))))award(p,"package-ready:"+packageId(c,t));
   for(Skin skin:skins(c.name))if(skin.wins>0&&wins>=skin.wins)award(p,"artwork:"+c.name+":"+skin.id());
  }
  for(String n:p.general)award(p,"card:"+n);
  for(var e:p.signature.entrySet())for(String n:e.getValue())award(p,"signature:"+e.getKey()+":"+n);
 }
 static void award(Profile p,String id){if(!p.achievements.contains(id))p.achievements.add(id);}
 static final String KAALIA="Kaalia of the Vast",KAALIA_GOAL="commander:kaalia",HEARTHHULL="Hearthhull, the Worldseed",HEARTHHULL_GOAL="commander:hearthhull",ARAGORN="Aragorn, the Uniter",ARAGORN_GOAL="commander:aragorn";
 static final String ATRAXA="Atraxa, Praetors' Voice",LATHRIL="Lathril, Blade of the Elves",SEPHIROTH="Sephiroth, Fabled SOLDIER",MULDROTHA="Muldrotha, the Gravetide";
 static final String ATRAXA_GOAL="commander:atraxa",LATHRIL_GOAL="commander:lathril",SEPHIROTH_GOAL="commander:sephiroth",MULDROTHA_GOAL="commander:muldrotha";
 static String commanderGoal(String name){return switch(name){case TEYSA->SAC_GOAL;case BREYA->ART_GOAL;case ERIETTE->DRAIN_GOAL;case SHANG->BOSS_GOAL;
  case ATRAXA->ATRAXA_GOAL;case KAALIA->KAALIA_GOAL;case HEARTHHULL->HEARTHHULL_GOAL;case ARAGORN->ARAGORN_GOAL;case LATHRIL->LATHRIL_GOAL;case SEPHIROTH->SEPHIROTH_GOAL;case MULDROTHA->MULDROTHA_GOAL;default->null;};}
 /** every commander unlocked by an achievement is a Bonus commander - label only, no extra power (user via Codex 2026-10-17) */
 static boolean bonusCommander(Cmd c){return c!=null&&(c.bonus||commanderGoal(c.name)!=null);}
 static boolean commanderOpen(Profile p,Cmd c){String id=commanderGoal(c.name);return id==null||p.achievements.contains(id);}
 static List<RogueGoal> commanderGoals(Profile p){return List.of(
  new RogueGoal(p,SAC_GOAL,"Nothing Goes to Waste","Sacrifice 20 permanents across any number of runs.",TEYSA,p.sacrificedPermanents,20),
  new RogueGoal(p,ART_GOAL,"Etherium Engine","Cast 30 artifact spells across runs AND defeat the first boss (level 5). Boss: "+(p.bossOneDefeated?"defeated":"pending"),BREYA,p.artifactsCast,30),
  new RogueGoal(p,DRAIN_GOAL,"A Charmed Ending","Win a battle by finishing the opponent with life drain or noncombat burn.",ERIETTE,p.bossDrainKill?1:0,1),
  new RogueGoal(p,BOSS_GOAL,"First Boss Down","Defeat the first boss (level 5).",SHANG,p.bossOneDefeated?1:0,1),
  new RogueGoal(p,ATRAXA_GOAL,"Voice of the Praetors","Reach the final boss on floor 15.",ATRAXA,p.reachedFinalBoss?1:0,1),
  new RogueGoal(p,LATHRIL_GOAL,"Blade of the Elves","Remove 20 enemy elves from the battlefield (each win against an elf opponent counts +1).",LATHRIL,p.elvesRemoved,20),
  new RogueGoal(p,SEPHIROTH_GOAL,"One-Winged Angel","Kill the first boss in True Rogue-like mode AND sacrifice 15 creatures across runs. Boss: "+(p.trueRogueBossOne?"defeated":"pending"),SEPHIROTH,p.trueRogueBossOne?p.creaturesSacrificed:Math.min(14,p.creaturesSacrificed),15),
  new RogueGoal(p,MULDROTHA_GOAL,"Return of the Gravetide","Play 30 cards from your graveyard across runs.",MULDROTHA,p.graveyardPlays,30),
  new RogueGoal(p,KAALIA_GOAL,"Angels, Demons and Dragons","Destroy one enemy Angel, Demon or Dragon in a battle. "+(p.killedAngel?"Angel ✓ ":"Angel · ")+(p.killedDemon?"Demon ✓ ":"Demon · ")+(p.killedDragon?"Dragon ✓":"Dragon"),KAALIA,p.killedAngel||p.killedDemon||p.killedDragon?1:0,1),
  new RogueGoal(p,HEARTHHULL_GOAL,"Worldseed","Sacrifice or destroy 15 lands across runs (every land counts, yours or the enemy's).",HEARTHHULL,p.landsDestroyed,15),
  new RogueGoal(p,ARAGORN_GOAL,"The Uniter","Cast 10 white, 10 blue, 10 red and 10 green spells across runs (a multicoloured spell counts for each of its colours). W "+Math.min(10,p.castWhite)+"/10 · U "+Math.min(10,p.castBlue)+"/10 · R "+Math.min(10,p.castRed)+"/10 · G "+Math.min(10,p.castGreen)+"/10",ARAGORN,Math.min(10,p.castWhite)+Math.min(10,p.castBlue)+Math.min(10,p.castRed)+Math.min(10,p.castGreen),40));}
 static String commanderRequirement(Profile p,Cmd c){
  for(RogueGoal g:commanderGoals(p))if(g.id.equals(commanderGoal(c.name)))return g.name+": "+g.description+" ["+Math.min(g.current,g.target)+"/"+g.target+"]";
  return "Available";
 }
 static List<RogueGoal> allGoals(Profile p){
  List<RogueGoal> out=new ArrayList<>(commanderGoals(p));out.addAll(dungeonGoals(p));
  for(Cmd c:content().commanders){int wins=p.best.getOrDefault(c.name,0);if(p.run!=null&&c.name.equals(p.run.commander))wins=Math.max(wins,p.run.wins);
   if(!commanderOpen(p,c))continue;   // hidden commanders stay hidden in the achievements
   for(Theme t:c.themes)if(t.progression&&t.cost>0)out.add(new RogueGoal(p,"package-ready:"+packageId(c,t),c.name.split(",")[0]+" · Package mastery","Win "+t.requiredWins+" duels in one run with this commander.",t.name+" ("+t.cost+" points)",wins,t.requiredWins));
  }
  try{for(String[] row:forge.localinstance.achievements.RogueForgeAchievements.describe(p.forgeAchievements)){
   String key="forge:"+row[0]+":display";if("true".equals(row[4]))award(p,key);
   out.add(new RogueGoal(p,key,row[1],row[2]+" · "+row[3],"Forge trophy", "true".equals(row[4])?1:0,1));
  }}catch(Exception e){log(e);}
  return out;
 }
 private boolean achievementScreen;private int achievementPage;
 /** which golden icon an achievement gets (achievement-icons/<name>.png, drawn by make_achievement_icons.py) */
 static String goalIcon(RogueGoal g){
  String t=((g.name==null?"":g.name)+" "+(g.description==null?"":g.description)).toLowerCase(Locale.ROOT);
  if(t.contains("sacrific")||t.contains("graveyard")||t.contains("dies")||t.contains("death")||t.contains("drain")||t.contains("zombie"))return "skull";
  if(t.contains("artifact")||t.contains("equipment")||t.contains("etherium")||t.contains("vehicle"))return "gear";
  if(t.contains("burn")||t.contains("damage")||t.contains("dragon")||t.contains("fire"))return "flame";
  if(t.contains("land")||t.contains("elf")||t.contains("elves")||t.contains("forest")||t.contains("creature"))return "leaf";
  if(t.contains("life")||t.contains("heal"))return "heart";
  if(t.contains("cast")||t.contains("spell")||t.contains("instant")||t.contains("planeswalker"))return "star";
  if(t.contains("boss")||t.contains("final")||t.contains("throne"))return "crown";
  if(t.contains("package")||t.contains("master")||t.contains("collect")||t.contains("card"))return "book";
  if(t.contains("win")||t.contains("survive")||t.contains("without"))return "shield";
  return "trophy";
 }
 private void buildAchievements(Profile p){
  background(p.selected,.80f);title("ACHIEVEMENTS","Commander unlocks · progress persists across runs");
  button("Back",W()-66,H()-22,56,16,()->{achievementScreen=false;rebuild();});
  List<RogueGoal> goals=allGoals(p);int pages=Math.max(1,(goals.size()+3)/4);achievementPage=Math.max(0,Math.min(achievementPage,pages-1));
  if(pages>1){button("<",12,8,30,16,()->{achievementPage=Math.max(0,achievementPage-1);rebuild();});button(">",W()-42,8,30,16,()->{achievementPage=Math.min(pages-1,achievementPage+1);rebuild();});}
  root.addActor(new Painter((b,a)->text(b,"Page "+(achievementPage+1)+" / "+pages,W()/2-40,18,6,DIM,a,80,Align.center,false)));
  goals=goals.subList(achievementPage*4,Math.min(goals.size(),achievementPage*4+4));float width=W()-24,height=Math.min(48,(H()-72)/4f);
  for(int i=0;i<goals.size();i++){RogueGoal g=goals.get(i);float y=H()-43-(i+1)*height;
   root.addActor(new Painter((b,a)->{
    rect(b,12,y,width,height-3,g.complete?Color.valueOf("183b2a"):DARK,a*.95f);frame(b,12,y,width,height-3,.7f,g.complete?GREEN:DIM,a);
    float is=height-11,tx=18+is+8;Texture icon=fileTexture(DIR+"achievement-icons/"+goalIcon(g)+".png");   // golden icon (user 2026-10-16)
    if(icon!=null){Color o=b.getColor().cpy();float k=g.complete?1:.55f;b.setColor(k,k,k,a*(g.complete?1:.7f));b.draw(icon,17,y+(height-3-is)/2,is,is);b.setColor(o);}
    text(b,(g.complete?"[COMPLETE] ":"[LOCKED] ")+g.name+"  "+Math.min(g.current,g.target)+"/"+g.target,tx,y+height-9,7,g.complete?GREEN:GOLD,a,width-tx+6,Align.left,false);
    text(b,g.description,tx,y+height-21,5.6f,TEXT,a,width-tx+6,Align.left,false);
    text(b,"Unlock: "+(!g.complete&&cmd(g.reward)!=null?"??? (a hidden commander)":g.reward),tx,y+9,6,DIM,a,width-tx+6,Align.left,false);
   }));
  }
 }
 // Observe native events only while an actual Rogue duel is active. Weak keys avoid retaining finished games.
 static final Map<forge.game.Game,DuelProgress> duelProgress=Collections.synchronizedMap(new WeakHashMap<>());
 public static void trackDuel(){
  updateBossMusic();
  Profile p=profile;if(p==null||p.run==null||p.run.duelStart<=0||!"fight".equals(p.run.phase))return;
  try{var view=forge.screens.match.MatchController.instance.getGameView();if(view==null)return;var game=view.getGame();
   if(game==null||game.isGameOver())return;
   synchronized(duelProgress){if(!duelProgress.containsKey(game)){DuelProgress d=new DuelProgress(p.run.duelStart);duelProgress.put(game,d);game.subscribeToEvents(d);game.subscribeToEvents(new LoopWatch(game));}}
   matchIdentity(p,view);
  }catch(Exception e){log(e);}
 }
 private static final Map<String,forge.assets.FImage> rogueAvatars=new HashMap<>();
 /** Players view / avatars of a Rogue duel: the commander's art for the human, the original portrait for the enemy */
 @SuppressWarnings("unchecked") static void matchIdentity(Profile p,forge.game.GameView view){
  try{var avatars=(Map<Object,Object>)(Map<?,?>)Forge.getAssets().avatarImages();
   for(var pv:view.getPlayers()){String key=pv.getLobbyPlayerName();if(key==null)continue;
    Texture t=pv.isAI()?enemyPortrait(p.run.opponent):commanderArtCrop(p.run.commander);if(t==null)continue;
    String id=key+"|"+t.hashCode();forge.assets.FImage own=rogueAvatars.get(id);
    if(own==null){int sq=Math.min(t.getWidth(),t.getHeight());own=new forge.assets.FTextureRegionImage(new TextureRegion(t,(t.getWidth()-sq)/2,0,sq,sq));rogueAvatars.put(id,own);}
    if(avatars.get(key)!=own)avatars.put(key,own);}
  }catch(Throwable t){log(t);}
 }
 /** the human plays Rogue duels under the Rogue profile name; Forge's own player name is restored afterwards */
 private static String savedGuiName;
 static void useRogueName(){
  try{Profile p=profile();if(p.playerName==null||p.playerName.isBlank())return;var gp=forge.player.GamePlayerUtil.getGuiPlayer();
   if(savedGuiName==null)savedGuiName=gp.getName();gp.setName(p.playerName);}catch(Throwable t){log(t);}
 }
 static void restoreGuiName(){
  try{if(savedGuiName!=null){forge.player.GamePlayerUtil.getGuiPlayer().setName(savedGuiName);savedGuiName=null;}}catch(Throwable t){log(t);}
  restoreEnemyNames();
 }
 /** Rogue figures that have no Forge enemy of their own borrow one for the duel (sprite/AI profile only) */
 static final Map<String,String> BASE_ENEMY=Map.of("Niv-Mizzet","Dragon","Wilhelt","Zombie Lord","Ob Nixilis","Archfiend",
  "Gishath","Dinosaur Elite","Kozilek","Eldrazi Devastator","Atraxa","Maelstrom Angel","Heliod","Sun Giant","Iroas","Sun Giant");   // bosses without an own Forge enemy (2026-10-18)   // act 2 bosses without an own Forge enemy (2026-10-18)
 private static final Map<EnemyData,String> enemyNames=new IdentityHashMap<>();
 /** Forge enemies carry a display nameOverride (Bird -> "Griffin Birdcaller"); during a Rogue duel the enemy is called
  *  exactly like the figure the player picked on the map. The original is restored after the duel. */
 static void showAs(EnemyData data,String name){
  if(data==null||name==null||name.isBlank())return;
  if(!enemyNames.containsKey(data))enemyNames.put(data,data.nameOverride==null?"":data.nameOverride);
  data.nameOverride=name.equals(data.name)?"":name;
 }
 static void restoreEnemyNames(){for(var en:enemyNames.entrySet())en.getKey().nameOverride=en.getValue();enemyNames.clear();}
 static final class DuelProgress{
  final long duel;long sacrifices,artifacts;final Set<Integer> lethalDrain=new HashSet<>();final Map<Integer,long[]> damageBatches=new HashMap<>();
  long elves,graveyard,creatureSacs;boolean angel,demon,dragon;long lands,castW,castU,castR,castG;   // Lathril / Muldrotha / Sephiroth / Kaalia unlock progress
  final Set<String> used=new LinkedHashSet<>();   // human cards cast or played as land this duel (merchant encounter)
  DuelProgress(long duel){this.duel=duel;}
  @com.google.common.eventbus.Subscribe public synchronized void zone(forge.game.event.GameEventCardChangeZone e){
   var f=e.from();var t=e.to();var c=e.card();if(c==null||f==null)return;
   boolean leftField=f.zoneType()==forge.game.zone.ZoneType.Battlefield&&(t==null||t.zoneType()!=forge.game.zone.ZoneType.Battlefield);
   if(leftField&&f.player()!=null&&f.player().isAI()&&c.getCurrentState()!=null&&c.getCurrentState().getType().hasCreatureType("Elf"))elves++;
   if(leftField&&t!=null&&t.zoneType()==forge.game.zone.ZoneType.Graveyard&&f.player()!=null&&f.player().isAI()&&c.getCurrentState()!=null){   // destroyed enemy angel/demon/dragon (Kaalia)
    var ty=c.getCurrentState().getType();if(ty.hasCreatureType("Angel"))angel=true;if(ty.hasCreatureType("Demon"))demon=true;if(ty.hasCreatureType("Dragon"))dragon=true;}
   if(leftField&&t!=null&&t.zoneType()==forge.game.zone.ZoneType.Graveyard&&c.getCurrentState()!=null&&c.getCurrentState().getType().isLand())lands++;   // Hearthhull: sacrificed or destroyed lands (both sides)
   if(f.player()!=null&&f.player().isAI()&&c.isCommander()){Profile pr=profile;   // boss voices: casts itself / dies
    if(pr!=null&&pr.run!=null&&pr.run.round%5==0){String boss=pr.run.opponent;
     if(f.zoneType()==forge.game.zone.ZoneType.Command&&t!=null&&t.zoneType()==forge.game.zone.ZoneType.Stack)bossVoice(boss,"commander",0);
     else if(leftField)bossVoice(boss,"death",0);}}
   if(leftField&&f.player()!=null&&!f.player().isAI()&&c.isCommander()){Profile pr=profile;   // commander death line (Codex records "death")
    if(pr!=null&&pr.run!=null){String cmdName=pr.run.commander;Gdx.app.postRunnable(()->voice(cmdName,"death"));}}
   // a land played from the graveyard (Muldrotha, Crucible of Worlds ...)
   if(f.zoneType()==forge.game.zone.ZoneType.Graveyard&&t!=null&&t.player()!=null&&!t.player().isAI()&&c.getCurrentState()!=null
      &&(t.zoneType()==forge.game.zone.ZoneType.Stack||t.zoneType()==forge.game.zone.ZoneType.Battlefield&&c.getCurrentState().getType().isLand()))graveyard++;   // cast or land played from the graveyard
  }
  @com.google.common.eventbus.Subscribe public synchronized void land(forge.game.event.GameEventLandPlayed e){
   if(e.player()!=null&&!e.player().isAI()&&e.land()!=null&&e.land().getName()!=null)used.add(e.land().getName());
  }
  @com.google.common.eventbus.Subscribe public synchronized void sacrificed(forge.game.event.GameEventCardSacrificed e){
   var c=e.card();if(c!=null&&c.getController()!=null&&!c.getController().isAI()&&c.getCurrentState().getType().isPermanent()){sacrifices++;if(c.getCurrentState().getType().isCreature())creatureSacs++;}
  }
  @com.google.common.eventbus.Subscribe public synchronized void cast(forge.game.event.GameEventSpellAbilityCast e){
   if(e.sa()==null||!e.sa().isSpell()||e.si()==null||e.si().getActivatingPlayer()==null||e.si().getActivatingPlayer().isAI())return;
   var c=e.sa().getHostCard();if(c==null||c.isToken()||c.isTokenCard()||c.getGamePieceType()==forge.card.GamePieceType.COPIED_SPELL)return;
   var type=c.getCurrentState().getType();if(type.isArtifact())artifacts++;used.add(c.getName());
   var col=c.getCurrentState()==null?null:c.getCurrentState().getColors();if(col!=null){if(col.hasWhite())castW++;if(col.hasBlue())castU++;if(col.hasRed())castR++;if(col.hasGreen())castG++;}   // Aragorn: spells cast per colour
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
 static boolean drainBossWin(boolean won,int round,boolean lifeLoss,boolean lethal){return won&&lifeLoss&&lethal;}   // Eriette: any battle now, not only bosses (eased 2026-10-17)
 /** One-time migration: old artifact-creature casts are a valid subset of all artifact casts. */
 static void migrateArtifactCounter(Profile p){
  if(!p.artifactCounterMigrated){p.artifactsCast+=Math.max(0,p.artifactCreaturesCast);p.artifactCounterMigrated=true;}
 }
 static void mergeDuelProgress(Profile p,Run r,long duel,long sacrifices,long artifacts,boolean bossBurn,boolean winner){
  if(duel<=0||r.lastAchievementDuel==duel)return;
  r.lastAchievementDuel=duel;migrateArtifactCounter(p);p.sacrificedPermanents+=Math.max(0,sacrifices);p.artifactsCast+=Math.max(0,artifacts);
  if(winner){p.bossOneDefeated|=r.round>=5;p.bossTenDefeated|=r.round>=10;p.bossDrainKill|=bossBurn;}
  updateCommanderAchievements(p);
 }
 static void finishDuelAchievements(Profile p,Run r,boolean winner){
  if(r.duelStart<=0||r.lastAchievementDuel==r.duelStart)return;
  try{var view=forge.screens.match.MatchController.instance.getGameView();if(view==null)return;var game=view.getGame();if(game==null)return;
   DuelProgress d=duelProgress.get(game);boolean burn=false;long sacs=0,arts=0;
   if(d!=null&&d.duel==r.duelStart){synchronized(d){sacs=d.sacrifices;arts=d.artifacts;r.lastUsed=new ArrayList<>(d.used);
    if(r.lastAchievementDuel!=r.duelStart){p.elvesRemoved+=d.elves;p.graveyardPlays+=d.graveyard;p.creaturesSacrificed+=d.creatureSacs;
     p.killedAngel|=d.angel;p.killedDemon|=d.demon;p.killedDragon|=d.dragon;p.landsDestroyed+=d.lands;p.castWhite+=d.castW;p.castBlue+=d.castU;p.castRed+=d.castR;p.castGreen+=d.castG;}
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
  if(p.packageChoices==null)p.packageChoices=new HashMap<>();if(p.neutralExcluded==null)p.neutralExcluded=new HashMap<>();
  if(p.achievements==null)p.achievements=new ArrayList<>();
  p.version=Math.max(4,p.version);updateCommanderAchievements(p);syncDungeonUnlock(p);
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
 /** run rule: pick 2 commander + 3 neutral packages (user 2026-10-18); 3 more of your open packages join each run at random */
 static final int CMD_PICKS=2,NEUTRAL_PICKS=3,RUN_PICKS=CMD_PICKS+NEUTRAL_PICKS,RUN_RANDOM=3,MIN_NEUTRAL_LEFT=4;   // user 2026-10-18: 2 commander + 3 neutral picks (was 2 + 2)
 static final int EXCLUDE_MAX=8,NEUTRAL_SUGGESTED=6;   // exclude list of every commander; neutral packages suggested before a run (user 2026-10-18)
 static int minimumPackages(Cmd c){return RUN_PICKS;}
 static String packageId(Cmd c,Theme t){return c.slug+":"+t.key;}
 static boolean packageOpen(Profile p,Cmd c,Theme t){return !t.progression||t.cost==0||p.packageUnlocks.contains(packageId(c,t));}
 static boolean unlockPackage(Profile p,Cmd c,Theme t){
  initProgression(p);
  if(packageOpen(p,c,t)||p.progressionPoints<t.cost||!p.achievements.contains("package-ready:"+packageId(c,t)))return false;
  p.progressionPoints-=t.cost;p.packageUnlocks.add(packageId(c,t));return true;
 }
 /** Unknown, duplicated and newly locked choices are removed; missing baseline slots are restored. */
 static boolean isNeutral(Theme t){return t!=null&&t.key!=null&&t.key.startsWith("neutral-");}
 static Theme findTheme(Cmd c,String key){for(Theme t:c.themes)if(t.key!=null&&t.key.equals(key))return t;return null;}
 /** Neutral packages excluded for a commander (chosen before a run, saved per commander, user 2026-10-16) never appear
  *  in its runs. Limit by colour count: mono 5, two 6, three 7, four 8, five 9. A commander always keeps at least
  *  MIN_NEUTRAL_LEFT neutral packages, otherwise its exclusions are ignored. */
 static int maxExcluded(Cmd c){return Math.max(0,Math.min(EXCLUDE_MAX,(int)Arrays.stream(c.themes).filter(RogueCommandersScene::isNeutral).count()-MIN_NEUTRAL_LEFT));}
 static List<String> excluded(Profile p,Cmd c){if(p.neutralExcluded==null)p.neutralExcluded=new HashMap<>();return p.neutralExcluded.computeIfAbsent(c.name,k->new ArrayList<>());}
 static boolean isExcluded(Profile p,Cmd c,Theme t){return isNeutral(t)&&p.neutralExcluded!=null&&p.neutralExcluded.getOrDefault(c.name,List.of()).contains(t.key);}
 static boolean neutralHidden(Profile p,Cmd c,Theme t){
  if(!isExcluded(p,c,t))return false;List<String> ex=excluded(p,c);
  long left=Arrays.stream(c.themes).filter(x->isNeutral(x)&&!ex.contains(x.key)).count();
  return left>=MIN_NEUTRAL_LEFT;
 }
 /** this run's random offer of neutral packages: ~70 % of the not excluded ones (at least 4), new with every run */
 /** favorites (added during runs) are always offered in the neutral tab, per commander, across runs (user 2026-10-17) */
 static List<String> favorites(Profile p,Cmd c){if(p.neutralFavorites==null)p.neutralFavorites=new HashMap<>();return p.neutralFavorites.computeIfAbsent(c.name,k->new ArrayList<>());}
 static boolean isFavorite(Profile p,Cmd c,Theme t){return isNeutral(t)&&p.neutralFavorites!=null&&p.neutralFavorites.getOrDefault(c.name,List.of()).contains(t.key);}
 static void toggleFavorite(Profile p,Cmd c,Theme t){if(!isNeutral(t))return;List<String> f=favorites(p,c);if(!f.remove(t.key)){f.add(t.key);excluded(p,c).remove(t.key);}}
 static final int NEUTRAL_RANDOM=6;
 static boolean neutralOffered(Profile p,Cmd c,Theme t){
  return !isNeutral(t)||!isExcluded(p,c,t);   // any neutral package that is not excluded (user 2026-10-18); the screen suggests the best six first
 }
 /** how well a neutral package fits the commander: mean of its ten best cards in the commander's own card scores (EDHREC synergy) */
 static final Map<String,Double> synergyCache=new HashMap<>();
 static double neutralSynergy(Cmd c,Theme t){
  return synergyCache.computeIfAbsent(c.slug+"/"+t.key,k->{List<Integer> v=new ArrayList<>();
   if(t.cards!=null)for(CardRef r:t.cards)if(r!=null)v.add(c.scores!=null&&c.scores.containsKey(r.n)?c.scores.get(r.n):r.s/3);
   v.sort(Comparator.reverseOrder());double sum=0;for(int i=0;i<10;i++)sum+=i<v.size()?v.get(i):0;return sum/10;});
 }
 /** neutral packages shown before a run: favorites, the NEUTRAL_SUGGESTED best fits and whatever is picked already */
 static List<String> suggestedNeutrals(Profile p,Cmd c){
  List<Theme> pool=new ArrayList<>();for(Theme t:c.themes)if(isNeutral(t)&&!isExcluded(p,c,t))pool.add(t);
  pool.sort(Comparator.comparingDouble((Theme t)->-neutralSynergy(c,t)));
  LinkedHashSet<String> out=new LinkedHashSet<>();for(Theme t:pool)if(isFavorite(p,c,t))out.add(t.key);
  int n=0;for(Theme t:pool)if(n<NEUTRAL_SUGGESTED&&!isFavorite(p,c,t)){out.add(t.key);n++;}
  List<String> picks=p.packageChoices.get(c.name);if(picks!=null)for(String k:picks){Theme t=findTheme(c,k);if(isNeutral(t)&&!isExcluded(p,c,t))out.add(k);}
  return new ArrayList<>(out);
 }
 static Map<String,Integer> packagePicks(Profile p){if(p.packagePicks==null)p.packagePicks=new HashMap<>();return p.packagePicks;}
 static Map<String,Integer> packageTaken(Profile p){if(p.packageTaken==null)p.packageTaken=new HashMap<>();return p.packageTaken;}
 static boolean toggleExcluded(Profile p,Cmd c,Theme t){
  if(!isNeutral(t))return false;List<String> ex=excluded(p,c);
  if(ex.remove(t.key))return true;
  if(ex.size()>=maxExcluded(c))return false;
  ex.add(t.key);favorites(p,c).remove(t.key);List<String> picks=p.packageChoices.get(c.name);if(picks!=null)picks.remove(t.key);   // an excluded package can not stay picked
  return true;
 }
 /** best starting pair per commander (always unlocked), chosen for its strategy (user 2026-10-17) */
 static final Map<String,String[]> DEFAULT_PACKAGES=Map.ofEntries(
  Map.entry("yshtola-nights-blessed",new String[]{"spellslinger","control"}),   // big noncreature spells fire Y'shtola, control keeps her alive
  Map.entry("teval-the-balanced-scale",new String[]{"self-mill","graveyard"}),   // fill the yard; cards leaving it make Zombies
  Map.entry("fire-lord-azula",new String[]{"combat-tricks","aggro"}),   // spells copied in combat + attackers to trigger her
  Map.entry("cloud-ex-soldier",new String[]{"equipment","voltron"}),   // equip on entry, draw per equipped creature
  Map.entry("ureni-of-the-unwritten",new String[]{"dragons","ramp"}),   // Dragons from the top eight + mana to get there
  Map.entry("karolina-dean-runaway",new String[]{"burn","legends"}),   // artifact ability engines + five-colour mana sinks
  Map.entry("pantlaza-sun-favored",new String[]{"dinosaurs","discover"}),   // Dinosaurs discover free spells
  Map.entry("jace-multiverse-architect",new String[]{"polymorph","planeswalkers"}),   // his Polymorph plan + superfriends
  Map.entry("the-wise-mothman",new String[]{"mill","plus-1-plus-1-counters"}),   // milled nonlands become +1/+1 counters
  Map.entry("shang-chi-master-of-kung-fu",new String[]{"ability-creatures","mana-dorks"}),   // hasty activated abilities + mana creatures
  Map.entry("teysa-karlov",new String[]{"sacrifice","death-drain"}),   // outlets + doubled death triggers
  Map.entry("breya-etherium-shaper",new String[]{"artifact-fodder","thopters"}),   // cheap machines to recycle + Thopter bodies
  Map.entry("eriette-of-the-charmed-apple",new String[]{"aura-control","aura-drain"}),   // charm their creatures, drain per Aura
  Map.entry("aragorn-the-uniter",new String[]{"gold-spells","soldier-tokens"}),   // multicolour spells fire several triggers
  Map.entry("atraxa-praetors-voice",new String[]{"plus-counters","superfriends"}),   // proliferate counters + loyalty
  Map.entry("hearthhull-the-worldseed",new String[]{"land-sacrifice","land-recursion"}),   // sacrifice lands, replay them, drain
  Map.entry("kaalia-of-the-vast",new String[]{"dragons","angels"}),   // the best things to drop in attacking
  Map.entry("lathril-blade-of-the-elves",new String[]{"elf-tribal","elf-tokens"}),   // Elves + Warrior tokens for the ten-Elf finish
  Map.entry("muldrotha-the-gravetide",new String[]{"self-mill","etb-value"}),   // mill, then recast value creatures every turn
  Map.entry("sephiroth-fabled-soldier",new String[]{"fodder","death-drain"}));   // bodies for Sephiroth to sacrifice + drain payoffs
 static List<String> packageSelection(Profile p,Cmd c){
  migratePackagePreferences(p,c);
  LinkedHashSet<String> out=new LinkedHashSet<>();List<String> saved=p.packageChoices.get(c.name);
  int cmd=0,neu=0;
  if(saved!=null)for(String k:saved)for(Theme t:c.themes)if(t.key.equals(k)&&packageOpen(p,c,t)&&!neutralHidden(p,c,t)&&!out.contains(k)){
   if(isNeutral(t)){if(neu<NEUTRAL_PICKS&&neutralOffered(p,c,t)){out.add(k);neu++;}}else if(cmd<CMD_PICKS){out.add(k);cmd++;}}
  if(saved==null){String[] best=DEFAULT_PACKAGES.get(c.slug);   // first visit: the two packages that fit the commander best (user 2026-10-17)
   if(best!=null)for(String k:best){Theme t=findTheme(c,k);if(t!=null&&cmd<CMD_PICKS&&!isNeutral(t)&&packageOpen(p,c,t)&&!out.contains(k)){out.add(k);cmd++;}}}
  if(saved==null)for(Theme t:c.themes)if(cmd<CMD_PICKS&&!t.progression&&!isNeutral(t)&&!out.contains(t.key)){out.add(t.key);cmd++;}   // fallback: a default commander pair
  return new ArrayList<>(out);
 }
 static boolean togglePackage(Profile p,Cmd c,Theme t){
  if(!packageOpen(p,c,t)||neutralHidden(p,c,t))return false;List<String> selected=packageSelection(p,c);
  if(selected.contains(t.key))selected.remove(t.key);
  else{boolean n=isNeutral(t);List<String> same=new ArrayList<>();for(String k:selected){Theme x=findTheme(c,k);if(x!=null&&isNeutral(x)==n)same.add(k);}
   if(same.size()>=(n?NEUTRAL_PICKS:CMD_PICKS))selected.remove(same.get(0));selected.add(t.key);}   // a third pick of a kind replaces the older one of that kind
  p.packageChoices.put(c.name,selected);return true;
 }
 /** the run's packages: the 4 picks (2 commander + 2 neutral) plus 3 random open, not excluded packages (seeded by the run) */
 static List<String> runPackages(Profile p,Cmd c,List<String> picks,long seed){
  List<String> out=new ArrayList<>(picks),pool=new ArrayList<>();
  for(Theme t:c.themes)if(!picks.contains(t.key)&&packageOpen(p,c,t)&&!neutralHidden(p,c,t)&&t.cards!=null&&t.cards.length>=6)pool.add(t.key);
  Collections.shuffle(pool,new Random(seed^0x5ac4L));
  for(int i=0;i<Math.min(RUN_RANDOM,pool.size());i++)out.add(pool.get(i));
  return out;
 }
 /** Retired package keys are mapped once; cards already earned and balances are untouched. */
 static List<String> currentPackageKeys(Cmd c,List<String> keys){
  LinkedHashSet<String> out=new LinkedHashSet<>();if(keys!=null)for(String old:keys){
   String key=findTheme(c,old)!=null?old:c.packageAliases==null?null:c.packageAliases.get(old);
   if(key!=null&&findTheme(c,key)!=null)out.add(key);
  }return new ArrayList<>(out);
 }
 static void migratePackagePreferences(Profile p,Cmd c){
  if(c.packageAliases==null)return;
  if(p.packageChoices!=null&&p.packageChoices.containsKey(c.name)){
   List<String> old=p.packageChoices.get(c.name),mapped=currentPackageKeys(c,old);
   if(!mapped.equals(old)){
    int cmd=0,neu=0;for(String k:mapped){if(isNeutral(findTheme(c,k)))neu++;else cmd++;}
    for(Theme t:c.themes)if(!mapped.contains(t.key)&&packageOpen(p,c,t)&&!isExcluded(p,c,t)){
     if(isNeutral(t)){if(neu<NEUTRAL_PICKS){mapped.add(t.key);neu++;}}else if(cmd<CMD_PICKS){mapped.add(t.key);cmd++;}}
    p.packageChoices.put(c.name,mapped);
   }
  }
  if(p.neutralExcluded!=null&&p.neutralExcluded.containsKey(c.name)){
   List<String> ex=currentPackageKeys(c,p.neutralExcluded.get(c.name));ex.removeIf(k->!isNeutral(findTheme(c,k)));
   p.neutralExcluded.put(c.name,new ArrayList<>(ex.subList(0,Math.min(ex.size(),maxExcluded(c)))));
  }
  if(p.neutralFavorites!=null&&p.neutralFavorites.containsKey(c.name))p.neutralFavorites.put(c.name,currentPackageKeys(c,p.neutralFavorites.get(c.name)));
 }
 static void migrateRunPackages(Cmd c,Run r){
  if(c.packageAliases==null)return;Profile p=profile();migratePackagePreferences(p,c);
  boolean retired=r.selectedPackages==null||r.selectedPackages.stream().anyMatch(k->findTheme(c,k)==null);
  if(!retired)return;
  List<String> old=currentPackageKeys(c,r.chosenPackages!=null?r.chosenPackages:r.selectedPackages);
  List<String> picks=new ArrayList<>();int cmd=0,neu=0;
  List<String> available=new ArrayList<>(old);for(Theme t:c.themes)if(!available.contains(t.key))available.add(t.key);
  for(String k:available){Theme t=findTheme(c,k);if(t==null||!packageOpen(p,c,t)||isExcluded(p,c,t))continue;
   if(isNeutral(t)){if(neu<NEUTRAL_PICKS){picks.add(k);neu++;}}else if(cmd<CMD_PICKS){picks.add(k);cmd++;}}
  r.chosenPackages=picks;r.selectedPackages=runPackages(p,c,picks,r.seed);
 }
 /** Current loadouts stay fixed except when a removed package requires migration. */
 static List<Theme> activeThemes(Cmd c,Run r){
  migrateRunPackages(c,r);
  List<Theme> out=new ArrayList<>();
  for(Theme t:c.themes)if(r.selectedPackages==null?!t.progression:r.selectedPackages.contains(t.key))out.add(t);
  return out;
 }
 static void progressionAfterRun(Profile p,Run r){
  initProgression(p);int earned=r.wins*5;
  String[] ids={"first-victory","boss-five","boss-ten","complete-run","three-wins:"+r.commander};
  boolean[] done={r.wins>=1,r.wins>=5,r.wins>=10,r.completed,r.wins>=3};int[] bonus={5,10,15,25,5};
  for(int i=0;i<ids.length;i++)if(done[i]&&!p.achievements.contains(ids[i])){p.achievements.add(ids[i]);earned+=bonus[i];}
  p.progressionPoints+=(int)Math.round(earned*UNLOCK_PERCENT/100.0*completionRewardMultiplier(r));
 }
 /** Selection is drawn directly, with a persistent filled checkbox, coloured surface and status text. */
 static final Map<String,Texture> roomTextures=new HashMap<>();static final Set<String> missingRooms=new HashSet<>();
 /** rogue-commanders/rooms/<room>.jpg (Codex art); null = fall back to the dungeon stage art */
 static Texture roomTexture(String room){
  if(room==null||missingRooms.contains(room))return null;Texture t=roomTextures.get(room);if(t!=null)return t;
  var f=Gdx.files.absolute(DIR+"rooms/"+room+".jpg");if(!f.exists()){missingRooms.add(room);return null;}
  try{t=new Texture(f,true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);roomTextures.put(room,t);return t;}
  catch(Exception e){log(e);missingRooms.add(room);return null;}
 }
 static final Map<String,Texture> roomSprites=new HashMap<>();static final Set<String> missingSprites=new HashSet<>();
 static Texture roomSprite(String room){
  if(room==null||missingSprites.contains(room))return null;Texture t=roomSprites.get(room);if(t!=null)return t;
  var f=Gdx.files.absolute(DIR+"rooms/sprites/"+room+".png");if(!f.exists()){missingSprites.add(room);return null;}
  try{t=new Texture(f,true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);roomSprites.put(room,t);return t;}
  catch(Exception e){log(e);missingSprites.add(room);return null;}
 }
 static void drawSprite(Batch b,String room,float cx,float cy,float size,float a){
  Texture t=roomSprite(room);if(t==null)return;Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(t,cx-size/2,cy-size/2,size,size);b.setColor(o);
 }
 static void drawCover(Batch b,Texture t,float x,float y,float w,float h,float a){
  float ta=t.getWidth()/(float)t.getHeight(),sa=w/h;int sw=t.getWidth(),sh=t.getHeight(),sx=0,sy=0;
  if(ta>sa){sw=Math.round(t.getHeight()*sa);sx=(t.getWidth()-sw)/2;}else{sh=Math.round(t.getWidth()/sa);sy=(t.getHeight()-sh)/2;}
  Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(t,x,y,w,h,sx,sy,sw,sh,false,false);b.setColor(o);
 }
 /** Low-cost ambient motes behind portraits/text; contained inside each room card. */
 static void roomParticles(Batch b,String room,float x,float y,float w,float h,float time,float a){
  if(room==null||w<8||h<8)return;
  int kind=switch(room){
   case "fire-temple","dragon-lair"->0;
   case "mystic-forest","jungle","backyard"->1;
   case "grotto"->2;
   case "crypt","graveyard"->3;
   case "void"->4;
   case "aerie","ruins"->5;
   case "workshop"->6;
   case "hive"->7;
   case "cathedral"->8;
   case "castle","library","den","warren"->9;
   default->9;
  };
  Color old=b.getColor().cpy();int src=b.getBlendSrcFunc(),dst=b.getBlendDstFunc(),sa=b.getBlendSrcFuncAlpha(),da=b.getBlendDstFuncAlpha();
  try{
   Texture dot=glowTexture();b.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,com.badlogic.gdx.graphics.GL20.GL_ONE);
   int count=kind==4?30:20;int salt=room.hashCode();
   for(int i=0;i<count;i++){
    float seed=hash(i,salt),speed=kind==0||kind==6?.14f:kind==5?.065f:.035f;
    float phase=(time*speed*(.6f+hash(i,83))+seed)%1f;
    float u=hash(i,41+salt),v=phase;
    if(kind==1||kind==7){u+=.035f*(float)Math.sin(time*(kind==7?1.1f:.7f)+i);v=.5f+.4f*(float)Math.sin(time*.21f+i*2.3f);}
    else if(kind==4){float theta=time*.15f+i*2.4f;u=.5f+.43f*(float)Math.cos(theta)*(1-phase*.6f);v=.5f+.43f*(float)Math.sin(theta)*(1-phase*.6f);}
    else if(kind==5){u=phase;v=hash(i,9)+.05f*(float)Math.sin(time*.5f+i);}
    else u+=.025f*(float)Math.sin(time*.6f+i);
    u=Math.max(.03f,Math.min(.97f,u));v=Math.max(.03f,Math.min(.97f,v));
    float size=Math.max(.35f,Math.min(w,h)*(.003f+.003f*hash(i,7)));
    float alpha=a*.48f*(float)Math.sin(phase*Math.PI);
    // Fade below the artwork to leave descriptions and controls quiet.
    alpha*=Math.min(1,Math.max(0,(v-.25f)*3));
    float r=1,g=.8f,blue=.5f;
    switch(kind){case 0:case 6:g=.35f+.35f*seed;blue=.08f;break;case 1:case 7:r=.65f;g=1;blue=.28f;break;case 2:r=.3f;g=.75f;blue=1;break;case 3:r=.35f;g=.85f;blue=.7f;break;case 4:r=.65f;g=.35f;blue=1;break;case 5:r=.75f;g=.8f;blue=.9f;break;case 8:r=1;g=.9f;blue=.65f;break;default:break;}
    b.setColor(r,g,blue,alpha);float px=x+u*w,py=y+v*h;
    b.draw(dot,px-size,py-size,size*2,size*2);
   }
  }finally{b.setBlendFunctionSeparate(src,dst,sa,da);b.setColor(old);}
 }
 /** width of a bold (title font) label - plates sized with chipWidth cut the first and last letter (user 2026-10-18) */
 static float boldWidth(String s,float size){BitmapFont f=font(true);f.getData().setScale(size/FONT_PX);return new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,s).width+9;}
 static float chipWidth(String s,float size){BitmapFont f=font(false);f.getData().setScale(size/FONT_PX);return new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,s).width+9;}
 /** tag chip colour; the automatic warning tags stand out (user 2026-10-18: plan artifact / enchantment removal, know stax) */
 static Color tagColor(String tag){return switch(tag){case "Stax"->Color.valueOf("ff8f6b");case "Artifacts"->Color.valueOf("c9d2dc");case "Enchantments"->Color.valueOf("f0a6e0");
  default->tag.endsWith(" Tribal")?GOLD:Color.valueOf("9cc2e6");};}
 /** A dungeon path as a room: the room art fills the card, the enemy stands in front, tags say what it plays. */
 class RoomCard extends Actor{
  final OpponentSpec spec;final TextureRegion avatar;final List<DungeonLine> info;final String side;final boolean selected;final ClickListener click;
  RoomCard(Run r,String id,OpponentSpec d,float x,float y,float w,float h,boolean selected,String side,TextureRegion avatar,List<DungeonLine> info){
   spec=d;this.avatar=avatar;this.info=info;this.side=side;this.selected=selected;setBounds(x,y,w,h);
   click=new ClickListener(){@Override public void clicked(InputEvent e,float xx,float yy){if(blocked(RoomCard.this))return;chooseRoute(r,id);save();rebuild();}};
   addListener(click);
  }
  @Override public void draw(Batch b,float a){
   float x=getX(),y=getY(),w=getWidth(),h=getHeight(),cx=x+w/2;
   Texture t=roomTexture(spec.room);
   if(t!=null)drawCover(b,t,x,y,w,h,a);else forge.adventure.util.DungeonMapArt.background(b,x,y,w,h,time,a);
   roomParticles(b,spec.room,x,y,w,h,time,a);
   rect(b,x,y,w,h,DARK,a*(selected?.12f:.30f));
   for(int k=0;k<10;k++)rect(b,x,y,w,h*.58f*(10-k)/10f,Color.BLACK,a*.075f);   // calm lower part for the text
   for(int k=0;k<6;k++)rect(b,x,y+h-h*.16f*(6-k)/6f,w,h*.16f*(6-k)/6f,Color.BLACK,a*.06f);
   if(click.isOver())rect(b,x,y,w,h,Color.WHITE,a*.035f);
   frame(b,x,y,w,h,selected?2f:.8f,selected?GOLD:Color.valueOf("3b3a46"),a);
   if(selected)frame(b,x-2,y-2,w+4,h+4,1,GOLD,a*(.25f+.15f*(float)Math.sin(time*3)));
   float em=Math.min(30,Math.min(w,h)*.16f);drawSprite(b,spec.room,x+w-em/2-5,y+h-em/2-5,em,a);
   float top=y+h-7;
   top-=text(b,side+(selected?"  ·  SELECTED":""),x+6,top,6.6f,selected?GOLD:Color.valueOf("c9c1b0"),a,w-12,Align.center,true)+5;
   float size=Math.min(w*.52f,h*.40f);
   Texture own=enemyPortrait(spec.enemy);
   if(own!=null||avatar!=null){float px=cx-size/2,py=top-size;rect(b,px-2,py-2,size+4,size+4,Color.BLACK,a*.55f);
    Color o=b.getColor().cpy();b.setColor(1,1,1,a);if(own!=null)b.draw(own,px,py,size,size);else b.draw(avatar,px,py,size,size);b.setColor(o);frame(b,px-1,py-1,size+2,size+2,1,selected?GOLD:DIM,a*.9f);top=py-6;}
   top-=text(b,spec.enemy,x+6,top,10.5f,GOLD,a,w-12,Align.center,true)+5;
   String[] tags=spec.tags==null?new String[0]:spec.tags;float cs=6.6f,chH=12,gap=3;
   List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();float used=0;
   for(String tag:tags){float cw=chipWidth(tag,cs);if(!row.isEmpty()&&used+gap+cw>w-14){rows.add(row);row=new ArrayList<>();used=0;}used+=(row.isEmpty()?0:gap)+cw;row.add(tag);}
   if(!row.isEmpty())rows.add(row);
   for(List<String> line:rows){float total=-gap;for(String tag:line)total+=chipWidth(tag,cs)+gap;float xx=cx-total/2;
    for(String tag:line){float cw=chipWidth(tag,cs);Color c=tagColor(tag);rect(b,xx,top-chH,cw,chH,Color.valueOf("0e1017"),a*.82f);frame(b,xx,top-chH,cw,chH,.6f,c,a*.9f);
     centerText(b,tag,xx,top-chH,cw,chH,cs,c,a,false);xx+=cw+gap;}
    top-=chH+gap;}
   top-=3;
   for(DungeonLine l:info){Color lc=l.color()==DIM?Color.valueOf("c9c1b0"):l.color();
    if(l.label().startsWith("\u2665")){String rest=l.label().substring(1);int cut=rest.indexOf(' ');int life=Integer.parseInt(cut<0?rest:rest.substring(0,cut));
     top-=lifeLine(b,life,cut<0?"":rest.substring(cut),x+6,top,w-12,l.size()+1,lc,a,true)+3;}
    else if(l.label().startsWith("Starts with:"))top-=boxedText(b,l.label(),x+6,top,w-12,l.size()+1,Color.valueOf("ffb43c"),Color.valueOf("ffc766"),a,true)+4;
    else top-=text(b,l.label(),x+6,top,l.size()+1,lc,a,w-12,Align.center,l.heading())+3;}
   if(!selected)text(b,"Tap to choose this path",x+6,y+12,6.2f,Color.valueOf("c9c1b0"),a*(.55f+.35f*(float)Math.sin(time*2.5f)),w-12,Align.center,false);
  }
 }
 // Optional hand-authored metadata. Keys are commander slug + "/" + theme key.
 static class PackageArt{String image,shortName;String[] tags;}
 static Map<String,PackageArt> packageArtMetadata;
 static Map<String,PackageArt> packageArtMetadata(){packageArt(null,null);return packageArtMetadata;}
 /** theme or shared (generic) package key for a package name as stored in run.picked */
 static String packageKeyByName(Cmd c,String name){
  if(c!=null)for(Theme t:c.themes)if(t.name.equals(name))return t.key;
  for(Generic g:content().generic)if(g.name.equals(name))return g.key;
  return null;
 }
 static PackageArt packageArt(Cmd c,Theme t){
  if(packageArtMetadata==null){packageArtMetadata=new HashMap<>();try{
   File f=new File(DIR+"package-art/packages.json");if(f.isFile()){
    java.lang.reflect.Type type=new com.google.gson.reflect.TypeToken<Map<String,PackageArt>>(){}.getType();
    Map<String,PackageArt> loaded=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),type);
    if(loaded!=null)packageArtMetadata.putAll(loaded);
   }
  }catch(Exception e){log(e);}}
  return c==null||t==null?null:packageArtMetadata.get(c.slug+"/"+t.key);
 }
 static Texture packageIllustration(PackageArt art){
  // Assets must be simple filenames inside images, never arbitrary metadata paths.
  if(art==null||art.image==null||!art.image.matches("[A-Za-z0-9_-]+\\.(png|jpg|jpeg)"))return null;
  return fileTexture(DIR+"package-art/images/"+art.image);
 }
 class PackageTile extends Actor{
  final Cmd commander;final Theme theme;boolean excludeMode;
  PackageTile(Cmd c,Theme t,float x,float y,float w,float h){commander=c;theme=t;setBounds(x,y,w,h);
   addListener(new ClickListener(){@Override public void clicked(InputEvent e,float xx,float yy){
    if(blocked(PackageTile.this))return;
    if(excludeMode){neverAgain(profile(),c,t);return;}   // allow again / exclude / swap dialog when the list is full (user 2026-10-18)
    if(isExcluded(profile(),c,t)){toast("Excluded for this commander - tap Allow to use it again.");return;}
    if(!packageOpen(profile(),c,t)){toast("Unlock with "+t.cost+" Ancient Relics after "+t.requiredWins+" wins in one run.");return;}
    togglePackage(profile(),c,t);
    save();rebuild();
   }});
  }
  @Override public void draw(Batch b,float a){
   Profile p=profile();boolean excluded=isExcluded(p,commander,theme),open=packageOpen(p,commander,theme)&&!excluded,selected=excludeMode?excluded:packageSelection(p,commander).contains(theme.key);
   if(excludeMode){drawExclude(b,a,excluded);return;}
   float x=getX(),y=getY(),w=getWidth(),h=getHeight();
   PackageArt art=packageArt(commander,theme);Texture image=packageIllustration(art);
   Color old=b.getColor().cpy();b.setColor(1,1,1,a);tablet(selected?1:0).draw(b,x,y,w,h);b.setColor(old);
   float iw=Math.min(h-4,w*.29f),ix=x+2,iy=y+2;
   if(image!=null){drawCover(b,image,ix,iy,iw,h-4,a*(open?1:.45f));
    for(int f=0;f<6;f++)rect(b,ix+iw-6+f,iy,1,h-4,DARK,a*f/7f);
   }else{
    rect(b,ix,iy,iw,h-4,Color.valueOf("202940"),a);
    centerText(b,"*",ix,iy,iw,h-4,Math.min(16,h*.4f),GOLD,a*.65f,true);
   }
   float tx=ix+iw+4,available=x+w-tx-5;
   String name=art!=null&&art.shortName!=null&&!art.shortName.isBlank()?art.shortName:theme.name;
   text(b,name,tx,y+h-4,packageTitleSize(name,available-10,Math.max(9,h*.36f)),open?TEXT:DIM,a,available-10,Align.left,true);
   if(art!=null&&art.tags!=null&&art.tags.length>0){
    float tagY=y+Math.max(17,h*.42f),tagW=tagY<y+23?available-(open?TILE_CARDS_W:TILE_UNLOCK_W)-6:available;   // low tiles: the tag line shares the row with the button
    String tags=String.join(" · ",Arrays.copyOf(art.tags,Math.min(3,art.tags.length)));
    if(art.tags.length>2&&fitSingleLine(tags,4.6f,tagW,false)<4.6f*.8f)tags=art.tags[0]+" · "+art.tags[1];   // two readable tags instead of three tiny ones
    if(art.tags.length>1&&fitSingleLine(tags,4.6f,tagW,false)<4.6f*.8f)tags=art.tags[0];
    text(b,tags,tx,tagY,fitSingleLine(tags,4.6f,tagW,false),Color.valueOf("a8bcef"),a,tagW,Align.left,false);
   }
   boolean fav=isFavorite(p,commander,theme);
   String state=excluded?"EXCLUDED":selected?(fav?"FAVORITE  ·  SELECTED":"SELECTED"):open?(fav?"FAVORITE  ·  CHOOSE":"CHOOSE"):"LOCKED";
   int pk=packagePicks(p).getOrDefault(packageId(commander,theme),0),tk=packageTaken(p).getOrDefault(packageId(commander,theme),0);
   String stats=pk+tk==0?"":"Picked "+pk+"x  ·  Taken "+tk+"x";   // over all runs (user 2026-10-18)
   float room=available-(open?TILE_CARDS_W:TILE_UNLOCK_W)-4,stateW=boldWidth(state,4.6f)+4;   // the state keeps its size, the statistics take what is left
   float stw=stats.isEmpty()?0:Math.min(room-stateW-4,chipWidth(stats,4.2f));if(stw<chipWidth(stats,4.2f)*.8f)stw=0;
   float sw=Math.max(8,room-stw-(stw>0?4:0));
   text(b,state,tx,y+10,fitSingleLine(state,4.6f,sw,true),fav?GOLD:selected?Color.valueOf("b6d8ff"):DIM,a,sw,Align.left,true);
   if(stw>0)text(b,stats,x+w-(open?TILE_CARDS_W:TILE_UNLOCK_W)-8-stw,y+10,fitSingleLine(stats,4.2f,stw,false),Color.valueOf("8f9bb8"),a,stw,Align.right,false);
   int hc=huntCount(p,commander,theme);if(hc>0)text(b,"Hunt "+hc+"/"+packageSize(theme),x+w-46,y+24,4.8f,Color.valueOf("ffb43c"),a,42,Align.right,true);   // hunted cards in this package (user 2026-10-17)
   if(selected){frame(b,x+.5f,y+.5f,w-1,h-1,1,Color.valueOf("a59ae8"),a);float cx=x+w-9,cy=y+h-10;
    disc(b,cx,cy,3.5f,Color.valueOf("5668ad"),a);line(b,cx-1.7f,cy,cx-.4f,cy-1.3f,.8f,TEXT,a,false);line(b,cx-.4f,cy-1.3f,cx+2,cy+1.5f,.8f,TEXT,a,false);
   }
   else if(!open){float cx=x+w-12,cy=y+h-12;frame(b,cx,cy,6,5,.8f,DIM,a);frame(b,cx+1,cy+4,4,3,.8f,DIM,a);}

  }
  /** Exclude tab: red when excluded for this commander, click toggles */
  void drawExclude(Batch b,float a,boolean excluded){   // same art/name/tags as the pick tabs (user 2026-10-17), red when excluded
   float x=getX(),y=getY(),w=getWidth(),h=getHeight();
   PackageArt art=packageArt(commander,theme);Texture image=packageIllustration(art);
   Color old=b.getColor().cpy();b.setColor(1,1,1,a);tablet(0).draw(b,x,y,w,h);b.setColor(old);
   if(excluded)rect(b,x,y,w,h,Color.valueOf("3a1418"),a*.55f);
   float iw=Math.min(h-4,w*.29f),ix=x+2,iy=y+2;
   if(image!=null){drawCover(b,image,ix,iy,iw,h-4,a*(excluded?.4f:1));
    for(int f=0;f<6;f++)rect(b,ix+iw-6+f,iy,1,h-4,DARK,a*f/7f);
   }else{
    rect(b,ix,iy,iw,h-4,Color.valueOf("202940"),a);
    centerText(b,"*",ix,iy,iw,h-4,Math.min(16,h*.4f),GOLD,a*.65f,true);
   }
   if(excluded){rect(b,ix,iy,iw,h-4,RED,a*.25f);centerText(b,"X",ix,iy,iw,h-4,Math.min(22,h*.5f),RED,a*.95f,true);}
   float tx=ix+iw+4,available=x+w-tx-5;
   String name=art!=null&&art.shortName!=null&&!art.shortName.isBlank()?art.shortName:theme.name;
   text(b,name,tx,y+h-4,packageTitleSize(name,available-10,Math.max(9,h*.36f)),excluded?Color.valueOf("ffb3b3"):TEXT,a,available-10,Align.left,true);
   if(art!=null&&art.tags!=null&&art.tags.length>0){
    String tags=String.join(" · ",Arrays.copyOf(art.tags,Math.min(3,art.tags.length)));
    text(b,tags,tx,y+Math.max(17,h*.42f),fitSingleLine(tags,4.6f,available,false),Color.valueOf("a8bcef"),a*(excluded?.6f:1),available,Align.left,false);
   }
   String state=excluded?"EXCLUDED - never appears":"INCLUDED";float sw=Math.max(8,available-44);
   text(b,state,tx,y+10,fitSingleLine(state,4.6f,sw,true),excluded?RED:DIM,a,sw,Align.left,true);
   if(excluded)frame(b,x+.5f,y+.5f,w-1,h-1,1.4f,RED,a);
  }

 }
 static int packageColumns(float width){return Math.max(1,(int)((width-20)/150));}
 static int packageColumns(float width,float height,int count){return Math.max(packageColumns(width),(count+packageRows(height)-1)/packageRows(height));}
 static float packageTitleSize(String name,float width,float height){
  float size=6.5f;BitmapFont face=font(true);
  while(size>3.5f){face.getData().setScale(size/FONT_PX);var layout=new com.badlogic.gdx.graphics.g2d.GlyphLayout(face,name,Color.WHITE,width,Align.left,true);if(layout.height<=height)break;size-=.25f;}
  return size;
 }
 boolean neutralShowAll;   // neutral tab: all packages instead of the best six (user 2026-10-18)
 static int packageRows(float height){return Math.max(1,(int)((height-112)/34));}
 private void buildPackages(Cmd c){
  if(!commanderOpen(profile(),c)){packageConfig=null;buildSelect(profile());return;}
  Profile p=profile();List<String> selected=packageSelection(p,c);
  Texture lib=fileTexture(DIR+"menu-art/package-selection-library-v1.png");   // library hall behind the package choice (user, Codex art 2026-10-17)
  if(lib!=null)root.addActor(new Painter((bb,aa)->{drawCover(bb,lib,0,0,W(),H(),aa);rect(bb,0,0,W(),H(),Color.BLACK,aa*.28f);}));else background(c.name,.86f);
  long cmdPicked=selected.stream().filter(k->!isNeutral(findTheme(c,k))).count(),neuPicked=selected.size()-cmdPicked;
  long neutralLeft=Arrays.stream(c.themes).filter(t->isNeutral(t)&&!neutralHidden(p,c,t)).count();int neuNeed=(int)Math.min(NEUTRAL_PICKS,neutralLeft);
  title("CHOOSE "+RUN_PICKS+" PACKAGES","Commander "+cmdPicked+" / "+CMD_PICKS+"  ·  Neutral "+neuPicked+" / "+NEUTRAL_PICKS);
  {Btn hb=button("Hunting list ("+huntList(p,c).size()+")",W()-14-122-120,H()-40,116,15,()->{huntPage=0;showHuntList(c);});hb.gold=true;}   // own button instead of hidden in the Compendium (user 2026-10-18)
  root.addActor(new Painter((b,a)->{relicAmount(b,p.progressionPoints,W()-14,H()-10,8,a);text(b,"Ancient Relics",W()-160,H()-24,5.4f,Color.valueOf("c9c1b0"),a,146,Align.right,false);}));
  float tabW=(W()-36)/3;
  Btn tabC=button("Commander packages  "+cmdPicked+"/"+CMD_PICKS,12,H()-56,tabW,15,()->{packageTab=0;packagePage=0;rebuild();});tabC.gold=packageTab==0;
  Btn tabN=button("Neutral packages  "+neuPicked+"/"+NEUTRAL_PICKS,18+tabW,H()-56,tabW,15,()->{packageTab=1;packagePage=0;rebuild();});tabN.gold=packageTab==1;
  Btn tabX=button("Exclude  "+excluded(p,c).size()+"/"+maxExcluded(c),24+tabW*2,H()-56,tabW,15,()->{packageTab=2;packagePage=0;rebuild();});tabX.gold=packageTab==2;
  List<Theme> ordered=new ArrayList<>(Arrays.asList(c.themes));
  List<String> suggested=suggestedNeutrals(p,c);
  if(packageTab==0)ordered.removeIf(t->isNeutral(t));else if(packageTab==1)ordered.removeIf(t->!isNeutral(t)||isExcluded(p,c,t)||(!neutralShowAll&&!suggested.contains(t.key)));else ordered.removeIf(t->!isNeutral(t)||!isExcluded(p,c,t));   // only what you excluded during runs; tap = allow again (user 2026-10-17)
  ordered.sort(Comparator.comparing((Theme t)->isExcluded(p,c,t)).thenComparing(t->!isFavorite(p,c,t)).thenComparing(t->!packageOpen(p,c,t))
   .thenComparingDouble(t->isNeutral(t)?-neutralSynergy(c,t):0));   // favorites first, then the best fits for this commander
  if(packageTab==1){long more=Arrays.stream(c.themes).filter(t->isNeutral(t)&&!isExcluded(p,c,t)&&!suggested.contains(t.key)).count();
   if(more>0||neutralShowAll)button(neutralShowAll?"Show suggested":"Show more ("+more+")",12,58,112,22,()->{neutralShowAll=!neutralShowAll;packagePage=0;rebuild();});}
  int cols=W()>=420?3:2;float gap=4,tw=(W()-24-(cols-1)*gap)/cols,top=H()-64;int desiredRows=Math.max(1,(ordered.size()+cols-1)/cols);float th=Math.max(34,Math.min(56,(top-84+gap)/desiredRows-gap));
  int rows=Math.max(1,(int)((top-84+gap)/(th+gap))),perPage=cols*rows,pages=Math.max(1,(ordered.size()+perPage-1)/perPage);
  packagePage=Math.max(0,Math.min(packagePage,pages-1));int first=packagePage*perPage;
  for(int i=first;i<Math.min(ordered.size(),first+perPage);i++){
   Theme t=ordered.get(i);int k=i-first;float x=12+(k%cols)*(tw+gap),y=top-(k/cols+1)*th-(k/cols)*gap;
   PackageTile tile=new PackageTile(c,t,x,y,tw,th);tile.excludeMode=packageTab==2;root.addActor(tile);
   boolean open=packageOpen(p,c,t);
   if(open)button("Cards",x+tw-TILE_CARDS_W-4,y+3,TILE_CARDS_W,11,()->showPackageCards(c,t));   // smaller (tester 2026-10-19)
   else if(!isNeutral(t))button("Unlock "+t.cost+" relics",x+tw-TILE_UNLOCK_W-4,y+3,TILE_UNLOCK_W,11,()->{
    if(unlockPackage(p,c,t)){save();toast("Package unlocked. You can now select it.");rebuild();}
    else toast("Needs "+t.cost+" Ancient Relics and "+t.requiredWins+" wins in one run with this commander.");
   });
   // exclusions have their own tab now
  }
  if(pages>1){final int pg=packagePage,pc=pages;   // pages instead of tiny tiles
   button("<",W()/2-112,58,66,22,()->{packagePage=Math.max(0,pg-1);rebuild();}).disabled=pg==0;   // bigger arrows (user 2026-10-17)
   root.addActor(new Painter((b,a)->centerText(b,"Page "+(pg+1)+" / "+pc,W()/2-42,60,84,18,7.4f,Color.valueOf("c9c1b0"),a,false)));
   button(">",W()/2+46,58,66,22,()->{packagePage=Math.min(pc-1,pg+1);rebuild();}).disabled=pg>=pc-1;}
  root.addActor(new Painter((b,a)->hintLine(b,packageTab==0?"MORE PACKAGES unlock with Ancient Relics + achievements. Your picks are saved for the next runs.":packageTab==1?(neutralShowAll?"Every neutral package, best fits for this commander first - pick "+NEUTRAL_PICKS+".":"The "+NEUTRAL_SUGGESTED+" neutral packages that fit this commander best, plus your favorites - pick "+NEUTRAL_PICKS+". Show more lists all."):(ordered.isEmpty()?"Nothing excluded yet. During a run, press Never again on a reward package you dislike (max "+maxExcluded(c)+").":"Excluded during your runs (max "+maxExcluded(c)+") - tap a package to allow it again."),12,46,GOLD,a)));
  button("Back",12,10,84,22,()->{packageConfig=null;compendiumOpen=false;rebuild();});
  button("Random",W()-130-96,10,88,22,()->randomPackages(p,c));   // random 2 commander + 3 neutral picks
  button("Help",202,10,48,22,this::explainPackages);   // right after Compendium   // wide enough for the painted caps
  Btn start=button("START RUN",W()-130,10,118,22,()->{packageConfig=null;compendiumOpen=false;startRun(c);});start.gold=true;start.disabled=cmdPicked!=CMD_PICKS||neuPicked<neuNeed;
  button("Compendium",102,10,96,22,()->{compendiumPage=0;showCompendium(c);});   // search cards across all packages (user 2026-10-17)
  if(!p.packagesExplained){p.packagesExplained=true;setTutorialSeen(p,"hunt",true);save();explainPackages();}   // first visit: how the game works with packages (hunting list included)
  else if(!tutorialSeen(p,"hunt")){maybeGuide("hunt");save();}   // players who knew the old screen meet the hunting list once (user 2026-10-18)
  else if(compendiumOpen)showCompendium(c);   // stays open after Pick / Unpick
 }
 /** random valid choice: CMD_PICKS open commander packages + NEUTRAL_PICKS neutrals offered this run (never excluded ones) */
 private void randomPackages(Profile p,Cmd c){
  List<Theme> cmd=new ArrayList<>(),neu=new ArrayList<>();
  List<String> sugg=suggestedNeutrals(p,c);   // random neutrals come from the suggested best fits
  for(Theme t:c.themes){if(!packageOpen(p,c,t)||isExcluded(p,c,t)||neutralHidden(p,c,t))continue;if(isNeutral(t)){if(sugg.contains(t.key))neu.add(t);}else cmd.add(t);}
  Collections.shuffle(cmd);Collections.shuffle(neu);List<String> pick=new ArrayList<>();
  for(int i=0;i<Math.min(CMD_PICKS,cmd.size());i++)pick.add(cmd.get(i).key);
  for(int i=0;i<Math.min(NEUTRAL_PICKS,neu.size());i++)pick.add(neu.get(i).key);
  p.packageChoices.put(c.name,pick);save();rebuild();toast("Random packages picked - tap Random again for another mix.");
 }
 static final String[][] PACKAGE_HELP={
  {"YOUR DECK GROWS DURING THE RUN","Your commander starts with a small starter deck. Packages decide which cards you can win on the way up."},
  {"PICK 5 PACKAGES","2 Commander packages (built around your commander's strategy) and 3 Neutral packages (general themes such as ramp, removal or card draw). The neutral tab suggests the 6 that fit your commander best - Show more lists all."},
  {"REWARDS","After every won battle you choose one reward package. Its cards go to your bench - open Edit deck to put them into your deck. Every reward offers at least one of your 4 picks."},
  {"RARITY","Normal, Rare, Epic and Legendary cards. Skipping a reward upgrades the next one. Tap Cards on a package to see everything it can give you."},
  {"UNLOCKS & FAVORITES","More packages unlock with Ancient Relics and achievements. On the reward screen, Add to favorites keeps a neutral package always in your choice; Never again hides it for this commander."},
  {"THE CLIMB","15 floors, bosses on floors 5, 10 and 15. One loss ends the run - relics and unlocks stay. Not sure? Press Random."}};
 /** controls tour in the first battle (ArenaBattleHudMod.Tour): due once per profile, only in a Rogue run */
 public static boolean battleTourDue(){Profile p=profile;return p!=null&&p.run!=null&&!p.battleTourDone;}
 public static void setBattleTourDone(){Profile p=profile;if(p!=null&&!p.battleTourDone){p.battleTourDone=true;save();}}
 /** one hint line under the package grid; shrinks to fit the screen width instead of running off both edges */
 void hintLine(Batch b,String s,float x,float y,Color c,float a){float w=W()-2*x;text(b,s,x,y,fitSingleLine(s,5.8f,w,false),c,a,w,Align.center,false);}
 private void explainPackages(){List<String[]> all=new ArrayList<>(Arrays.asList(PACKAGE_HELP));all.addAll(Arrays.asList(GUIDES.get("hunt")));guideOverlay("HOW PACKAGES WORK",all.toArray(new String[0][]));}
 /** a short first-time guide: title + up to six (heading, text) blocks in two columns, "Got it" closes */
 private void guideOverlay(String title,String[][] items){guideOverlay(title,items,0);}
 /** a short first-time guide, one tip per page: Back / Next / Skip, arrow keys work too (user 2026-10-17) */
 private void guideOverlay(String title,String[][] items,int page){
  final int pg=Math.max(0,Math.min(items.length-1,page));
  Group g=pushOverlay();float w=Math.min(W()-40,340),h=Math.min(H()-40,150),x=(W()-w)/2,y=(H()-h)/2;
  Runnable back=()->{if(pg>0){closeOverlay();guideOverlay(title,items,pg-1);}};
  Runnable next=()->{closeOverlay();if(pg<items.length-1)guideOverlay(title,items,pg+1);};
  g.addActor(new Painter((b,a)->{
   rect(b,0,0,W(),H(),Color.BLACK,a*.55f);rect(b,x,y,w,h,Color.valueOf("0d1220"),a*.96f);frame(b,x,y,w,h,1.2f,GOLD,a);
   text(b,title,x,y+h-9,8,DIM,a,w,Align.center,true);
   text(b,items[pg][0],x+14,y+h-30,10,GOLD,a,w-28,Align.center,true);
   text(b,items[pg][1],x+18,y+h-52,7,TEXT,a,w-36,Align.center,false);
   text(b,(pg+1)+" / "+items.length+"   ·   arrow keys: back / next   ·   Settings > Tutorials shows it again",x,y+34,5,DIM,a,w,Align.center,false);
  }));
  Btn bk=new Btn("Back",x+10,y+8,70,18,back);bk.disabled=pg==0;g.addActor(bk);
  Btn sk=new Btn("Skip",x+w/2-35,y+8,70,18,this::closeOverlay);g.addActor(sk);
  Btn nx=new Btn(pg<items.length-1?"Next":"Got it",x+w-80,y+8,70,18,next);nx.gold=true;g.addActor(nx);
  Actor keys=new Actor();keys.setBounds(0,0,0,0);
  keys.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){@Override public boolean keyDown(InputEvent e,int key){
   if(key==com.badlogic.gdx.Input.Keys.RIGHT||key==com.badlogic.gdx.Input.Keys.ENTER||key==com.badlogic.gdx.Input.Keys.SPACE){next.run();return true;}
   if(key==com.badlogic.gdx.Input.Keys.LEFT){back.run();return true;}
   if((key==com.badlogic.gdx.Input.Keys.ESCAPE||key==com.badlogic.gdx.Input.Keys.BACK)){closeOverlay();return true;}return false;}});
  g.addActor(keys);if(keys.getStage()!=null)keys.getStage().setKeyboardFocus(keys);
 }
 // ------------------------------------------------------------------ tutorials (user 2026-10-17): every guide once, resettable in Settings > Tutorials
 static final String[] TUTORIAL_IDS={"select","packages","hunt","map","run","reward","deck","battle"};
 static final String[] TUTORIAL_NAMES={"Commander selection","Package choice","Hunting list","Dungeon map","Before the fight","Rewards","Deck & bench","Battle controls"};
 static final Map<String,String[][]> GUIDES=Map.of(
  "select",new String[][]{{"YOUR COMMANDER","Each commander has its own strategy, starter deck and packages. Tap one in the row below to see it; Artwork changes its picture."},
   {"LOCKED COMMANDERS","Commanders with a ? unlock through achievements - tap one to see what it needs."},
   {"TRUE ROGUE-LIKE","In the header: life you lose stays lost for the whole run. For experts - no extra rewards."},
   {"PROGRESS","Achievements and Collection show your unlocks, heirlooms and signature cards."},
   {"START RUN","Next you pick 4 packages - the themes your rewards come from."}},
  "map",new String[][]{{"THE CLIMB","You climb from the bottom to the next boss at the top. Every place is one opponent; the lit roads show where you can go next."},
   {"CHOOSE","Tap a place to see the opponent: deck style, starting life and why it stands in your way. Take this path decides."},
   {"SYMBOLS","? unknown event, $ treasure, E elite (harder, reward one tier better - Epic from floor 9), ^ campfire (True Rogue-like only)."},
   {"BOSSES","Bosses wait on floors 5, 10 and 15. Edit deck opens your deck at any time."}},
  "run",new String[][]{{"NEXT OPPONENT","Its deck style, starting life and hand size. Bosses talk back and some start with extra cards."},
   {"FIGHT","Starts the battle. One loss ends the run."},
   {"YOUR RUN","The strip at the top shows your act and floor, the row below your packages."},
   {"EDIT DECK","Change your deck before the fight - new cards wait on the bench."}},
  "reward",new String[][]{{"CHOOSE ONE","Pick one package - its cards go to your bench."},
   {"WHAT A PACKAGE HOLDS","5 cards. Normal: 3 Normal + 2 Rare (5%: one Epic). Rare: 1 Normal + 3 Rare + 1 Epic (5%: a second Epic). Epic: 5 Epic (20%: one Legendary). Legendary: 4 Legendary + 1 Epic."},
   {"SKIP","Skipping upgrades one option of the next reward a tier, one more per skip in a row. Epic rewards start at floor 9 - an Epic bonus waits until then."},
   {"FAVORITES","Add to favorites: this neutral package is always in your package choice in future runs."},
   {"NEVER AGAIN","It never appears again for this commander. Up to 8 per commander - when the list is full, you choose one to allow again."},
   {"HUNT","\"Hunt 2/40\" counts cards from your hunting list (package screen > Hunting list); hunted cards here carry a gold HUNTED tag."}},
  "deck",new String[][]{{"DECK",tx("The cards you play this run. Right-click a card to move it to the bench.","The cards you play this run. Long-press a card to move it to the bench.")},
   {"BENCH",tx("Every card you won but are not playing. Right-click to put it into the deck.","Every card you won but are not playing. Long-press it to put it into the deck.")},
   {"NEW CARDS","Rewards always land on the bench first - you decide what goes in. NEW marks your last reward."},
   {"SIZE","70-99 cards plus your commander; missing slots are filled with basic lands. Tap a card to see it big."}},
  "hunt",new String[][]{{"YOUR HUNTING LIST","Every commander starts with 10 hunted cards: its combo pieces and strongest synergy cards. Open Hunting list to see them - remove or add cards any time."},
   {"PICK THE RIGHT PACKAGES","Every package shows Hunt 2/40: how many of your hunted cards it holds. Packages with many hunted cards bring them into your rewards."},
   {"GRAB THEM","On the reward screen your hunted cards carry a gold HUNTED tag."}});
 static boolean tutorialSeen(Profile p,String id){
  if("packages".equals(id))return p.packagesExplained;if("battle".equals(id))return p.battleTourDone;
  return p.tutorialsSeen!=null&&p.tutorialsSeen.contains(id);
 }
 static void setTutorialSeen(Profile p,String id,boolean seen){
  if("packages".equals(id))p.packagesExplained=seen;else if("battle".equals(id))p.battleTourDone=seen;
  else{if(p.tutorialsSeen==null)p.tutorialsSeen=new HashSet<>();if(seen)p.tutorialsSeen.add(id);else p.tutorialsSeen.remove(id);}
  save();
 }
 /** shows a screen's guide the first time it is opened */
 void maybeGuide(String id){Profile p=profile();if(tutorialSeen(p,id)||!GUIDES.containsKey(id))return;setTutorialSeen(p,id,true);String[][] g=GUIDES.get(id);
  guideOverlay(TUTORIAL_NAMES[Arrays.asList(TUTORIAL_IDS).indexOf(id)].toUpperCase(Locale.ROOT),g);}
 private void buildTutorialSettings(){
  Profile p=profile();float width=Math.min(360,W()-24),x=(W()-width)/2,rowH=21,top=H()-66;
  for(int i=0;i<TUTORIAL_IDS.length;i++){final String id=TUTORIAL_IDS[i];final String name=TUTORIAL_NAMES[i];float y=top-i*rowH;boolean seen=tutorialSeen(p,id);
   root.addActor(new Painter((b,a)->{text(b,name,x,y+13,6.4f,TEXT,a,width*.5f,Align.left,false);text(b,tutorialSeen(p,id)?"seen":"will show",x+width*.5f,y+13,5.6f,tutorialSeen(p,id)?DIM:GOLD,a,width*.2f,Align.left,false);}));
   Btn r=button("Show again",x+width*.7f,y,width*.3f,16,()->{setTutorialSeen(p,id,false);toast(name+": the guide shows again next time.");rebuild();});r.disabled=!seen;}
  Btn all=button("Reset all tutorials",x+width/2-80,40,160,18,()->{for(String id:TUTORIAL_IDS)setTutorialSeen(p,id,false);toast("All tutorials show again.");rebuild();});all.gold=true;
  root.addActor(new Painter((b,a)->text(b,"A guide appears the first time you open each screen. Reset one if you forgot how something works.",12,23,5.5f,DIM,a,W()-24,Align.center,false)));
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
  for(String cat:SHOP_CATEGORIES)stock.addAll(rollCategory(r,c,cat,rng,previous));
  return stock;
 }
 static List<CardRef> rollCategory(Run r,Cmd c,String cat,Random rng,Set<String> previous){
  List<CardRef> pool=new ArrayList<>();Set<String> seen=new HashSet<>();
  for(CardRef x:shopCatalog())if(cat.equals(x.cat)&&seen.add(x.n)&&shopEligible(x,c,r))pool.add(x);
  Collections.shuffle(pool,rng);
  pool.sort(Comparator.comparingInt(x->r.deck.contains(x.n)||bench(r).contains(x.n)?2:previous.contains(x.n)?1:0));
  return new ArrayList<>(pool.subList(0,Math.min(6,pool.size())));
 }
 /** Reopening/reloading is free and stable; rerolling one category (6 cards) costs two banked gold. */
 static void stockShop(Run r,Cmd c){
  initRunEconomy(r);if(r.shopRound==r.round&&r.shopCatalogVersion==SHOP_VERSION&&!r.shopStock.isEmpty())return;
  r.shopRound=r.round;r.shopRerolls=0;r.shopCatalogVersion=SHOP_VERSION;r.shopStock=rollShop(r,c,0,Set.of());
 }
 static boolean rerollShop(Profile p,Run r,Cmd c,String cat){
  if(r==null||!"fight".equals(r.phase)||r.duelStart>0||p.goldBalance<REROLL_COST)return false;
  stockShop(r,c);Set<String> previous=new HashSet<>();for(CardRef x:r.shopStock)if(cat.equals(x.cat))previous.add(x.n);
  int n=r.shopRerolls+1;Random rng=new Random(r.seed^((long)r.round*0x5a09L)^((long)n*0x731f95L)^((long)cat.hashCode()*0x9e3779b9L));
  List<CardRef> fresh=rollCategory(r,c,cat,rng,previous);if(fresh.isEmpty())return false;
  List<CardRef> next=new ArrayList<>();boolean placed=false;
  for(CardRef x:r.shopStock){if(!cat.equals(x.cat)){next.add(x);continue;}if(!placed){next.addAll(fresh);placed=true;}}
  if(!placed)next.addAll(fresh);
  p.goldBalance-=REROLL_COST;r.shopRerolls=n;r.shopStock=next;return true;
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
  Profile p=profile();title("DIVINE FAVOR SHOP · "+p.goldBalance+" FAVOR","Divine Favor carries between runs. Bought cards last only this run.");
  // rows = card + 9 price button + 4 gap; the column reroll button sits at 36..47 above the bottom bar
  float gap=7,pw=(W()-24-gap*2)/3,top=H()-49,bottom=51;
  float cw=Math.min((pw-15)/2,Math.max(10,(top-15-bottom-9-2*13)/3)*.716f),ch=cw/.716f;
  for(int cat=0;cat<3;cat++){
   String category=SHOP_CATEGORIES[cat];float px=12+cat*(pw+gap);
   root.addActor(new Painter((b,a)->text(b,category,px,top+8,7,GOLD,a,pw,Align.center,true)));
   int slot=0;for(CardRef x:r.shopStock)if(category.equals(x.cat)){
    int col=slot%2,row=slot/2;slot++;float xx=px+(pw-2*cw-5)/2+col*(cw+5),yy=top-15-ch-row*(ch+13);
    CardView v=new CardView(x.n,xx,yy,cw,ch);root.addActor(v);
    boolean owned=r.deck.contains(x.n)||bench(r).contains(x.n),bought=r.shopBought.contains(x.n);
    Btn buy=button(bought?"Bought":owned?"Owned":x.price+" favor",xx,yy-9,cw,9,()->{
     if(buyShop(p,r,x)){save();toast("Bought "+x.n+(r.deck.contains(x.n)?" — added to deck.":" — on your bench (deck full)."));rebuild();}
    });buy.disabled=owned||bought||p.goldBalance<x.price;buy.gold=!buy.disabled;
   }
   if(slot<6)root.addActor(new Painter((b,a)->text(b,"Not enough compatible cards in this catalogue.",px,bottom+12,5,DIM,a,pw,Align.center,false)));
   Btn reroll=button(pw<120?"Reroll · 2":"Reroll "+category+" · 2 favor",px+pw*.1f,36,pw*.8f,11,()->{if(rerollShop(p,r,c,category)){save();rebuild();}});
   reroll.disabled=p.goldBalance<REROLL_COST;
  }
  button("Back",12,10,84,21,()->{shopOpen=false;save();rebuild();});
  root.addActor(new Painter((b,a)->text(b,"Boss rewards: 6 / 10 / 15 Divine Favor · All lands: 6 favor",88,24,5.5f,GOLD,a,W()-212,Align.center,false)));
  button("Edit deck",W()-112,10,100,21,()->showRunDeck(r));
 }

 // ------------------------------------------------------------------ card images (Forge downloads missing art on demand)
 private static final Map<String,String> imageKeys=new HashMap<>();private static final Set<String> requested=new HashSet<>();
 private static volatile boolean fetchedSomething;private static long lastClear;
 static Texture cardTexture(String name){
  if(SKINS.containsKey(name)){Skin sk=selectedSkin(profile(),name);if(sk!=null&&sk.custom())return customCard(name,sk);return cardTexture(commanderPaper(name));}
  PaperCard basic=isBasic(name)?basicPaper(activeCommander(),name,0):null;
  String key=basic!=null?basic.getImageKey(false):imageKeys.computeIfAbsent(name,n->{PaperCard p=paper(n);return p==null?"":p.getImageKey(false);});
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
   :new String[]{ROOT+"_mods/modern-hud/ui.ttf",w+"seguisb.ttf",w+"segoeui.ttf",w+"arial.ttf"};   // ui.ttf = Cinzel (SIL OFL) for all text (user 2026-10-17)
  for(String p:c)if(new File(p).exists())return p;
  return null;
 }
 private static BitmapFont gen(String path){
  var g=new FreeTypeFontGenerator(Gdx.files.absolute(path));var p=new FreeTypeFontGenerator.FreeTypeFontParameter();
  p.size=(int)FONT_PX;p.minFilter=Texture.TextureFilter.Linear;p.magFilter=Texture.TextureFilter.Linear;
  p.characters=FreeTypeFontGenerator.DEFAULT_CHARS+"äöüÄÖÜßǵ–—·…’‘“”";
  BitmapFont f=g.generateFont(p);g.dispose();f.setUseIntegerPositions(false);return f;
 }
 // Cool fantasy silverwork, midnight enamel and softly bevelled corners; no baked wood textures.
 // variant: 0 normal, 1 hover, 2 pressed, 3 disabled
 private static final NinePatch[] frames=new NinePatch[4];
 static NinePatch panel(){return tablet(0);}
 private static final NinePatch[] buttonFrames=new NinePatch[4];
 /** buttons after the user's concept art (2026-10-17): dark stone, outer silver rim, dark gap, fine inner silver line,
  *  clipped corners with an L-bracket and a small diamond. variant 0 normal, 1 hover, 2 pressed, 3 disabled */
 static NinePatch buttonFrame(int variant){
  if(buttonFrames[variant]!=null)return buttonFrames[variant];
  int s=128,bd=32;Pixmap p=new Pixmap(s,s,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  float lift=variant==1?1.25f:variant==2?.8f:1f,alpha=variant==3?.6f:.96f;
  Color rimHi=Color.valueOf(variant==1?"dfe6f5":"b7c0d4"),rimLo=Color.valueOf("5d6680"),gap=Color.valueOf("07080c");
  for(int y=0;y<s;y++)for(int x=0;x<s;x++){
   int ex=Math.min(x,s-1-x),ey=Math.min(y,s-1-y),e=Math.min(ex,ey);
   if(ex+ey<7)continue;                                                     // clipped corner
   float t=1-y/(float)(s-1),n=(float)((Math.sin(x*12.9898+y*78.233)*43758.5453)%1+1)%1;   // fine stone grain
   float g=(.85f+.3f*t)*(.94f+.12f*n)*lift;
   Color c=new Color(.085f*g,.095f*g,.13f*g,alpha);
   int d=Math.min(e,ex+ey-7);                                               // distance to the bevelled outline
   if(d<=2)c=d==0?rimLo.cpy():rimHi.cpy().lerp(rimLo,d*.35f);              // outer silver rim (light top, darker edge)
   else if(d<=5)c=gap.cpy();                                                // dark channel
   else if(d==6)c=rimLo.cpy().lerp(rimHi,.45f);                             // fine inner silver line
   if(d<=6&&y>s/2&&d!=4&&d!=5){c.mul(.82f,.82f,.82f,1);}                   // lower half of the frame slightly darker
   c.a=d<=6?Math.min(1,alpha+.04f):alpha;p.setColor(c);p.drawPixel(x,y);
  }
  // corner ornaments: L-bracket inside each corner plus a small silver diamond
  for(int cx=0;cx<2;cx++)for(int cy=0;cy<2;cy++){
   int ox=cx==0?9:s-10,oy=cy==0?9:s-10,dx=cx==0?1:-1,dy=cy==0?1:-1;
   p.setColor(rimHi);
   for(int i=0;i<14;i++){p.drawPixel(ox+dx*i,oy);p.drawPixel(ox,oy+dy*i);p.drawPixel(ox+dx*i,oy+dy);p.drawPixel(ox+dx,oy+dy*i);}
   int qx=ox+dx*6,qy=oy+dy*6;
   for(int yy=-3;yy<=3;yy++)for(int xx=-3;xx<=3;xx++)if(Math.abs(xx)+Math.abs(yy)<=3){p.setColor(Math.abs(xx)+Math.abs(yy)<=1?Color.valueOf("8fa8ff"):rimHi);p.drawPixel(qx+xx,qy+yy);}
  }
  Texture tex=new Texture(p);p.dispose();tex.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
  NinePatch n=new NinePatch(tex,bd,bd,bd,bd);n.scale(.17f,.17f);buttonFrames[variant]=n;return n;
 }
 private static final NinePatch[] tablets=new NinePatch[4];
 /** boxes as weathered ruin tablets (user 2026-10-17): same colours as panel(variant), but chipped irregular edges,
  *  hairline cracks and stone grain in the border - brightness only, no colour change. Buttons keep panel(v). */
 static NinePatch tablet(int variant){
  if(tablets[variant]!=null)return tablets[variant];
  int s=160,bd=40;Pixmap p=new Pixmap(s,s,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  float lift=variant==1?1.20f:variant==2?.82f:1f,alpha=variant==3?.58f:.94f;
  Random rnd=new Random(0x5EEDL+variant);
  // chip depth along each of the four edges (smooth value noise, 0..7 px), deeper bites near the corners
  float[][] chip=new float[4][s];
  for(int e=0;e<4;e++){float[] k=new float[s/8+2];for(int i=0;i<k.length;i++)k[i]=rnd.nextFloat();
   for(int i=0;i<s;i++){float f=i/8f;int i0=(int)f;float t=f-i0;t=t*t*(3-2*t);float v=k[i0]*(1-t)+k[i0+1]*t;
    float corner=Math.max(0,1-Math.min(i,s-1-i)/22f);chip[e][i]=v*v*11f+corner*corner*16f*(.4f+.6f*rnd.nextFloat());}}
  float[][] grain=new float[s][s];{float[][] g=new float[s/4+2][s/4+2];for(float[] r:g)for(int i=0;i<r.length;i++)r[i]=rnd.nextFloat();
   for(int y=0;y<s;y++)for(int x=0;x<s;x++){float fx=x/4f,fy=y/4f;int x0=(int)fx,y0=(int)fy;float tx=fx-x0,ty=fy-y0;
    float v=(g[y0][x0]*(1-tx)+g[y0][x0+1]*tx)*(1-ty)+(g[y0+1][x0]*(1-tx)+g[y0+1][x0+1]*tx)*ty;grain[y][x]=v+(rnd.nextFloat()-.5f)*.35f;}}
  Color rim=Color.valueOf(variant==1?"a5b9e5":variant==3?"424c65":"697a9e");
  for(int y=0;y<s;y++)for(int x=0;x<s;x++){
   float d=Math.min(Math.min(x-chip[0][y],s-1-x-chip[1][y]),Math.min(y-chip[2][x],s-1-y-chip[3][x]));   // distance to the broken edge
   if(d<0)continue;
   float t=1-y/(float)(s-1),edgeZone=Math.max(0,1-Math.min(Math.min(x,s-1-x),Math.min(y,s-1-y))/(float)bd);
   float shade=1+(grain[y][x]-.5f)*1.1f*edgeZone;   // stone grain only towards the rim
   float r=(15+13*t)*lift/255f*shade,g=(20+14*t)*lift/255f*shade,bl=(35+22*t)*lift/255f*shade;
   if(d<3.2f){float w=.55f+.7f*grain[y][x];r=rim.r*w;g=rim.g*w;bl=rim.b*w;}                       // worn rim along the break
   else if(d<7f){float k=.45f+.55f*(d-3.2f)/3.8f;r*=k;g*=k;bl*=k;}                                                        // shadow inside the rim
   p.setColor(Math.min(1,r),Math.min(1,g),Math.min(1,bl),alpha);p.drawPixel(x,y);
  }
  // hairline cracks running in from the edges (darker line + faint light line beside it)
  for(int c=0;c<16;c++){int e=rnd.nextInt(4);float px=e==0?3:e==1?s-4:12+rnd.nextFloat()*(s-24),py=e==2?3:e==3?s-4:12+rnd.nextFloat()*(s-24);
   float dx=e==0?1:e==1?-1:(rnd.nextFloat()-.5f),dy=e==2?1:e==3?-1:(rnd.nextFloat()-.5f);int len=16+rnd.nextInt(30);
   for(int i=0;i<len;i++){dx+=(rnd.nextFloat()-.5f)*.6f;dy+=(rnd.nextFloat()-.5f)*.6f;float l=(float)Math.sqrt(dx*dx+dy*dy);px+=dx/l;py+=dy/l;
    int ix=Math.round(px),iy=Math.round(py);if(ix<1||iy<1||ix>=s-1||iy>=s-1)break;
    int edge=Math.min(Math.min(ix,s-1-ix),Math.min(iy,s-1-iy));if(edge>bd-4)break;
    int c0=p.getPixel(ix,iy);if((c0&0xff)==0)continue;Color k=new Color(c0);p.setColor(k.r*.3f,k.g*.3f,k.b*.3f,k.a);p.drawPixel(ix,iy);p.drawPixel(ix,iy+1);
    int c1=p.getPixel(ix+1,iy-1);if((c1&0xff)!=0){Color k1=new Color(c1);p.setColor(Math.min(1,k1.r*1.35f),Math.min(1,k1.g*1.35f),Math.min(1,k1.b*1.35f),k1.a);p.drawPixel(ix+1,iy-1);}}}
  Texture tex=new Texture(p);p.dispose();tex.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
  NinePatch n=new NinePatch(tex,bd,bd,bd,bd);n.scale(.16f,.16f);tablets[variant]=n;return n;
 }
 static NinePatch panel(int variant){
  if(frames[variant]!=null)return frames[variant];
  int s=48;Pixmap p=new Pixmap(s,s,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  float lift=variant==1?1.20f:variant==2?.82f:1f,alpha=variant==3?.58f:.94f;
  for(int y=0;y<s;y++)for(int x=0;x<s;x++){
   int edge=Math.min(Math.min(x,s-1-x),Math.min(y,s-1-y));
   if((x<5&&y<5&&x+y<5)||(x>s-6&&y<5&&s-1-x+y<5)||(x<5&&y>s-6&&x+s-1-y<5)||(x>s-6&&y>s-6&&2*s-2-x-y<5))continue;
   float t=1-y/(float)(s-1);p.setColor((15+13*t)*lift/255f,(20+14*t)*lift/255f,(35+22*t)*lift/255f,alpha);p.drawPixel(x,y);
   if(edge==1){p.setColor(Color.valueOf(variant==1?"a5b9e5":variant==3?"424c65":"697a9e"));p.drawPixel(x,y);}
   if(edge==3){p.setColor(new Color(.55f,.65f,.88f,variant==3?.08f:.18f));p.drawPixel(x,y);}
  }
  // Small engraved corner flourishes survive nine-slice stretching.
  p.setColor(Color.valueOf(variant==3?"49526a":"aab9d6"));
  p.drawLine(5,7,10,7);p.drawLine(7,5,7,10);p.drawLine(s-11,7,s-6,7);p.drawLine(s-8,5,s-8,10);
  p.drawLine(5,s-8,10,s-8);p.drawLine(7,s-11,7,s-6);p.drawLine(s-11,s-8,s-6,s-8);p.drawLine(s-8,s-11,s-8,s-6);
  Texture tex=new Texture(p);p.dispose();tex.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
  NinePatch n=new NinePatch(tex,12,12,12,12);n.scale(.28f,.28f);frames[variant]=n;return n;
 }
 private static Texture glowTex;
 static Texture glowTexture(){
  if(glowTex!=null)return glowTex;int n=64;Pixmap p=new Pixmap(n,n,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  for(int y=0;y<n;y++)for(int x=0;x<n;x++){float dx=(x-n/2f+.5f)/(n/2f),dy=(y-n/2f+.5f)/(n/2f),r=(float)Math.sqrt(dx*dx+dy*dy);float a=r>=1?0:(float)Math.pow(1-r,2.2f);p.setColor(1,1,1,a);p.drawPixel(x,y);}
  glowTex=new Texture(p);p.dispose();glowTex.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);return glowTex;
 }
 static final Map<String,Texture> portalLayers=new HashMap<>();static final Set<String> missingPortal=new HashSet<>();
 static Texture portalLayer(String name){
  if(missingPortal.contains(name))return null;Texture t=portalLayers.get(name);if(t!=null)return t;
  var f=Gdx.files.absolute(DIR+"portal/"+name+".png");if(!f.exists()){missingPortal.add(name);return null;}
  try{t=new Texture(f,true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);portalLayers.put(name,t);return t;}
  catch(Exception e){log(e);missingPortal.add(name);return null;}
 }
 /** Animated portal behind the commander select screen (Codex layers: glow, vortex, frame), tinted by the commander. */
 static com.badlogic.gdx.graphics.glutils.ShaderProgram universeShader;
 static boolean universeShaderTried;
 private void universePortalBackground(){
  root.addActor(new Painter((b,a)->{
   Texture art=portalLayer("portal-universe-v1");if(art==null)return;
   if(!universeShaderTried){
    universeShaderTried=true;
    String vs="attribute vec4 a_position; attribute vec4 a_color; attribute vec2 a_texCoord0; uniform mat4 u_projTrans; varying vec4 v_color; varying vec2 v_uv; void main(){v_color=a_color;v_color.a*=255.0/254.0;v_uv=a_texCoord0;gl_Position=u_projTrans*a_position;}";
    String fs="#ifdef GL_ES\nprecision mediump float;\n#endif\nvarying vec4 v_color;varying vec2 v_uv;uniform sampler2D u_texture;uniform float u_time;void main(){vec2 center=vec2(.500,.362);vec2 axes=vec2(.116,.182);vec2 q=(v_uv-center)/axes;float r=length(q);float angle=u_time*.13;float cs=cos(angle),sn=sin(angle);vec2 rotated=vec2(cs*q.x-sn*q.y,sn*q.x+cs*q.y);vec2 drift=vec2(0.0);float island1=1.0-smoothstep(.55,1.0,length((v_uv-vec2(.052,.210))/vec2(.047,.105)));float island2=1.0-smoothstep(.55,1.0,length((v_uv-vec2(.931,.362))/vec2(.054,.120)));float island3=1.0-smoothstep(.5,1.0,length((v_uv-vec2(.966,.578))/vec2(.026,.065)));drift.y=.0035*sin(u_time*.48)*island1+.004*sin(u_time*.39+2.1)*island2+.0025*sin(u_time*.61+4.0)*island3;drift.x=.001*sin(u_time*.31+1.0)*(island1-island2);vec4 base=texture2D(u_texture,v_uv+drift);vec4 swirl=texture2D(u_texture,center+rotated*axes);float mask=1.0-smoothstep(.72,1.0,r);gl_FragColor=mix(base,swirl,mask)*v_color;}";
    universeShader=new com.badlogic.gdx.graphics.glutils.ShaderProgram(vs,fs);
    if(!universeShader.isCompiled()){Gdx.app.error("UniversePortal",universeShader.getLog());universeShader.dispose();universeShader=null;}
   }
   float w=W(),h=H();Color old=b.getColor().cpy();var previous=b.getShader();
   int src=b.getBlendSrcFunc(),dst=b.getBlendDstFunc(),srcA=b.getBlendSrcFuncAlpha(),dstA=b.getBlendDstFuncAlpha();
   try{
    if(universeShader!=null){b.setShader(universeShader);universeShader.setUniformf("u_time",time);}
    drawCover(b,art,0,0,w,h,a);b.setShader(previous);
    // Match cover cropping, so particles remain anchored at every aspect ratio.
    float scale=Math.max(w/art.getWidth(),h/art.getHeight()),aw=art.getWidth()*scale,ah=art.getHeight()*scale;
    float cx=w*.5f,cy=(h-ah)*.5f+ah*(1-.362f),rx=aw*.116f,ry=ah*.182f;
    Texture dot=glowTexture();b.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,com.badlogic.gdx.graphics.GL20.GL_ONE);
    for(int i=0;i<80;i++){
     float seed=hash(i,29),phase=(time*(.035f+.035f*hash(i,11))+seed)%1f;
     float angle=seed*6.283185f+time*.16f+phase*3f,rad=1.1f-.96f*phase;
     float x=cx+(float)Math.cos(angle)*rx*rad,y=cy+(float)Math.sin(angle)*ry*rad;
     float sz=Math.max(1.2f,h*.002f)*(1+hash(i,7));float alpha=a*.65f*(float)Math.sin(phase*Math.PI);
     b.setColor(i%3==0?.65f:.25f,i%3==0?.35f:.85f,1,alpha);b.draw(dot,x-sz,y-sz,sz*2,sz*2);
    }
   }finally{b.setShader(previous);b.setBlendFunctionSeparate(src,dst,srcA,dstA);b.setColor(old);}
   rect(b,0,0,w,h,DARK,a*.15f);
  }));
 }
 private void portalBackground(Cmd c){
  root.addActor(new Painter((b,a)->{
   float w=W(),h=H(),cx=w*.5f,cy=h*.47f,R=Math.min(w*.42f,h*.62f);
   rect(b,0,0,w,h,Color.valueOf("05060a"),a);
   String cols=c==null||c.colors==null||c.colors.isEmpty()?"U":c.colors;
   Color c1=portalColor(cols.charAt(0)),c2=portalColor(cols.charAt(cols.length()-1));
   Texture glow=portalLayer("portal-glow"),vortex=portalLayer("portal-vortex"),frame=portalLayer("portal-frame"),dot=glowTexture();Color old=b.getColor().cpy();
   float pulse=.85f+.15f*(float)Math.sin(time*1.4f);
   b.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,com.badlogic.gdx.graphics.GL20.GL_ONE);
   if(glow!=null){b.setColor(c1.r,c1.g,c1.b,a*.55f*pulse);b.draw(glow,cx-R*1.5f,cy-R*1.5f,R*3,R*3);}
   b.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
   if(vortex!=null){TextureRegion v=new TextureRegion(vortex);
    b.setColor(1,1,1,a);b.draw(v,cx-R*.86f,cy-R*.86f,R*.86f,R*.86f,R*1.72f,R*1.72f,1,1,-time*9);
    b.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,com.badlogic.gdx.graphics.GL20.GL_ONE);
    b.setColor(c1.r,c1.g,c1.b,a*.45f);b.draw(v,cx-R*.86f,cy-R*.86f,R*.86f,R*.86f,R*1.72f,R*1.72f,1,1,-time*9);   // commander tint
    b.setColor(c2.r,c2.g,c2.b,a*.30f);b.draw(v,cx-R*.62f,cy-R*.62f,R*.62f,R*.62f,R*1.24f,R*1.24f,1,1,time*15+60);
    for(int i=0;i<70;i++){float seed=hash(i,17),spd=.04f+.07f*hash(i,3),ph=(time*spd+seed)%1f,ang=seed*6.2832f+ph*2.6f,rad=R*(1.7f-1.25f*ph);
     float px=cx+(float)Math.cos(ang)*rad,py=cy+(float)Math.sin(ang)*rad,sz=1.6f+3.4f*hash(i,5),al=(float)Math.sin(ph*Math.PI);
     Color pc=i%2==0?c1:c2;b.setColor(pc.r,pc.g,pc.b,a*al*.7f);b.draw(dot,px-sz/2,py-sz/2,sz,sz);}
    b.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);}
   if(frame!=null){b.setColor(1,1,1,a);b.draw(frame,cx-R,cy-R,R*2,R*2);
    b.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,com.badlogic.gdx.graphics.GL20.GL_ONE);
    b.setColor(c2.r,c2.g,c2.b,a*.18f*pulse);b.draw(frame,cx-R,cy-R,R*2,R*2);   // runes breathe in the commander's colour
    b.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);}
   b.setColor(old);rect(b,0,0,w,h,DARK,a*.22f);
  }));
 }
 static Color portalColor(char ch){return switch(ch){case 'W'->Color.valueOf("ffc94a");case 'U'->Color.valueOf("4fb3ff");case 'B'->Color.valueOf("a05cff");case 'R'->Color.valueOf("ff5a3c");case 'G'->Color.valueOf("4fe08a");default->Color.valueOf("7fd6ff");};}
 private static float hash(int x,int y){int n=x*374761393+y*668265263;n=(n^(n>>>13))*1274126177;return ((n^(n>>>16))&0xffff)/65535f;}
 static Texture white(){if(white==null){Pixmap p=new Pixmap(1,1,Pixmap.Format.RGBA8888);p.setColor(Color.WHITE);p.fill();white=new Texture(p);p.dispose();}return white;}
 static void rect(Batch b,float x,float y,float w,float h,Color c,float a){Color o=b.getColor().cpy();b.setColor(c.r,c.g,c.b,c.a*a);b.draw(white(),x,y,w,h);b.setColor(o);}
 static void frame(Batch b,float x,float y,float w,float h,float t,Color c,float a){rect(b,x,y,w,t,c,a);rect(b,x,y+h-t,w,t,c,a);rect(b,x,y,t,h,c,a);rect(b,x+w-t,y,t,h,c,a);}
 /** text centred horizontally AND vertically in a box (uses the cap height, not the line height) */
 static float centerText(Batch b,String s,float x,float y,float w,float h,float size,Color c,float a,boolean title){
  BitmapFont f=font(title);f.getData().setScale(size/FONT_PX);float cap=f.getCapHeight();
  return text(b,s,x,y+h/2+cap/2,size,c,a,w,Align.center,title);
 }
 private static Texture relicTex;
 /** Ancient Relics icon: a gold-set relic crystal (procedural) */
 static Texture relicTexture(){
  if(relicTex!=null)return relicTex;int n=96;Pixmap p=new Pixmap(n,n,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  for(int y=0;y<n;y++)for(int x=0;x<n;x++){
   float dx=(x-n/2f+.5f)/(n*.36f),dy=(y-n/2f+.5f)/(n*.46f),d=Math.abs(dx)+Math.abs(dy);   // diamond / rhombus
   float glow=(float)Math.max(0,1-Math.sqrt(dx*dx*.5f+dy*dy*.5f)/1.25f);
   if(d<=1){float facet=dx<0?(dy<0?1f:.72f):(dy<0?.86f:.58f);float edge=d>.86f?1:0;
    float r=edge>0?.93f:.10f*facet+.05f,g=edge>0?.74f:.72f*facet+.12f,b=edge>0?.32f:.78f*facet+.14f;
    if(edge==0&&Math.abs(dx)<.06f)r=g=b=Math.min(1,g+.25f);   // centre ridge highlight
    p.setColor(r,g,b,1);}
   else p.setColor(.35f,.85f,.9f,glow*glow*.5f);   // soft halo
   p.drawPixel(x,y);}
  relicTex=new Texture(p,true);p.dispose();relicTex.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return relicTex;
 }
 /** "<relic> 37" right-aligned at (rightX, y top) */
 static void relicAmount(Batch b,int amount,float rightX,float y,float size,float a){
  BitmapFont f=font(true);f.getData().setScale(size/FONT_PX);String t=String.valueOf(amount);float tw=new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,t).width,cap=f.getCapHeight(),is=cap*1.9f;
  Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(relicTexture(),rightX-tw-is-size*.3f,y-cap/2-is/2,is,is);b.setColor(o);
  text(b,t,rightX-tw,y,size,GOLD,a,tw+2,Align.left,true);
 }
 private static Texture heartTex;
 static Texture heartTexture(){
  if(heartTex!=null)return heartTex;int n=64;Pixmap p=new Pixmap(n,n,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  for(int y=0;y<n;y++)for(int x=0;x<n;x++){float fx=(x-n/2f+.5f)/(n*.42f),fy=-(y-n*.46f)/(n*.42f);   // classic heart curve
   float v=(float)(Math.pow(fx*fx+fy*fy-1,3)-fx*fx*fy*fy*fy);
   if(v<=0){float shade=.85f+.15f*(1-(y/(float)n));p.setColor(.93f*shade,.27f*shade,.28f*shade,1);}else p.setColor(0,0,0,0);p.drawPixel(x,y);}
  heartTex=new Texture(p,true);p.dispose();heartTex.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return heartTex;
 }
 static void drawHeart(Batch b,float x,float y,float size,float a){Color o=b.getColor().cpy();b.setColor(0,0,0,a*.6f);b.draw(heartTexture(),x+.6f,y-.6f,size,size);b.setColor(1,1,1,a);b.draw(heartTexture(),x,y,size,size);b.setColor(o);}
 /** "<heart> 25   ·   Hand 7" - the heart replaces the word Life; returns the line height */
 static float lifeLine(Batch b,int life,String rest,float x,float y,float width,float size,Color c,float a,boolean centred){
  BitmapFont f=font(false);f.getData().setScale(size/FONT_PX);float cap=f.getCapHeight(),hs=cap*1.35f;
  String label=life+rest;float tw=new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,label).width,total=hs+size*.35f+tw;
  float sx=centred?x+(width-total)/2:x;drawHeart(b,sx,y-cap-(hs-cap)/2,hs,a);
  return text(b,label,sx+hs+size*.35f,y,size,c,a,width,Align.left,false);
 }
 /** a framed label box (like the tag chips); returns its height */
 static float boxedText(Batch b,String s,float x,float y,float maxWidth,float size,Color border,Color c,float a,boolean centred){
  float h=size*1.85f;size=Math.min(size,fitSingleLine(s,size,maxWidth-10,false));   // one line inside the frame (wrapped text ran out of it)
  float w=Math.min(maxWidth,chipWidth(s,size)+8),bx=centred?x+(maxWidth-w)/2:x;
  rect(b,bx,y-h,w,h,Color.valueOf("0e1017"),a*.82f);frame(b,bx,y-h,w,h,.7f,border,a*.95f);centerText(b,s,bx,y-h,w,h,size,c,a,false);return h;
 }
 static float text(Batch b,String s,float x,float y,float size,Color c,float a,float width,int align,boolean title){
  BitmapFont f=font(title);f.getData().setScale(size/FONT_PX);
  if(c.r+c.g+c.b>.9f){float o=Math.max(.35f,size*.055f),sh=Math.max(.5f,size*.09f);f.setColor(0,0,0,.75f*c.a*a);   // outline + shadow: readable on any background
   for(float[] d:new float[][]{{-o,0},{o,0},{0,-o},{0,o},{-o*.7f,-o*.7f},{o*.7f,o*.7f},{-o*.7f,o*.7f},{o*.7f,-o*.7f},{sh,-sh}})f.draw(b,s,x+d[0],y+d[1],width,align,true);}
  f.setColor(c.r,c.g,c.b,c.a*a);
  String mk=tierMarkup(s,c.a*a);   // "Rare" / "Epic" / "Legendary" in their tier colour (user 2026-10-16)
  com.badlogic.gdx.graphics.g2d.GlyphLayout gl;
  if(mk==null)gl=f.draw(b,s,x,y,width,align,true);
  else{f.getData().markupEnabled=true;try{gl=f.draw(b,mk,x,y,width,align,true);}finally{f.getData().markupEnabled=false;}}
  if(auditTexts!=null&&!auditInButton&&s!=null&&!s.isBlank()&&auditFrame(Gdx.graphics.getFrameId())){   // dev layout audit: where did this text really land? (one frame only)
   float lx=align==Align.center?x+(width-gl.width)/2:align==Align.right?x+width-gl.width:x;
   auditTexts.add(new AuditText(s,lx,y-gl.height,gl.width,gl.height,width,auditLayer));}
  return gl.height;
 }
 // ------------------------------------------------------------------ dev layout audit (user 2026-10-17: errors must be found by script, not by eye)
 record AuditText(String s,float x,float y,float w,float h,float box,Object layer){}
 static volatile List<AuditText> auditTexts;static boolean auditInButton;static final List<String> auditNotes=new ArrayList<>();static Object auditLayer;static long auditFrameId=-1;
 /** the audit records exactly one frame: the first frame after arming */
 static boolean auditFrame(long fid){if(auditFrameId<0)auditFrameId=fid;return fid==auditFrameId;}
 /** checks the frame recorded after "audit": text wider than its box, text off screen, text over buttons, buttons
  *  overlapping each other or squeezed labels, overlapping texts. One line per finding. */
 String auditResult(){
  List<AuditText> all=auditTexts;auditTexts=null;auditFrameId=-1;if(all==null)return "not armed";
  Object topLayer=overlays.isEmpty()?root:overlays.get(overlays.size()-1);   // only what is on top (an open guide / dialog covers the screen below)
  List<AuditText> texts=new ArrayList<>();for(AuditText x:all)if(x.layer()==topLayer)texts.add(x);
  List<String> out=new ArrayList<>(auditNotes);auditNotes.clear();
  List<float[]> btn=new ArrayList<>();List<String> btnName=new ArrayList<>();
  java.util.function.Consumer<Group> walk=new java.util.function.Consumer<>(){public void accept(Group g){for(Actor ac:g.getChildren()){if(!ac.isVisible())continue;
   if(ac instanceof Btn bt){com.badlogic.gdx.math.Vector2 v=bt.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(0,0));btn.add(new float[]{v.x,v.y,bt.getWidth(),bt.getHeight()});btnName.add(bt.label);}
   if(ac instanceof Group gg)accept(gg);}}};
  if(overlays.isEmpty())walk.accept(root);else walk.accept(overlays.get(overlays.size()-1));
  float W=W(),H=H();
  for(AuditText t:texts){
   if(t.w()>t.box()+1.5f)out.add("overflow: '"+t.s()+"' needs "+Math.round(t.w())+" of "+Math.round(t.box()));
   if(t.x()<-2||t.y()<-2||t.x()+t.w()>W+2||t.y()+t.h()>H+2)out.add("offscreen: '"+t.s()+"'");
   for(int i=0;i<btn.size();i++){float[] r=btn.get(i);float ix=Math.min(t.x()+t.w(),r[0]+r[2])-Math.max(t.x(),r[0]),iy=Math.min(t.y()+t.h(),r[1]+r[3])-Math.max(t.y(),r[1]);
    if(ix>2&&iy>2)out.add("text over button '"+btnName.get(i)+"': '"+t.s()+"'");}
  }
  for(int i=0;i<btn.size();i++)for(int j=i+1;j<btn.size();j++){float[] a=btn.get(i),c=btn.get(j);
   if(Math.min(a[0]+a[2],c[0]+c[2])-Math.max(a[0],c[0])>2&&Math.min(a[1]+a[3],c[1]+c[3])-Math.max(a[1],c[1])>2)out.add("buttons overlap: '"+btnName.get(i)+"' / '"+btnName.get(j)+"'");}
  for(int i=0;i<texts.size();i++)for(int j=i+1;j<texts.size();j++){AuditText a=texts.get(i),c=texts.get(j);if(a.s().equals(c.s()))continue;
   float ix=Math.min(a.x()+a.w(),c.x()+c.w())-Math.max(a.x(),c.x()),iy=Math.min(a.y()+a.h(),c.y()+c.h())-Math.max(a.y(),c.y());
   if(ix>3&&iy>Math.min(a.h(),c.h())*.35f)out.add("texts overlap: '"+a.s()+"' / '"+c.s()+"'");}
  List<String> uniq=new ArrayList<>(new LinkedHashSet<>(out));
  return uniq.isEmpty()?"audit ok ("+texts.size()+" texts, "+btn.size()+" buttons)":String.join("\n",uniq.subList(0,Math.min(40,uniq.size())));
 }
 // only reward-tier uses: ALL-CAPS words, or a capitalised word followed by +, punctuation, line end or option/reward/card/tier/pick/Find -
 // not names like "Legendary Permanents" or "Epic Spells" (user 2026-10-17)
 static final java.util.regex.Pattern TIER_WORD=java.util.regex.Pattern.compile("\\b(RARE|EPIC|LEGENDARY)\\b|\\b(Rare|Epic|Legendary)\\b(?=\\s*(?:$|[+·,.)/:\\]]|(?:options?|rewards?|cards?|tier|picks?|Find)\\b))");
 /** the tier words of a text wrapped in BitmapFont colour markup, or null when it has none */
 static String tierMarkup(String s,float alpha){
  if(s==null||!(s.contains("are")||s.contains("pic")||s.contains("egendary")||s.contains("ARE")||s.contains("PIC")||s.contains("EGENDARY")))return null;
  java.util.regex.Matcher m=TIER_WORD.matcher(s);StringBuilder o=null;int last=0;
  String aa=String.format("%02x",Math.max(0,Math.min(255,Math.round(alpha*255))));
  while(m.find()){if(o==null)o=new StringBuilder();String w=m.group(1)!=null?m.group(1):m.group(2),u=w.toUpperCase(Locale.ROOT);
   String hex=u.equals("RARE")?"5aa7ff":u.equals("EPIC")?"b46cff":"ff9a2e";
   o.append(s.substring(last,m.start()).replace("[","[[")).append("[#").append(hex).append(aa).append("]").append(w).append("[]");last=m.end();}
  if(o==null)return null;
  return o.append(s.substring(last).replace("[","[[")).toString();
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
 static final Color GOLD=Color.valueOf("b8c9f1"),TEXT=Color.valueOf("f0eff8"),DIM=Color.valueOf("a5adc5"),DARK=Color.valueOf("0b0e1b"),
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
   case "Seltenheit"->"Rarity";
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
   case "Letztes Paket"->"Newest package";
   case "Neueste"->"Newest unlock";
   case "Gespielt"->"Most played";
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
 /** Silver-edged fantasy enamel button with an explicit blue-violet active state. */
 class Btn extends Actor{
  String label;Runnable action;boolean gold,disabled;Color accent;final ClickListener click;   // accent: colour-coded filter chip (user 2026-10-18)
  Btn(String label,float x,float y,float w,float h,Runnable action){this.label=label;this.action=action;setBounds(x,y,w,h);
   click=new ClickListener(){@Override public void clicked(InputEvent e,float cx,float cy){if(!disabled&&!blocked(Btn.this))try{action.run();}catch(Throwable t){log(t);toast("Error: "+t);}}};addListener(click);}
  @Override public void draw(Batch b,float a){
   int v=disabled?3:click.isPressed()?2:click.isOver()?1:0;float bx=getX(),by=getY(),bw=getWidth(),bh=getHeight();
   boolean chip=bh<15||bw<bh*2.2f;   // narrow buttons (- + x ?) too: the painted end caps leave no room for the label   // small filter/sort chips: plain silver frame - the ornate end caps crowd them (user 2026-10-17)
   if(chip||!FantasyButtonArt.draw(b,v,gold,bx,by,bw,bh,a)){
    Color old=b.getColor().cpy();b.setColor(1,1,1,a);buttonFrame(v).draw(b,bx,by,bw,bh);b.setColor(old);
    if(gold&&!disabled&&accent==null)rect(b,bx+2,by+2,bw-4,bh-4,Color.valueOf(v==2?"231d4a":v==1?"3d3480":"2e2766"),a*.75f);
   }
   if(accent!=null&&!disabled){rect(b,bx+2,by+2,bw-4,bh-4,accent,a*(gold?.40f:v==1?.22f:.10f));rect(b,bx+3,by+2.2f,bw-6,1.4f,accent,a*(gold?1f:.75f));
    if(gold)frame(b,bx+.6f,by+.6f,bw-1.2f,bh-1.2f,.9f,accent,a);}
   // Ornate side caps stay clear of text; very small icon buttons keep usable labels.
   float inset=chip?4:FantasyButtonArt.textInset(bw,bh),safeWidth=Math.max(1,bw-2*inset);auditInButton=true;
   // size from the PAINTED bar height: narrow buttons draw a lower bar, the text overflowed it (user 2026-10-17, Back button)
   float size=Math.min((chip?bh:FantasyButtonArt.paintedHeight(bw,bh))*.48f,9.5f);
   BitmapFont face=font(true);face.getData().setScale(size/FONT_PX);
   float measured=new com.badlogic.gdx.graphics.g2d.GlyphLayout(face,label).width;
   if(measured>safeWidth&&measured>0)size*=safeWidth/measured;
   float nominal=Math.min((chip?bh:FantasyButtonArt.paintedHeight(bw,bh))*.48f,9.5f);
   if(auditTexts!=null&&auditFrame(Gdx.graphics.getFrameId())&&size<nominal*.7f)auditNotes.add("squeezed label: '"+label+"' at "+Math.round(size/nominal*100)+"%");
   centerText(b,label,getX(),getY()+(chip?0:getHeight()*.06f),getWidth(),getHeight(),size,disabled?DIM:TEXT,a,true);auditInButton=false;   /* caps sit optically low on the painted bar (user 2026-10-17) */
  }
 }
 /** card image (or a framed placeholder while the art downloads); tap opens the big preview unless an action is set */
 class CardView extends Actor{boolean huntMark,stats;int group=-1;List<String> hunt;Runnable onHuntChange;   /* package card view: H toggles the hunt (user 2026-10-19) */   /* reward package the card belongs to: the big view walks one package after the other (user 2026-10-18) */   /* gold frame: a card from the hunting list (user 2026-10-18) */
  String name,tag;Skin customArt;Color border;boolean selected,dim,mystery;Runnable action;PaperCard illustration;int rating=-1;
  CardView(String name,float x,float y,float w,float h){this.name=name;setBounds(x,y,w,h);
   addListener(new ClickListener(){@Override public void clicked(InputEvent e,float cx,float cy){if(blocked(CardView.this))return;try{if(action!=null)action.run();else preview(CardView.this);}catch(Throwable t){log(t);}}});}
  @Override public void draw(Batch b,float a){
   float x=getX(),y=getY(),w=getWidth(),h=getHeight();
   if(selected)rect(b,x-2.5f,y-2.5f,w+5,h+5,GOLD,a*(.55f+.3f*(float)Math.sin(time*4)));
   rect(b,x+1.2f,y-1.5f,w,h,Color.BLACK,a*.45f);
   if(mystery){   // a hidden (locked) commander: no art, no name - just a question mark (user 2026-10-16)
    rect(b,x,y,w,h,Color.valueOf("0c0e15"),a);for(int k=0;k<8;k++)rect(b,x,y+h*k/8f,w,h/8f,Color.valueOf("1a1f2e"),a*(.10f+.05f*k));
    frame(b,x,y,w,h,Math.max(.8f,w*.02f),selected?GOLD:Color.valueOf("5b5f6e"),a);frame(b,x+w*.08f,y+w*.08f,w*.84f,h-w*.16f,Math.max(.5f,w*.01f),Color.valueOf("3a3f4e"),a);
    centerText(b,"?",x,y+h*.1f,w,h*.8f,Math.max(6,h*.42f),selected?GOLD:Color.valueOf("8a93a7"),a,true);
    if(h>40)centerText(b,"LOCKED",x,y+h*.04f,w,h*.12f,Math.max(3.2f,h*.055f),Color.valueOf("8a93a7"),a,true);
    return;}
   Texture t=customArt!=null?customCard(name,customArt):illustration==null?cardTexture(name):cardTexture(illustration);
   if(t!=null){Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(t,x,y,w,h);b.setColor(o);}
   else{rect(b,x,y,w,h,Color.valueOf("1c1a22"),a);frame(b,x,y,w,h,.8f,DIM,a);float nt=rating>=0?y+h*.84f-Math.max(5,w*.16f)-2:y+h-3;text(b,name,x+2,nt,Math.max(3.2f,w/9),TEXT,a,w-4,Align.center,false);}   // placeholder name below the rating badge (audit 2026-10-18)
   if(dim)rect(b,x,y,w,h,Color.BLACK,a*.62f);
   if(rating>=0){float s=Math.max(5,w*.16f),bx=x+w-s-w*.06f,by=y+h*.84f-s;Color rc=ratingColor(rating);   // smaller rating badge (user 2026-10-17)
    rect(b,bx-.5f,by-.5f,s+1f,s+1f,Color.BLACK,a*.8f);rect(b,bx,by,s,s,rc,a*.92f);
    text(b,String.valueOf(rating),bx,by+s*.76f,s*.6f,Color.BLACK,a,s,Align.center,true);}
   if(border!=null)frame(b,x-.6f,y-.6f,w+1.2f,h+1.2f,1.1f,border,a);
   if(huntMark)frame(b,x-2.2f,y-2.2f,w+4.4f,h+4.4f,1.3f,GOLD,a*(.75f+.25f*(float)Math.sin(time*3)));
   if(tag!=null){float th=Math.max(4.5f,h*.11f);rect(b,x,y,w,th,Color.BLACK,a*.72f);text(b,tag,x,y+th*.78f,th*.72f,border!=null?border:TEXT,a,w,Align.center,false);}
  }
 }
 class Painter extends Actor{
  final java.util.function.BiConsumer<Batch,Float> paint;
  Painter(java.util.function.BiConsumer<Batch,Float> paint){this.paint=paint;setTouchable(Touchable.disabled);}
  boolean failed;
  @Override public void draw(Batch b,float a){if(failed)return;
   if(auditTexts!=null){Actor top=this;while(top.getParent()!=null&&top.getParent()!=root&&top.getParent()!=ui)top=top.getParent();auditLayer=top.getParent()==root?root:top;}   // which layer draws this text
   try{paint.accept(b,a);}catch(Throwable t){failed=true;log(t);}}
 }

 // ------------------------------------------------------------------ scene
 private static RogueCommandersScene object;
 public static RogueCommandersScene instance(){if(object==null)object=new RogueCommandersScene();return object;}
 /** test tools for the dev instance (DevModeMod /scene?action=&arg=, user 2026-10-17). Only reachable through the
  *  dev-mode HTTP port, which exists only with -Dforge.devmode. */
 private void clearScreens(){displayScreen=false;mainMenu=false;achievementScreen=false;packageConfig=null;compendiumOpen=false;shopOpen=false;wardrobe=null;}
 public static String dev(String action,String arg){
  if(System.getProperty("forge.devmode")==null)return "dev mode only";
  Profile p=profile();Run r=p.run;RogueCommandersScene s=instance();int n=0;try{String dg=arg.replaceAll("[^0-9]","");if(!dg.isEmpty())n=Integer.parseInt(dg);}catch(Exception ignored){}
  switch(action){
   case "state"->{return r==null?"no run; result="+(p.result!=null)+" runs="+p.runs:"commander="+r.commander+" round="+r.round+" wins="+r.wins+" phase="+r.phase+" opponent="+r.opponent+" storyAct="+r.storyAct+" mapPos="+r.mapPos+" life="+r.life;}
   case "reward"->{if(r==null)return "no run";r.phase="package";r.offers=makeOffers(r);}
   case "map"->{if(r==null)return "no run";r.phase="fight";}
   case "round"->{if(r==null)return "no run";r.round=Math.max(1,Math.min(15,n));r.wins=r.round-1;r.mapPos=-1;r.phase="fight";r.storyAct=storyAct(r);}
   case "story"->{if(r==null)return "no run";p.storyOff=false;r.storyAct=Math.max(0,n-1);if(n>=1&&n<=3&&storyAct(r)<n){r.round=(n-1)*5+1;r.mapPos=-1;}}
   case "relics"->p.progressionPoints+=Math.max(1,n);
   case "favor"->p.goldBalance+=Math.max(1,n);
   case "tour"->p.battleTourDone=false;
   case "explain"->p.packagesExplained=false;
   case "flags"->{p.battleTourDone=false;p.packagesExplained=false;p.storyOff=false;p.tutorialsSeen=new HashSet<>();}
   case "unlock"->{for(Cmd c:content().commanders){String g=commanderGoal(c.name);if(g!=null)p.achievements.add(g);}}
   case "failed"->{p.lastRunFailed=n!=0;}
   case "audit"->{auditNotes.clear();auditFrameId=-1;auditTexts=Collections.synchronizedList(new ArrayList<>());Gdx.graphics.requestRendering();return "armed";}
   case "auditresult"->{return s.auditResult();}
   // ---- build any game state directly (user 2026-10-17: "game states erzeugen und pruefen nach belieben")
   case "newrun"->{Cmd c=null;String q=arg.toLowerCase(Locale.ROOT);for(Cmd x:content().commanders)if(x.slug.startsWith(q)||x.name.toLowerCase(Locale.ROOT).startsWith(q))c=c==null?x:c;
    if(c==null)return "unknown commander: "+arg;
    String goal=commanderGoal(c.name);if(goal!=null)p.achievements.add(goal);   // dev: locked commanders are playable for tests
    p.run=null;p.result=null;p.selected=c.name;s.clearScreens();save();s.startRun(c);return p.run==null?"error: run did not start for "+c.name:"new run: "+c.name;}
   case "opponent"->{if(r==null)return "no run";OpponentSpec d=specById(arg.trim());if(d==null)return "unknown deck id: "+arg;r.extraFight=true;r.extraDeck=d.id;r.phase="fight";resumeOpponent(r);}
   case "fight"->{if(r==null)return "no run";r.phase="fight";s.clearScreens();r.storyAct=Math.max(r.storyAct,storyAct(r));
    if(mapActive(r)&&currentNode(r)==null&&r.round<15){List<MapNode> reach=reachable(r);if(reach.isEmpty()){r.mapPos=-1;reach=reachable(r);}if(!reach.isEmpty()){MapNode nd=reach.get(0);r.mapPos=nd.id;r.mapPath.add(nd.id);applyNode(r,nd);}}   // pick the first room on the map
    save();s.startRound();return "fight: "+r.opponent;}
   case "deck"->{if(r==null)return "no run";if(paper(arg.trim())==null)return "unknown card: "+arg;r.deck.add(arg.trim());}
   case "bench"->{if(r==null)return "no run";if(paper(arg.trim())==null)return "unknown card: "+arg;bench(r).add(arg.trim());}
   case "life"->{if(r==null)return "no run";r.life=n;}
   case "endrun"->{if(r==null)return "no run";if(arg.startsWith("win")){r.completed=true;r.wins=15;}s.clearScreens();s.endRun();return "run ended";}
   case "screen"->{s.clearScreens();
    switch(arg.replaceAll("\\d","")){
     case "menu"->s.mainMenu=true;
     case "achievements"->s.achievementScreen=true;
     case "settings"->{s.displayScreen=true;s.settingsTab=Math.max(0,Math.min(5,n));}
     case "packages","compendium"->{Cmd c=cmd(p.selected);if(c==null)return "no commander selected";s.packageConfig=c;s.compendiumOpen=arg.startsWith("compendium");}
     case "deck"->{if(r==null)return "no run";s.rebuild();s.showRunDeck(r);return "deck browser";}
     case "loadout"->{Cmd c=cmd(p.selected);if(c==null)return "no commander";s.rebuild();s.showLoadout(c,false);return "loadout browser";}
     case "select","run",""->{}
     default->{return "screens: menu, select, run, packages, compendium, achievements, settings0-4";}
    }}
   case "snapshot"->{try{File f=new File(DIR+"dev-snapshots/"+(arg.isBlank()?"last":arg.trim())+".json");f.getParentFile().mkdirs();Files.writeString(f.toPath(),GSON.toJson(p),StandardCharsets.UTF_8);return "saved "+f.getName();}catch(Exception e){return "error "+e;}}
   case "restore"->{try{File f=new File(DIR+"dev-snapshots/"+(arg.isBlank()?"last":arg.trim())+".json");if(!f.isFile())return "no snapshot "+f.getName();
    Profile loaded=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),Profile.class);if(loaded==null)return "unreadable";profile=loaded;s.clearScreens();}catch(Exception e){return "error "+e;}}
   default->{return "actions: state, newrun CMD, opponent DECKID, fight, deck CARD, bench CARD, life N, endrun [win], screen NAME, snapshot NAME, restore NAME, reward, map, round N, story N(1-3), relics N, favor N, tour, explain, flags, unlock, failed 0|1";}
  }
  save();s.rebuild();return "ok "+action+(arg.isEmpty()?"":" "+arg);
 }
 private final Group root=new Group();private final List<Group> overlays=new ArrayList<>();private float time;private String message;private float messageTime;
 private long lastInput=System.currentTimeMillis(),lastTick=System.currentTimeMillis();
 private AdventureEventData editing;private String editingCommander;private boolean editingRun,editingThenPackages;private AdventureEventData.AdventureEventParticipant opponent;
 private Cmd wardrobe,packageConfig;private int packagePage,packageTab;private boolean shopOpen;
 private RogueCommandersScene(){
  super(Forge.isLandscapeMode()?"ui/rogue_commanders.json":"ui/rogue_commanders_portrait.json");
  ui.addActor(root);
 }
 float W(){return getIntendedWidth();}
 float H(){return getIntendedHeight();}

 /** StartScene constructor hook: wires the "Rogue" button of the main menu */
 public static void hookStart(StartScene start){try{start.getUI().onButtonPress("Rogue",RogueCommandersScene::openFromMenu);
  if(standalone()){Actor st=start.getUI().findActor("Settings");if(st!=null)st.setVisible(false);}   // Forge's own settings: Rogue has its own (tester 2026-10-19)
 }catch(Exception e){System.err.println("[Rogue] menu: "+e);}}
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
  try{profile();content();if(editing!=null)finishEditing();if(!menuShown){menuShown=true;mainMenu=true;}rebuild();}catch(Throwable t){log(t);}
 }
 @Override public boolean leave(){save();return super.leave();}
 @Override public boolean back(){if(displayScreen){displayScreen=false;rebuild();return true;}
  if(mainMenu&&naming){naming=false;rebuild();return true;}
  if(mainMenu){if(standalone())confirm("Quit Rogue Commanders?","Your progress is saved.","Quit","Stay",()->Gdx.app.exit());else Forge.switchScene(StartScene.instance());return true;}if(achievementScreen){achievementScreen=false;rebuild();return true;}if(!overlays.isEmpty()){closeOverlay();return true;}if(packageConfig!=null){packageConfig=null;rebuild();return true;}if(shopOpen){shopOpen=false;rebuild();return true;}if(wardrobe!=null){wardrobe=null;rebuild();return true;}if(standalone()){confirm("Quit Rogue Commanders?","Your run is saved automatically.","Quit","Keep playing",()->Gdx.app.exit());return true;}Forge.switchScene(StartScene.instance());return true;}
 @Override public void act(float delta){
  super.act(delta);time+=delta;if(messageTime>0)messageTime-=delta;
  long now=System.currentTimeMillis();
  if(Gdx.input.isTouched()||Gdx.input.justTouched())lastInput=now;
  if(now-lastInput<120_000)profile().activeMs+=Math.min(250,now-lastTick);   // idle menus do not count
  lastTick=now;
 }
 private void toast(String m){message=m;messageTime=4f;}

 private void rebuild(){subLift=.025f;
  while(!overlays.isEmpty())closeOverlay();
  root.clearChildren();root.setBounds(0,0,W(),H());
  Profile p=profile();
  if(displayScreen)buildDisplay();
  else if(mainMenu)buildMainMenu(p);
  else if(achievementScreen)buildAchievements(p);
  else if(packageConfig!=null)buildPackages(packageConfig);
  else if(shopOpen&&p.run!=null&&"fight".equals(p.run.phase))buildShop(p.run);
  else if(wardrobe!=null)buildSkins(wardrobe);
  else if(p.result!=null&&p.result.completed&&!p.result.storySeen&&!p.storyOff)buildStory(p,4);   // Portal Mage: ending (user 2026-10-17)
  else if(p.result!=null&&p.result.dungeonNotice!=null&&!p.result.dungeonNoticeSeen)buildDungeonVictory(p);
  else if(p.result!=null)buildResult(p);
  else if(p.result==null&&p.run!=null&&!p.storyOff&&p.run.round==15&&p.run.cursed&&"Emrakul".equals(p.run.opponent)&&p.run.storyAct<5&&"fight".equals(p.run.phase)&&storyChapter(p,5)!=null)buildStory(p,5);   // Codex curse beat, only when Emrakul is revealed
  else if(p.result==null&&p.run!=null&&!p.storyOff&&storyAct(p.run)>p.run.storyAct&&!"package".equals(p.run.phase))buildStory(p,storyAct(p.run));   // Portal Mage: run start + act change
  else if(p.run!=null&&"curse".equals(p.run.phase))buildCurse(p.run);
  else if(p.run!=null&&"treasure".equals(p.run.phase))buildTreasure(p.run);
  else if(p.run!=null&&"rest".equals(p.run.phase))buildRest(p.run);
  else if(p.run!=null&&"encounter".equals(p.run.phase))buildEncounter(p.run);
  else if(p.run!=null&&"package".equals(p.run.phase))buildOffers(p.run);
  else if(p.run!=null)buildRun(p.run);
  else buildSelect(p);
  root.addActor(new Painter((b,a)->{if(messageTime>0&&message!=null){float w=Math.min(W()-40,260);float x=(W()-w)/2,ty=40;rect(b,x,ty,w,16,Color.BLACK,a*.85f*Math.min(1,messageTime));frame(b,x,ty,w,16,.6f,GOLD,a*Math.min(1,messageTime));text(b,message,x,ty+11,6,TEXT,a*Math.min(1,messageTime),w,Align.center,false);}}));
 }
 private String artKind;private float artStart;
 /** Codex' dungeon art (DungeonMapArt): kind null = dungeon stage, else an encounter illustration (relics, favor,
  *  offer, merchant). dim darkens it for readable panels. */
 private void artBackground(String kind,float dim){
  String key=kind==null?"dungeon":kind;if(!key.equals(artKind)){artKind=key;artStart=time;}
  root.addActor(new Painter((b,a)->{
   float w=W(),h=H();
   if(kind==null)forge.adventure.util.DungeonMapArt.background(b,0,0,w,h,time,a);
   else forge.adventure.util.DungeonMapArt.encounter(b,kind,0,0,w,h,time-artStart,a);
   rect(b,0,0,w,h,DARK,a*dim);
   for(int i=0;i<12;i++){rect(b,0,0,w,h*.05f*(12-i)/12f,Color.BLACK,a*.05f);rect(b,0,h-h*.04f*(12-i)/12f,w,h*.04f*(12-i)/12f,Color.BLACK,a*.05f);}
  }));
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
   float ts=fitSingleLine(big,15,W()-200,true);   // long titles shrink to one line instead of wrapping into the subtitle (Heirloom Vault, 2026-10-18)
   text(b,big,13,H()-7,ts,Color.BLACK,a*.6f,W()-160,Align.left,true);
   text(b,big,12,H()-6,ts,GOLD,a,W()-160,Align.left,true);
   if(small!=null)text(b,small,13,H()-25,7.2f,DIM,a,W()-160,Align.left,false);
  }));
 }
 private Btn button(String label,float x,float y,float w,float h,Runnable r){Btn b=new Btn(label,x,y,w,h,r);root.addActor(b);return b;}
 private void pips(Batch b,String colors,float x,float y,float r,float a){
  for(int i=0;i<colors.length();i++){char ch=colors.charAt(i);float cx=x+i*(r*2+2.5f);
   TextureRegion icon=manaIcon(ch);
   if(icon!=null){   // Forge's own mana symbols (res/skins/default/sprite_manaicons.png), soft shadow + thin gold ring
    disc(b,cx+r+.5f,y+r-.7f,r+.9f,Color.BLACK,a*.55f);
    disc(b,cx+r,y+r,r+.55f,Color.valueOf("c9a85a"),a*.9f);
    b.setColor(1,1,1,a);b.draw(icon,cx,y,r*2,r*2);b.setColor(Color.WHITE);
    continue;}
   rect(b,cx-.6f,y-.6f,r*2+1.2f,r*2+1.2f,Color.BLACK,a*.7f);rect(b,cx,y,r*2,r*2,pip(ch),a);
   text(b,String.valueOf(ch),cx,y+r*1.55f,r*1.3f,ch=='W'?Color.DARK_GRAY:Color.WHITE,a,r*2,Align.center,true);}
 }
 private static Texture manaSprite;private static boolean manaSpriteTried;
 /** W/U/B/R/G from Forge's skin sprite (not copied into our files); null -> letter boxes */
 static TextureRegion manaIcon(char ch){
  if(!manaSpriteTried){manaSpriteTried=true;try{
   var f=Gdx.files.absolute(forge.localinstance.properties.ForgeConstants.DEFAULT_SKINS_DIR+"sprite_manaicons.png");
   if(f.exists()){manaSprite=new Texture(f,true);manaSprite.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);}
  }catch(Exception e){log(e);}}
  if(manaSprite==null)return null;
  int[] xy=switch(ch){case 'B'->new int[]{165,0};case 'R'->new int[]{329,0};case 'G'->new int[]{164,82};case 'U'->new int[]{329,82};case 'W'->new int[]{411,82};default->null;};
  return xy==null?null:new TextureRegion(manaSprite,xy[0],xy[1],82,82);
 }
 private void bar(Batch b,float x,float y,float w,float h,float f,Color c,float a){rect(b,x,y,w,h,Color.BLACK,a*.6f);rect(b,x,y,w*Math.max(0,Math.min(1,f)),h,c,a);frame(b,x,y,w,h,.5f,DIM,a*.6f);}
 static String hours(long ms){long m=ms/60000;return (m/60)+":"+String.format("%02d",m%60)+" h";}
 float generalShare(){return content().unlocks.length==0?0:profile().general.size()/(float)content().unlocks.length;}

 // Common desktop presets use Forge's native persisted video mode names.
 static final int[][] DISPLAY_SIZES={{1280,720},{1600,900},{1920,1080},{2560,1440},{3840,2160}};
 static final String[] DISPLAY_MODES={"720p","900p","1080p","1440p","2160p"};
 private boolean displayScreen,displayFullscreen;private int displaySelection=2,settingsTab,displayMonitor;
 // ---- monitor choice (tester 2026-10-19: "Ich kann den Bildschirm, auf dem ich spielen moechte, nicht aendern")
 static com.badlogic.gdx.Graphics.Monitor[] monitors(){try{var ms=Gdx.graphics.getMonitors();if(ms!=null&&ms.length>0)return ms;}catch(Throwable t){}return new com.badlogic.gdx.Graphics.Monitor[]{Gdx.graphics.getMonitor()};}
 static String monitorKey(com.badlogic.gdx.Graphics.Monitor m){return m==null?"":m.name+"@"+m.virtualX+","+m.virtualY;}
 static int monitorIndex(com.badlogic.gdx.Graphics.Monitor m){var ms=monitors();for(int i=0;i<ms.length;i++)if(monitorKey(ms[i]).equals(monitorKey(m)))return i;return 0;}
 static com.badlogic.gdx.Graphics.Monitor monitorByKey(String key){if(key==null)return null;var ms=monitors();for(var m:ms)if(monitorKey(m).equals(key))return m;
  for(var m:ms)if(key.startsWith(m.name+"@"))return m;return null;}   // same screen, other position: the name still finds it
 com.badlogic.gdx.Graphics.Monitor chosenMonitor(){var ms=monitors();return displayMonitor>=0&&displayMonitor<ms.length?ms[displayMonitor]:Gdx.graphics.getMonitor();}
 /** windowed: centre the window on that monitor */
 static void moveWindowTo(com.badlogic.gdx.Graphics.Monitor m,int w,int h){
  try{var dm=Gdx.graphics.getDisplayMode(m);var win=((com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics)Gdx.graphics).getWindow();
   win.setPosition(m.virtualX+Math.max(0,(dm.width-w)/2),m.virtualY+Math.max(0,(dm.height-h)/2));}catch(Throwable t){log(t);}
 }
 static int displayIndex(int width,int height){for(int i=0;i<DISPLAY_SIZES.length;i++)if(DISPLAY_SIZES[i][0]==width&&DISPLAY_SIZES[i][1]==height)return i;return -1;}
 static boolean windowFits(int index,int desktopWidth,int desktopHeight){return index>=0&&index<DISPLAY_SIZES.length&&DISPLAY_SIZES[index][0]<=desktopWidth&&DISPLAY_SIZES[index][1]<=desktopHeight;}
 private void openDisplay(){
  int index=displayIndex(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
  displaySelection=index<0?Math.max(0,displayIndex(Config.instance().getSettingData().width,Config.instance().getSettingData().height)):index;
  displayFullscreen=Gdx.graphics.isFullscreen();displayMonitor=monitorIndex(Gdx.graphics.getMonitor());displayScreen=true;rebuild();
 }
 static com.badlogic.gdx.Graphics.DisplayMode displayMode(int index){return displayMode(index,Gdx.graphics.getMonitor());}
 static com.badlogic.gdx.Graphics.DisplayMode displayMode(int index,com.badlogic.gdx.Graphics.Monitor monitor){
  if(index<0||index>=DISPLAY_SIZES.length)return null;
  var current=Gdx.graphics.getDisplayMode(monitor);com.badlogic.gdx.Graphics.DisplayMode best=null;
  for(var mode:Gdx.graphics.getDisplayModes(monitor))if(mode.width==DISPLAY_SIZES[index][0]&&mode.height==DISPLAY_SIZES[index][1]){
   if(best==null||Math.abs(mode.refreshRate-current.refreshRate)<Math.abs(best.refreshRate-current.refreshRate))best=mode;
  }
  return best;
 }
 private void applyDisplay(){
  final int index=displaySelection;final boolean fullscreen=displayFullscreen;final var mon=chosenMonitor();
  Gdx.app.postRunnable(()->{
   try{
    boolean accepted;
    if(fullscreen){var mode=displayMode(index,mon);if(mode==null){toast("This fullscreen resolution is not supported by that monitor.");return;}accepted=Gdx.graphics.setFullscreenMode(mode);}
    else{var desktop=Gdx.graphics.getDisplayMode(mon);if(!windowFits(index,desktop.width,desktop.height)){toast("Choose a resolution that fits your monitor.");return;}accepted=Gdx.graphics.setWindowedMode(DISPLAY_SIZES[index][0],DISPLAY_SIZES[index][1]);
     if(accepted)moveWindowTo(mon,DISPLAY_SIZES[index][0],DISPLAY_SIZES[index][1]);}
    if(!accepted){toast("The display could not apply that mode.");return;}
    profile().monitorKey=monitorKey(mon);save();
    var settings=Config.instance().getSettingData();settings.fullScreen=fullscreen;settings.width=DISPLAY_SIZES[index][0];settings.height=DISPLAY_SIZES[index][1];settings.videomode=DISPLAY_MODES[index];Config.instance().saveSettings();
    FModel.getPreferences().setPref(forge.localinstance.properties.ForgePreferences.FPref.UI_VIDEO_MODE,DISPLAY_MODES[index]);FModel.getPreferences().save();
    rebuild();toast("Applied "+DISPLAY_SIZES[index][0]+" x "+DISPLAY_SIZES[index][1]+(fullscreen?" fullscreen":" windowed"));
   }catch(Exception e){log(e);toast("Display change failed. Your saved mode was kept.");}
  });
 }
 private void buildDisplay(){
  background(profile().selected,.8f);title("SETTINGS",null);
  String[] tabs={"Display","Audio","Battle UI","Keys","Game","Tutorials"};   // Battle UI + Keys + Game (story) tabs (user 2026-10-17)
  boolean tch=touch();if(tch&&(settingsTab==0||settingsTab==3))settingsTab=1;   // touch: no window/monitor or key settings
  int shown=tch?tabs.length-2:tabs.length,slot=0;float tw=(W()-24-(shown-1)*6)/shown;
  for(int i=0;i<tabs.length;i++){if(tch&&(i==0||i==3))continue;final int tab=i;button(settingsTab==i?"[ "+tabs[i]+" ]":tabs[i],12+slot*(tw+6),H()-44,tw,16,()->{settingsTab=tab;bindingAction=-1;rebuild();});slot++;}
  if(settingsTab>0){
   button("Back",W()-66,H()-22,56,16,()->{displayScreen=false;bindingAction=-1;rebuild();});
   if(settingsTab==1)buildAudioSettings();else if(settingsTab==2)buildBattleUiSettings();else if(settingsTab==3)buildKeySettings();else if(settingsTab==4)buildGameSettings();else buildTutorialSettings();
   return;
  }
  button("Back",W()-66,H()-22,56,16,()->{displayScreen=false;rebuild();});
  float width=Math.min(330,W()-24),x=(W()-width)/2;
  Btn window=button(displayFullscreen?"Windowed":"[ Windowed ]",x,H()-76,(width-6)/2,20,()->{displayFullscreen=false;rebuild();});window.gold=!displayFullscreen;
  Btn full=button(displayFullscreen?"[ Fullscreen ]":"Fullscreen",x+(width+6)/2,H()-76,(width-6)/2,20,()->{displayFullscreen=true;rebuild();});full.gold=displayFullscreen;
  var ms=monitors();var mon=chosenMonitor();var desktop=Gdx.graphics.getDisplayMode(mon);
  {String ml="Monitor "+(monitorIndex(mon)+1)+" of "+ms.length+"   ·   "+desktop.width+" x "+desktop.height+(ms.length>1?"   (click: next monitor)":"");
   Btn mb=button(ml,x,H()-100,width,18,()->{displayMonitor=(monitorIndex(chosenMonitor())+1)%monitors().length;rebuild();});mb.disabled=ms.length<2;}
  for(int i=0;i<DISPLAY_SIZES.length;i++){
   final int index=i;boolean supported=displayFullscreen?displayMode(i,mon)!=null:windowFits(i,desktop.width,desktop.height);
   String label=DISPLAY_SIZES[i][0]+" x "+DISPLAY_SIZES[i][1]+(displaySelection==i?"   [ SELECTED ]":"")+(supported?"":"   (unavailable)");
   Btn preset=button(label,x,H()-126-i*22,width,20,()->{displaySelection=index;rebuild();});preset.gold=displaySelection==i;preset.disabled=!supported;
  }
  Btn apply=button("APPLY",x,35,width,22,this::applyDisplay);apply.gold=true;apply.disabled=displayFullscreen?displayMode(displaySelection,mon)==null:!windowFits(displaySelection,desktop.width,desktop.height);
  root.addActor(new Painter((b,a)->text(b,"Current: "+Gdx.graphics.getWidth()+" x "+Gdx.graphics.getHeight()+" · "+(Gdx.graphics.isFullscreen()?"fullscreen":"windowed")+" · saved for next start",12,22,5.6f,DIM,a,W()-24,Align.center,false)));
 }
 static int clampVolume(int value){return Math.max(0,Math.min(100,value));}
 /** voice / narration / boss-line volume 0..1; the dev test instance (-Dforge.devmode) is always silent (user 2026-10-18: "bei deinen Tests auf muted") */
 static float voicePlayback(){return System.getProperty("forge.devmode")!=null?0f:clampVolume(profile().voiceVolume)/100f;}
 static int volumeFromPosition(float x,float width){return clampVolume(Math.round(x/Math.max(1,width)*20)*5);}
 static int audioVolume(int channel){
  if(channel==2)return clampVolume(profile().voiceVolume);
  var prefs=FModel.getPreferences();
  return clampVolume(prefs.getPrefInt(channel==0?forge.localinstance.properties.ForgePreferences.FPref.UI_VOL_MUSIC:forge.localinstance.properties.ForgePreferences.FPref.UI_VOL_SOUNDS));
 }
 static void setAudioVolume(int channel,int value,boolean persist){
  value=clampVolume(value);
  if(channel==2){profile().voiceVolume=value;if(currentVoice!=null&&currentVoiceId>=0)currentVoice.setVolume(currentVoiceId,value/100f);if(persist)save();return;}
  var prefs=FModel.getPreferences();
  prefs.setPref(channel==0?forge.localinstance.properties.ForgePreferences.FPref.UI_VOL_MUSIC:forge.localinstance.properties.ForgePreferences.FPref.UI_VOL_SOUNDS,Integer.toString(value));
  prefs.setPref(channel==0?forge.localinstance.properties.ForgePreferences.FPref.UI_ENABLE_MUSIC:forge.localinstance.properties.ForgePreferences.FPref.UI_ENABLE_SOUNDS,value>0);
  if(channel==0)forge.sound.SoundSystem.instance.refreshVolume();
  if(persist)prefs.save();
 }
 class VolumeSlider extends Actor{
  final int channel;
  VolumeSlider(int channel,float x,float y,float width){this.channel=channel;setBounds(x,y,width,22);
   addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){
    @Override public boolean touchDown(InputEvent e,float x,float y,int pointer,int button){if(button!=0)return false;setAudioVolume(channel,volumeFromPosition(x,getWidth()),false);return true;}
    @Override public void touchDragged(InputEvent e,float x,float y,int pointer){setAudioVolume(channel,volumeFromPosition(x,getWidth()),false);}
    @Override public void touchUp(InputEvent e,float x,float y,int pointer,int button){setAudioVolume(channel,volumeFromPosition(x,getWidth()),true);}
   });
  }
  @Override public void draw(Batch b,float alpha){
   float level=audioVolume(channel)/100f;
   rect(b,getX(),getY()+8,getWidth(),6,DARK,alpha);rect(b,getX(),getY()+8,getWidth()*level,6,GOLD,alpha);
   rect(b,getX()+getWidth()*level-3,getY()+4,6,14,TEXT,alpha);
  }
 }
 private void buildAudioSettings(){
  float width=Math.min(330,W()-24),x=(W()-width)/2;
  String[] labels={"Music","Sound effects","Character voices"};
  for(int i=0;i<3;i++){
   final int channel=i;float y=H()-81-i*51;
   root.addActor(new Painter((b,a)->{text(b,labels[channel],x,y,7,TEXT,a,width-58,Align.left,false);text(b,audioVolume(channel)+"%",x+width-55,y,7,GOLD,a,55,Align.right,false);}));
   button("-",x,y-27,24,20,()->setAudioVolume(channel,audioVolume(channel)-5,true));
   root.addActor(new VolumeSlider(channel,x+34,y-28,width-68));
   button("+",x+width-24,y-27,24,20,()->setAudioVolume(channel,audioVolume(channel)+5,true));
  }
  root.addActor(new Painter((b,a)->text(b,"Drag a slider or use + / -. 0% mutes. Changes are saved automatically.",12,23,5.5f,DIM,a,W()-24,Align.center,false)));
 }
 // ------------------------------------------------------------------ Settings > Battle UI: button sizes (user 2026-10-17)
 static int sizePercent(int group){return Math.round(forge.adventure.util.BattleUiSettings.size(group)*100);}
 static void setSizePercent(int group,int percent,boolean persist){
  forge.adventure.util.BattleUiSettings.setSize(group,percent/100f);if(persist)forge.adventure.util.BattleUiSettings.save();
 }
 class SizeSlider extends Actor{
  final int group;
  SizeSlider(int group,float x,float y,float width){this.group=group;setBounds(x,y,width,22);
   addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){
    @Override public boolean touchDown(InputEvent e,float x,float y,int pointer,int button){if(button!=0)return false;set(x,false);return true;}
    @Override public void touchDragged(InputEvent e,float x,float y,int pointer){set(x,false);}
    @Override public void touchUp(InputEvent e,float x,float y,int pointer,int button){set(x,true);}
   });
  }
  void set(float x,boolean persist){float f=Math.max(0,Math.min(1,x/Math.max(1,getWidth())));setSizePercent(group,Math.round((60+f*100)/5f)*5,persist);}
  @Override public void draw(Batch b,float alpha){
   float level=(sizePercent(group)-60)/100f,mid=.4f;
   rect(b,getX(),getY()+8,getWidth(),6,DARK,alpha);rect(b,getX(),getY()+8,getWidth()*level,6,GOLD,alpha);
   rect(b,getX()+getWidth()*mid-1,getY()+5,2,12,DIM,alpha);   // 100 % mark
   rect(b,getX()+getWidth()*level-3,getY()+4,6,14,TEXT,alpha);
  }
 }
 private void buildBattleUiSettings(){
  float width=Math.min(330,W()-24),x=(W()-width)/2;
  String[] labels=forge.adventure.util.BattleUiSettings.SIZE_LABELS;
  for(int i=0;i<labels.length;i++){
   final int group=i;float y=H()-74-i*40;   // four groups since the trigger text size (2026-10-18)
   root.addActor(new Painter((b,a)->{text(b,labels[group],x,y,7,TEXT,a,width-58,Align.left,false);text(b,sizePercent(group)+"%",x+width-55,y,7,GOLD,a,55,Align.right,false);}));
   button("-",x,y-27,24,20,()->setSizePercent(group,sizePercent(group)-5,true));
   root.addActor(new SizeSlider(group,x+34,y-28,width-68));
   button("+",x+width-24,y-27,24,20,()->setSizePercent(group,sizePercent(group)+5,true));
  }
  button("Reset to 100%",x+width/2-50,26,100,16,()->{forge.adventure.util.BattleUiSettings.resetSizes();forge.adventure.util.BattleUiSettings.save();rebuild();});
  root.addActor(new Painter((b,a)->text(b,"Sizes of the buttons and texts on the battlefield, 60% to 160%. Used from the next battle on; saved automatically.",12,14,5.5f,DIM,a,W()-24,Align.center,false)));
 }
 // ------------------------------------------------------------------ tips & tricks on the VS screen (user 2026-10-17)
 // story/tips.json: short paraphrased advice from real players with name + accurate title (sources kept in the file).
 static final class Tip{String tip,name,title,topic,source,url;}
 static final class TipFile{List<Tip> tips=new ArrayList<>();}
 private static List<Tip> tips;private static int lastTip=-1;
 static Tip randomTip(){
  if(tips==null){tips=new ArrayList<>();
   try{File f=new File(DIR+"story/tips.json");if(f.isFile()){TipFile tf=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),TipFile.class);
    if(tf!=null&&tf.tips!=null)for(Tip x:tf.tips)if(x!=null&&x.tip!=null&&!x.tip.isBlank()&&x.name!=null&&!x.name.isBlank())tips.add(x);}}catch(Exception e){log(e);}}
  if(tips.isEmpty())return null;
  int i=new Random().nextInt(tips.size());if(tips.size()>1&&i==lastTip)i=(i+1)%tips.size();lastTip=i;   // never the same tip twice in a row
  return tips.get(i);
 }
 static float tipHeight(String s,float width,float size){
  BitmapFont f=font(false);f.getData().setScale(size/FONT_PX);
  return new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,s,Color.WHITE,width,Align.center,true).height;
 }
 // ------------------------------------------------------------------ story: the Portal Mage (user 2026-10-17)
 // A mysterious quest giver tells what is going on at the start of a run, at every act change and after the final boss.
 // Art + idle animation by Codex: story/portal-mage.png and story/portal-mage-frames/frame_XX.png (12 fps); a hooded
 // silhouette is drawn until they exist. Settings > Game turns the story off for players who have seen it often.
 static int storyAct(Run r){return Math.max(1,Math.min(3,(r.round-1)/5+1));}
 static final String[] STORY_TITLES={"","ACT I  ·  THE WILDS","ACT II  ·  THE HALLS OF THE WARDENS","ACT III  ·  THE SUMMIT","HOMECOMING"};
 static final String[] STORY_TEXTS={"",
  "Ah... you heard the call. Good. I am the one who opened the portal - do not look at me like that. Your world has lost something: a shard of it was swallowed by the Shard Spire, a tower adrift between the planes, fifteen floors high. The creatures down here are strays, lost like you. Climb, and take back what is yours. Fall... and the Spire forgets you. Only your relics will remember.",
  "You broke through the Wilds. Above you wait the Wardens. Some were promised freedom if no climber reaches the top. And they are right about one thing: wrenching a shard free would wound the others. That is why you must reach the Heart. The seals are already failing - leaving everything as it is will not save them.",
  "The Summit. Can you feel it? The walls hum with stolen worlds. Something at the very top has been feeding on the Spire for ages - and now it has noticed you. I cannot follow you any further. From here on, you climb alone.",
  "You did it. The Heart Shard is yours, and the portal opens for you. Go home, commander... but do not be surprised if one day you hear my call again. The Spire never truly forgets."};
 private Texture mageStill;private final List<Texture> mageFrames=new ArrayList<>();private boolean mageLoaded;
 private void loadMage(){
  if(mageLoaded)return;mageLoaded=true;
  try{
   File dir=new File(DIR+"story/portal-mage-frames");
   File[] fs=dir.isDirectory()?dir.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".png")):null;
   if(fs!=null){Arrays.sort(fs);for(File f:fs){Texture t=new Texture(Gdx.files.absolute(f.getPath()),true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);mageFrames.add(t);}}
   File still=new File(DIR+"story/portal-mage.png");
   if(still.isFile()){mageStill=new Texture(Gdx.files.absolute(still.getPath()),true);mageStill.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);}
  }catch(Throwable e){log(e);}
 }
 // Codex story v2 (story-redesign-20261017): short voiced beats, one subtitle at a time, stage art + Portal Mage.
 // story/story.json chapters (act1, act2, act3, ending, curse, + a return chapter after a failed run), audio in
 // story/audio/<beat id>.wav, background story/stage.png. Falls back to the built-in single texts.
 record Beat(String id,String text){}
 private static Map<String,List<Beat>> storyChapters;private static Map<String,String> storyTitles;
 static void loadStory(){
  if(storyChapters!=null)return;storyChapters=new HashMap<>();storyTitles=new HashMap<>();
  try{File f=new File(DIR+"story/story.json");if(!f.isFile())return;
   Map<String,Object> all=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,Object>>(){}.getType());
   if(all.get("chapters") instanceof List<?> ch)for(Object o:ch)if(o instanceof Map<?,?> m){
    String id=String.valueOf(m.get("id"));List<Beat> beats=new ArrayList<>();
    if(m.get("beats") instanceof List<?> bl)for(Object bo:bl)if(bo instanceof Map<?,?> bm&&bm.get("text")!=null)beats.add(new Beat(String.valueOf(bm.get("id")),String.valueOf(bm.get("text"))));
    if(!beats.isEmpty()){storyChapters.put(id,beats);storyTitles.put(id,m.get("title")==null?"":String.valueOf(m.get("title")));}}
  }catch(Exception e){log(e);}
 }
 /** chapter for this scene: act1..3, ending; act 1 after a failed run uses a return chapter if Codex delivered one */
 static String storyChapter(Profile p,int act){
  loadStory();
  if(act==4)return storyChapters.containsKey("ending")?"ending":null;
  if(act==5)return storyChapters.containsKey("curse")?"curse":null;   // curse-mode final: Emrakul breaks in
  // Codex triggers: first run "act1"; after a failed run "failure" + "retry"; after a completed run "return"
  if(act==1&&p.runs>0){String k=p.lastRunFailed?"retry":"return";if(storyChapters.containsKey(k))return k;}
  String k="act"+act;return storyChapters.containsKey(k)?k:null;
 }
 static List<Beat> storyBeats(Profile p,int act,String chapter){
  if(chapter==null)return List.of(new Beat("legacy",STORY_TEXTS[act]));
  List<Beat> out=new ArrayList<>();
  if("retry".equals(chapter)&&storyChapters.containsKey("failure"))out.addAll(storyChapters.get("failure"));   // "I pulled you out when your tether broke..."
  out.addAll(storyChapters.get(chapter));return out;
 }
 private int storyKey=-1,storyBeat;private float storyStart,beatStart;private String storyChapterId;
 private void playBeat(List<Beat> beats){
  try{if(currentVoice!=null)currentVoice.stop();}catch(Throwable ignored){}
  subText=null;   // the mage speaks alone: no commander line / subtitle on top of the story
  if(storyBeat>=beats.size())return;File f=new File(DIR+"story/audio/"+beats.get(storyBeat).id()+".wav");
  beatAudioMs=f.isFile()?wavMs(f):0;
  if(!f.isFile())return;
  try{float vol=voicePlayback();if(vol<=0)return;
   com.badlogic.gdx.audio.Sound snd=voiceCache.computeIfAbsent(f.getPath(),q->Gdx.audio.newSound(Gdx.files.absolute(q)));
   currentVoiceId=snd.play(Math.min(1f,vol));currentVoice=snd;lastVoice=System.currentTimeMillis();voiceEnds=lastVoice+beatAudioMs;}catch(Throwable e){log(e);}
 }
 private long beatAudioMs;
 /** act 1-3 = act scene, 4 = ending after the final boss */
 private void buildStory(Profile p,int act){
  loadMage();String chapter=storyChapter(p,act);
  List<Beat> beats=storyBeats(p,act,chapter);
  String heading=chapter==null?STORY_TITLES[act]:storyTitles.getOrDefault(chapter,"");
  if(storyKey!=act||!Objects.equals(storyChapterId,chapter)){storyKey=act;storyChapterId=chapter;storyBeat=0;storyStart=time;beatStart=time;playBeat(beats);}
  Texture stage=fileTexture(DIR+"story/stage.png");
  if(stage!=null)root.addActor(new Painter((b,a)->{float pan=(float)Math.sin(time*.05f)*10;drawCover(b,stage,-12+pan,-6,W()+24,H()+12,a);rect(b,0,0,W(),H(),Color.BLACK,a*.18f);}));
  else if(act==4)background(p.result.commander,.62f);else artBackground(null,.5f);
  float pw=Math.min(W()*.38f,H()-30),px=W()*.03f,py=Math.max(0,(H()-pw)/2-6);
  Runnable done=()->{
   try{if(currentVoice!=null)currentVoice.stop();}catch(Throwable ignored){}
   if(act==4){p.result.storySeen=true;}else if(p.run!=null){p.run.storyAct=act;if(act==1)p.lastRunFailed=false;}
   storyKey=-1;save();rebuild();};
  Runnable advance=()->{
   String text=beats.get(Math.min(storyBeat,beats.size()-1)).text();
   if((time-beatStart)*45<text.length()){beatStart=time-text.length()/45f-.1f;return;}   // first complete the subtitle
   storyBeat++;beatStart=time;if(storyBeat>=beats.size())done.run();else playBeat(beats);};
  root.addActor(new Painter((b,a)->{
   float t=time-storyStart,in=Math.min(1,t/.8f);
   float cx=px+pw/2,cy=py+pw/2,pulse=.75f+.25f*(float)Math.sin(time*1.7f),hover=(float)Math.sin(time*1.6f)*3;
   for(int i=5;i>=1;i--){float r=pw*(.28f+i*.04f);ellipse(b,cx,cy,r,r,Color.valueOf("5b3fb8"),a*in*.035f*pulse);}   // soft portal glow
   Texture frame=mageFrames.isEmpty()?mageStill:mageFrames.get((int)(time*12)%mageFrames.size());
   if(frame!=null){Color o=b.getColor().cpy();b.setColor(1,1,1,a*in);b.draw(frame,px,py+(mageFrames.isEmpty()?hover:0),pw,pw);b.setColor(o);}
   // small chapter label for the first seconds
   float la=Math.max(0,Math.min(1,Math.min(t/.4f,(3.2f-t)/.6f)));
   if(la>0){text(b,"THE PORTAL MAGE",W()*.42f,H()-14,6,DIM,a*la,W()*.55f,Align.left,false);if(!heading.isBlank())text(b,heading,W()*.42f,H()-24,9,GOLD,a*la,W()*.55f,Align.left,true);}
   // one subtitle at a time, lower middle, shallow translucent strip
   if(storyBeat<beats.size()){Beat bt=beats.get(storyBeat);float bt0=time-beatStart;int shown=Math.min(bt.text().length(),Math.max(0,(int)(bt0*45)));
    float sw=Math.min(W()*.62f,360),sx=W()*.36f,sy=56;rect(b,sx-8,sy-26,sw+16,36,Color.BLACK,a*.42f);
    text(b,bt.text().substring(0,shown),sx,sy,7.6f,TEXT,a,sw,Align.center,false);
    // auto-advance: audio end + 0.4 s, but at least max(1.5 s, words / 3.3)
    int words=bt.text().split("\\s+").length;float hold=Math.max(Math.max(1.5f,words/3.3f),beatAudioMs/1000f+.4f);
    if(bt0>hold+.2f)Gdx.app.postRunnable(()->{if(storyKey==act&&storyBeat<beats.size()&&time-beatStart>hold+.2f)advance.run();});
    text(b,(storyBeat+1)+" / "+beats.size(),sx,sy-20,5,DIM,a*.8f,sw,Align.center,false);}
   Gdx.graphics.requestRendering();
  }));
  Actor tapper=new Actor();tapper.setBounds(0,40,W(),H()-40);
  tapper.addListener(new ClickListener(){@Override public void clicked(InputEvent e,float x,float y){if(!blocked(tapper))advance.run();}});
  tapper.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){@Override public boolean keyDown(InputEvent e,int key){
   if(key==com.badlogic.gdx.Input.Keys.SPACE||key==com.badlogic.gdx.Input.Keys.ENTER){advance.run();return true;}
   if((key==com.badlogic.gdx.Input.Keys.ESCAPE||key==com.badlogic.gdx.Input.Keys.BACK)){done.run();return true;}return false;}});
  root.addActor(tapper);if(tapper.getStage()!=null)tapper.getStage().setKeyboardFocus(tapper);
  Btn go=button("Continue",W()-12-110,12,110,20,advance);go.gold=true;
  button("Skip",12,12,70,18,done);   // skips this scene only - Settings > Game turns the story off for good
 }
 // ------------------------------------------------------------------ Settings > Game: story on/off (user 2026-10-17)
 private void buildGameSettings(){
  Profile p=profile();float width=Math.min(330,W()-24),x=(W()-width)/2,y=H()-81;
  root.addActor(new Painter((b,a)->{
   text(b,"Story",x,y,7,TEXT,a,width,Align.left,false);
   text(b,"The Portal Mage tells the story at the start of a run, at every new act and after the final boss.",x,y-10,5.5f,DIM,a,width,Align.left,false);}));
  Btn on=button(p.storyOff?"Story: Off":"Story: On",x,y-44,width,20,()->{p.storyOff=!p.storyOff;save();rebuild();});
  on.gold=!p.storyOff;
  button("Show the battle tour again",x,y-96,width,18,()->{p.battleTourDone=false;save();toast("The controls tour starts in your next battle.");});
  button("Show the intro again",x,y-70,width,18,()->{if(p.run!=null)p.run.storyAct=0;p.storyOff=false;save();displayScreen=false;rebuild();});
  root.addActor(new Painter((b,a)->text(b,"\"Show the intro again\" replays the story of your current act (or of the next run).",12,23,5.5f,DIM,a,W()-24,Align.center,false)));
 }
 // ------------------------------------------------------------------ Settings > Keys: battle keyboard shortcuts (user 2026-10-17)
 private int bindingAction=-1;
 private void buildKeySettings(){
  String[] labels=forge.adventure.util.BattleUiSettings.ACTION_LABELS;
  float colW=(W()-36)/2,rowH=27,top=H()-70;int perCol=(labels.length+1)/2;
  for(int i=0;i<labels.length;i++){
   final int action=i;float x=12+(i/perCol)*(colW+12),y=top-(i%perCol)*rowH;
   root.addActor(new Painter((b,a)->text(b,labels[action],x,y+13,6.5f,TEXT,a,colW*.58f,Align.left,false)));
   boolean waiting=bindingAction==i;
   Btn k=button(waiting?"Press a key...":forge.adventure.util.BattleUiSettings.keyName(i),x+colW*.6f,y,colW*.4f-22,18,()->{bindingAction=bindingAction==action?-1:action;rebuild();});
   k.gold=waiting;
   button("x",x+colW-18,y,18,18,()->{forge.adventure.util.BattleUiSettings.clearKey(action);forge.adventure.util.BattleUiSettings.save();bindingAction=-1;rebuild();});
  }
  button("Reset keys",W()/2-50,36,100,16,()->{forge.adventure.util.BattleUiSettings.resetKeys();forge.adventure.util.BattleUiSettings.save();bindingAction=-1;rebuild();});
  root.addActor(new Painter((b,a)->text(b,bindingAction>=0?"Press the new key - Escape cancels.":"Click a key to change it, x removes it. Forge's own keys (Space, Enter, Escape) still work as before.",12,23,5.5f,bindingAction>=0?GOLD:DIM,a,W()-24,Align.center,false)));
  // invisible key catcher: receives the next key press while a binding waits
  Actor catcher=new Actor();catcher.setBounds(0,0,0,0);
  catcher.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){@Override public boolean keyDown(InputEvent e,int key){
   if(bindingAction<0)return false;
   if(key!=com.badlogic.gdx.Input.Keys.ESCAPE){forge.adventure.util.BattleUiSettings.setKey(bindingAction,key);forge.adventure.util.BattleUiSettings.save();}
   bindingAction=-1;rebuild();return true;}});
  root.addActor(catcher);
  if(catcher.getStage()!=null)catcher.getStage().setKeyboardFocus(catcher);
 }
 // Three distinct header bands; button positions are shared with the layout regression check.
 /** True Rogue-like on/off (header button since 2026-10-17); first switch-on asks once */
 private void toggleTrueRogue(){
  Profile pp=profile();
  if(!pp.trueRogueOn&&!pp.trueRogueWarned){confirm("True Rogue-like mode","For very good players only. Life you lose stays lost for the whole run, and healing never brings you above your starting life after a battle. There are NO extra rewards. Can be combined with Curses.","Enable","Cancel",()->{Profile q=profile();q.trueRogueWarned=true;q.trueRogueOn=true;save();rebuild();});return;}
  pp.trueRogueOn=!pp.trueRogueOn;save();rebuild();
 }
 static float[] selectHeaderButton(float width,int index){float gap=4,w=(width-24-4*gap)/5;return new float[]{12+index*(w+gap),w};}
 private void selectHeader(Profile p){
  root.addActor(new Painter((b,a)->{
   text(b,p.playerName==null?"ROGUE COMMANDERS":(p.runs>0?"Welcome back, ":"Welcome, ")+p.playerName+"!",12,H()-6,12,GOLD,a,W()-24,Align.left,true);
   if(p.playerName!=null)text(b,"Which adventure do you choose today?",12+W()*.42f,H()-9,7,Color.valueOf("c9c1b0"),a,W()*.58f-24,Align.right,false);
   String status="General unlocks "+p.general.size()+"/"+content().unlocks.length+"   ·   Playtime "+hours(p.activeMs);
   relicAmount(b,p.progressionPoints,W()-14,H()-22,8,a);
   text(b,status,12,H()-24,5.5f,DIM,a,W()-24,Align.left,false);bar(b,12,H()-36,W()-24,3,generalShare(),GOLD,a);
  }));
  String[] labels={"Achievements","Collection","True Rogue: "+(p.trueRogueOn?"On":"Off"),"Settings","Main menu"};   // True Rogue-like in the header (user 2026-10-17)
  Runnable[] actions={()->{achievementScreen=true;rebuild();},()->showCollection(cmd(p.selected)),this::toggleTrueRogue,this::openDisplay,()->{mainMenu=true;rebuild();}};
  for(int i=0;i<labels.length;i++){float[] position=selectHeaderButton(W(),i);Btn hb=button(labels[i],position[0],H()-57,position[1],16,actions[i]);if(i==2)hb.gold=p.trueRogueOn;}
 }

 private static boolean restoredDisplay;
 private static void restoreSavedDisplay(){
  if(restoredDisplay||Gdx.graphics==null||!Gdx.graphics.supportsDisplayModeChange())return;restoredDisplay=true;
  var settings=Config.instance().getSettingData();int index=displayIndex(settings.width,settings.height);
  // Forge's launcher starts fullscreen at desktop resolution; restore the explicitly saved preset once Rogue is ready.
  // the monitor chosen in Settings > Display (tester 2026-10-19); never for the off-screen dev test instance
  com.badlogic.gdx.Graphics.Monitor target=null;try{Profile p=profile();target=System.getProperty("forge.devmode")==null&&p!=null?monitorByKey(p.monitorKey):null;}catch(Throwable t){log(t);}
  final var mon=target!=null&&!monitorKey(target).equals(monitorKey(Gdx.graphics.getMonitor()))?target:null;
  if(settings.fullScreen&&index>=0&&(mon!=null||Gdx.graphics.getWidth()!=settings.width||Gdx.graphics.getHeight()!=settings.height))Gdx.app.postRunnable(()->{try{var mode=displayMode(index,mon!=null?mon:Gdx.graphics.getMonitor());if(mode!=null)Gdx.graphics.setFullscreenMode(mode);}catch(Exception e){log(e);}});
  else if(!settings.fullScreen&&mon!=null&&!Gdx.graphics.isFullscreen())Gdx.app.postRunnable(()->moveWindowTo(mon,Gdx.graphics.getWidth(),Gdx.graphics.getHeight()));
 }

 // ------------------------------------------------------------------ commander select

 // ------------------------------------------------------------------ main menu (user 2026-10-16)
 private static boolean menuShown;private boolean mainMenu,naming,namingNew;
 static boolean hasProgress(Profile p){return p.runs>0||p.run!=null||!p.general.isEmpty()||!p.achievements.isEmpty()||p.progressionPoints>0;}
 /** shown in the main menu; bump with every release and add an entry to _mods/release/PATCH-NOTES.md */
 static final String GAME_VERSION="0.2.8";
 static final String ITCH_PAGE="https://kingnishi.itch.io/card-force-rogue-lite-mode";
 private static volatile String latestVersion;private static boolean updateAsked;
 /** once per game start: ask itch.io (public build API, no account) which version the windows channel has; silent offline */
 private void checkForUpdate(){
  if(updateAsked)return;updateAsked=true;
  Thread t=new Thread(()->{
   try{
    var c=(java.net.HttpURLConnection)new java.net.URL("https://itch.io/api/1/x/wharf/latest?target=kingnishi/card-force-rogue-lite-mode&channel_name=windows").openConnection();
    c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setRequestProperty("User-Agent","RogueCommanders/"+GAME_VERSION);
    String body;try(var in=c.getInputStream()){body=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
    var m=java.util.regex.Pattern.compile("\"latest\"\\s*:\\s*\"([0-9.]+)\"").matcher(body);
    if(m.find()&&newer(m.group(1),System.getProperty("rogue.testversion",GAME_VERSION))){latestVersion=m.group(1);Gdx.app.postRunnable(()->{if(mainMenu&&!naming)rebuild();});}
   }catch(Throwable e){/* offline or itch unreachable: no hint */}
  },"rogue-update-check");
  t.setDaemon(true);t.start();
 }
 static boolean newer(String a,String b){
  String[] x=a.split("\\."),y=b.split("\\.");
  for(int i=0;i<Math.max(x.length,y.length);i++){
   int u=i<x.length?Integer.parseInt(x[i]):0,v=i<y.length?Integer.parseInt(y[i]):0;
   if(u!=v)return u>v;
  }
  return false;
 }
 private void buildMainMenu(Profile p){
  if(portalLayer("portal-universe-v1")!=null)universePortalBackground();else background(p.selected,.55f);
  float W=W(),H=H();
  root.addActor(new Painter((b,a)->{
   rect(b,0,0,W,H,Color.BLACK,a*.18f);
   Texture logo=fileTexture(DIR+"logo.png");   // rogue-commanders/make_logo.py (user 2026-10-17: cooler logo)
   if(logo!=null){float lh=H*.27f,lw=lh*logo.getWidth()/logo.getHeight();if(lw>W*.9f){lw=W*.9f;lh=lw*logo.getHeight()/logo.getWidth();}
    b.setColor(1,1,1,a);b.draw(logo,(W-lw)/2,H*.69f,lw,lh);b.setColor(Color.WHITE);}
   else centerText(b,"ROGUE COMMANDERS",0,H*.74f,W,30,24,GOLD,a,true);
   centerText(b,p.playerName==null?"A Commander roguelite":(p.runs>0?"Welcome back, ":"Welcome, ")+p.playerName,0,H*.66f,W,14,8.5f,Color.valueOf("e6dcc8"),a,false);
   if(p.run!=null&&!naming)centerText(b,"Run in progress: "+p.run.commander.split(",")[0]+"  ·  floor "+p.run.round+" / 15",0,H*.60f,W,12,6.6f,Color.valueOf("c9c1b0"),a,false);
   text(b,"Version "+GAME_VERSION,W-110,12,6,Color.valueOf("c9c1b0"),a,100,Align.right,false);   // bottom right
  }));
  float bw=Math.min(200,W*.42f),bx=(W-bw)/2,bh=22,gap=7,y=H*.50f;
  if(naming){buildNaming(p,bx,bw,y);return;}
  checkForUpdate();
  if(latestVersion!=null){   // new version on itch.io: gold button bottom right opens the download page
   float uw=Math.min(150,W*.36f);
   Btn up=button("Update "+latestVersion+" available",W-uw-10,24,uw,18,()->Gdx.net.openURI(ITCH_PAGE));up.gold=true;
  }
  if(hasProgress(p)||p.playerName!=null){Btn cont=button(p.run!=null?"Continue run":"Continue",bx,y,bw,bh,()->{if(p.playerName==null){naming=true;namingNew=false;rebuild();return;}mainMenu=false;rebuild();});cont.gold=true;y-=bh+gap;}
  Btn ng=button("New Game",bx,y,bw,bh,()->{naming=true;namingNew=true;rebuild();});ng.gold=!hasProgress(p)&&p.playerName==null;y-=bh+gap;
  button("Settings",bx,y,bw,bh,this::openDisplay);y-=bh+gap;
  button("Exit",bx,y,bw,bh,()->confirm("Quit Rogue Commanders?","Your progress is saved.","Quit","Stay",()->Gdx.app.exit()));
 }
 /** text input in the glass style with the scene's crisp font (the Adventure skin field was brown and pixelated) */
 class NameField extends Actor{
  final StringBuilder value=new StringBuilder();Runnable onEnter,onChange;String placeholder="Your name";
  int maxLen=20;boolean multiline;   // multi-line + Ctrl+V: hunting list bulk add (user 2026-10-19)
  NameField(String initial,float x,float y,float w,float h){if(initial!=null)value.append(initial);setBounds(x,y,w,h);
   addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){
    @Override public boolean keyTyped(com.badlogic.gdx.scenes.scene2d.InputEvent e,char ch){
     int before=value.length();
     if(ch=='\b'){if(value.length()>0)value.setLength(value.length()-1);}
     else if(ch=='\r'||ch=='\n'){if(multiline){if(value.length()<maxLen)value.append('\n');}else if(onEnter!=null)onEnter.run();}
     else if(ch==27){value.setLength(0);}
     else if(ch>=32&&ch!=127&&value.length()<maxLen)value.append(ch);
     if(onChange!=null&&value.length()!=before)onChange.run();
     return true;}
    @Override public boolean keyDown(com.badlogic.gdx.scenes.scene2d.InputEvent e,int key){   // Ctrl+V pastes, Ctrl+Enter confirms a multi-line field
     boolean ctrl=Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)||Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);
     if(ctrl&&key==com.badlogic.gdx.Input.Keys.V){paste();return true;}
     if(ctrl&&multiline&&key==com.badlogic.gdx.Input.Keys.ENTER&&onEnter!=null){onEnter.run();return true;}
     return false;}
    @Override public boolean touchDown(com.badlogic.gdx.scenes.scene2d.InputEvent e,float x,float y,int pointer,int button){if(getStage()!=null)getStage().setKeyboardFocus(NameField.this);if(touch())Gdx.input.setOnscreenKeyboardVisible(true);return true;}});
   onLongPress(this,this::paste);   // touch: long press pastes the clipboard
  }
  void paste(){String clip=null;try{clip=Gdx.app.getClipboard().getContents();}catch(Throwable t){}
   if(clip!=null){int before=value.length();clip=clip.replace("\r\n","\n").replace('\r','\n');
    for(char ch:clip.toCharArray()){if(value.length()>=maxLen)break;if(ch=='\n'||ch=='\t')value.append(multiline&&ch=='\n'?'\n':' ');else if(ch>=32&&ch!=127)value.append(ch);}
    if(onChange!=null&&value.length()!=before)onChange.run();}}
  String entered(){return value.toString().trim();}
  @Override public void draw(Batch b,float a){
   float x=getX(),y=getY(),w=getWidth(),h=getHeight();Color o=b.getColor().cpy();b.setColor(1,1,1,a);panel(1).draw(b,x,y,w,h);b.setColor(o);
   frame(b,x,y,w,h,1.2f,GOLD,a*.9f);
   if(multiline){boolean focused=getStage()!=null&&getStage().getKeyboardFocus()==this;float size=7f;
    String s=value.length()==0?placeholder:value+(focused&&((int)(time*2))%2==0?"_":"");
    BitmapFont f=font(false);f.getData().setScale(size/FONT_PX);
    com.badlogic.gdx.graphics.g2d.GlyphLayout gl=new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,s,TEXT,w-16,Align.left,true);
    while(gl.height>h-12&&s.length()>1){int nl=s.indexOf('\n');s=nl>=0&&nl<s.length()-1?s.substring(nl+1):s.substring(Math.max(1,s.length()/6));gl.setText(f,s,TEXT,w-16,Align.left,true);}   // long lists: the end stays visible
    text(b,s,x+8,y+h-6,size,value.length()==0?Color.valueOf("7f8796"):TEXT,a,w-16,Align.left,false);return;}
   String shown=value.length()==0?placeholder:value.toString();float size=h*.5f;
   BitmapFont f=font(false);f.getData().setScale(size/FONT_PX);float cap=f.getCapHeight(),tw=new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,value.toString()).width;
   text(b,shown,x+8,y+h/2+cap/2,size,value.length()==0?Color.valueOf("7f8796"):TEXT,a,w-16,Align.left,false);
   boolean focused=getStage()!=null&&getStage().getKeyboardFocus()==this;
   if(focused&&((int)(time*2))%2==0)rect(b,x+8+tw+1.5f,y+h*.22f,Math.max(.8f,h*.05f),h*.56f,GOLD,a);   // blinking cursor while typing
  }
 }
 private void buildNaming(Profile p,float bx,float bw,float y){
  root.addActor(new Painter((b,a)->centerText(b,namingNew?"Start a new game - what is your name, commander?":"What is your name, commander?",0,y+4,W(),14,8.5f,TEXT,a,false)));
  NameField field=new NameField(p.playerName==null?"":p.playerName,bx-20,y-26,bw+40,24);root.addActor(field);
  if(root.getStage()!=null)root.getStage().setKeyboardFocus(field);
  Runnable go=()->{String name=field.entered().isEmpty()?"Commander":field.entered();
   if(namingNew&&hasProgress(p))confirm("Start a new game?","Your current save ("+(p.playerName==null?"unnamed":p.playerName)+": "+p.runs+" runs, "+p.general.size()+" unlocks) is backed up first, then a fresh profile starts.","New game","Cancel",()->newProfile(name));
   else{boolean first=p.playerName==null&&!hasProgress(p);p.playerName=name;save();if(first)cleanCardOverlays();naming=false;mainMenu=false;rebuild();}};   // Continue: just the name, asked once
  field.onEnter=go;
  float by=y-58;
  Btn ok=button(namingNew?"Start new game":"Continue",bx,by,bw,22,go);ok.gold=true;
  button("Back",bx,by-28,bw,20,()->{naming=false;rebuild();});
 }
 /** first Rogue start: Forge's default overlays (card name + ID in big letters over every picture) off, once */
 static void cleanCardOverlays(){
  try{var prefs=forge.model.FModel.getPreferences();
   prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.UI_OVERLAY_CARD_NAME,false);
   prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.UI_OVERLAY_CARD_ID,false);
   prefs.save();}catch(Throwable t){log(t);}
 }
 /** New Game: the old profile is copied to rogue_commanders.backup-<time>.json, then a fresh one starts */
 private void newProfile(String name){
  try{File f=profileFile();if(f.exists())Files.copy(f.toPath(),new File(f.getParentFile(),"rogue_commanders.backup-"+System.currentTimeMillis()+".json").toPath());}catch(Exception e){log(e);}
  int volume=profile().voiceVolume;Profile np=new Profile();np.playerName=name;np.voiceVolume=volume;profile=np;save();profile=null;profile();
  naming=false;mainMenu=false;toast("New game started. Your old save was backed up.");rebuild();
 }
 private void buildSelect(Profile p){maybeGuide("select");
  Cmd[] all=content().commanders;if(all.length==0){background(null,.6f);title("Rogue Commanders","content.json is missing");button("Back",W()-62,H()-22,52,16,this::back);return;}
  Cmd sel=cmd(p.selected);if(sel==null){sel=all[0];p.selected=sel.name;}
  final Cmd c=sel;
  if(portalLayer("portal-universe-v1")!=null)universePortalBackground();else if(portalLayer("portal-vortex")!=null)portalBackground(c);else background(c.name,.55f);   // painted portal (Codex) once delivered
  selectHeader(p);
  float W=W(),H=H();boolean portrait=H>W;
  // landscape: the big card gives up a little height so the two roster rows get bigger cards (user 2026-10-16:
  // "nutze mehr Platz fuer die Commander, damit man die besser erkennen kann") - roster cards 26 wide
  float cw=portrait?96:Math.max(70,Math.min(96,(H-68-8-4-3-2*(26/.716f))*.716f)),ch=cw/.716f,cx=12,cy=H-68-ch;
  CardView big=new CardView(c.name,cx,cy,cw,ch);big.selected=true;big.mystery=!commanderOpen(p,c);root.addActor(big);
  float ix=portrait?cx:cx+cw+12,iw=portrait?W-24:W-ix-12,iy=portrait?cy-6:H-68;
  List<String> unl=unlockedFor(c);int sig=p.signature.getOrDefault(c.name,List.of()).size();
  int best=p.best.getOrDefault(c.name,0);List<String> deckNow=loadout(c);long built=unl.stream().filter(deckNow::contains).count();
  root.addActor(new Painter((b,a)->{
   float top=portrait?iy:iy;float px=portrait?ix:ix,pw=iw;
   if(!portrait){Color o=b.getColor().cpy();b.setColor(1,1,1,a*.92f);panel().draw(b,px-4,cy-2,pw+8,top-cy+6);b.setColor(o);}
   float y=top-4;
   if(!commanderOpen(p,c)){   // hidden until unlocked: only how to get it
    y-=text(b,"???  -  a hidden commander",px+4,y,11,Color.valueOf("8a93a7"),a,pw-8,Align.left,true)+8;
    y-=text(b,"HOW TO UNLOCK",px+4,y,6.6f,GOLD,a,pw-8,Align.left,true)+4;
    text(b,commanderRequirement(p,c),px+4,y,7,TEXT,a,pw-8,Align.left,false);return;}
   float nameW=pw-8-(skins(c.name).size()>1?94:0);y-=text(b,c.name,px+4,y,fitSingleLine(c.name,11,nameW,true),GOLD,a,nameW,Align.left,true)+4;
   pips(b,c.colors,px+4,y-10,5.6f,a);y-=14;
   y-=text(b,c.blurb,px+4,y,6.2f,TEXT,a,pw-8,Align.left,false)+5;
   StringBuilder th=new StringBuilder();int shared=0;for(Theme t:c.themes){if(t.key!=null&&t.key.startsWith("neutral-")){shared++;continue;}if(th.length()>0)th.append("  ·  ");th.append(t.name);}   // 30+ shared packages would overflow into the buttons
   if(shared>0)th.append("   +  "+shared+" shared packages");
   y-=text(b,"Themes: "+th,px+4,y,6,DIM,a,pw-8,Align.left,false)+6;
   text(b,"Signature unlocks  "+sig+"/"+c.specific.length,px+4,y,6,TEXT,a,pw-8,Align.left,false);
   bar(b,px+pw*.48f,y-5.5f,pw*.48f,5,c.specific.length==0?0:sig/(float)c.specific.length,tierColor("signatur"),a);y-=11;
   text(b,"Best run  "+wins(best)+"   ·   Deck: "+unl.stream().filter(loadout(c)::contains).count()+" unlocks added",px+4,y,6,TEXT,a,pw-8,Align.left,false);
  }));
  // roster strip
  // landscape: two rows (user 2026-10-16: "mach da unten 2 Reihen, das ist zu crowded"), as big as the room below the big card allows
  int n=all.length,rowsN=portrait?1:2,perRow=(n+rowsN-1)/rowsN;float gap=3,tw,th;
  if(portrait){tw=Math.min(26,(W-24-(n-1)*gap)/n);th=tw/.716f;}
  else{th=Math.min(30/.716f,(cy-8-4-gap*(rowsN-1))/rowsN);tw=Math.min(th*.716f,(W-150-(perRow-1)*gap)/perRow);th=tw/.716f;}
  float sx=12,sy=8;
  for(int i=0;i<n;i++){Cmd o=all[i];int row=i/perRow,col=i%perRow;
   CardView v=new CardView(o.name,sx+col*(tw+gap),sy+(rowsN-1-row)*(th+gap),tw,th);v.selected=o==c;
   if(!commanderOpen(p,o)){v.mystery=true;v.border=DIM;}else if(bonusCommander(o)){v.tag="Bonus";v.border=Color.valueOf("ffb43c");}
   v.action=()->{profile().selected=o.name;save();rebuild();if(commanderOpen(profile(),o))voice(o.name,"select");};root.addActor(v);}
  float bx=portrait?12:W-128,bw=portrait?(W-30)/2:116;
  // no "Prepare deck" here: starting a run opens the deck screen anyway (user 2026-10-17)
  if(portrait){Btn s=button("START RUN",12,sy+th+6,W-24,18,()->beginRun(c));s.gold=true;s.disabled=!commanderOpen(p,c);}
  else{Btn s=button("START RUN",bx,sy,bw,22,()->beginRun(c));s.gold=true;s.disabled=!commanderOpen(p,c);}
  int available=(int)skins(c.name).stream().filter(s->skinUnlocked(p,c.name,s)).count();
  boolean artBtn=skins(c.name).size()>1&&commanderOpen(p,c);   // right-aligned next to the commander name (user 2026-10-17)
  if(artBtn)button("Artwork "+available+"/"+skins(c.name).size(),ix+iw-4-86,iy-21,86,16,()->{wardrobe=c;rebuild();});

  if(p.cursesUnlocked){Btn cb=button("Curses: "+(p.cursesOn?"On":"Off"),portrait?12:bx,portrait?sy+th+30:sy+32,portrait?W-24:bw,16,()->{profile().cursesOn=!profile().cursesOn;save();rebuild();});cb.gold=p.cursesOn;}   // only once unlocked (user 2026-10-17)

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
 /** START RUN: players must see the deck preparation first (global unlocks only count once they are put into the
  *  deck), so the run start goes through "Prepare deck" and returns to the package selection afterwards. */
 private void beginRun(Cmd c){
  if(!commanderOpen(profile(),c)){toast(commanderRequirement(profile(),c));return;}
  List<String> pool=unlockedFor(c);pool.removeAll(loadout(c));
  if(pool.isEmpty()){packageConfig=c;packagePage=0;rebuild();return;}
  showLoadout(c,true);
 }
 private void openLoadoutEditor(Cmd c){showLoadout(c,false);}
 /** pre-run deck in the same browser as the run deck: tab Deck and tab Heirlooms (unlocked cards not in the deck,
  *  starter cards taken out, basic lands); right-click moves a card (user 2026-10-16) */
 private void showLoadout(Cmd c,boolean thenPackages){
  List<String> deck=new ArrayList<>(loadout(c));Set<String> heir=new HashSet<>(unlockedFor(c));Set<String> starter=new HashSet<>(defaultDeck(c));
  int[] tab={0};
  // separate pools (user 2026-10-17): tab 1 = shared heirlooms (+ starter cards taken out), tab 2 = this commander's signature cards
  java.util.function.Supplier<List<String>> poolNow=()->{List<String> l=new ArrayList<>();Set<String> sig=new HashSet<>(signaturesFor(c));
   for(String n:heirloomsFor(c))if(!deck.contains(n)&&!sig.contains(n))l.add(n);
   for(String n:new LinkedHashSet<>(defaultDeck(c)))if(!isBasic(n)&&!deck.contains(n)&&!l.contains(n)&&!sig.contains(n))l.add(n);
   return l;};
  java.util.function.Supplier<List<String>> sigNow=()->{List<String> l=new ArrayList<>();for(String n:signaturesFor(c))if(!deck.contains(n))l.add(n);return l;};
  java.util.function.Supplier<List<Item>> items=()->{
   Map<String,Integer> counts=new LinkedHashMap<>();
   if(tab[0]==0){for(String n:deck)if(!n.equals(c.name))counts.merge(n,1,Integer::sum);}
   else if(tab[0]==2){for(String n:sigNow.get())counts.merge(n,1,Integer::sum);}
   else{for(String n:poolNow.get())counts.merge(n,1,Integer::sum);
    String colors=c.colors==null?"":c.colors;for(char ch:colors.toCharArray()){int k=COLORS.indexOf(ch);if(k>=0)counts.putIfAbsent(BASIC_NAMES[k],0);}
    if(colors.isEmpty())counts.putIfAbsent("Wastes",0);}
   List<Item> out=new ArrayList<>();
   for(var e:counts.entrySet()){Item i=new Item();i.name=e.getKey();i.count=Math.max(1,e.getValue());i.recent=-1;
    if(tab[0]==1&&isBasic(i.name)){i.tag="Basic land";i.tagColor=DIM;}
    else if(heir.contains(i.name)){i.heirloom=true;   /* no "Heirloom" label/frame (user 2026-10-17); the Heirlooms filter still finds them */}
    else if(tab[0]==1&&starter.contains(i.name)){i.tag="Starter";i.tagColor=DIM;}
    out.add(i);}
   return out;
  };
  BrowserEdit edit=new BrowserEdit(){
   public String[] tabs(){return new String[]{"Deck  ("+deck.size()+")","Heirlooms  ("+poolNow.get().size()+")","Signatures  "+sigNow.get().size()+"/"+(c.specific==null?0:c.specific.length)};}
   public int tab(){return tab[0];}
   public void tab(int t){tab[0]=t;}
   public void act(Item i){
    if(tab[0]==0){deck.remove(i.name);toast(i.name+(isBasic(i.name)?" removed":signaturesFor(c).contains(i.name)?" moved to Signatures":" moved to Heirlooms")+"  -  deck: "+deck.size());}
    else{if(deck.size()>=MAX_DECK){toast("The deck is full ("+MAX_DECK+" cards + commander). Take a card out first.");return;}
     deck.add(i.name);toast(i.name+" added to the deck  -  deck: "+deck.size());}
   }
   public void closed(){
    int before=deck.size();List<String> fixed=normalize(c,deck);profile().loadout.put(c.name,fixed);save();
    if(thenPackages){packageConfig=c;packagePage=0;toast("Deck saved ("+fixed.size()+" cards) - now choose "+RUN_PICKS+" packages for this run.");rebuild();return;}
    toast(before>MAX_DECK?"Deck had "+before+" cards - the weakest were removed (max 99).":before<DECK?"Deck filled with basic lands to the minimum of "+DECK+" ("+(DECK-before)+" added).":"Deck saved: "+fixed.size()+" cards + commander.");
   }
  };
  showBrowser("Prepare deck  ·  "+c.name.split(",")[0],items,
   new String[]{"Alle","Kreaturen","Zauber","Artefakte","Verzauberungen","Planeswalker","Länder","Neu im Run","Erbstücke"},
   new String[]{"Neueste","Mana","Typ","Farbe","Rating","Name","Neu"},   // heirlooms: newest unlock first by default (user 2026-10-17)
   list->{int lands=0;for(String n:deck)if(isLand(n))lands++;int n=deck.size();
    return n+" cards + commander   ·   "+lands+" lands   ·   "+(n-lands)+" others"+(n<DECK?"   ·   needs at least "+DECK+" (basic lands are added)":n>MAX_DECK?"   ·   max "+MAX_DECK:"")
     +tx("   ·   right-click a card: ","   ·   long-press a card: ")+(tab[0]==0?"out of the deck":"into the deck");},
   ()->showCollection(c),"Unlocked",edit);
 }
 private void openForgeLoadoutEditor(Cmd c){editingThenPackages=false;
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
   :before<DECK?(DECK-before)+" basic land(s) added to reach the minimum of "+DECK+" cards.":"Run deck saved: "+fixed.size()+" cards + commander.");
 }
 private void finishEditing(){
  Cmd c=cmd(editingCommander);Deck d=editing.registeredDeck;editing=null;if(c==null||d==null)return;
  if(editingRun){editingRun=false;finishRunEditing(c,d);return;}
  Set<String> allowed=new HashSet<>(defaultDeck(c));allowed.addAll(unlockedFor(c));
  List<String> main=new ArrayList<>();
  for(var e:d.getMain())for(int i=0;i<e.getValue();i++){String n=e.getKey().getName();if(allowed.contains(n)||isBasic(n))main.add(n);}
  int before=main.size();List<String> fixed=normalize(c,main);
  profile().loadout.put(c.name,fixed);save();
  if(editingThenPackages){editingThenPackages=false;packageConfig=c;packagePage=0;
   toast("Deck saved ("+fixed.size()+" cards) - now choose "+RUN_PICKS+" packages for this run.");return;}
  toast(before>MAX_DECK?"Deck had "+before+" cards - the weakest were removed (max 99).":before<DECK?"Deck filled with basic lands to the minimum of "+DECK+" ("+(DECK-before)+" added).":"Deck saved: "+fixed.size()+" cards + commander.");
 }

 // ------------------------------------------------------------------ run
 private void startRun(Cmd c){
  if(!commanderOpen(profile(),c)){toast(commanderRequirement(profile(),c));return;}
  Run r=new Run();r.commander=c.name;r.seed=new Random().nextLong();r.deck=normalize(c,loadout(c));
  r.chosenPackages=packageSelection(profile(),c);r.selectedPackages=runPackages(profile(),c,r.chosenPackages,r.seed);initRunEconomy(r);shopOpen=false;
  for(String k:r.chosenPackages)packagePicks(profile()).merge(c.slug+":"+k,1,Integer::sum);   // statistics: picked before a run
  {List<String> saved=profile().packageChoices.get(c.name);if(saved!=null)saved.removeIf(k->isNeutral(findTheme(c,k)));}   // neutral picks: this run only
  r.cursed=profile().cursesUnlocked&&profile().cursesOn;
  r.trueRogue=profile().trueRogueOn;r.life=LIFE;   // true rogue-like: life carries over between battles
  // every run swaps up to eight starter cards for other cards this commander's players like, so early rounds differ
  if(c.alts!=null&&c.alts.length>0){
   Random rv=new Random(r.seed^0xa17L);Set<String> starter=new HashSet<>(Arrays.asList(c.starter));starter.removeAll(List.of("Sol Ring","Arcane Signet"));if(c.protectedStarter!=null)starter.removeAll(Arrays.asList(c.protectedStarter));
   List<String> swappable=new ArrayList<>();for(String n:r.deck)if(starter.contains(n))swappable.add(n);
   List<String> alts=new ArrayList<>(Arrays.asList(c.alts));alts.removeAll(r.deck);Collections.shuffle(alts,rv);Collections.shuffle(swappable,rv);
   for(int i=0;i<Math.min(8,Math.min(alts.size(),swappable.size()));i++){if(paper(alts.get(i))==null)continue;r.deck.remove(swappable.get(i));r.deck.add(alts.get(i));}
  }
  {List<String> unl=unlockedFor(c);for(String n:r.deck)if(unl.contains(n))r.heirlooms++;}r.opponent=pickOpponent(r);
  if(r.cursed){r.curseOptions=curseOffer(r,false);if(!r.curseOptions.isEmpty())r.phase="curse";}   // curse mode: the run begins with a curse
  profile().run=r;save();rebuild();voice(c.name,"run_start");
 }


 /** Infinite-combo shortcut (user 2026-10-16). The same activated ability of the human player, repeated with an
  *  identical battlefield/stack and the same net gain every time, is a loop (e.g. Basalt Monolith + Kinnan: +1
  *  colorless per cycle). After three equal cycles the player chooses how often to repeat it and the net gain - mana by
  *  colour, cards drawn, own life gained, opponent life lost - is applied at once. Basic loops only: activated
  *  abilities; trigger-only combos stay manual. Snapshot: W U B R G C pool, hand, library, life, opponent life. */
 static final class LoopWatch{
  final forge.game.Game game;final Map<String,List<long[]>> snaps=new HashMap<>();final Map<String,String> boards=new HashMap<>();
  final Set<String> offered=new HashSet<>();int turn=-1;
  static final byte[] COLS={forge.card.MagicColor.WHITE,forge.card.MagicColor.BLUE,forge.card.MagicColor.BLACK,forge.card.MagicColor.RED,forge.card.MagicColor.GREEN,forge.card.MagicColor.COLORLESS};
  static final String[] COL_NAMES={"white","blue","black","red","green","colorless"};
  LoopWatch(forge.game.Game g){game=g;}
  @com.google.common.eventbus.Subscribe public void cast(forge.game.event.GameEventSpellAbilityCast e){
   try{
    if(e.sa()==null||e.sa().isSpell()||e.si()==null||e.si().getActivatingPlayer()==null||e.si().getActivatingPlayer().isAI())return;
    forge.game.player.Player me=null,opp=null;for(var pl:game.getRegisteredPlayers()){if(pl.isAI())opp=pl;else me=pl;}if(me==null)return;
    int t=game.getPhaseHandler().getTurn();if(t!=turn){turn=t;snaps.clear();boards.clear();offered.clear();}
    var host=e.sa().getHostCard();String key=(host==null?"?":String.valueOf(host.getId()))+"|"+e.sa().getDescription();
    long[] v=snapshot(me,opp);String board=board(me);
    List<long[]> list=snaps.computeIfAbsent(key,k->new ArrayList<>());
    String before=boards.put(key,board);if(before!=null&&!before.equals(board))list.clear();   // the board must come back exactly
    list.add(v);if(list.size()>6)list.remove(0);
    if(list.size()<4||offered.contains(key))return;
    long[] d=diff(list.get(list.size()-1),list.get(list.size()-2));
    for(int i=list.size()-4;i<list.size()-1;i++)if(!Arrays.equals(diff(list.get(i+1),list.get(i)),d))return;   // three equal cycles
    if(!beneficial(d))return;
    offered.add(key);offer(me,opp,host==null?"this ability":host.getName(),host==null?null:game.findById(host.getId()),d);
   }catch(Throwable ex){log(ex);}
  }
  long[] snapshot(forge.game.player.Player me,forge.game.player.Player opp){
   long[] v=new long[10];for(int i=0;i<6;i++)v[i]=me.getManaPool().getAmountOfColor(COLS[i]);
   v[6]=me.getCardsIn(forge.game.zone.ZoneType.Hand).size();v[7]=me.getCardsIn(forge.game.zone.ZoneType.Library).size();
   v[8]=me.getLife();v[9]=opp==null?0:opp.getLife();return v;
  }
  String board(forge.game.player.Player me){
   List<forge.game.card.Card> cards=new ArrayList<>();for(var c:me.getCardsIn(forge.game.zone.ZoneType.Battlefield))cards.add(c);cards.sort(Comparator.comparingInt(forge.game.card.Card::getId));
   StringBuilder sb=new StringBuilder("stack").append(game.getStack().size());
   for(var c:cards)sb.append(';').append(c.getId()).append(c.isTapped()?'T':'U').append(c.getCounters());
   return sb.toString();
  }
  static long[] diff(long[] a,long[] b){long[] d=new long[a.length];for(int i=0;i<a.length;i++)d[i]=a[i]-b[i];return d;}
  static boolean beneficial(long[] d){
   if(d[8]<0)return false;   // never shortcut a loop that costs the player life
   for(int i=0;i<6;i++)if(d[i]<0)return false;
   for(int i=0;i<6;i++)if(d[i]>0)return true;
   return d[6]>0&&d[7]<0||d[8]>0||d[9]<0;
  }
  static String describe(long[] d){
   List<String> out=new ArrayList<>();for(int i=0;i<6;i++)if(d[i]>0)out.add("+"+d[i]+" "+COL_NAMES[i]+" mana");
   if(d[6]>0&&d[7]<0)out.add("+"+(-d[7])+" card"+(-d[7]==1?"":"s"));if(d[8]>0)out.add("+"+d[8]+" life");if(d[9]<0)out.add((-d[9])+" damage to the opponent");
   return String.join(", ",out);
  }
  void offer(forge.game.player.Player me,forge.game.player.Player opp,String name,forge.game.card.Card source,long[] d){
   if(!(me.getController() instanceof forge.player.PlayerControllerHuman h))return;
   int max=500;long draw=d[7]<0?-d[7]:0;
   if(draw>0)max=(int)Math.min(max,Math.max(0,me.getCardsIn(forge.game.zone.ZoneType.Library).size()-1)/draw);   // never deck yourself
   if(d[9]<0&&opp!=null)max=(int)Math.min(max,(opp.getLife()+(-d[9])-1)/(-d[9]));   // enough to finish the opponent
   if(max<=0)return;
   int n=h.chooseNumber(null,"Infinite loop found ("+name+"): each repetition gives "+describe(d)+". How many times? (0 = keep playing it manually)",0,max);
   if(n<=0)return;
   for(int i=0;i<6;i++){long k=d[i]*n;if(k<=0)continue;List<forge.game.mana.Mana> mana=new ArrayList<>();
    for(long j=0;j<Math.min(k,5000);j++)mana.add(new forge.game.mana.Mana(COLS[i],source,null,me));me.getManaPool().addMana(mana);}
   if(draw>0)me.drawCards((int)(draw*n));
   if(d[8]>0)me.gainLife((int)(d[8]*n),source,null);
   if(d[9]<0&&opp!=null)opp.loseLife((int)(-d[9]*n),false,false,null);
   snaps.clear();boards.clear();
  }
 }
 // ------------------------------------------------------------------ Slay-the-Spire style dungeon map (user 2026-10-16)
 static final int MAP_VERSION=3,TREASURE_FAVOR=5;
 static boolean mapActive(Run r){return r!=null&&r.map!=null&&!r.map.isEmpty();}
 static MapNode mapNode(Run r,int id){return r.map!=null&&id>=0&&id<r.map.size()?r.map.get(id):null;}
 static MapNode currentNode(Run r){MapNode n=mapNode(r,r.mapPos);return n!=null&&n.floor==r.round?n:null;}
 static float rel(MapNode n){return n.count<=1?.5f:n.lane/(float)(n.count-1);}
 /** decks of the floor's tier; elite = the next tier up */
 static final List<Set<String>> ACT_ROOMS_L=List.of(
  Set.of("jungle","mystic-forest","den","aerie","grotto","warren","backyard","dragon-lair","ruins","hive"),
  Set.of("castle","graveyard","crypt","cathedral","den","library","ruins"),
  Set.of("void","cathedral","fire-temple","library","workshop","aerie","hive","grotto"));
 @SuppressWarnings("unchecked") static final Set<String>[] ACT_ROOMS=ACT_ROOMS_L.toArray(new Set[0]);
 static List<OpponentSpec> tierDecks(Run r,int floor,boolean elite){
  Run t=new Run();t.round=floor;t.cursed=r.cursed;String tier=opponentTier(t);
  if("starter".equals(tier));   // floor 1 stays vanilla even in an elite room (user 2026-10-17)
  else if(elite&&floor<=4)tier="early";   // floors 1-4: an elite room is an "early" deck (+1 reward tier), not yet Modern (user 2026-10-16)
  else if(elite)tier=switch(tier){case "intro","early"->"modern-solid";case "modern-solid"->"modern-strong";case "modern-strong"->"legacy-low";default->"legacy-top";};
  List<OpponentSpec> out=new ArrayList<>();List<String> ids=opponents().pools.get(tier);
  if(ids!=null)for(String id:ids){OpponentSpec d=specById(id);if(d!=null)out.add(d);}
  if(floor%5!=0){   // act themes (user 2026-10-18): act 1 animals / jungle / dinos / dragons, act 2 undead / demons / humans, act 3 celestials / gods / Eldrazi
   int act=Math.max(0,Math.min(2,(floor-1)/5));Set<String> rooms=ACT_ROOMS[act];List<OpponentSpec> themed=new ArrayList<>();
   for(OpponentSpec d:out){boolean fits=false;if(d.acts!=null){for(int x:d.acts)if(x==act+1)fits=true;}else fits=d.room!=null&&rooms.contains(d.room);if(fits)themed.add(d);}
   if(themed.size()>=4)return themed;   // enough variety - otherwise the whole tier
  }
  return out;
 }
 /** places per floor (index = floor; 5, 10, 15 are the single boss): floor 1 only two choices to start (user 2026-10-17) */
 static final int[] FLOOR_SLOTS={0, 2,3,2,3, 1, 3,2,3,2, 1, 3,2,3,2, 1};   // at most 3 per floor (user 2026-10-18: "4 Paths sind immer ein Problem"), alternating = staggered rows
 static void ensureMap(Run r){
  if(r==null||r.commander==null||(r.map!=null&&r.mapVersion==MAP_VERSION&&!r.map.isEmpty()))return;
  Random rnd=new Random(r.seed*0x9E3779B1L+0x51A9L);List<MapNode> m=new ArrayList<>();List<List<MapNode>> floors=new ArrayList<>();floors.add(new ArrayList<>());
  for(int f=1;f<=15;f++){List<MapNode> row=new ArrayList<>();
   if(f%5==0){MapNode n=new MapNode();n.floor=f;n.count=1;n.type="boss";
    if(f<15){List<OpponentSpec> boss=tierDecks(r,f,false);if(!boss.isEmpty())n.deck=boss.get(rnd.nextInt(boss.size())).id;}
    row.add(n);}
   else{int count=FLOOR_SLOTS[f];   /* fixed number of places per floor = fixed spots on the painted map; the roads between them stay random (user 2026-10-18) */List<OpponentSpec> pool=tierDecks(r,f,false);Collections.shuffle(pool,rnd);boolean elite=false;
    for(int i=0;i<count;i++){MapNode n=new MapNode();n.floor=f;n.lane=i;n.count=count;n.type="fight";
     if(f>=3&&!elite&&rnd.nextFloat()<.22f){List<OpponentSpec> ep=tierDecks(r,f,true);if(!ep.isEmpty()){n.type="elite";n.deck=ep.get(rnd.nextInt(ep.size())).id;elite=true;}}
     if(n.deck==null&&!pool.isEmpty())n.deck=pool.get(i%pool.size()).id;
     row.add(n);}}
   for(MapNode n:row){n.id=m.size();m.add(n);}floors.add(row);}
  for(int f=1;f<15;f++){List<MapNode> a=floors.get(f),b=floors.get(f+1);
   for(MapNode n:a){final float ya=rel(n);List<MapNode> near=new ArrayList<>(b);near.sort(Comparator.comparingDouble(x->Math.abs(rel(x)-ya)));
    n.next.add(near.get(0).id);if(near.size()>1&&rnd.nextFloat()<.45f)n.next.add(near.get(1).id);}
   for(MapNode x:b){boolean has=false;for(MapNode n:a)if(n.next.contains(x.id))has=true;
    if(!has){MapNode best=a.get(0);for(MapNode n:a)if(Math.abs(rel(n)-rel(x))<Math.abs(rel(best)-rel(x)))best=n;best.next.add(x.id);}}}
  List<MapNode> slots=new ArrayList<>();for(MapNode n:m)if(n.floor>=3&&n.floor<=14&&n.floor%5!=0)slots.add(n);Collections.shuffle(slots,rnd);
  int k=0;for(String pre:new String[]{"treasure","treasure","unknown","unknown","unknown","unknown"})if(k<slots.size())slots.get(k++).pre=pre;
  if(r.trueRogue)for(int fl:new int[]{4,9,14})for(MapNode n:slots)if(n.floor==fl&&n.pre==null){n.pre="rest";break;}   // campfires only in True Rogue-like
  r.map=m;r.mapVersion=MAP_VERSION;r.mapPath=new ArrayList<>();r.mapPos=-1;
 }
 static List<MapNode> reachable(Run r){
  List<MapNode> out=new ArrayList<>();if(!mapActive(r))return out;MapNode at=mapNode(r,r.mapPos);
  if(at!=null&&at.floor==r.round-1){for(int id:at.next){MapNode n=mapNode(r,id);if(n!=null)out.add(n);}}
  else for(MapNode n:r.map)if(n.floor==r.round)out.add(n);   // run start or an older save: any room of this floor
  return out;
 }
 static void applyNode(Run r,MapNode n){
  if(n==null||n.floor==15){if(n!=null)r.eliteFight=false;return;}   // the final boss is chosen at the final gate
  OpponentSpec d=n.deck==null?null:specById(n.deck);if(d==null)return;
  r.opponent=d.enemy;r.eliteFight="elite".equals(n.type);
  if(routedRound(r.round)){r.routeRound=r.round;r.routeOptions=new ArrayList<>(List.of(d.id));r.routeChoice=d.id;r.routeConfirmed=true;}
 }
 /** the opponent of the next fight: a pending ambush, else the chosen map room, else the classic pick */
 static void resumeOpponent(Run r){
  if(r.extraFight&&r.extraDeck!=null){OpponentSpec d=specById(r.extraDeck);if(d!=null){r.opponent=d.enemy;return;}}
  MapNode n=currentNode(r);if(n!=null&&n.floor<15){applyNode(r,n);return;}
  r.opponent=pickOpponent(r);
 }
 static String treasureCard(Run r){
  Cmd c=cmd(r.commander);if(c==null)return null;Set<String> own=new HashSet<>(r.deck);own.addAll(bench(r));List<String> pool=new ArrayList<>();
  for(Theme t:activeThemes(c,r))if(t.cards!=null)for(CardRef x:t.cards)if("epic".equals(x.tier)&&!own.contains(x.n)&&paper(x.n)!=null&&!pool.contains(x.n))pool.add(x.n);
  if(pool.isEmpty())return null;return pool.get(new Random(r.seed^((long)r.round*0x9E37L)^0x7EA5L).nextInt(pool.size()));
 }
 /** enter a room; its extra step (treasure / ? / campfire) comes first */
 void enterNode(Run r,MapNode n){
  if(n==null||reachable(r).stream().noneMatch(x->x.id==n.id))return;
  r.mapPos=n.id;r.mapPath.add(n.id);applyNode(r,n);mapSelected=-1;
  if(n.pre!=null&&!n.preDone){n.preDone=true;
   switch(n.pre){
    case "treasure"->{r.treasureCard=treasureCard(r);r.phase="treasure";}
    case "rest"->r.phase="rest";
    default->{Random rnd=new Random(r.seed^((long)n.id*0x2545F491L));
     List<String> kinds=new ArrayList<>(List.of("relics","favor","offer","merchant"));kinds.removeAll(r.encounterTypes);if(merchantCards(r).isEmpty())kinds.remove("merchant");
     if(!kinds.isEmpty()&&rnd.nextBoolean()){String enc=kinds.get(rnd.nextInt(kinds.size()));r.encounter=enc;r.encounters++;r.encounterTypes.add(enc);r.phase="encounter";merchantPick.clear();}
     else{List<OpponentSpec> pool=tierDecks(r,r.round,false);pool.removeIf(d->d.id.equals(n.deck));
      if(!pool.isEmpty()){r.extraFight=true;r.extraDeck=pool.get(rnd.nextInt(pool.size())).id;resumeOpponent(r);toast("Ambush! An extra fight blocks the way - it gives a normal reward.");}}}
   }}
  save();rebuild();
 }
 private static Texture discTex;
 static Texture discTexture(){
  if(discTex!=null)return discTex;int n=64;Pixmap p=new Pixmap(n,n,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
  for(int y=0;y<n;y++)for(int x=0;x<n;x++){float dx=x-n/2f+.5f,dy=y-n/2f+.5f,r=(float)Math.sqrt(dx*dx+dy*dy);p.setColor(1,1,1,Math.max(0,Math.min(1,n/2f-r)));p.drawPixel(x,y);}
  discTex=new Texture(p,true);p.dispose();discTex.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return discTex;
 }
 static void disc(Batch b,float cx,float cy,float r,Color c,float a){Color o=b.getColor().cpy();b.setColor(c.r,c.g,c.b,c.a*a);b.draw(discTexture(),cx-r,cy-r,r*2,r*2);b.setColor(o);}
 static void line(Batch b,float x1,float y1,float x2,float y2,float t,Color c,float a,boolean dashed){
  float len=(float)Math.hypot(x2-x1,y2-y1),ang=(float)Math.toDegrees(Math.atan2(y2-y1,x2-x1));if(len<=0)return;
  TextureRegion w=new TextureRegion(white());Color o=b.getColor().cpy();b.setColor(c.r,c.g,c.b,c.a*a);
  if(!dashed)b.draw(w,x1,y1-t/2,0,t/2,len,t,1,1,ang);
  else for(float s=0;s<len;s+=t*4.5f){float seg=Math.min(t*2.6f,len-s);b.draw(w,x1+(x2-x1)*s/len,y1+(y2-y1)*s/len-t/2,0,t/2,seg,t,1,1,ang);}
  b.setColor(o);
 }
 /** floors on the map screen (user 2026-10-18: "der Boss bekommt eine eigene Background-Karte, dann haben wir mehr Platz"):
  *  normal floors -> the act's four floors (1-4, 6-9, 11-14); boss floor -> only the boss (from == to) */
 static int mapFrom(Run r){int f=Math.max(1,Math.min(15,r.round));return f%5==0?f:((f-1)/5)*5+1;}
 static int mapTo(Run r){int f=Math.max(1,Math.min(15,r.round));return f%5==0?f:((f-1)/5)*5+4;}
 static int actFrom(Run r){int seg=((Math.max(1,Math.min(15,r.round))-1)/5)*5+1;return seg>1?seg-1:1;}   // the boss you came from + the act
 static int actTo(Run r){return Math.min(15,((Math.max(1,Math.min(15,r.round))-1)/5)*5+5);}
 /** act view, left -> right (user 2026-10-16): the boss you came from on the left, the next boss on the right */
 static float[] nodePos(MapNode n,float x,float y,float w,float h,int from,int to){
  float cols=to-from+1;return new float[]{x+w*((n.floor-from)+.5f)/cols,y+h*(n.count<=1?.5f:.86f-.72f*rel(n))};}
 /** compact full strip (run screen), left -> right */
 static float[] nodePos(MapNode n,float x,float y,float w,float h){return new float[]{x+w*(.035f+.93f*(n.floor-1)/14f),y+h*(n.count<=1?.5f:.88f-.76f*rel(n))};}
 /** the map between floors from..to: dashed paths, walked path in gold, reachable rooms pulse; big = sprites + badges */
 void drawSpireMap(Batch b,float a,Run r,float x,float y,float w,float h,float nr,int selected,boolean big){drawSpireMap(b,a,r,x,y,w,h,nr,selected,big,1,15);}
 void drawSpireMap(Batch b,float a,Run r,float x,float y,float w,float h,float nr,int selected,boolean big,int from,int to){
  List<MapNode> reach=reachable(r);Set<Integer> reachIds=new HashSet<>();for(MapNode n:reach)reachIds.add(n.id);
  Color dim=Color.valueOf("8b93a7"),cyan=Color.valueOf("78c8ff");
  for(MapNode n:r.map)for(int id:n.next){MapNode m=mapNode(r,id);if(m==null||n.floor<from||m.floor>to)continue;float[] p1=big?nodePos(n,x,y,w,h,from,to):nodePos(n,x,y,w,h),p2=big?nodePos(m,x,y,w,h,from,to):nodePos(m,x,y,w,h);
   int i=r.mapPath.indexOf(n.id);boolean walked=i>=0&&i+1<r.mapPath.size()&&r.mapPath.get(i+1)==m.id;boolean next=n.id==r.mapPos&&reachIds.contains(m.id);
   line(b,p1[0],p1[1],p2[0],p2[1],big?(walked||next?1.6f:1f):.8f,walked?GOLD:next?cyan:dim,a*(walked?.95f:next?.9f:.35f),!walked);}
  float pulse=.5f+.5f*(float)Math.sin(time*4);
  for(MapNode n:r.map){if(n.floor<from||n.floor>to)continue;float[] q=big?nodePos(n,x,y,w,h,from,to):nodePos(n,x,y,w,h);float rr=n.type.equals("boss")?nr*1.3f:nr;
   boolean walked=r.mapPath.contains(n.id),can=reachIds.contains(n.id),sel=n.id==selected;
   Color ring=n.type.equals("boss")||n.type.equals("elite")?RED:walked?GOLD:can?cyan:Color.valueOf("4a5266");
   if(can)disc(b,q[0],q[1],rr*(1.35f+.15f*pulse),cyan,a*.22f);
   if(sel)disc(b,q[0],q[1],rr*1.55f,GOLD,a*.35f);
   disc(b,q[0],q[1],rr,ring,a);disc(b,q[0],q[1],rr*.82f,Color.valueOf("0e1119"),a*.95f);
   OpponentSpec d=n.deck==null?null:specById(n.deck);
   if(big&&d!=null){float al=walked&&!sel?.5f:1f;drawSprite(b,d.room,q[0],q[1],rr*1.45f,a*al);}
   else if(big&&n.floor==15)centerText(b,"15",q[0]-rr,q[1]-rr,rr*2,rr*2,rr*.8f,RED,a,true);
   if(big&&"elite".equals(n.type)){float ex=q[0]+rr*.92f,ey=q[1]+rr*.92f,er=rr*.36f;disc(b,ex,ey,er,RED,a);disc(b,ex,ey,er*.78f,Color.valueOf("2a0e10"),a);centerText(b,"E",ex-er,ey-er,er*2,er*2,er*1.1f,RED,a,true);}
   if(n.pre!=null&&!n.preDone){float bx=q[0]-rr*.92f,by=q[1]+rr*.92f,br=Math.max(2.2f,rr*.4f);
    Color pc="treasure".equals(n.pre)?Color.valueOf("ffc94a"):"rest".equals(n.pre)?Color.valueOf("ff8a3c"):Color.valueOf("c79bff");
    disc(b,bx,by,br,pc,a);disc(b,bx,by,br*.78f,Color.valueOf("0e1119"),a);
    if(big)centerText(b,"treasure".equals(n.pre)?"$":"rest".equals(n.pre)?"^":"?",bx-br,by-br,br*2,br*2,br*1.15f,pc,a,true);}
  }
 }
 // ------------------------------------------------------------------ "The road ahead": path map v2 (Codex design, user 2026-10-17)
 // Locations (room illustrations) instead of circles, broad lit stone roads, only the selected branch brightens,
 // walked roads quiet silver, later floors in fog, a marker where you stand. Room art: rooms/<id>.jpg (Codex).
 static String roomName(String room){
  if(room==null)return "";StringBuilder s=new StringBuilder();
  for(String p:room.split("-")){if(p.isEmpty())continue;if(s.length()>0)s.append(' ');s.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}
  return s.toString();
 }
 /** tile rectangle {x,y,w,h} of a location, centred on its node position */
 static float[] tileRect(MapNode n,float x,float y,float w,float h,int from,int to,float tw,float th){
  float[] q=nodePos(n,x,y,w,h,from,to);float k="boss".equals(n.type)?1.28f:1f;return new float[]{q[0]-tw*k/2,q[1]-th*k/2+4,tw*k,th*k};
 }
 static void road(Batch b,float x1,float y1,float x2,float y2,float t,Color fill,float a){
  line(b,x1,y1,x2,y2,t*1.7f,Color.BLACK,a*.55f,false);   // dark verge
  line(b,x1,y1,x2,y2,t,fill,a,false);
  line(b,x1,y1,x2,y2,Math.max(.6f,t*.28f),Color.WHITE,a*.18f,false);   // worn centre
 }
 void drawRoadMap(Batch b,float a,Run r,float x,float y,float w,float h,int selected,int from,int to,float tw,float th){
  List<MapNode> reach=reachable(r);Set<Integer> reachIds=new HashSet<>();for(MapNode n:reach)reachIds.add(n.id);
  Color stone=Color.valueOf("cbbfa3"),silver=Color.valueOf("aeb6c6"),violet=Color.valueOf("8a72ff"),lamp=Color.valueOf("ffd27a");
  float pulse=.5f+.5f*(float)Math.sin(time*3),t=Math.max(2.4f,th*.16f);
  MapNode at=mapNode(r,r.mapPos);
  // roads: future in fog, walked silver, open choices with a violet edge light, the selected one bright
  for(int pass=0;pass<2;pass++)for(MapNode n:r.map)for(int id:n.next){MapNode m=mapNode(r,id);if(m==null||n.floor<from||m.floor>to)continue;
   float[] p1=nodePos(n,x,y,w,h,from,to),p2=nodePos(m,x,y,w,h,from,to);
   int i=r.mapPath.indexOf(n.id);boolean walked=i>=0&&i+1<r.mapPath.size()&&r.mapPath.get(i+1)==m.id;
   boolean open=(at==null?n.floor==r.round-1:n.id==r.mapPos)&&reachIds.contains(m.id),sel=open&&m.id==selected;
   boolean top=sel||open;if((pass==1)!=top)continue;   // open roads drawn last, on top
   if(sel){line(b,p1[0],p1[1],p2[0],p2[1],t*3.2f,lamp,a*.16f*(.7f+.3f*pulse),false);road(b,p1[0],p1[1],p2[0],p2[1],t*1.15f,Color.valueOf("f1e2bd"),a);}
   else if(open){line(b,p1[0],p1[1],p2[0],p2[1],t*2.3f,violet,a*.22f*(.6f+.4f*pulse),false);road(b,p1[0],p1[1],p2[0],p2[1],t,stone,a*.92f);}
   else if(walked)road(b,p1[0],p1[1],p2[0],p2[1],t*.85f,silver,a*.6f);
   else road(b,p1[0],p1[1],p2[0],p2[1],t*.75f,stone,a*(m.floor<=r.round?.32f:.2f));   // (v2 map)
   if(open||sel){float len=(float)Math.hypot(p2[0]-p1[0],p2[1]-p1[1]);int lamps=Math.max(1,(int)(len/16));   // lanterns along the road
    for(int k=1;k<=lamps;k++){float f=k/(lamps+1f),lx=p1[0]+(p2[0]-p1[0])*f,ly=p1[1]+(p2[1]-p1[1])*f+t*1.1f;
     float fl=.75f+.25f*(float)Math.sin(time*5+k*1.7f);disc(b,lx,ly,t*1.6f,lamp,a*.22f*fl*(sel?1.4f:1));disc(b,lx,ly,t*.42f,lamp,a*(sel?1:.8f)*fl);}}
  }
  // locations
  for(MapNode n:r.map){if(n.floor<from||n.floor>to)continue;
   float[] q=tileRect(n,x,y,w,h,from,to,tw,th);boolean walked=r.mapPath.contains(n.id),can=reachIds.contains(n.id),sel=n.id==selected,boss="boss".equals(n.type),elite="elite".equals(n.type);
   boolean fog=n.floor>r.round&&!can;OpponentSpec d=n.deck==null?null:specById(n.deck);
   Color edge=boss||elite?RED:sel?GOLD:can?violet:walked?silver:Color.valueOf("3a4152");
   if(sel)for(int k=4;k>=1;k--)rect(b,q[0]-k*2,q[1]-k*2,q[2]+k*4,q[3]+k*4,GOLD,a*.07f);
   else if(can)for(int k=3;k>=1;k--)rect(b,q[0]-k*1.6f,q[1]-k*1.6f,q[2]+k*3.2f,q[3]+k*3.2f,violet,a*.06f*(.6f+.4f*pulse));
   rect(b,q[0]-1.5f,q[1]-1.5f,q[2]+3,q[3]+3,Color.BLACK,a*.7f);
   Texture tex=d==null?null:roomTexture(d.room);
   if(tex!=null)drawCover(b,tex,q[0],q[1],q[2],q[3],a*(fog?.55f:walked&&!sel?.6f:1));
   else rect(b,q[0],q[1],q[2],q[3],boss?Color.valueOf("2a0e14"):Color.valueOf("141824"),a);
   for(int k=0;k<5;k++)rect(b,q[0],q[1],q[2],q[3]*.12f*(5-k),Color.BLACK,a*.07f);   // darker foot for the label
   if(fog){rect(b,q[0],q[1],q[2],q[3],Color.valueOf("1b2233"),a*.5f);for(int k=0;k<4;k++)rect(b,q[0],q[1]+q[3]*k/4f,q[2],q[3]/4f,Color.valueOf("8aa0c0"),a*.05f*(1+(float)Math.sin(time*.6f+k+n.lane)));}
   if(walked&&!sel)rect(b,q[0],q[1],q[2],q[3],Color.valueOf("9aa3b5"),a*.12f);
   if(d!=null&&!fog){float s=Math.min(q[2],q[3])*.42f;drawSprite(b,d.room,q[0]+q[2]/2,q[1]+q[3]*.58f,s,a*(walked&&!sel?.55f:1));}
   if(n.floor==15)centerText(b,"FINAL GATE",q[0],q[1]+q[3]*.45f,q[2],10,Math.min(6.4f,q[2]*.13f),RED,a,true);
   frame(b,q[0],q[1],q[2],q[3],sel?1.4f:.9f,edge,a*(fog?.5f:1));
   // label plate under the location
   String label=boss?(n.floor==15?"The Summit":"BOSS"):d==null?"":roomName(d.room);
   String state=walked?"Completed":can?(elite?"Elite encounter":"Upcoming encounter"):boss?"Floor "+n.floor:fog?"":"";
   float ls=Math.min(5.6f,q[2]*.13f);float lh=state.isEmpty()?ls+3:ls*2+4;
   if(!label.isEmpty()&&!fog){rect(b,q[0]-2,q[1]-lh-1,q[2]+4,lh,Color.valueOf("0b0e16"),a*.82f);frame(b,q[0]-2,q[1]-lh-1,q[2]+4,lh,.5f,boss||elite?RED:sel?GOLD:Color.valueOf("5b6378"),a*.8f);
    centerText(b,label,q[0]-2,q[1]-ls-1.5f,q[2]+4,ls+1,ls,boss||elite?RED:sel?GOLD:TEXT,a,true);
    if(!state.isEmpty())centerText(b,state,q[0]-2,q[1]-lh+.5f,q[2]+4,ls,ls*.78f,Color.valueOf("aab2c4"),a,false);}
   if(n.pre!=null&&!n.preDone&&!fog){float bx=q[0]+3,by=q[1]+q[3]-3,br=Math.max(2.4f,q[3]*.13f);
    Color pc="treasure".equals(n.pre)?Color.valueOf("ffc94a"):"rest".equals(n.pre)?Color.valueOf("ff8a3c"):Color.valueOf("c79bff");
    disc(b,bx,by,br,pc,a);disc(b,bx,by,br*.78f,Color.valueOf("0e1119"),a);centerText(b,"treasure".equals(n.pre)?"$":"rest".equals(n.pre)?"^":"?",bx-br,by-br,br*2,br*2,br*1.15f,pc,a,true);}
   if(elite&&!fog){float ex=q[0]+q[2]-3,ey=q[1]+q[3]-3,er=Math.max(2.4f,q[3]*.13f);disc(b,ex,ey,er,RED,a);disc(b,ex,ey,er*.78f,Color.valueOf("2a0e10"),a);centerText(b,"E",ex-er,ey-er,er*2,er*2,er*1.1f,RED,a,true);}
  }
  // where you stand: a small gold marker on the last location (or at the road's start)
  float[] me;if(at!=null&&at.floor>=from&&at.floor<=to){float[] q=tileRect(at,x,y,w,h,from,to,tw,th);me=new float[]{q[0]+q[2]/2,q[1]+q[3]+3};}
  else me=new float[]{x+4,y+h/2};
  float bob=(float)Math.sin(time*3)*1.2f;disc(b,me[0],me[1]+5+bob,4.2f,GOLD,a*.3f);disc(b,me[0],me[1]+5+bob,2.6f,GOLD,a);
  line(b,me[0],me[1]+bob,me[0],me[1]+4+bob,1.2f,GOLD,a,false);
  Gdx.graphics.requestRendering();
 }
 // ------------------------------------------------------------------ path map v3: a physical climb (user 2026-10-17)
 // Places stand on a landscape and the land climbs from the bottom (near, big) to the next boss at the top (far,
 // small). Curved stone roads with lanterns, fog over later floors. Codex art: rooms/map/act<n>.jpg (landscape),
 // rooms/locations/<room>.png (diorama on a plateau); fallback = the small room sprites on a drawn plateau.
 static float climbRow(MapNode n,int from,int to){return (n.floor-from)/(float)Math.max(1,to-from);}   // 0 bottom .. 1 top (boss map: 0)
 static float climbScale(MapNode n,int from,int to){return 1.18f-.42f*climbRow(n,from,to);}
 // ------------------------------------------------------------------ fixed spots on the painted act maps (user 2026-10-18:
 // "das schwebt in der Luft" - map-v4-art/place_spots.py writes rooms/map/spots.json: per act the ground point of every
 // place (floor x lane), the road start at the bottom and the exit to the boss, in pixels of the 2560x1440 map)
 static final class ActSpots{int[][][] floors;int[] start,exit;}
 private static Map<String,ActSpots> spots;
 /** screen size of the map screen: the act map is drawn over the whole screen, spot pixels map onto it */
 static float mapScreenW=480,mapScreenH=270;
 static ActSpots actSpots(int act){
  if(spots==null){spots=new HashMap<>();try{File f=new File(DIR+"rooms/map/spots.json");if(f.isFile())
   spots=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,ActSpots>>(){}.getType());}catch(Exception e){log(e);}}
  return spots==null?null:spots.get(String.valueOf(act));
 }
 static float[] spotUi(int[] px){return new float[]{px[0]/2560f*mapScreenW,mapScreenH-px[1]/1440f*mapScreenH};}
 /** ground point of the landmark painted into a boss map (rooms/map/boss-anchors.json, 2560x1440 px; user 2026-10-18:
  *  "die Boss-Gebaeude zentraler") - maps without an entry keep the computed layout (old (1030,880) convention) */
 private static Map<String,int[]> bossAnchors;private static long bossAnchorsStamp=-1;
 static int[] bossAnchor(String map){
  File f=new File(DIR+"rooms/map/boss-anchors.json");long stamp=f.isFile()?f.lastModified():0;
  if(stamp!=bossAnchorsStamp){bossAnchorsStamp=stamp;bossAnchors=new HashMap<>();
   if(stamp>0)try{Map<String,int[]> m=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,int[]>>(){}.getType());if(m!=null)bossAnchors.putAll(m);}catch(Exception ex){log(ex);}}
  int[] p=bossAnchors.get(map);return p!=null&&p.length==2?p:null;
 }
 static float[] spotOf(MapNode n,int from,int to){
  if(from==to){if(!bossPainted||bossMap==null)return null;int[] p=bossAnchor(bossMap);return p==null?null:spotUi(p);}
  ActSpots sp=actSpots((from-1)/5+1);if(sp==null||sp.floors==null)return null;
  int row=n.floor-from;if(row<0||row>=sp.floors.length)return null;int[][] f=sp.floors[row];
  if(n.lane<0||n.lane>=f.length||f.length!=n.count)return null;   // an older run with other counts: computed layout
  return spotUi(f[n.lane]);
 }
 static float[] climbPos(MapNode n,float x,float y,float w,float h,int from,int to){
  {float[] sp=spotOf(n,from,to);if(sp!=null)return sp;}
  // staggered rows (user 2026-10-18 "nicht genug Platz"): fixed spacing, each row sits in the gaps of the row before
  // (2-3-4-3 / 3-4-3-4 places), mild narrowing towards the top for depth; mirrored in map-v4-art/make_spot_guides.py
  if(from==to)return new float[]{x+w*.5f,y+h*.30f};   // boss map: the boss in the middle, the road comes from below
  float row=climbRow(n,from,to),jitter=((n.id*37)%11-5)/5f;   // organic, stable per node
  float xx=x+w*(.5f+(n.lane-(n.count-1)/2f)*.28f*(1-.12f*row)+jitter*.012f),yy=y+h*(.17f+.75f*row)+jitter*h*.008f;
  return new float[]{xx,yy};
 }
 /** clickable / label box of a place: {x,y,w,h} around its plateau */
 /** size of a place on the map (user 2026-10-18: "Orte werden groesser, wenn sie auswaehlbar sind; nicht auswaehlbare bleiben
  *  klein und semi-transparent, sodass man am Ende highlighted Objects + Path hat"): selected 1.35, choices 1.2, your path 1, rest .7 */
 static float placeEmphasis(boolean walked,boolean can,boolean sel){return sel?1.22f:can?1.1f:walked?.95f:.68f;}
 static float[] placeRect(MapNode n,float x,float y,float w,float h,int from,int to,float base){
  float[] q=climbPos(n,x,y,w,h,from,to);float s=base*climbScale(n,from,to)*("boss".equals(n.type)?1.35f:1)*placeEmphasis(false,true,false);return new float[]{q[0]-s*.55f,q[1]-s*.22f,s*1.1f,s*.95f};
 }
 static void ellipse(Batch b,float cx,float cy,float rw,float rh,Color c,float a){Color o=b.getColor().cpy();b.setColor(c.r,c.g,c.b,c.a*a);b.draw(discTexture(),cx-rw,cy-rh,rw*2,rh*2);b.setColor(o);}
 static float[] bez(float[] p1,float[] p2,float t){   // S-curve between two places: leaves upward, arrives from below
  float dy=(p2[1]-p1[1])*.55f,u=1-t;
  float c1x=p1[0],c1y=p1[1]+dy,c2x=p2[0],c2y=p2[1]-dy;
  return new float[]{u*u*u*p1[0]+3*u*u*t*c1x+3*u*t*t*c2x+t*t*t*p2[0],u*u*u*p1[1]+3*u*u*t*c1y+3*u*t*t*c2y+t*t*t*p2[1]};
 }
 void curvedRoad(Batch b,float[] p1,float[] p2,float w1,float w2,Color fill,float a,boolean lamps,boolean bright){
  int steps=18;float[] prev=p1;
  for(int i=1;i<=steps;i++){float t=i/(float)steps;float[] q=bez(p1,p2,t);float wd=w1+(w2-w1)*t;
   line(b,prev[0],prev[1],q[0],q[1],wd*1.8f,Color.BLACK,a*.45f,false);prev=q;}
  prev=p1;
  for(int i=1;i<=steps;i++){float t=i/(float)steps;float[] q=bez(p1,p2,t);float wd=w1+(w2-w1)*t;
   line(b,prev[0],prev[1],q[0],q[1],wd,fill,a,false);
   if(i%2==0)line(b,prev[0],prev[1],q[0],q[1],Math.max(.5f,wd*.22f),Color.WHITE,a*.16f,false);   // worn stones
   prev=q;}
  if(lamps)for(int i=1;i<=3;i++){float t=i/4f;float[] q=bez(p1,p2,t);float wd=w1+(w2-w1)*t,side=(i%2==0?1:-1)*wd*1.25f;
   float fl=.75f+.25f*(float)Math.sin(time*5+i*1.7f+p1[0]);Color lamp=Color.valueOf("ffd27a");
   rect(b,q[0]+side-.4f,q[1],.8f,wd*1.4f,Color.valueOf("2a2018"),a);   // post
   disc(b,q[0]+side,q[1]+wd*1.5f,wd*2.2f,lamp,a*.20f*fl*(bright?1.5f:1));disc(b,q[0]+side,q[1]+wd*1.5f,wd*.5f,lamp,a*fl);}
 }
 /** a real paved road along the S-curve (user 2026-10-17: "mehr nach einer richtigen Strasse"): shadow, dark curb,
  *  dirt shoulder, light paving with cobble stones (or Codex' rooms/map/road.png laid along the curve), lanterns */
 private int mapAct=1;
 void street(Batch b,float[] p1,float[] p2,float w1,float w2,Color stone,float a,boolean lamps,boolean bright){
  int steps=24;float[][] pt=new float[steps+1][];for(int i=0;i<=steps;i++)pt[i]=bez(p1,p2,i/(float)steps);
  java.util.function.BiFunction<Integer,Float,Float> wd=(i,k)->(w1+(w2-w1)*i/(float)steps)*k;
  Color[] layer={Color.BLACK,Color.valueOf("2b2118"),Color.valueOf("6b5a40")};float[] widths={2.25f,1.75f,1.45f},alphas={.42f,.95f,.9f};
  Texture tex=fileTexture(DIR+"rooms/map/road-act"+mapAct+".png");if(tex==null)tex=fileTexture(DIR+"rooms/map/road.png");   // Codex road art per act
  for(int l=0;l<(tex!=null?1:layer.length);l++)for(int i=1;i<=steps;i++)line(b,pt[i-1][0],pt[i-1][1],pt[i][0],pt[i][1],wd.apply(i,widths[l]),layer[l],a*alphas[l]*(tex!=null?.5f:1),false);   // painted roads bring their own edges
  if(tex!=null)tex.setWrap(Texture.TextureWrap.Repeat,Texture.TextureWrap.ClampToEdge);
  float u=0;   // texture position along the whole curve: the painted pattern runs on without seams
  for(int i=1;i<=steps;i++){float x1=pt[i-1][0],y1=pt[i-1][1],x2=pt[i][0],y2=pt[i][1],len=(float)Math.hypot(x2-x1,y2-y1),ww=wd.apply(i,1f);
   if(len<=0)continue;
   if(tex!=null){float ang=(float)Math.toDegrees(Math.atan2(y2-y1,x2-x1)),rw=ww*1.7f,segU=len/(rw*tex.getWidth()/(float)tex.getHeight());   // painted strip incl. its soft grass edges
    TextureRegion reg=new TextureRegion(tex);reg.setRegion(u,0f,u+segU,1f);u+=segU;
    Color o=b.getColor().cpy();b.setColor(stone.r,stone.g,stone.b,a);b.draw(reg,x1,y1-rw/2,0,rw/2,len+.4f,rw,1,1,ang);b.setColor(o);continue;}
   line(b,x1,y1,x2,y2,ww,stone,a,false);
   float nx=-(y2-y1)/len,ny=(x2-x1)/len;   // cobbles: two staggered rows of stones
   for(int k=0;k<2;k++){float f=(k+.5f)/2,side=((i+k)%2==0?1:-1)*ww*.22f,cx=x1+(x2-x1)*f+nx*side,cy=y1+(y2-y1)*f+ny*side,rr=ww*.2f;
    ellipse(b,cx,cy,rr,rr*.72f,Color.valueOf("5a4e3c"),a*.38f);ellipse(b,cx-rr*.12f,cy+rr*.1f,rr*.72f,rr*.5f,Color.valueOf("efe4c8"),a*.28f);}}
  if(lamps)for(int i=1;i<=3;i++){float t=i/4f;float[] q=bez(p1,p2,t);float ww=w1+(w2-w1)*t,side=(i%2==0?1:-1)*ww*1.15f;
   float fl=.75f+.25f*(float)Math.sin(time*5+i*1.7f+p1[0]);Color lamp=Color.valueOf("ffd27a");
   Texture lt=fileTexture(DIR+"rooms/map/lantern.png");float lh=ww*2.6f;
   disc(b,q[0]+side,q[1]+lh*.78f,ww*1.9f,lamp,a*.22f*fl*(bright?1.5f:1));
   if(lt!=null){Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(lt,q[0]+side-lh/4,q[1],lh/2,lh);b.setColor(o);}   // Codex lantern
   else{rect(b,q[0]+side-ww*.08f,q[1],ww*.16f,ww*1.3f,Color.valueOf("2a2018"),a);disc(b,q[0]+side,q[1]+ww*1.4f,ww*.38f,lamp,a*fl);}}
 }
 // ------------------------------------------------------------------ run-path strip v2 (Codex concept A, user approved 2026-10-18)
 // The "Next opponent" screen shows the current act as a painted panorama band (rooms/map/run-path-act<n>.png, 1000x150
 // design units): five columns (4 floors + boss), the places you walked / are about to enter as painted dioramas, all
 // places of later floors small, lit road along your path, a lantern at your position. Past and current floors show only
 // the place you chose. Returns false without the art (old node strip as fallback).
 boolean drawRunPath(Batch b,float a,Run r,float bx,float by,float bw,float bh,int from,int to){
  int act=Math.max(1,Math.min(3,(to-1)/5+1));mapAct=act;
  Texture band=fileTexture(DIR+"rooms/map/run-path-act"+act+".png");if(band==null)return false;
  float sw=Math.min(bw,bh/.15f),sh=sw*.15f,sx=bx+(bw-sw)/2,sy=by+(bh-sh)/2,u=sw/1000f;
  java.util.function.BiFunction<Float,Float,float[]> P=(dx,dy)->new float[]{sx+dx*u,sy+sh-dy*u};   // design (x right, y down) -> screen
  {Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(band,sx,sy,sw,sh);b.setColor(o);}
  MapNode at=mapNode(r,r.mapPos);
  float[] colX={90,295,500,705,910};
  Map<Integer,float[]> pos=new LinkedHashMap<>();   // id -> {x,y,size}
  for(int f=from;f<=to;f++){int col=f-from;if(col<0||col>4)continue;
   List<MapNode> row=new ArrayList<>();for(MapNode n:r.map)if(n.floor==f)row.add(n);
   row.sort(Comparator.comparingInt(n->n.lane));
   boolean boss=f%5==0;
   List<MapNode> shown=new ArrayList<>();
   if(f<r.round||at!=null&&at.floor==f){for(MapNode n:row)if(r.mapPath.contains(n.id)||at!=null&&n.id==at.id)shown.add(n);}   // chosen place only
   if(shown.isEmpty()&&f>=r.round)shown=row;   // a past floor without a recorded choice shows nothing
   int k=shown.size();float X=colX[col];
   for(int i=0;i<k;i++){MapNode n=shown.get(i);float size=boss?100:(f<r.round||at!=null&&n.id==at.id)?86:56,x=X,y=112;
    if(k==2){x=X+(i==0?-30:30);y=i==0?100:118;}
    else if(k>=3){x=X+(i%2==0?-38:38);y=i<2?70:118;if(k==3&&i==2)x=X;}
    pos.put(n.id,new float[]{x,y,size});}
  }
  // roads: lead-in from the left, then every connection between two shown places; your path is lit
  Color lit=Color.valueOf("f3e6c4"),dim=Color.valueOf("8a8f9c");
  float rw=17*u;
  for(MapNode n:r.map){float[] p=pos.get(n.id);if(p==null)continue;
   if(n.floor==from){boolean walked=r.mapPath.contains(n.id)||at!=null&&n.id==at.id;hroad(b,P.apply(4f,112f),P.apply(p[0],p[1]),rw,walked?lit:dim,a*(walked?1f:.7f));}
   for(int id:n.next){float[] q=pos.get(id);if(q==null)continue;
    int i=r.mapPath.indexOf(n.id);boolean walked=i>=0&&(i+1<r.mapPath.size()&&r.mapPath.get(i+1)==id||i+1==r.mapPath.size()&&at!=null&&at.id==id);
    hroad(b,P.apply(p[0],p[1]),P.apply(q[0],q[1]),rw,walked?lit:dim,a*(walked?1f:.7f));}}
  // places, back rows first
  List<Map.Entry<Integer,float[]>> order=new ArrayList<>(pos.entrySet());order.sort(Comparator.comparingDouble(e->e.getValue()[1]));
  for(var e:order){MapNode n=mapNode(r,e.getKey());float[] p=e.getValue();if(n==null)continue;
   OpponentSpec d=n.deck==null?null:specById(n.deck);String room=n.floor==15?"final-gate":d==null?null:d.room;
   float S=p[2]*u;float[] g=P.apply(p[0],p[1]);
   boolean current=at!=null&&n.id==at.id,done=n.floor<r.round,future=!done&&!current;
   if(current){float pulse=.5f+.5f*(float)Math.sin(time*3);ellipse(b,g[0],g[1]+S*.3f,S*.62f,S*.4f,Color.valueOf("ffcf5c"),a*(.22f+.12f*pulse));}
   ellipse(b,g[0],g[1]+S*.02f,S*.42f,S*.1f,Color.BLACK,a*.45f);
   Texture loc=room==null?null:fileTexture(DIR+"rooms/locations/act"+act+"/"+room+".png");
   if(loc==null&&room!=null)loc=fileTexture(DIR+"rooms/locations/"+room+".png");
   float al=a*(future?.82f:1f);
   if(loc!=null){Color o=b.getColor().cpy();b.setColor(done?.78f:1,done?.78f:1,done?.82f:1,al);b.draw(loc,g[0]-S*.6f,g[1]-S*.12f,S*1.2f,S*1.2f);b.setColor(o);}
   else if(room!=null)drawSprite(b,room,g[0],g[1]+S*.36f,S*.62f,al);
   // markers: E (elite) right, event ($ ^ ?) left - on dark plates, 14-16 design px
   float mr=Math.max(2.6f,8*u),my=g[1]+S*.78f;
   if("elite".equals(n.type)){float ex=g[0]+S*.42f;disc(b,ex,my,mr,RED,a);disc(b,ex,my,mr*.78f,Color.valueOf("2a0e10"),a);centerText(b,"E",ex-mr,my-mr,mr*2,mr*2,mr*1.1f,RED,a,true);}
   if(n.pre!=null&&!n.preDone){float ex=g[0]-S*.42f;Color pc="treasure".equals(n.pre)?Color.valueOf("ffc94a"):"rest".equals(n.pre)?Color.valueOf("ff8a3c"):Color.valueOf("c79bff");
    disc(b,ex,my,mr,pc,a);disc(b,ex,my,mr*.78f,Color.valueOf("0e1119"),a);centerText(b,"treasure".equals(n.pre)?"$":"rest".equals(n.pre)?"^":"?",ex-mr,my-mr,mr*2,mr*2,mr*1.15f,pc,a,true);}
  }
  // lantern at your position
  if(at!=null&&pos.containsKey(at.id)){float[] p=pos.get(at.id);float S=p[2]*u;float[] g=P.apply(p[0],p[1]);
   Texture lt=fileTexture(DIR+"rooms/map/lantern.png");float lh=32*u,lx=g[0]+S*.5f;float fl=.75f+.25f*(float)Math.sin(time*5);
   disc(b,lx,g[1]+lh*.75f,lh*.55f,Color.valueOf("ffd27a"),a*.25f*fl);
   if(lt!=null){Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(lt,lx-lh/4,g[1]-lh*.07f,lh/2,lh);b.setColor(o);}}
  // floor labels under the band
  for(int f=from;f<=to;f++){int col=f-from;if(col<0||col>4)continue;boolean boss=f%5==0;
   Color cc=f<r.round?GREEN:f==r.round?Color.valueOf("ffcf5c"):boss?RED:DIM;float[] q=P.apply(colX[col],150f);
   text(b,boss?"BOSS "+f:"FLOOR "+f,q[0]-30,q[1]+1,Math.max(4.8f,16*u),cc,a,60,Align.center,boss||f==r.round);}
  Gdx.graphics.requestRendering();
  return true;
 }
 /** horizontal S-road (leaves and arrives level) with the painted road texture of the act */
 void hroad(Batch b,float[] p1,float[] p2,float w,Color tint,float a){
  int steps=20;float dx=(p2[0]-p1[0])*.5f;float[][] pt=new float[steps+1][];
  for(int i=0;i<=steps;i++){float t=i/(float)steps,v=1-t;
   pt[i]=new float[]{v*v*v*p1[0]+3*v*v*t*(p1[0]+dx)+3*v*t*t*(p2[0]-dx)+t*t*t*p2[0],v*v*v*p1[1]+3*v*v*t*p1[1]+3*v*t*t*p2[1]+t*t*t*p2[1]};}
  Texture tex=fileTexture(DIR+"rooms/map/road-act"+mapAct+".png");if(tex==null)tex=fileTexture(DIR+"rooms/map/road.png");
  for(int i=1;i<=steps;i++)line(b,pt[i-1][0],pt[i-1][1],pt[i][0],pt[i][1],w*1.5f,Color.BLACK,a*.3f,false);
  if(tex==null){for(int i=1;i<=steps;i++)line(b,pt[i-1][0],pt[i-1][1],pt[i][0],pt[i][1],w,tint,a,false);return;}
  tex.setWrap(Texture.TextureWrap.Repeat,Texture.TextureWrap.ClampToEdge);
  float uu=0,rw=w*1.7f;
  for(int i=1;i<=steps;i++){float x1=pt[i-1][0],y1=pt[i-1][1],x2=pt[i][0],y2=pt[i][1],len=(float)Math.hypot(x2-x1,y2-y1);if(len<=0)continue;
   float ang=(float)Math.toDegrees(Math.atan2(y2-y1,x2-x1)),segU=len/(rw*tex.getWidth()/(float)tex.getHeight());
   TextureRegion reg=new TextureRegion(tex);reg.setRegion(uu,0f,uu+segU,1f);uu+=segU;
   Color o=b.getColor().cpy();b.setColor(tint.r,tint.g,tint.b,a);b.draw(reg,x1,y1-rw/2,0,rw/2,len+.4f,rw,1,1,ang);b.setColor(o);}
 }
 void drawClimbMap(Batch b,float a,Run r,float x,float y,float w,float h,int selected,int from,int to,float base){
  List<MapNode> reach=reachable(r);Set<Integer> reachIds=new HashSet<>();for(MapNode n:reach)reachIds.add(n.id);
  int act=Math.max(1,Math.min(3,(to-1)/5+1));mapAct=act;Texture land=fileTexture(DIR+"rooms/map/act"+act+".jpg");
  if(land!=null&&climbFullScreen){}   // the landscape is already the screen background (buildSpireMap)
  else if(land!=null)drawCover(b,land,x-8,y-20,w+16,h+30,a);
  else{rect(b,x-8,y-20,w+16,h+30,Color.valueOf("0b1020"),a*.55f);   // fallback ground: darker far away, lighter near
   for(int k=0;k<14;k++)rect(b,x-8,y-20+(h+30)*k/14f,w+16,(h+30)/14f,Color.valueOf("1d2a40"),a*.05f*(14-k));}
  Color stone=Color.valueOf("cbbfa3"),silver=Color.valueOf("aeb6c6"),violet=Color.valueOf("8a72ff"),lamp=Color.valueOf("ffd27a");
  float pulse=.5f+.5f*(float)Math.sin(time*3);MapNode at=mapNode(r,r.mapPos);
  // roads, top (far) first so near roads overlap them
  List<MapNode> order=new ArrayList<>(r.map);order.sort(Comparator.comparingInt((MapNode n)->-n.floor));
  for(int pass=0;pass<2;pass++)for(MapNode n:order)for(int id:n.next){MapNode m=mapNode(r,id);if(m==null||n.floor<from||m.floor>to)continue;
   int i=r.mapPath.indexOf(n.id);boolean walked=i>=0&&i+1<r.mapPath.size()&&r.mapPath.get(i+1)==m.id;
   boolean open=(at==null?n.floor==r.round-1:n.id==r.mapPos)&&reachIds.contains(m.id),sel=open&&m.id==selected;
   if((pass==1)!=(open||sel))continue;
   float[] p1=climbPos(n,x,y,w,h,from,to),p2=climbPos(m,x,y,w,h,from,to);float w1=base*.15f*climbScale(n,from,to),w2=base*.15f*climbScale(m,from,to);   // wider: a real road
   if(sel){curvedRoad(b,p1,p2,w1*2.6f,w2*2.6f,lamp,a*.18f*(.7f+.3f*pulse),false,false);street(b,p1,p2,w1*1.1f,w2*1.1f,Color.valueOf("f3e6c4"),a,true,true);}
   else if(open){curvedRoad(b,p1,p2,w1*2f,w2*2f,violet,a*.20f*(.6f+.4f*pulse),false,false);street(b,p1,p2,w1,w2,stone,a*.95f,true,false);}
   else if(walked)street(b,p1,p2,w1*.9f,w2*.9f,silver,a*.7f,false,false);
   else street(b,p1,p2,w1*.7f,w2*.7f,stone,a*.32f,false,false);   // other roads faint: your path and the open roads stand out (user 2026-10-18)
  }
  // the start of the climb: a road from the bottom edge to the first places
  if(at==null||at.floor<from)for(MapNode m:reach){if(m.floor<from||m.floor>to)continue;float[] p2=climbPos(m,x,y,w,h,from,to);float wd=base*.15f*climbScale(m,from,to)*(from==to?2.2f:1);
   ActSpots asp=from==to?null:actSpots((from-1)/5+1);float[] st=asp!=null&&asp.start!=null?spotUi(asp.start):new float[]{x+w/2,y-14};
   street(b,st,p2,wd*1.2f,wd,m.id==selected?Color.valueOf("f3e6c4"):stone,a*.95f,true,m.id==selected);}
  if(from!=to&&to%5!=0)for(MapNode n:r.map)if(n.floor==to){   // roads of the last floor lead on to the boss (its own map) - out at the top
   boolean mine=r.mapPath.contains(n.id)||at!=null&&at.id==n.id;float[] p1=climbPos(n,x,y,w,h,from,to);float wd=base*.15f*climbScale(n,from,to);
   ActSpots asp=actSpots((from-1)/5+1);float[] ex=asp!=null&&asp.exit!=null?spotUi(asp.exit):new float[]{x+w/2,y+h+16};
   street(b,p1,ex,wd,wd*.7f,stone,a*(mine?.8f:.3f),false,false);}
  List<Runnable> labels=new ArrayList<>();final MapNode[] hoverN={null};final float[][] hoverQ={null};final float[] hoverS={0};var mouse=stageMouse();
  // places, far first
  for(MapNode n:order){if(n.floor<from||n.floor>to)continue;
   boolean walked=r.mapPath.contains(n.id),can=reachIds.contains(n.id),sel=n.id==selected,boss="boss".equals(n.type),elite="elite".equals(n.type);
   float[] q=climbPos(n,x,y,w,h,from,to);float s=base*climbScale(n,from,to)*("boss".equals(n.type)?1.35f:1)*placeEmphasis(walked,can,sel);
   if(mouse!=null&&mouse.x>=0&&Math.abs(mouse.x-q[0])<s*.45f&&mouse.y>q[1]-s*.15f&&mouse.y<q[1]+s*.9f){hoverN[0]=n;hoverQ[0]=q;hoverS[0]=s;}   // front-most place under the mouse
   boolean fog=n.floor>r.round&&!can;OpponentSpec d=n.deck==null?null:specById(n.deck);String room=n.floor==15?"final-gate":d==null?null:d.room;
   float alpha=sel||can||walked||boss?1f:.72f;   // later floors readable for planning (tester 2026-10-19), still behind your choices   // highlighted places + your path stay clear, everything else small and see-through (user 2026-10-18)
   boolean painted=loc0(act,room);   // Codex dioramas: highlight glows behind the building, no empty ground ring
   float gy=painted?q[1]+s*.42f:q[1],grw=painted?s*.66f:s*.62f,grh=painted?s*.5f:s*.24f;
   // choices stand out (user 2026-10-18, "Auswahl besser sichtbar"): stronger glow + a bright pulsing ring
   // no rings any more (user 2026-10-18: "Umrandung nicht mehr notwendig bei klaren Color-Schemes - nur ein kleiner Highlight-Outline"):
   // a soft glow under the selected place, the outline around the building does the rest
   if(sel)ellipse(b,q[0],q[1]+s*.08f,s*.5f,s*.16f,PICK,a*.22f*(.7f+.3f*pulse));
   if(!(boss&&bossPainted&&from==to))ellipse(b,q[0],q[1]+(painted?s*.04f:-s*.02f),s*(painted?.42f:.5f),s*(painted?.1f:.17f),Color.BLACK,a*(painted?.4f:.55f)*alpha);   // ground shadow (tight under painted rock)
   if(boss&&bossPainted&&from==to)room=null;   // castle painted into the boss map
   String locPath=room==null?null:DIR+"rooms/locations/act"+act+"/"+room+".png";   // each act its own buildings
   Texture loc=locPath==null?null:fileTexture(locPath);
   if(loc==null&&room!=null){locPath=DIR+"rooms/locations/"+room+".png";loc=fileTexture(locPath);}
   if(loc!=null){Color edge=sel?PICK:can?CHOICE:boss?Color.valueOf("ff9a8a"):Color.valueOf("e9e2d0");
    outlined(b,loc,locPath,q[0]-s*.6f,q[1]-s*.12f,s*1.2f,s*1.2f,a*alpha,edge,Math.min(.9f,Math.max(.45f,s*.008f))*(sel||can?1.3f:1f),sel||can?.9f:walked?.5f:.15f);}   /* places you cannot pick: faint outline */
   else if(!(boss&&bossPainted&&from==to)){   // fallback: drawn rock plateau + the room sprite on top (not on a painted boss map)
    ellipse(b,q[0],q[1]+s*.03f,s*.44f,s*.15f,Color.valueOf("3b3a44"),a*alpha);ellipse(b,q[0],q[1]+s*.07f,s*.40f,s*.12f,Color.valueOf("5a5868"),a*alpha);
    ellipse(b,q[0],q[1]+s*.09f,s*.36f,s*.10f,boss?Color.valueOf("4a2228"):Color.valueOf("2f3d2c"),a*alpha);
    if(d!=null&&n.floor!=15)drawSprite(b,d.room,q[0],q[1]+s*.36f,s*.62f,a*alpha);
    else if(n.floor==15)centerText(b,"FINAL GATE",q[0]-s*.5f,q[1]+s*.2f,s,s*.3f,Math.min(7,s*.16f),RED,a,true);}
   // (no fog blobs: they read as grey circles - later floors are simply dimmed; user 2026-10-17)
   // label plate below the plateau
   String label=boss?(n.floor==15?"The Summit":"BOSS"):d==null?"":roomName(d.room);
   String state=can?(elite?"Elite encounter":sel?"Selected":tx("Click to choose","Tap to choose")):"";
   if(!label.isEmpty()&&!fog&&(can||sel||boss&&n.floor>=r.round))labels.add(()->{/* labels only where you can go (and bosses); drawn after all places so no building covers them */
    float ls=Math.max(4.6f,Math.min(6.4f,s*.11f))*(can?1.1f:1),lw=Math.max(boldWidth(label,ls),state.isEmpty()?0:chipWidth(state,ls*.8f))+12,lh=state.isEmpty()?ls+4:ls*2+6,ly=q[1]-s*.2f-lh;
    if(from!=to&&n.count>1){float slot=w*.275f*(1-.12f*climbRow(n,from,to))-3;if(lw>slot){float k=slot/lw;ls*=k;lw=slot;lh=state.isEmpty()?ls+4:ls*2+6;ly=q[1]-s*.2f-lh;}}   // plates never wider than their spot: no overlap with the neighbour (2026-10-18)
    float lx=Math.max(x+24,Math.min(x+w-lw,q[0]-lw/2));   // clear of the FLOOR labels at the left edge
    Color edge=boss||elite?RED:sel?PICK:can?CHOICE:Color.valueOf("5b6378");
    rect(b,lx,ly,lw,lh,Color.valueOf(sel?"241d48":can?"121830":"0b0e16"),a*(can?.94f:.8f));
    if(can)frame(b,lx-1.2f,ly-1.2f,lw+2.4f,lh+2.4f,.8f,edge,a*(sel?.35f+.3f*pulse:.25f));   // outer glow line
    frame(b,lx,ly,lw,lh,can?1f:.5f,edge,a*(can?1f:.8f));
    centerText(b,label,lx,ly+lh-ls-2.4f,lw,ls+1,ls,boss||elite?RED:sel?PICK:TEXT,a,true);
    if(!state.isEmpty())centerText(b,state,lx,ly+1.2f,lw,ls,ls*.8f,sel?Color.valueOf("ffe6a8"):Color.valueOf("c9c0ff"),a,false);
    if(sel){float cy=q[1]+s*1.16f+(float)Math.sin(time*4)*s*.05f,cw=s*.15f,t=Math.max(1.5f,s*.045f);   // bobbing double arrow above the chosen place
     for(int k=0;k<2;k++){float yy=cy+k*cw*.75f;line(b,q[0]-cw,yy+cw,q[0],yy,t,PICK,a*(k==0?1f:.6f),false);line(b,q[0]+cw,yy+cw,q[0],yy,t,PICK,a*(k==0?1f:.6f),false);}}});
   float bx=q[0]-s*.42f,by=q[1]+s*.55f,br=Math.max(2.6f,s*.08f);
   if(n.pre!=null&&!n.preDone&&!fog){Color pc="treasure".equals(n.pre)?Color.valueOf("ffc94a"):"rest".equals(n.pre)?Color.valueOf("ff8a3c"):Color.valueOf("c79bff");
    disc(b,bx,by,br,pc,a);disc(b,bx,by,br*.78f,Color.valueOf("0e1119"),a);centerText(b,"treasure".equals(n.pre)?"$":"rest".equals(n.pre)?"^":"?",bx-br,by-br,br*2,br*2,br*1.15f,pc,a,true);}
   if(elite&&!fog){float ex=q[0]+s*.42f;disc(b,ex,by,br,RED,a);disc(b,ex,by,br*.78f,Color.valueOf("2a0e10"),a);centerText(b,"E",ex-br,by-br,br*2,br*2,br*1.1f,RED,a,true);}
  }
  for(Runnable lb:labels)lb.run();
  if(hoverN[0]!=null){MapNode hn=hoverN[0];float[] q=hoverQ[0];float s=hoverS[0];OpponentSpec hd=hn.deck==null?null:specById(hn.deck);   // tooltip: what waits there
   String t1=("boss".equals(hn.type)?"BOSS":"FLOOR")+" "+hn.floor+(hn.floor==15?"  ·  THE SUMMIT":hd!=null?"  ·  "+roomName(hd.room).toUpperCase(Locale.ROOT):"");
   List<String> more=new ArrayList<>();if("elite".equals(hn.type))more.add("Elite: harder, reward one tier better");
   if(hn.pre!=null&&!hn.preDone)more.add("treasure".equals(hn.pre)?"Treasure on the way":"rest".equals(hn.pre)?"Campfire on the way":"Unknown event on the way");
   if(r.mapPath.contains(hn.id))more.add("Completed");else if(hn.floor>r.round)more.add("Reachable later - plan your road");
   String t2=String.join("  ·  ",more);float ls=6.2f,tw=Math.max(boldWidth(t1,ls),t2.isEmpty()?0:chipWidth(t2,ls*.85f))+14,th=t2.isEmpty()?ls+6:ls*2+8;
   float tx=Math.max(x,Math.min(x+w-tw,q[0]-tw/2)),ty=Math.min(y+h-th,q[1]+s*.95f);
   rect(b,tx,ty,tw,th,Color.valueOf("0b0e16"),a*.94f);frame(b,tx,ty,tw,th,.8f,"boss".equals(hn.type)||"elite".equals(hn.type)?RED:GOLD,a);
   centerText(b,t1,tx,ty+th-ls-3,tw,ls+1,ls,"boss".equals(hn.type)||"elite".equals(hn.type)?RED:TEXT,a,true);
   if(!t2.isEmpty())centerText(b,t2,tx,ty+2,tw,ls,ls*.85f,Color.valueOf("c9c0ff"),a,false);}
  // you are here
  ActSpots meSp=from==to?null:actSpots((from-1)/5+1);float[] me=at!=null&&at.floor>=from&&at.floor<=to?climbPos(at,x,y,w,h,from,to):meSp!=null&&meSp.start!=null?spotUi(meSp.start):new float[]{x+w/2,y-10};
  float bob=(float)Math.sin(time*3)*1.2f,ms=Math.max(2.4f,base*.07f);
  disc(b,me[0],me[1]+ms*3+bob,ms*1.6f,GOLD,a*.3f);disc(b,me[0],me[1]+ms*3+bob,ms,GOLD,a);line(b,me[0],me[1]+bob,me[0],me[1]+ms*2.4f+bob,ms*.45f,GOLD,a,false);
  Gdx.graphics.requestRendering();
 }
 /** white silhouettes of painted places (alpha kept), for outlines: built once per image from its file */
 private static final Map<String,Texture> silhouettes=new HashMap<>();
 static Texture silhouette(String path){
  return silhouettes.computeIfAbsent(path,pth->{try{var f=Gdx.files.absolute(pth);if(!f.exists())return null;
   com.badlogic.gdx.graphics.Pixmap src=new com.badlogic.gdx.graphics.Pixmap(f);
   com.badlogic.gdx.graphics.Pixmap out=new com.badlogic.gdx.graphics.Pixmap(src.getWidth(),src.getHeight(),com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
   out.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
   for(int y=0;y<src.getHeight();y++)for(int x=0;x<src.getWidth();x++){int al=src.getPixel(x,y)&0xff;if(al>0)out.drawPixel(x,y,0xffffff00|al);}
   Texture t=new Texture(out,true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);src.dispose();out.dispose();return t;
  }catch(Exception e){log(e);return null;}});
 }
 /** a painted place with a thin outline in "edge": its white silhouette drawn around it in 8 directions, then the image */
 static void outlined(Batch b,Texture t,String path,float x,float y,float w,float h,float a,Color edge,float d){outlined(b,t,path,x,y,w,h,a,edge,d,.9f);}
 static void outlined(Batch b,Texture t,String path,float x,float y,float w,float h,float a,Color edge,float d,float strength){
  Texture sil=path==null?null:silhouette(path);Color o=b.getColor().cpy();
  if(sil!=null){b.setColor(edge.r,edge.g,edge.b,a*strength);
   for(int k=0;k<8;k++){double ang=Math.PI*k/4;b.draw(sil,x+(float)Math.cos(ang)*d,y+(float)Math.sin(ang)*d,w,h);}}
  b.setColor(1,1,1,a);b.draw(t,x,y,w,h);b.setColor(o);
 }
 static final Color CHOICE=Color.valueOf("c3b4ff"),PICK=Color.valueOf("ffcf5c"),FAV=Color.valueOf("ffcf5c"),NEVER=Color.valueOf("ff5c6c");
 /** ellipse outline from short segments (map choice rings) */
 static void ring(Batch b,float cx,float cy,float rx,float ry,float t,Color c,float a){
  int n=48;for(int k=0;k<n;k++){double u=Math.PI*2*k/n,v=Math.PI*2*(k+1)/n;
   line(b,cx+rx*(float)Math.cos(u),cy+ry*(float)Math.sin(u),cx+rx*(float)Math.cos(v),cy+ry*(float)Math.sin(v),t,c,a,false);}
 }
 static boolean loc0(int act,String room){return room!=null&&(fileTexture(DIR+"rooms/locations/act"+act+"/"+room+".png")!=null||fileTexture(DIR+"rooms/locations/"+room+".png")!=null);}
 // choice lines (Codex design + Claude draft): what an opponent says when you pick it on the map
 private static Map<String,String[]> choiceLines;
 /** {text, presentation} - presentation "spoken" or "nonverbal" */
 static String[] choiceLine(String enemy){
  if(choiceLines==null){choiceLines=new HashMap<>();
   try{File f=new File(DIR+"story/choice_lines.json");if(f.isFile()){
    Map<String,Object> all=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,Object>>(){}.getType());
    Object e=all.get("enemies");
    if(e instanceof Map<?,?> m){for(Map.Entry<?,?> x:m.entrySet())if(x.getValue() instanceof Map<?,?> v){
      Object txt=v.get("choice_text");if(txt==null)txt=v.get("spoken_text");if(txt==null)txt=v.get("narrator_caption");
      if(txt!=null)choiceLines.put(String.valueOf(x.getKey()),new String[]{String.valueOf(txt),String.valueOf(v.get("presentation"))});}}
    else for(Map.Entry<String,Object> x:all.entrySet())if(!x.getKey().startsWith("_")&&x.getValue() instanceof String s)choiceLines.put(x.getKey(),new String[]{s,s.startsWith("[")?"nonverbal":"spoken"});
   }}catch(Exception ex){log(ex);}}
  return enemy==null?null:choiceLines.get(enemy);
 }
 class MapHit extends Actor{
  MapHit(int id,float[] rect){this(id,rect[0]+rect[2]/2,rect[1]+rect[3]/2,Math.max(rect[2],rect[3])/2);setBounds(rect[0]-2,rect[1]-10,rect[2]+4,rect[3]+12);}
  final int id;MapHit(int id,float cx,float cy,float r){this.id=id;setBounds(cx-r,cy-r,r*2,r*2);
   addListener(new ClickListener(){@Override public void clicked(InputEvent e,float xx,float yy){if(blocked(MapHit.this))return;
    Run r=profile().run;if(r==null)return;if(mapSelected!=MapHit.this.id){mapSelected=MapHit.this.id;rebuild();}/* a click only selects; "Enter room" decides (user 2026-10-17) */}});}
 }
 private int mapSelected=-1;
 /** buildSpireMap draws the act landscape over the whole screen; drawClimbMap then skips its own framed copy */
 private boolean climbFullScreen;
 /** a short line on the boss map (user 2026-10-18: "kleiner Flavor-Text wie 'you approach a big cave' / bei Sorin 'a big castle'") */
 /** boss approach line; acts 1+2 end at one painted destination (sanctuary gate / castle gate, Codex 2026-10-18), the second half names who waits */
 static final Map<String,String> BOSS_FLAVOR=Map.ofEntries(
  Map.entry("b5-dino","The old sanctuary gate looms ahead - the ground shakes under something huge."),
  Map.entry("b5-gishath","The old sanctuary gate looms ahead - the stones are warm, the herd is near."),
  Map.entry("final-kozilek","You approach a rift where reality folds in on itself."),
  Map.entry("final-atraxa","You approach a citadel of light - a thousand wings beat as one."),
  Map.entry("final-atraxa-curse","You approach a citadel of light - the dead angels are rising."),
  Map.entry("final-kozilek-curse","You approach a rift that devours every rule of the world."),
  Map.entry("final-heliod","You approach a shrine of blinding sunlight - a god is watching."),
  Map.entry("final-iroas","You approach a blazing war shrine - the god of victory raises his spear."),
  Map.entry("b5-equip","The old sanctuary gate looms ahead - blades hang from every broken pillar."),
  Map.entry("b5-dragon-fixed","The old sanctuary gate looms ahead - it smells of storm and fire."),
  Map.entry("b10-vamp","The castle gate stands open - every window above glows red."),
  Map.entry("b10-zombie","The castle gate stands open - behind it, something is digging its way out."),
  Map.entry("b10-demon","The castle gate stands open - smoke pours out, the air tastes of brimstone."),
  Map.entry("b10-control","The castle gate stands open - inside, time itself holds its breath."),
  Map.entry("b10-dragon","The castle gate stands open - gold glitters between the bones."),
  Map.entry("final-chandra","You approach a burning temple at the top of the Spire."),
  Map.entry("final-sliver","You approach a humming hive - the walls are moving."),
  Map.entry("b15-emrakul","You approach a rift in the sky - the world bends towards it."));
 static String bossFlavor(Run r){
  for(MapNode n:r.map==null?List.<MapNode>of():r.map)if(n.floor==r.round&&n.deck!=null){
   String line=BOSS_FLAVOR.get(n.deck);if(line!=null)return line;
   OpponentSpec d=specById(n.deck);if(d!=null&&d.room!=null)return "You approach the "+roomName(d.room).toLowerCase(Locale.ROOT)+" - every road of this act ends here.";}
  return "Every road of this act ends here.";
 }
 /** the boss map has the boss's castle painted in: the place itself is not drawn again (label, glow and click stay) */
 private static boolean bossPainted;private static String bossMap;   // the painted boss map on screen (its ground point: boss-anchors.json)
 private void buildSpireMap(Run r){maybeGuide("map");
  mapScreenW=W();mapScreenH=H();
  {int act=Math.max(1,Math.min(3,(mapTo(r)-1)/5+1));Texture boss=null;bossPainted=false;
   if(r.round%5==0){bossMap=null;
    if(bossAnchor("boss-act"+act)!=null){boss=fileTexture(DIR+"rooms/map/boss-act"+act+".jpg");if(boss!=null)bossMap="boss-act"+act;}   // the act's destination, building painted in (Codex / user 2026-10-18)
    if(boss==null)for(MapNode bn:r.map)if(bn.floor==r.round&&bn.deck!=null){boss=fileTexture(DIR+"rooms/map/boss-"+bn.deck+".jpg");if(boss!=null)bossMap="boss-"+bn.deck;break;}   // the boss's own map with its castle (user 2026-10-18)
    bossPainted=boss!=null;}
   Texture land=boss!=null?boss:fileTexture(DIR+"rooms/map/act"+act+".jpg");climbFullScreen=land!=null;   // boss floor: its own map (Codex art; until then the act map)
   if(land!=null)root.addActor(new Painter((b,a)->{float w=W(),h=H();drawCover(b,land,0,0,w,h,a);   // the map fills the screen, no box on a background
    int n=40;for(int k=0;k<n;k++){rect(b,0,h-56+56f*k/n,w,56f/n,Color.BLACK,a*.6f*k/n);rect(b,0,40f*k/n,w,40f/n,Color.BLACK,a*.5f*(n-k)/n);}}));   // soft shade behind title (darkest at the top) and bottom bar
   else artBackground(null,.45f);}
  title(r.round%5==0?"BOSS  ·  FLOOR "+r.round+" / 15":"THE ROAD AHEAD  ·  FLOOR "+r.round+" / 15",r.round%5==0?bossFlavor(r)+(bestOfThree(r)?"  Best of 3.":""):"Choose your path - roads cross and meet at every boss."+(r.trueRogue?"  Campfires heal.":""));
  List<MapNode> reach=reachable(r);if(reach.isEmpty()){r.mapPos=-1;reach=reachable(r);}
  final int[] selId={mapSelected};if(reach.stream().noneMatch(n->n.id==selId[0]))mapSelected=reach.isEmpty()?-1:reach.get(0).id;
  final int from=mapFrom(r),to=mapTo(r);int rows=to-from+1;
  float W=W(),H=H(),pw0=Math.max(112,W*.235f),mx=50,my=54,mw=W-12-pw0-18-mx,mh=H-54-48;   // more room: wider and taller than the old framed box
  final float base=rows<=1?Math.min(mw*.26f,mh*.46f)/(1.18f*1.35f*1.22f):Math.min(mw*.12f,mh/(rows-1)*.72f);   // boss map: one big boss; 4 floors: more room   // place size at the near (bottom) row (bigger was too crowded, user 2026-10-18 - Codex redesigns the places)
  root.addActor(new Painter((b,a)->{drawClimbMap(b,a,r,mx,my,mw,mh,mapSelected,from,to,base);
   if(from!=to)for(int f=from;f<=to;f++){float fy=my+mh*(.17f+.75f*(f-from)/(float)Math.max(1,to-from));ActSpots fsp=actSpots((from-1)/5+1);   /* labels next to the spot rows */
    if(fsp!=null&&fsp.floors!=null&&f-from<fsp.floors.length){float sum=0;for(int[] pt:fsp.floors[f-from])sum+=spotUi(pt)[1];fy=sum/fsp.floors[f-from].length+4;}boolean boss=f%5==0;   // floor labels on the left, climbing up
    text(b,boss?"BOSS "+f:"FLOOR "+f,mx-6,fy+3,5.6f,boss?RED:f==r.round?GOLD:Color.valueOf("c9c1b0"),a,40,Align.left,boss||f==r.round);}
   float lx=16;for(String[] l:new String[][]{{"?","Unknown","c79bff"},{"$","Treasure","ffc94a"},{"^","Campfire","ff8a3c"},{"E","Elite +1 tier","ef6b6b"}}){
    if(l[0].equals("^")&&!r.trueRogue)continue;Color lc=Color.valueOf(l[2]);disc(b,lx+5,23,5,lc,a);disc(b,lx+5,23,3.9f,Color.valueOf("0e1119"),a);centerText(b,l[0],lx,18,10,10,5.5f,lc,a,true);
    text(b,l[1],lx+13,25.6f,5.8f,Color.valueOf("c9c1b0"),a,90,Align.left,false);lx+=20+chipWidth(l[1],5.8f);}
  }));
  for(MapNode n:reach){if(n.floor<from||n.floor>to)continue;root.addActor(new MapHit(n.id,placeRect(n,mx,my,mw,mh,from,to,base)));}
  // the selected room on the right
  MapNode sel=mapNode(r,mapSelected);float px=mx+mw+18,pw=W-px-12,py=46,ph=H-46-44;
  if(sel!=null){OpponentSpec d=sel.deck==null?null:specById(sel.deck);
   TextureRegion avatar=null;   // our own portraits only (no Forge avatar fallback)
   final TextureRegion av=avatar;Run pv=new Run();pv.seed=r.seed;pv.round=sel.floor;pv.cursed=r.cursed;pv.curses=new ArrayList<>(r.curses);
   if(d!=null){pv.opponent=d.enemy;pv.extraFight=true;pv.extraDeck=d.id;}
   int life=d==null?0:enemyStartingLife(pv);List<String> bn=d==null?List.of():boons(pv);
   root.addActor(new Painter((b,a)->{
    Texture t=d==null?null:roomTexture(d.room);rect(b,px+2,py-2,pw,ph,Color.BLACK,a*.35f);if(t!=null)drawCover(b,t,px,py,pw,ph,a);else rect(b,px,py,pw,ph,Color.valueOf("0e1119"),a*.9f);
    rect(b,px,py,pw,ph,DARK,a*.28f);for(int k=0;k<10;k++)rect(b,px,py,pw,ph*.55f*(10-k)/10f,Color.BLACK,a*.07f);
    frame(b,px,py,pw,ph,1.6f,"elite".equals(sel.type)||"boss".equals(sel.type)?RED:GOLD,a);
    float top=py+ph-8,cx=px+pw/2;
    String hdr="FLOOR "+sel.floor+("elite".equals(sel.type)?"  ·  ELITE":"boss".equals(sel.type)?"  ·  BOSS":"")+(d!=null?"  ·  "+d.room.toUpperCase(Locale.ROOT):"");   /* one line: a wrapped header pushed the panel over its edge (2026-10-18) */
    top-=text(b,hdr,px+6,top,Math.min(6.4f,fitSingleLine(hdr,6.4f,pw-12,true)),"elite".equals(sel.type)||"boss".equals(sel.type)?RED:GOLD,a,pw-12,Align.center,true)+5;
    if(sel.floor==15){text(b,"THE FINAL GATE",px+6,top-20,10,RED,a,pw-12,Align.center,true);text(b,"Choose your final boss behind this door.",px+6,top-40,6.6f,TEXT,a,pw-12,Align.center,false);return;}
    if(d==null)return;
    float size=Math.min(pw*.46f,ph*(sel.pre!=null&&!sel.preDone?(bn.isEmpty()?.15f:.11f):bn.isEmpty()&&!"elite".equals(sel.type)?.25f:bn.size()>1||"elite".equals(sel.type)&&!bn.isEmpty()?.14f:.19f));   /* the "before this room" line must fit above the button */Texture own=enemyPortrait(d.enemy);   // smaller when start cards / elite / pre-room text must fit too
    if(own!=null||av!=null){float ax=cx-size/2,ay=top-size;rect(b,ax-2,ay-2,size+4,size+4,Color.BLACK,a*.55f);Color o=b.getColor().cpy();b.setColor(1,1,1,a);if(own!=null)b.draw(own,ax,ay,size,size);else b.draw(av,ax,ay,size,size);b.setColor(o);frame(b,ax-1,ay-1,size+2,size+2,1,GOLD,a*.9f);top=ay-6;}
    top-=text(b,d.enemy,px+6,top,Math.min(11,fitSingleLine(d.enemy,11,pw-12,true)),GOLD,a,pw-12,Align.center,true)+5;
    String[] said=choiceLine(d.enemy);   // why this opponent stands in your way (story, user 2026-10-17)
    if(said!=null){boolean speech=!"nonverbal".equals(said[1]);
     top-=text(b,speech?"“"+said[0]+"”":said[0],px+10,top,5.7f,speech?Color.valueOf("efe6d2"):Color.valueOf("b9c3d6"),a,pw-20,Align.center,false)+6;}
    if(d.commander!=null)top-=text(b,"Commander: "+d.commander,px+6,top,6.6f,GOLD,a,pw-12,Align.center,true)+4;
    String[] tags=d.tags==null?new String[0]:d.tags;float cs=6.4f,chH=12,gap=3;
    List<List<String>> chipRows=new ArrayList<>();List<String> chipRow=new ArrayList<>();float used=0;   // wrap, then centre each row on what is really drawn
    for(String tg:tags){float cw=chipWidth(tg,cs);if(!chipRow.isEmpty()&&used+gap+cw>pw-12){chipRows.add(chipRow);chipRow=new ArrayList<>();used=0;}used+=(chipRow.isEmpty()?0:gap)+cw;chipRow.add(tg);}
    if(!chipRow.isEmpty())chipRows.add(chipRow);
    for(List<String> line:chipRows){float total=-gap;for(String tg:line)total+=chipWidth(tg,cs)+gap;float xx=cx-total/2;
     for(String tg:line){float cwid=chipWidth(tg,cs);Color tc=tagColor(tg);rect(b,xx,top-chH,cwid,chH,Color.valueOf("0e1017"),a*.82f);frame(b,xx,top-chH,cwid,chH,.6f,tc,a*.9f);centerText(b,tg,xx,top-chH,cwid,chH,cs,tc,a,false);xx+=cwid+gap;}
     top-=chH+gap;}
    if(!chipRows.isEmpty())top-=3;
    top-=lifeLine(b,life,"   ·   Hand "+opponentStartCards(pv),px+6,top,pw-12,7f,TEXT,a,true)+5;
    if(!bn.isEmpty())top-=boxedText(b,"Starts with: "+String.join(", ",bn),px+6,top,pw-12,6.4f,Color.valueOf("ffb43c"),Color.valueOf("ffc766"),a,true)+5;
    // (elite hint removed: "ELITE" is in the panel header and the legend says "Elite +1 tier" - it overflowed the button)
    if(sel.pre!=null&&!sel.preDone)text(b,"Before this room: "+("treasure".equals(sel.pre)?"a treasure chest (free Epic card + "+TREASURE_FAVOR+" Divine Favor)":"rest".equals(sel.pre)?"a campfire (heal or pray)":"something unknown (?)"),px+6,top,6.2f,Color.valueOf("d9c4ff"),a,pw-12,Align.center,false);
   }));
   Btn enter=button(sel.floor==15?"Final gate":"Take this path",px-4,12,pw+8,24,()->enterNode(r,sel));enter.gold=true;
  }
  button("Edit deck",W-86-80,H-22,74,16,()->showRunDeck(r));   // top right next to Settings, like on the run screen (user 2026-10-18)
  button("Settings",W-86,H-22,74,16,this::openDisplay);
 }
 private void buildTreasure(Run r){
  root.addActor(new Painter((b,a)->{float w=W(),h=H();Texture t=roomTexture("dragon-lair");if(t!=null)drawCover(b,t,0,0,w,h,a);else rect(b,0,0,w,h,DARK,a);rect(b,0,0,w,h,DARK,a*.4f);}));
  title("TREASURE","A forgotten chest - a free Epic card for your bench and "+TREASURE_FAVOR+" Divine Favor.");
  float cw=118,ch=cw/.716f;
  if(r.treasureCard!=null){CardView v=new CardView(r.treasureCard,W()/2-cw/2,H()/2-ch/2+4,cw,ch);v.selected=true;root.addActor(v);}
  root.addActor(new Painter((b,a)->{
   if(r.treasureCard==null)text(b,"The chest holds no card you are missing.",0,H()/2+10,8,TEXT,a,W(),Align.center,false);
   boxedText(b,"+"+TREASURE_FAVOR+" Divine Favor",W()/2-60,52,120,7f,GOLD,GOLD,a,true);}));
  Btn take=button("Take it",W()/2-70,12,140,24,()->{if(r.treasureCard!=null&&!r.deck.contains(r.treasureCard)&&!bench(r).contains(r.treasureCard))r.bench.add(r.treasureCard);
   profile().goldBalance+=TREASURE_FAVOR;r.treasureCard=null;r.phase="fight";resumeOpponent(r);save();rebuild();});take.gold=true;
 }
 private void buildRest(Run r){
  root.addActor(new Painter((b,a)->{float w=W(),h=H();Texture t=roomTexture("den");if(t!=null)drawCover(b,t,0,0,w,h,a);else rect(b,0,0,w,h,DARK,a);rect(b,0,0,w,h,DARK,a*.42f);}));
  title("CAMPFIRE","Rest a moment before the next room.");
  final int from=r.life>0?r.life:LIFE,to=Math.min(LIFE,from+Math.max(1,Math.round(LIFE*.4f)));
  root.addActor(new Painter((b,a)->lifeLine(b,from,"  of "+LIFE+" life",0,H()*.62f,W(),9,TEXT,a,true)));
  Btn rest=button("Rest: heal to "+to+" life",W()/2-120,H()/2-4,240,26,()->{r.life=to;r.phase="fight";resumeOpponent(r);save();rebuild();});rest.gold=true;rest.disabled=from>=LIFE;
  button("Pray: +4 Divine Favor",W()/2-120,H()/2-38,240,26,()->{profile().goldBalance+=4;r.phase="fight";resumeOpponent(r);save();rebuild();});
 }
 private void buildRun(Run r){
  ensureMap(r);if(mapActive(r)&&currentNode(r)==null&&r.round<=15){buildSpireMap(r);return;}
  if(routedRound(r.round)&&!routeReady(r)){buildDungeon(r);return;}
  Cmd c=cmd(r.commander);OpponentSpec spec=opponentSpec(r);
  root.addActor(new Painter((b,a)->{   // the opponent's room fills the screen (user 2026-10-16)
   float w=W(),h=H();Texture t=roomTexture(spec.room);
   if(t!=null)drawCover(b,t,0,0,w,h,a);else forge.adventure.util.DungeonMapArt.background(b,0,0,w,h,time,a);
   rect(b,0,0,w,h,DARK,a*.34f);
   for(int k=0;k<10;k++){rect(b,0,0,w,h*.34f*(10-k)/10f,Color.BLACK,a*.06f);rect(b,0,h-h*.30f*(10-k)/10f,w,h*.30f*(10-k)/10f,Color.BLACK,a*.05f);}
   for(int k=0;k<8;k++)rect(b,0,0,w*.3f*(8-k)/8f,h,Color.BLACK,a*.04f);
  }));
  title(r.commander.split(",")[0]+"  ·  Round "+r.round+" / "+runLimit(r),"Wins "+r.wins+"   ·   Glory "+r.ruhm+lifeTag(r)+(r.cursed?"   ·   CURSE MODE":"")+(r.trueRogue?"   ·   TRUE ROGUE-LIKE MODE":""));
  float W=W(),H=H();float cw=84,ch=cw/.716f;
  root.addActor(new Painter((b,a)->{   // the dungeon path: only the current act up to its boss, rooms as icons (user 2026-10-17)
   float mx=W*.24f,mw=W*.58f,my=H-88,mh=46;   // no backing box: it overlapped the info tablet below and doubled the shadow (user 2026-10-17)
   int from=Math.max(1,((Math.max(1,r.round)-1)/5)*5+1),to=Math.min(15,from+4);
   if(mapActive(r)&&drawRunPath(b,a,r,W*.16f,my-22,W*.74f,mh+34,from,to)){   // bigger than the old node strip: there is room below it
    text(b,"ACT "+((from-1)/5+1),W*.16f-W*.075f,my+mh/2+4,7f,GOLD,a,W*.07f,Align.center,true);
   }else if(mapActive(r)){
    drawSpireMap(b,a,r,mx,my,mw,mh,7.6f,-1,true,from,to);
    for(int floor=from;floor<=to;floor++){float lx=-1;for(MapNode n:r.map)if(n.floor==floor){lx=nodePos(n,mx,my,mw,mh,from,to)[0];break;}if(lx<0)continue;
     Color cc=floor<r.round?GREEN:floor==r.round?GOLD:floor%5==0?RED:DIM;
     text(b,floor%5==0?"BOSS "+floor:"FLOOR "+floor,lx-24,my-1,5.4f,cc,a,48,Align.center,floor%5==0||floor==r.round);}
    text(b,"ACT "+((from-1)/5+1),mx-W*.075f,my+mh/2+4,7f,GOLD,a,W*.06f,Align.center,true);
   }else{
    forge.adventure.util.DungeonMapArt.map(b,mx,my,mw,mh,r.round,r.branchSide,time,a);
    for(int floor=1;floor<=15;floor++){float[] q=forge.adventure.util.DungeonMapArt.nodePosition(floor,0);Color cc=floor<r.round?GREEN:floor==r.round?GOLD:floor%5==0?RED:DIM;
     text(b,floor%5==0?"BOSS "+floor:String.valueOf(floor),mx+mw*q[0]-16,my-1,5.4f,cc,a,32,Align.center,floor%5==0||floor==r.round);}
   }
  }));
  CardView big=new CardView(r.commander,12,H-114-ch,cw,ch);root.addActor(big);
  String foeName=spec!=null&&spec.enemy!=null?spec.enemy:r.opponent;boolean boss=r.round%5==0;
  TextureRegion avatar=null;   // only our own portraits - Forge enemy avatars can be card illustrations
  final TextureRegion av=avatar;
  float px=cw+30,pw=W-px-12,top=H-114;   // clear of the dungeon strip labels (user 2026-10-17)
  root.addActor(new Painter((b,a)->{
   float size=Math.min(112,Math.min(pw*.32f,top-118));   // keep the package line below free
   text(b,boss?"BOSS ROUND":r.extraFight?"AMBUSH  ·  EXTRA FIGHT, NORMAL REWARD":r.eliteFight?"ELITE ROOM  ·  +1 REWARD TIER":"NEXT OPPONENT",px,top+2,7,boss||r.eliteFight||r.extraFight?RED:Color.valueOf("c9c1b0"),a,pw,Align.left,true);
   Texture own=enemyPortrait(foeName);
   if(own!=null||av!=null){float ay=top-8-size;rect(b,px-2,ay-2,size+4,size+4,Color.BLACK,a*.5f);Color o=b.getColor().cpy();b.setColor(1,1,1,a);if(own!=null)b.draw(own,px,ay,size,size);else b.draw(av,px,ay,size,size);b.setColor(o);frame(b,px-1,ay-1,size+2,size+2,1,boss?RED:GOLD,a*.8f);}
   float tx=px+size+14,tw=pw-size-14,y=top-6;
   y-=text(b,foeName,tx,y,13,GOLD,a,tw,Align.left,true)+6;
   if(spec.commander!=null)y-=text(b,"Commander: "+spec.commander,tx,y,7.4f,GOLD,a,tw,Align.left,true)+5;
   String[] tags=spec.tags==null?new String[0]:spec.tags;float xx=tx,cs=6.8f,chH=12.5f;
   for(String tag:tags){float cwid=chipWidth(tag,cs);if(xx+cwid>tx+tw){xx=tx;y-=chH+3;}Color tc=tagColor(tag);
    rect(b,xx,y-chH,cwid,chH,Color.valueOf("0e1017"),a*.78f);frame(b,xx,y-chH,cwid,chH,.6f,tc,a*.9f);centerText(b,tag,xx,y-chH,cwid,chH,cs,tc,a,false);xx+=cwid+4;}
   if(tags.length>0)y-=chH+7;
   y-=lifeLine(b,enemyStartingLife(r),"   ·   Hand "+opponentStartCards(r),tx,y,tw,7.4f,TEXT,a,false)+5;
   List<String> bn=boons(r);
   if(!bn.isEmpty())y-=boxedText(b,"Starts with: "+String.join(", ",bn),tx,y,tw,6.8f,Color.valueOf("ffb43c"),Color.valueOf("ffc766"),a,false)+7;else
   y-=text(b,!bn.isEmpty()?"Starts with: "+String.join(", ",bn):boss?"Every fifth round has a boss with extra life.":r.round>=7?"From round 10, opponents start with bonus cards.":"Opponents get stronger each round.",tx,y,6.8f,bn.isEmpty()?Color.valueOf("c9c1b0"):Color.valueOf("ffb43c"),a,tw,Align.left,false)+7;
   String story=intro(foeName);
   if(story!=null)text(b,fitStory(story,tw,y-80,5.1f),tx,y,5.1f,TEXT,a*.95f,tw,Align.left,false);   // stop above the package row (user 2026-10-17)
   // every package of the run - the chosen ones and each reward picked - as pictures; auto-scaled so 14+ fit (user 2026-10-17)
   List<String[]> pk=new ArrayList<>();   // {name, image key}
   if(r.chosenPackages!=null)for(String k:r.chosenPackages){Theme th=c==null?null:findTheme(c,k);if(th!=null)pk.add(new String[]{th.name,k});}
   if(r.picked!=null)for(String pickedName:r.picked){String nm=pickedName.replaceAll("\\s*\\([^)]*\\)$","");java.util.regex.Matcher rm=java.util.regex.Pattern.compile("\\(([^)]*)\\)$").matcher(pickedName);
    pk.add(new String[]{nm,packageKeyByName(c,nm),rm.find()?displayText(rm.group(1)):null});}
   int n=pk.size();float iy=40,gap=4,isz=Math.max(9,Math.min(18,(pw-(n-1)*gap)/Math.max(1,n)-(n<=5?pw*.12f:0)));boolean showNames=n<=5;
   float slot=showNames?Math.min(pw/Math.max(1,n),pw*.34f):isz+gap;
   text(b,"Your packages this run"+(n>0?"  ·  "+n:"")+":",px,iy+isz+9,5.6f,Color.valueOf("c9c1b0"),a,pw,Align.left,false);
   com.badlogic.gdx.math.Vector2 mouse=stageMouse();
   int hover=-1;
   for(int k=0;k<n;k++){String[] e=pk.get(k);PackageArt art=c==null||e[1]==null?null:packageArtMetadata().get(c.slug+"/"+e[1]);Texture pic=packageIllustration(art);float sx=px+k*slot;
    if(mouse!=null&&mouse.x>=sx&&mouse.x<=sx+(showNames?slot-4:isz)&&mouse.y>=iy&&mouse.y<=iy+isz)hover=k;
    rect(b,sx-.6f,iy-.6f,isz+1.2f,isz+1.2f,Color.BLACK,a*.8f);
    if(pic!=null)drawCover(b,pic,sx,iy,isz,isz,a);else{rect(b,sx,iy,isz,isz,Color.valueOf("202940"),a);centerText(b,"*",sx,iy,isz,isz,isz*.45f,GOLD,a*.65f,true);}
    if(showNames){String nm=art!=null&&art.shortName!=null&&!art.shortName.isBlank()?art.shortName:e[0];float nw=slot-isz-8;
     text(b,nm,sx+isz+4,iy+isz*.68f,fitSingleLine(nm,5.6f,nw,false),TEXT,a,nw,Align.left,false);}}
   if(hover>=0){   // which package is this? (user 2026-10-18: "on hover anzeigen welches Package das ist")
    String[] e=pk.get(hover);String tip=e[0]+(e.length>2&&e[2]!=null?"  ·  "+e[2]:k0(r,hover)?"  ·  chosen at the start":"");
    float sx=px+hover*slot,tipW=chipWidth(tip,6.2f)+10,tipH=13,tipX=Math.max(4,Math.min(W()-tipW-4,sx+isz/2-tipW/2)),tipY=iy+isz+12;
    frame(b,sx-1,iy-1,isz+2,isz+2,.8f,GOLD,a);
    rect(b,tipX,tipY,tipW,tipH,Color.valueOf("0b0f17"),a*.95f);frame(b,tipX,tipY,tipW,tipH,.6f,GOLD,a);
    centerText(b,tip,tipX,tipY,tipW,tipH,6.2f,TEXT,a,false);}
   if(n==0)text(b,"–",px,iy+12,6.8f,TEXT,a,pw,Align.left,false);
   float isz2=isz;
   if(!r.curses.isEmpty()){List<String> names=new ArrayList<>();for(String id:r.curses){Curse cu=curse(id);if(cu!=null)names.add(displayText(cu.name()));}
    text(b,"Run curses: "+String.join("  ·  ",names),px,iy+isz2+9,5.6f,RED,a,pw,Align.right,false);}
   Curse path=routeCurse(r,r.routeChoice);if(path!=null){text(b,"Path curse · this encounter: "+displayText(path.name())+"  -  "+pathCurseEffect(path),px,iy+isz2+18,5.6f,RED,a,pw,Align.left,true);}
  }));
  if(!mapActive(r)&&routedRound(r.round)&&segmentStart(r.round))button("Dungeon route",12,38,86,18,()->{r.routeConfirmed=false;save();rebuild();});
  if(r.nextFightPenalty!=null)root.addActor(new Painter((b,a)->text(b,"CONTRACT: "+("half".equals(r.nextFightPenalty)?"you start this fight with half life":"your opening hand has only 4 cards")+"  ·  win for +1 Epic package",cw+30,46,6,RED,a,W-cw-42,Align.left,true)));
  Btn fight=button("FIGHT",W-130,10,118,24,this::startRound);fight.gold=true;maybeGuide("run");
  button("Settings",W-86,H-22,74,16,this::openDisplay);   // top right on the run screen (user 2026-10-16)
  button("Edit deck",W-86-80,H-22,74,16,()->showRunDeck(r));   // top right next to Settings, small (user 2026-10-17)
  button("Abandon run",12,10,90,18,()->confirm("Abandon run?","The run ends with "+r.wins+(r.wins==1?" win":" wins")+" and "+r.ruhm+" Glory. Unlocks and points are awarded as for a loss.","Abandon","Keep playing",this::endRun));
 }
 // ------------------------------------------------------------------ dungeon routes and permanent boss achievements
 static final String DUNGEON_CHANDRA="dungeon:chandra-normal",DUNGEON_SLIVER="dungeon:sliver-queen",DUNGEON_CURSES="dungeon:curse-mode-v2",DUNGEON_EMRAKUL="dungeon:emrakul-cursed";
 static int dungeonBossCount(Profile p){return (p.achievements.contains(DUNGEON_CHANDRA)?1:0)+(p.achievements.contains(DUNGEON_SLIVER)?1:0);}
 static void syncDungeonUnlock(Profile p){
  p.cursesUnlocked=dungeonBossCount(p)>=1;if(p.cursesUnlocked)award(p,DUNGEON_CURSES);else p.cursesOn=false;   // finish the game once (user 2026-10-16)
 }
 static List<RogueGoal> dungeonGoals(Profile p){return List.of(
  new RogueGoal(p,DUNGEON_CHANDRA,"Through the Inferno","Defeat Chandra, the normal dungeon final boss on floor 15.","Unlocks Curse mode",p.achievements.contains(DUNGEON_CHANDRA)?1:0,1),
  new RogueGoal(p,DUNGEON_SLIVER,"Break the Hive","Defeat Sliver Queen, the other normal dungeon final boss on floor 15.","Unlocks Curse mode",p.achievements.contains(DUNGEON_SLIVER)?1:0,1),
  new RogueGoal(p,DUNGEON_CURSES,"Into the Abyss","Finish the dungeon once: defeat a final boss on floor 15 (Chandra or Sliver Queen).","Unlock Curse mode and its third final boss: Emrakul",Math.min(1,dungeonBossCount(p)),1),
  new RogueGoal(p,DUNGEON_EMRAKUL,"The Aeons End","Defeat Emrakul on floor 15 of a cursed dungeon run.","Defeat the third final boss",p.achievements.contains(DUNGEON_EMRAKUL)?1:0,1));}
 static void recordDungeonWin(Profile p,Run r,int wonRound){
  if(wonRound!=15)return;
  if(!r.cursed&&"Chandra".equals(r.opponent))award(p,DUNGEON_CHANDRA);
  if(!r.cursed&&"Sliver Queen".equals(r.opponent))award(p,DUNGEON_SLIVER);
  if(r.cursed&&"Emrakul".equals(r.opponent))award(p,DUNGEON_EMRAKUL);
  syncDungeonUnlock(p);
 }
 static String dungeonProgressNotice(Profile p){
  if(p.achievements.contains(DUNGEON_EMRAKUL))return "Emrakul defeated! Achievement unlocked: The Aeons End.";
  if(p.cursesUnlocked)return "Both final bosses defeated! Curse mode and its ultimate boss, Emrakul, are now unlocked.";
  String remaining=p.achievements.contains(DUNGEON_CHANDRA)?"Sliver Queen":p.achievements.contains(DUNGEON_SLIVER)?"Chandra":"Chandra and Sliver Queen";
  return "Defeat "+remaining+" to unlock Curse mode and Emrakul. Progress: "+dungeonBossCount(p)+"/2 different normal final bosses.";
 }
 static String dungeonVictoryNotice(Profile p,Run r){
  if(r.cursed)return "Emrakul defeated! Achievement unlocked: The Aeons End.";
  if(p.cursesUnlocked)return "Both final bosses defeated! Curse mode and the third final boss, Emrakul, are now unlocked.";
  String remaining=p.achievements.contains(DUNGEON_CHANDRA)?"Sliver Queen":"Chandra";
  return "Final boss defeated! Now defeat "+remaining+" in another run to unlock Curse mode and the third final boss. Progress: "+dungeonBossCount(p)+"/2 different bosses.";
 }
 static OpponentSpec specById(String id){for(OpponentSpec d:opponents().decks)if(d.id.equals(id))return d;return null;}
 static void ensureRoute(Run r){
  if(!routedRound(r.round))return;
  if(mapActive(r)&&r.round<15)return;   // the Spire map decides the room
  List<OpponentSpec> pool=opponentPool(r);Set<String> allowed=new HashSet<>();for(OpponentSpec d:pool)allowed.add(d.id);
  int count=Math.min(2,pool.size());
  boolean valid=r.routeRound==r.round&&r.routeOptions!=null&&r.routeOptions.size()==count&&new HashSet<>(r.routeOptions).size()==count&&allowed.containsAll(r.routeOptions);
  if(!valid){
   Collections.shuffle(pool,new Random(r.seed*113L+r.round*7919L));
   r.routeRound=r.round;r.routeOptions=new ArrayList<>();for(int i=0;i<count;i++)r.routeOptions.add(pool.get(i).id);
   r.routeChoice=null;r.routeConfirmed=false;r.routeCurses=null;r.routeCurseRound=0;
  }
  if(r.routeChoice!=null&&!r.routeOptions.contains(r.routeChoice)){r.routeChoice=null;r.routeConfirmed=false;}
  ensureRouteCurses(r);
 }
 static final List<Curse> PATH_ONLY_CURSES=List.of(
  new Curse("route_wounds","Offene Wunden","Du startest diese Begegnung mit 4 Leben weniger.",false,-4,0,0,null),
  new Curse("route_resolve","Eiserner Wille","Dieser Gegner startet mit 6 Leben mehr.",false,0,0,6,null));
 static List<String> routeCurseCandidates(Run r,String deckId){
  List<String> candidates=new ArrayList<>(List.of("thin_hand","tough_foes","consuls","tainted"));
  // Symmetric effects must not destroy the opponent's own central engine.
  if(deckId.contains("vamp")||deckId.contains("elves")||deckId.contains("fish")||deckId.contains("goblin")||deckId.contains("sliver"))candidates.add("thorn");
  if(deckId.contains("elves")||deckId.contains("fish")||deckId.contains("goblin")||deckId.contains("sliver")||deckId.contains("show")||deckId.contains("emrakul"))candidates.add("rest");
  if(deckId.contains("show")||deckId.contains("emrakul")||deckId.contains("tron"))candidates.add("betrayal");
  if(deckId.contains("show")||deckId.contains("emrakul")||deckId.contains("control")||deckId.contains("fish")||deckId.contains("burn"))candidates.add("vortex");
  if(r.round>=13)candidates.add("empty_hand");
  boolean handPenalty=false;for(String id:r.curses){Curse c=curse(id);if(c!=null&&c.playerCards()<0)handPenalty=true;}
  List<String> out=new ArrayList<>();
  for(String id:candidates){Curse c=curse(id);if(c==null||r.curses.contains(id)||c.major()&&r.round<13||handPenalty&&c.playerCards()<0)continue;out.add(id);}
  // Safe numeric fallback also repairs heavily modified old runs with every base curse already present.
  out.add("route_wounds");out.add("route_resolve");return out;
 }
 static void ensureRouteCurses(Run r){
  if(!r.cursed||r.round<8){r.routeCurses=null;r.routeCurseRound=0;return;}
  boolean valid=r.routeCurseRound==r.round&&r.routeCurses!=null&&r.routeCurses.keySet().equals(new HashSet<>(r.routeOptions))&&new HashSet<>(r.routeCurses.values()).size()==r.routeOptions.size();
  if(valid)for(String id:r.routeOptions)if(!routeCurseCandidates(r,id).contains(r.routeCurses.get(id))){valid=false;break;}
  if(valid)return;
  Map<String,String> offered=new LinkedHashMap<>();Set<String> used=new HashSet<>();
  for(String id:r.routeOptions){
   List<String> candidates=routeCurseCandidates(r,id);candidates.removeAll(used);
   // Use fallbacks only when no contextual base curse remains.
   List<String> contextual=new ArrayList<>();for(String n:candidates)if(!n.startsWith("route_"))contextual.add(n);
   if(!contextual.isEmpty())candidates=contextual;
   String chosen=candidates.get(new Random(r.seed*197L+r.round*7919L+id.hashCode()).nextInt(candidates.size()));
   offered.put(id,chosen);used.add(chosen);
  }
  r.routeCurses=offered;r.routeCurseRound=r.round;
  r.routeConfirmed=false; // Existing saved paths must show their newly introduced curse before a fight.
 }
 static Curse routeCurse(Run r,String id){return r.cursed&&r.routeCurseRound==r.round&&r.routeCurses!=null?curse(r.routeCurses.get(id)):null;}
 static List<String> encounterCurseIds(Run r){
  LinkedHashSet<String> ids=new LinkedHashSet<>(r.curses);
  if(r.cursed&&r.routeConfirmed&&r.routeRound==r.round&&r.routeChoice!=null&&r.routeOptions!=null&&r.routeOptions.contains(r.routeChoice)){
   Curse path=routeCurse(r,r.routeChoice);if(path!=null)ids.add(path.id());
  }
  return new ArrayList<>(ids);
 }
 static int enemyStartingLife(Run r){int life=enemyLife(r.round);for(String id:encounterCurseIds(r)){Curse c=curse(id);if(c!=null)life+=c.enemyLife();}return life;}
 static String pathCurseEffect(Curse c){return switch(c.id()){
  case "thin_hand"->"You start with one fewer card.";
  case "empty_hand"->"You start with two fewer cards.";
  case "tough_foes"->"This enemy starts with 8 extra life.";
  case "consuls"->"Authority of the Consuls: your creatures enter tapped; enemy gains life.";
  case "tainted"->"Tainted Remedy: your lifegain becomes life loss.";
  case "thorn"->"Thorn of Amethyst: noncreature spells cost 1 more for both players.";
  case "rest"->"Rest in Peace: graveyards are exiled.";
  case "betrayal"->"Night of Souls' Betrayal: all creatures get -1/-1.";
  case "vortex"->"Sulfuric Vortex: 2 damage each upkeep; no lifegain.";
  case "route_wounds"->"You start this encounter with 4 less life.";
  case "route_resolve"->"This enemy starts with 6 extra life.";
  default->c.desc();};}
 // ---- branching dungeon (user 2026-10-16): pick LEFT or RIGHT at floors 1, 6 and 11; the path holds until the boss
 // (floors 5 and 10, where both paths meet); floor 15 is the final-boss choice.
 static boolean routedRound(int round){return round>=1&&round!=5&&round!=10;}
 static int segmentOf(int round){return round<=5?0:round<=10?1:2;}
 static boolean segmentStart(int round){return round==1||round==6||round==11||round==15;}
 static String sideName(int index){return index==0?"LEFT PATH":"RIGHT PATH";}
 static boolean chooseRoute(Run r,String id){
  ensureRoute(r);if(!routedRound(r.round)||id==null||!r.routeOptions.contains(id))return false;
  r.routeChoice=id;r.routeConfirmed=false;r.opponent=specById(id).enemy;
  if(r.round!=15){r.branchSide=r.routeOptions.indexOf(id);r.branchSegment=segmentOf(r.round);}return true;
 }
 static boolean routeReady(Run r){
  if(!routedRound(r.round))return true;
  if(mapActive(r)&&r.round<15)return currentNode(r)!=null;
  ensureRoute(r);
  // inside a segment the chosen side is followed automatically (the illusion of a path)
  if(!segmentStart(r.round)&&!r.routeConfirmed&&r.branchSegment==segmentOf(r.round)&&r.branchSide>=0&&!r.routeOptions.isEmpty()){
   r.routeChoice=r.routeOptions.get(Math.min(r.branchSide,r.routeOptions.size()-1));confirmRoute(r);}
  return r.routeConfirmed&&r.routeChoice!=null&&r.routeOptions.contains(r.routeChoice)&&specById(r.routeChoice).enemy.equals(r.opponent);
 }
 static boolean confirmRoute(Run r){
  ensureRoute(r);if(r.routeChoice==null||!r.routeOptions.contains(r.routeChoice))return false;
  r.opponent=specById(r.routeChoice).enemy;r.routeConfirmed=true;return true;
 }
 static float[] dungeonCardBounds(float w,float h,int index,int count){
  boolean vertical=h>w*1.2f;float gap=12,available=h-132;
  float cw=vertical?w-24:(w-24-gap*(count-1))/count,ch=vertical?(available-gap*(count-1))/count:available;
  return new float[]{vertical?12:12+index*(cw+gap),vertical?50+(count-1-index)*(ch+gap):50,cw,ch};
 }
 record DungeonLine(String label,float size,Color color,boolean heading){}
 static float dungeonContentHeight(List<DungeonLine> lines,float width,float scale,float avatar){
  float total=avatar+(avatar>0?4*scale:0);
  for(DungeonLine line:lines){BitmapFont f=font(line.heading());f.getData().setScale(line.size()*scale/FONT_PX);total+=new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,line.label(),Color.WHITE,width,Align.center,true).height+3*scale;}
  return total;
 }
 static float[] fitDungeonContent(List<DungeonLine> lines,float width,float height){
  float scale=1,avatar=Math.min(52,Math.min(height*.22f,width*.22f));
  for(int i=0;i<48;i++){float measured=dungeonContentHeight(lines,width-20,scale,avatar);if(measured<=height-46)return new float[]{scale,avatar,measured};if(avatar>18)avatar=Math.max(0,avatar-5);else scale*=.9f;}
  return new float[]{scale,avatar,dungeonContentHeight(lines,width-20,scale,avatar)};
 }
 static float drawDungeonContent(Batch b,float a,float x,float y,float w,float h,List<DungeonLine> lines,TextureRegion avatar){
  float[] fit=fitDungeonContent(lines,w,h);float scale=fit[0],size=fit[1],cy=y+h-8;
  for(int i=0;i<lines.size();i++){
   DungeonLine line=lines.get(i);cy-=text(b,line.label(),x+10,cy,line.size()*scale,line.color(),a,w-20,Align.center,line.heading())+3*scale;
   if(i==1&&size>0){if(avatar!=null){Color old=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(avatar,x+(w-size)/2,cy-size,size,size);b.setColor(old);}cy-=size+4*scale;}
  }
  return cy; // Always above the button band [y+8,y+26].
 }
 static float[] versusArtBounds(float w,float h,boolean enemy){float pw=(w-92)/2,ph=Math.min(h*.46f,pw/.8f),y=Math.max(h*.38f,h*.60f-ph*.5f);return new float[]{enemy?w-18-pw:18,y,pw,ph};}
 static int[] artSourceBounds(int w,int h,boolean card,float targetW,float targetH){
  int sx=card?Math.round(w*.09f):0,sy=card?Math.round(h*.14f):0,sw=card?Math.round(w*.82f):w,sh=card?Math.round(h*.32f):h;
  float scale=Math.max(targetW/sw,targetH/sh);int cw=Math.max(1,Math.min(sw,Math.round(targetW/scale))),ch=Math.max(1,Math.min(sh,Math.round(targetH/scale)));
  return new int[]{sx+(sw-cw)/2,sy+(sh-ch)/2,cw,ch};
 }
 /** drops trailing sentences until the wrapped text fits into maxHeight */
 static String fitStory(String story,float width,float maxHeight,float size){
  BitmapFont f=font(false);f.getData().setScale(size/FONT_PX);String out=story;
  while(new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,out,Color.WHITE,width,Align.left,true).height>maxHeight){
   int cut=out.lastIndexOf(". ",out.length()-2);if(cut<0)return "";out=out.substring(0,cut+1);}   // not even one sentence fits: no story
  return maxHeight<8?"":out;
 }
 static float fitSingleLine(String label,float size,float width,boolean heading){BitmapFont f=font(heading);f.getData().setScale(size/FONT_PX);float measured=new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,label).width;return measured>width?size*width/measured:size;}
 private void buildDungeon(Run r){
  ensureRoute(r);save();artBackground(null,.35f);
  title("DUNGEON · FLOOR "+r.round+" / 15",r.round==15?(r.cursed?"The cursed final gate: Emrakul awaits.":"Choose a final boss. Defeat both different bosses across runs to unlock Curses."):"Choose the left or the right path. It leads you to the next boss - both paths meet there."+(r.cursed?" Path curses last for one encounter.":""));
  float W=W(),H=H();int n=r.routeOptions.size();
  int side=r.routeChoice==null?-1:r.routeOptions.indexOf(r.routeChoice);
  root.addActor(new Painter((b,a)->{   // branching map (Codex art 2026-10-16): lanes meet at the bosses 5/10/15
   float mx=24,mw=W-48,my=H-76,mh=34;rect(b,mx-6,my-7,mw+12,mh+9,Color.BLACK,a*.45f);
   forge.adventure.util.DungeonMapArt.map(b,mx,my,mw,mh,r.round,side,time,a);
   for(int k=0;k<r.routeOptions.size()&&k<2;k++){OpponentSpec spec=specById(r.routeOptions.get(k));if(spec==null)continue;
    float[] q=forge.adventure.util.DungeonMapArt.nodePosition(r.round,r.round%5==0?0:k);drawSprite(b,spec.room,mx+mw*q[0],my+mh*q[1]+1,Math.max(11,mh*.42f),a);}
   for(int floor=1;floor<=15;floor++){float[] q=forge.adventure.util.DungeonMapArt.nodePosition(floor,0);Color c=floor<r.round?GREEN:floor==r.round?GOLD:floor%5==0?RED:DIM;
    text(b,floor%5==0?"BOSS "+floor:String.valueOf(floor),mx+mw*q[0]-16,my-1,5.4f,c,a,32,Align.center,floor%5==0||floor==r.round);}
  }));
  for(int i=0;i<n;i++){
   String id=r.routeOptions.get(i);OpponentSpec d=specById(id);boolean selected=id.equals(r.routeChoice);
   float[] box=dungeonCardBounds(W,H,i,n);float x=box[0],y=box[1],w=box[2],h=box[3];
   TextureRegion avatar=null;   // our own portraits only (no Forge avatar fallback)
   final TextureRegion av=avatar;Run preview=new Run();preview.seed=r.seed;preview.round=r.round;preview.cursed=r.cursed;preview.opponent=d.enemy;
   Curse path=routeCurse(r,id);preview.curses=new ArrayList<>(r.curses);if(path!=null)preview.curses.add(path.id());
   List<String> boon=boons(preview);boolean defeated=(d.enemy.equals("Chandra")&&profile().achievements.contains(DUNGEON_CHANDRA))||(d.enemy.equals("Sliver Queen")&&profile().achievements.contains(DUNGEON_SLIVER));
   List<DungeonLine> lines=new ArrayList<>();
   if(d.commander!=null)lines.add(new DungeonLine("Commander: "+d.commander,6.2f,GOLD,true));
   lines.add(new DungeonLine("\u2665"+enemyStartingLife(preview)+"   ·   Hand "+opponentStartCards(r),5.8f,DIM,false));   // \u2665 = draw a heart icon
   if(!boon.isEmpty())lines.add(new DungeonLine("Starts with: "+String.join(", ",boon),5.6f,TEXT,false));
   if(path!=null){lines.add(new DungeonLine("CURSE · "+displayText(path.name())+" (this encounter)",5.8f,RED,true));lines.add(new DungeonLine(pathCurseEffect(path),5.3f,TEXT,false));}
   if(r.round==15&&!r.cursed)lines.add(new DungeonLine(defeated?"Boss achievement complete":"New boss achievement · Curse key",5.8f,defeated?GREEN:GOLD,true));
   root.addActor(new RoomCard(r,id,d,x,y,w,h,selected,r.round==15?"FINAL GATE":sideName(i),av,lines));
  }
  float nextW=Math.min(118,W*.34f),leftW=W-36-nextW;
  Btn next=button(r.round==15?"Face final boss":"Continue",W-12-nextW,10,nextW,24,()->{if(confirmRoute(r)){save();rebuild();}});next.gold=true;next.disabled=r.routeChoice==null;
  button("Edit deck",18+leftW*.6f,12,leftW*.4f-6,20,()->showRunDeck(r));
 }
 private void buildDungeonVictory(Profile p){
  Result r=p.result;background(r.commander,.8f);title("FINAL BOSS DEFEATED",r.dungeonNotice);
  root.addActor(new Painter((b,a)->{
   float w=W()-48,x=24,y=H()*.25f,h=H()*.48f;Color old=b.getColor().cpy();b.setColor(1,1,1,a);panel().draw(b,x,y,w,h);b.setColor(old);
   text(b,r.dungeonNotice,x+16,y+h-18,9,GOLD,a,w-32,Align.center,true);
   text(b,"Chandra: "+(p.achievements.contains(DUNGEON_CHANDRA)?"DEFEATED":"awaits your challenge"),x+12,y+h*.47f,8,TEXT,a,w-24,Align.center,true);
   text(b,"Sliver Queen: "+(p.achievements.contains(DUNGEON_SLIVER)?"DEFEATED":"awaits your challenge"),x+12,y+h*.31f,8,TEXT,a,w-24,Align.center,true);
   text(b,p.cursesUnlocked?"CURSE MODE + EMRAKUL UNLOCKED":"Curse unlock: "+dungeonBossCount(p)+" / 2 different final bosses",x+12,y+18,8,p.cursesUnlocked?GREEN:DIM,a,w-24,Align.center,true);
  }));
  Btn next=button("Continue to rewards",W()/2-90,24,180,24,()->{r.dungeonNoticeSeen=true;save();rebuild();});next.gold=true;
 }

 // Hand-picked decks live outside Adventure's shared decks and enemy definitions.
 static class OpponentSpec{String id,enemy,label,room,commander;Map<String,Integer> cards;String[] boons,tags;int[] acts;}   // acts: where the deck fits the theme (1-3)
 private static final Map<String,String> specColorCache=new HashMap<>();
 /** WUBRG colours of an opponent deck (non-land cards), for the versus screen pips (user 2026-10-17) */
 static String specColors(OpponentSpec d){
  if(d==null||d.cards==null)return "";
  return specColorCache.computeIfAbsent(d.id==null?d.label:d.id,k->{boolean[] has=new boolean[5];
   for(String n:d.cards.keySet()){PaperCard p=paper(n);if(p==null||p.getRules().getType().isLand())continue;var c=p.getRules().getColor();
    has[0]|=c.hasWhite();has[1]|=c.hasBlue();has[2]|=c.hasBlack();has[3]|=c.hasRed();has[4]|=c.hasGreen();}
   StringBuilder o=new StringBuilder();for(int i=0;i<5;i++)if(has[i])o.append("WUBRG".charAt(i));return o.toString();});
 }   // room + tags: tag_opponents.py
 static class OpponentCatalog{int version;Map<String,List<String>> pools;OpponentSpec[] decks;}
 static OpponentCatalog opponentCatalog;
 static OpponentCatalog opponents(){
  if(opponentCatalog==null)try{opponentCatalog=new Gson().fromJson(Files.readString(new File(DIR+"opponents.json").toPath(),StandardCharsets.UTF_8),OpponentCatalog.class);}
  catch(Exception e){throw new IllegalStateException("Rogue opponent catalog missing or invalid",e);}
  return opponentCatalog;
 }
 static int runLimit(Run r){return 15;}
 static String opponentTier(Run r){
  if(r.round==15)return r.cursed?"boss15-curse":"boss15";
  if(r.round==10)return "boss10";if(r.round==5)return "boss5";
  // floors 11-14 one step easier in normal runs (user 2026-10-18: "beatable"); the curse mode keeps the hard decks
  if(r.round>=13)return r.cursed?"legacy-top":"modern-strong";
  if(r.round>=11)return r.cursed?"legacy-low":"modern-solid";
  if(r.round>=8)return r.cursed?"modern-strong":"modern-solid";
  if(r.round==1&&opponents().pools.containsKey("starter"))return "starter";   // floor 1: vanilla creatures only, basically free (user 2026-10-17)
  if(r.round<=4&&opponents().pools.containsKey("intro"))return "intro";   // floors 1-4: easy beginner decks, the run starts relaxed (user 2026-10-17)
  return "early";
 }
 static List<OpponentSpec> opponentPool(Run r){
  OpponentCatalog c=opponents();List<OpponentSpec> out=new ArrayList<>();List<String> ids=c.pools.get(opponentTier(r));
  if(ids==null||ids.isEmpty())throw new IllegalStateException("Empty Rogue opponent tier");
  for(String id:ids)for(OpponentSpec d:c.decks)if(id.equals(d.id)){out.add(d);break;}
  if(out.size()!=ids.size())throw new IllegalStateException("Unknown Rogue deck id");return out;
 }
 static OpponentSpec opponentSpec(Run r){
  if(r.extraFight&&r.extraDeck!=null){OpponentSpec x=specById(r.extraDeck);if(x!=null)return x;}   // ambush from a ? room
  if(mapActive(r)&&r.round<15){MapNode n=currentNode(r);if(n!=null&&n.deck!=null){OpponentSpec x=specById(n.deck);if(x!=null)return x;}}
  List<OpponentSpec> pool=opponentPool(r);
  if(routedRound(r.round)&&r.routeRound==r.round&&r.routeChoice!=null&&r.routeOptions!=null&&r.routeOptions.contains(r.routeChoice))
   for(OpponentSpec d:pool)if(d.id.equals(r.routeChoice))return d;
  // Already-saved opponents keep their archetype whenever that tier supports it.
  for(OpponentSpec d:pool)if(d.enemy.equals(r.opponent))return d;
  if(r.opponent!=null&&r.opponent.toLowerCase(Locale.ROOT).contains("vampire"))
   for(OpponentSpec d:pool)if(d.id.endsWith("vamp"))return d;
  // Adjacent rounds within a tier use different decks; replaying a save is deterministic.
  int offset=new Random(r.seed*31L+opponentTier(r).hashCode()).nextInt(pool.size());
  return pool.get(Math.floorMod(offset+r.round,pool.size()));
 }
 static Deck opponentDeck(Run r){
  OpponentSpec d=opponentSpec(r);Deck out=new Deck("Rogue - "+d.label);int total=0;
  for(var row:d.cards.entrySet()){
   PaperCard p=paper(row.getKey());
   // The Adventure vampire deck referred to an unavailable Resale Promo print.
   if(row.getKey().equals("Vito, Thorn of the Dusk Rose")){
    PaperCard stable=FModel.getMagicDb().getCommonCards().getCard(row.getKey(),"M21");if(stable!=null)p=stable;
   }
   if(p==null||row.getValue()<1)throw new IllegalStateException("Invalid opponent card: "+row.getKey());
   out.getMain().add(p,row.getValue());total+=row.getValue();
  }
  if(total!=60)throw new IllegalStateException("Opponent deck must contain 60 cards: "+d.id);
  if(d.commander!=null){PaperCard leader=paper(d.commander);if(leader!=null)out.getOrCreate(DeckSection.Commander).add(leader,1);}   // bosses play with a commander (user 2026-10-16)
  return out;
 }
 static int opponentStartCards(Run r){return r.cursed&&r.round==15?10:7;}
 static List<String> boons(Run r){
  OpponentSpec d=opponentSpec(r);List<String> out=new ArrayList<>();
  if(r.round>=8||r.round==5)if(d.boons!=null)out.addAll(Arrays.asList(d.boons));
  if(r.round==10)out.add("Mind's Eye");
  if(r.cursed&&r.round==15)out.add("Thought Vessel");
  for(String n:out)if(paper(n)==null)throw new IllegalStateException("Missing opponent boon: "+n);
  return out;
 }
 // Run-wide boss curses (unlocked after defeating both different normal final bosses): first boss a minor curse, second boss a major one, chosen from three
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
 static Curse curse(String id){if(id==null)return null;for(Curse c:CURSES)if(c.id().equals(id))return c;for(Curse c:PATH_ONLY_CURSES)if(c.id().equals(id))return c;return null;}
 static List<String> curseOffer(Run r,boolean major){
  List<String> pool=new ArrayList<>();for(Curse c:CURSES)if(c.major()==major&&!r.curses.contains(c.id()))pool.add(c.id());
  Collections.shuffle(pool,new Random(r.seed*71+r.round));return new ArrayList<>(pool.subList(0,Math.min(3,pool.size())));
 }
 private void buildCurse(Run r){
  background(r.commander,.78f);
  boolean major=r.round>10;
  String why=r.wins==0?"Your run begins cursed. Choose one curse - it lasts until the run ends.":r.round>=15?"The final gate demands a price: one last curse before the final boss.":"The boss is defeated, but curses you. Choose a curse that lasts until the run ends.";
  title(r.wins==0?"A Cursed Beginning":r.round>=15?"Curse of the Final Gate":major?"Major Curse":"Minor Curse",why);
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
  r.phase=r.offers.isEmpty()?"fight":"package";if(r.offers.isEmpty())resumeOpponent(r);save();rebuild();
 }
 /** opponents' starting life rises from 15 (round 1) to 60 (round 15); bosses get 5 more */
 // curve instead of a straight line (user 2026-10-16: early floors too hard): 15,16,17,19,30(boss),25,28,31,34,43(boss),42,46,51,55,65(boss)
 static int enemyLife(int round){return 15+Math.round(45f*(float)Math.pow((Math.max(1,round)-1)/(float)(MAX_ROUNDS-1),1.5))+(round==5?8:round%5==0?5:0);}   // first boss 30 life (user 2026-10-17)
 static int enemyBonus(int round){return enemyLife(round)-LIFE;}
 /** opponents get stronger with the round (by adventure life), every fifth round is a boss */
 static final java.util.regex.Pattern WEAK_FOES=java.util.regex.Pattern.compile(
  "(?i)apprentice|novice|initiate|acolyte|squire|recruit|peasant|farmer|villager|beggar|urchin|pickpocket|cutpurse|young|student|trainee|militia");
 static String pickOpponent(Run r){
  String previous=r.opponent;r.opponent=null;
  try{return opponentSpec(r).enemy;}finally{r.opponent=previous;}
 }
 static EffectData encounterEnemyEffect(Run r){
  EffectData bonus=new EffectData();bonus.name="Rogue Level "+r.round;bonus.lifeModifier=enemyBonus(r.round);bonus.changeStartCards=opponentStartCards(r)-7;
  List<String> boons=boons(r);if(!boons.isEmpty())bonus.startBattleWithCard=boons.toArray(new String[0]);
  EffectData cursed=new EffectData();cursed.name="Curse";List<String> curseCards=new ArrayList<>();
  if(bonus.startBattleWithCard!=null)curseCards.addAll(Arrays.asList(bonus.startBattleWithCard));
  for(String id:encounterCurseIds(r)){Curse c=curse(id);if(c==null)continue;
   bonus.lifeModifier+=c.enemyLife();cursed.lifeModifier+=c.playerLife();cursed.changeStartCards+=c.playerCards();
   if(c.card()!=null&&paper(c.card())!=null&&!curseCards.contains(c.card()))curseCards.add(c.card());}
  if(!curseCards.isEmpty())bonus.startBattleWithCard=curseCards.toArray(new String[0]);
  if(r.trueRogue&&r.life>0&&r.life<LIFE)cursed.lifeModifier+=r.life-LIFE;
  if("half".equals(r.nextFightPenalty))cursed.lifeModifier-=((r.trueRogue&&r.life>0?r.life:LIFE)+1)/2;   // An Offer You Can't Refuse
  if("hand4".equals(r.nextFightPenalty))cursed.changeStartCards-=3;   // true rogue-like: start with the life you ended the last battle with
  if(cursed.lifeModifier!=0||cursed.changeStartCards!=0)bonus.opponent=cursed;
  return bonus;
 }
 private void startRound(){
  Run r=profile().run;if(r==null)return;
  if(r.round>runLimit(r)){r.completed=true;save();endRun();return;}
  if(routedRound(r.round)&&!routeReady(r)){rebuild();return;}
  if(mapActive(r)&&currentNode(r)==null){rebuild();return;}
  r.opponent=opponentSpec(r).enemy;
  if(r.round==15){profile().reachedFinalBoss=true;updateCommanderAchievements(profile());}   // Atraxa unlock
  EnemyData data=WorldData.getEnemy(r.opponent);if(data==null){r.opponent=pickOpponent(r);data=WorldData.getEnemy(r.opponent);}
  if(data==null)data=WorldData.getEnemy(BASE_ENEMY.getOrDefault(r.opponent,"Dragon"));   // our own figures without a Forge enemy (Niv-Mizzet)
  if(data==null)data=WorldData.getAllEnemies().first();
  showAs(data,r.opponent);   // the duel, Players view and VS screen show OUR figure ("Bird"), not Forge's "Griffin Birdcaller"
  try{
   Constructor<AdventureEventData.AdventureEventParticipant> ctor=AdventureEventData.AdventureEventParticipant.class.getDeclaredConstructor();ctor.setAccessible(true);
   opponent=ctor.newInstance().generate(data);
  }catch(ReflectiveOperationException e){System.err.println("[Rogue] opponent: "+e);return;}
  opponent.setDeck(opponentDeck(r));
  AdventureEventData event=new AdventureEventData(r.seed,AdventureEventController.EventFormat.Constructed,AdventureEventController.EventStyle.Bracket,null);
  event.eventRules=new AdventureEventData.AdventureEventRules(AdventureEventController.EventFormat.Constructed,AdventureEventData.PairingStyle.SingleElimination,1f);
  event.eventRules.gameType=GameType.Commander;event.eventRules.startingLife=LIFE;event.eventRules.gamesPerMatch=bestOfThree(r)?3:1;   // bosses 10 + 15: best of 3 (user 2026-10-18)
  event.eventRules.allowsShards=false;event.eventRules.allowsItems=false;event.eventRules.allowsBlessings=true;   // blessings carry the enemy life bonus
  Cmd rc=cmd(r.commander);if(rc!=null&&r.deck.size()<DECK){r.deck=normalize(rc,growToDefault(rc,r.deck,bench(r)));save();}   // runs from before the 70-card minimum
  event.registeredDeck=deckOf(r.commander,r.deck,"Rogue - "+r.commander);
  // the bench is the sideboard in the duel: Karn, Wishes and other "from outside the game" cards fetch from it (user 2026-10-17)
  for(String n:bench(r))if(!r.deck.contains(n)){PaperCard bp=paper(n);if(bp!=null)event.registeredDeck.getOrCreate(DeckSection.Sideboard).add(bp,1);}
  event.rounds=Math.max(r.round,1);event.currentRound=r.round;event.nextOpponent=opponent;
  event.eventStatus=AdventureEventController.EventStatus.Started;
  EnemySprite enemy=opponent.getSprite();
  enemy.effect=encounterEnemyEffect(r);
  if(r.savedBlessing==null)r.savedBlessing=Current.player().getBlessing();   // the Adventure blessing is kept for the overworld
  Current.player().clearBlessing();
  r.duelStart=System.currentTimeMillis();save();
  DuelScene duel=DuelScene.instance();
  showVersus(r,enemy,r.round%5==0,()->{
   TransitionScreen t=new TransitionScreen(()->{useRogueName();duel.initDuels(null,enemy,false,event);Forge.switchScene(duel);},ScreenUtil.getInstance().takeScreenshot(),false,false);
   FThreads.invokeInEdtNowOrLater(()->Forge.setTransitionScreen(t));
  });
 }
 /** the bosses on floors 10 and 15 are played as best of 3 (user 2026-10-18); Forge runs the games, setWinner sees the match */
 static boolean bestOfThree(Run r){return r!=null&&!r.extraFight&&(r.round==10||r.round==15);}
 /** for the match result screen (ModernWinLose): label while a Rogue duel runs, else null */
 public static String duelLabel(){Profile p=profile;if(p==null||p.run==null||!"fight".equals(p.run.phase)||p.run.duelStart<=0)return null;
  return "ROGUE COMMANDERS  ·  ROUND "+p.run.round+"/"+runLimit(p.run)+(p.run.round%5==0?"  ·  BOSS":"")+(bestOfThree(p.run)?"  ·  BEST OF 3":"");}
 /** the commander's card image (selected illustration) for the result screen */
 public static Texture duelArt(){Profile p=profile;if(p==null||p.run==null)return null;try{return cardTexture(p.run.commander);}catch(Throwable t){return null;}}
 @Override public void setWinner(boolean winner,boolean isArena){
  restoreGuiName();
  Run r=profile().run;if(r==null||!"fight".equals(r.phase))return;
  finishDuelAchievements(profile(),r,winner);
  if(r.savedBlessing!=null){try{Current.player().clearBlessing();Current.player().addBlessing(r.savedBlessing);}catch(Exception e){log(e);}r.savedBlessing=null;}
  long duelMs=r.duelStart>0?Math.max(0,System.currentTimeMillis()-r.duelStart):-1;   // real time of this duel
  try{recordCardStats(profile(),r,winner);}catch(Throwable t){log(t);}   // card history (user 2026-10-19)
  if(r.duelStart>0){profile().activeMs+=Math.min(45*60_000L,duelMs);r.duelStart=0;}
  updateBossMusic();
  lastInput=System.currentTimeMillis();
  if(winner){OpponentSpec os=opponentSpec(r);if((os!=null&&os.id.contains("elves"))||(r.opponent!=null&&r.opponent.contains("Elf")))profile().elvesRemoved++;
   if(r.trueRogue&&r.round==5)profile().trueRogueBossOne=true;}
  if(r.nextFightPenalty!=null){if(winner)r.bonusEpicReady=r.bonusEpicPending;r.nextFightPenalty=null;r.bonusEpicPending=false;}
  if(winner){
   if(r.round%5==0)r.bosses++;
   try{matchStats(r);}catch(Throwable t){log(t);}
   if(r.trueRogue){int end=humanLife();if(end>0)r.life=Math.max(1,Math.min(LIFE,end));}   // healing never above the starting life
   if(duelMs>0&&duelMs<4*60_000L)r.quickWin++;else if(duelMs>20*60_000L)r.longWin++;   // score: won in < 4 min / > 20 min
   if(r.round%5==0&&bossVoiceId(r.opponent)!=null){long ms=bossVoice(r.opponent,"defeat",0);voiceLater(r.commander,"victory",ms+500);}else voice(r.commander,"victory");
   if(r.extraFight){r.extraFight=false;r.extraDeck=null;r.extraWins++;r.ruhm+=Math.max(1,r.round/2);   // ambush from a ? room: normal reward, the floor stays
    r.phase="package";r.offers=makeOffers(r);if(r.offers.isEmpty())afterReward(r);save();rebuild();return;}
   if(r.eliteFight){r.eliteFight=false;r.eliteBonus=1;r.ruhm+=2;}
   int won=r.round;recordDungeonWin(profile(),r,won);
   if(won%5==0&&won<15){Profile pr=profile();pr.packageRerolls++;toast("Boss beaten - you earned a package reroll ("+pr.packageRerolls+", kept over runs).");}   // one reroll per boss (user 2026-10-18)
   creditBossGold(profile(),r,r.round);
   r.wins++;r.ruhm+=r.round;r.round++;updateCommanderAchievements(profile());
   if(r.wins>=runLimit(r)){r.completed=true;save();endRun();return;}r.phase="package";r.offers=makeOffers(r);if(r.offers.isEmpty())afterReward(r);
   if(r.cursed&&(won==5||won==10||won==14)){r.curseOptions=curseOffer(r,won>=10);if(!r.curseOptions.isEmpty())r.phase="curse";}   // + one before the final boss
   save();rebuild();}
  else{r.lost=true;if(r.round%5==0&&bossVoiceId(r.opponent)!=null){long ms=bossVoice(r.opponent,"victory",0);voiceLater(r.commander,"defeat",ms+500);}else voice(r.commander,"defeat");endRun();}
 }

 // ------------------------------------------------------------------ packages (run-only)
 /** reads the finished game for the score categories (life, turns, how the opponent lost, mulligans, ...) */
 /** life of the human player in the finished game (-1 if unknown) */
 static int humanLife(){
  try{var gv=forge.screens.match.MatchController.instance.getGameView();if(gv==null)return -1;
   for(var pv:gv.getPlayers())if(!pv.isAI())return pv.getLife();}catch(Throwable t){log(t);}
  return -1;
 }
 static String lifeTag(Run r){return r!=null&&r.trueRogue?"   ·   Life "+(r.life>0?r.life:LIFE)+" / "+LIFE:"";}
 static CardStat cardStat(Profile p,String card){if(p.cardStats==null)p.cardStats=new HashMap<>();return p.cardStats.computeIfAbsent(card,k->new CardStat());}
 static final CardStat NO_STAT=new CardStat();
 static CardStat cardStatOf(Profile p,String card){CardStat s=p==null||p.cardStats==null?null:p.cardStats.get(card);return s==null?NO_STAT:s;}
 /** card history (user 2026-10-19: "wie haeufig man mit der Karte gespielt hat ... welche Karten man jeden Run added und
  *  spielt"): every duel counts for each card in the run deck; played = cast as a spell or played as a land (Forge game log) */
 static void recordCardStats(Profile p,Run r,boolean winner){
  Set<String> deck=new LinkedHashSet<>();for(String n:r.deck)if(n!=null&&!isBasic(n)&&!n.equals(r.commander))deck.add(n);
  if(r.statCards==null)r.statCards=new HashSet<>();
  for(String n:deck){CardStat cs=cardStat(p,n);cs.games++;if(winner)cs.wins++;if(r.statCards.add(n))cs.runs++;}
  Map<String,String> byFace=new HashMap<>();for(String n:deck){byFace.put(n,n);int sl=n.indexOf(" // ");if(sl>0)byFace.put(n.substring(0,sl),n);}
  var gv=forge.screens.match.MatchController.instance.getGameView();forge.game.Game game=gv==null?null:gv.getGame();if(game==null)return;
  forge.game.player.Player me=null;for(forge.game.player.Player pl:game.getRegisteredPlayers())if(!pl.isAI())me=pl;if(me==null)return;
  String cast=" "+forge.util.Localizer.getInstance().getMessage("lblCast")+" ";
  for(forge.game.GameLogEntry e:game.getGameLog().getLogEntries(null)){
   if(e==null||e.sourceCard()==null)continue;
   boolean land=e.type()==forge.game.GameLogEntryType.LAND,spell=e.type()==forge.game.GameLogEntryType.STACK_ADD&&e.message()!=null&&e.message().contains(cast);
   if(!land&&!spell)continue;
   var who=e.sourceCard().getController();if(who==null||who.getId()!=me.getId())continue;
   String n=byFace.get(e.sourceCard().getName());if(n!=null)cardStat(p,n).played++;
  }
 }
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
 // Per-screen deterministic rarity composition. skippedThirteen is retained only for old-save compatibility.
 static final int REWARD_SCHEMA=6;   // 6: five-card packages - older saved offers are rolled again
 static int rewardTier(Run run){return Math.max(0,Math.min(2,run.rewardTier));}
 static String rewardLabel(int tier){return switch(tier){case 1->"Rare";case 2->"Epic";case 3->"Legendary";default->"Normal";};}
 static String rarityKey(int tier){return switch(tier){case 1->"rare";case 2->"epic";case 3->"legendary";default->null;};}
 static String offerRarity(int tier){return switch(tier){case 1->"Selten";case 2->"Episch";case 3->"Legendär";default->"Normal";};}
 static int offerRarityTier(Offer o){return "Legendär".equals(o.rarity)?3:"Episch".equals(o.rarity)?2:"Selten".equals(o.rarity)?1:0;}
 static Color rewardColor(int tier){return Color.valueOf(switch(tier){case 1->"5aa7ff";case 2->"b07cf0";case 3->"ffb43c";default->"c8c8c8";});}
 static final float TILE_CARDS_W=34,TILE_UNLOCK_W=62;   // package tile buttons
 static int baseRewardTier(int level){return level==10||level==14?3:level==12||level==13?2:level>=4?1:0;}
 /** Epic rewards start at floor 9 (user 2026-10-19 "epic rewards fangen ab stage 9 an"): before it the first boss gives Rare,
  *  elite rooms and skip bonuses lift to Rare at most - an Epic skip bonus is kept until floor 9 */
 static final int EPIC_FROM=9;
 static int tierCap(int level){return level>=EPIC_FROM?3:1;}
 static int guaranteedLegendaries(int level,int tier){return tier==3?5:0;}
 static int maxTier(int level){return level==10||level==14?3:2;}
 static int tierAt(int level,int skipTier){int base=baseRewardTier(level);return base>=2?base:Math.max(base,Math.max(0,Math.min(2,skipTier)));}
 /** the floor's tier for the whole screen (elite rooms: +1 tier); a skip upgrade only lifts option UPGRADED_OPTION */
 static int offerTier(Run run){if(run.forcedTier>=0)return run.forcedTier;return Math.min(tierCap(run.wins),Math.min(3,baseRewardTier(run.wins)+run.eliteBonus));}
 /** user 2026-10-16: skipping upgrades exactly ONE option of the next reward ("skipping Rare gives 2 Rare + 1 Epic;
  *  skipping Epic gives again only one Epic") - the first option, so it also exists when a depleted screen has fewer */
 static final int UPGRADED_OPTION=0;
 static int rewardOptionTier(Run r,int index){
  if(r.forcedTier>=0)return r.forcedTier;
  int base=Math.min(tierCap(r.wins),Math.min(3,(r.wins==11?(index==0?2:1):baseRewardTier(r.wins))+r.eliteBonus));
  return index<upgradeCount(r)?Math.min(tierCap(r.wins),Math.max(base,rewardTier(r))):base;
 }
 /** how many options of this reward the skip bonus lifts: every skip in a row one more, up to all three
  *  (user 2026-10-18: "nach Rare skipped 1 Epic 2 Rare, beim naechsten Skip 2 Epic 1 Rare usw.") */
 static int upgradeCount(Run r){return rewardTier(r)<=0?0:Math.max(1,Math.min(REWARD_OPTIONS,r.rewardUpgrades));}
 static int nextUpgradeCount(Run r){return rewardTier(r)>0?Math.min(REWARD_OPTIONS,upgradeCount(r)+1):1;}
 /** Three choices per win with the consolidated packages (user 2026-10-26). */
 static final int REWARD_OPTIONS=3;
 static final int REWARD_CARDS=5;   // cards per reward package (user 2026-11-03: "Mach von 6 auf 5")
 static int bestOptionTier(Run r){int best=0;for(int i=0;i<REWARD_OPTIONS;i++)best=Math.max(best,rewardOptionTier(r,i));return best;}
 /** the upgrade a skip carries to the next reward: one tier above the best skipped option, at most Epic */
 static int nextSkipTier(Run r){return Math.min(2,bestOptionTier(r)+1);}
 static int nextRewardTier(Run r){return Math.min(tierCap(r.wins+1),baseRewardTier(r.wins+1));}
 static boolean epicLegendaryBonus(Run r){
  // Independent of package, option count, exclusion sets and prior random draws; reloads cannot reroll it.
  return new Random(r.seed^((long)r.wins*0x9e3779b97f4a7c15L)^0x45504943424f4e55L).nextInt(5)==0;
 }
 static int[] rewardComposition(Run r,int tier){return switch(tier){
  case 1->rareEpicBonus(r)?new int[]{0,3,2,0}:new int[]{1,3,1,0};case 2->epicLegendaryBonus(r)?new int[]{0,0,4,1}:new int[]{0,0,5,0};
  case 3->new int[]{0,0,1,4};default->normalEpicBonus(r)?new int[]{2,2,1,0}:new int[]{3,2,0,0};};}
 /** Normal reward: 5% chance that one Normal card is an Epic instead (user 2026-10-17); fixed per run + win like the Epic bonus */
 static boolean normalEpicBonus(Run r){return new Random(r.seed^((long)r.wins*0x9e3779b97f4a7c15L)^0x4e4f524d45504943L).nextInt(20)==0;}
 /** Rare reward: 5% chance of a second Epic instead of the Normal card (user 2026-10-18); fixed per run + win */
 static boolean rareEpicBonus(Run r){return new Random(r.seed^((long)r.wins*0x9e3779b97f4a7c15L)^0x5241524545504943L).nextInt(20)==0;}
 /** the composition without the bonus card - used when a package has too few cards of the bonus tier, so it is still offered */
 static int[] baseComposition(int tier){return switch(tier){case 1->new int[]{1,3,1,0};case 2->new int[]{0,0,5,0};case 3->new int[]{0,0,1,4};default->new int[]{3,2,0,0};};}
 static String compositionText(Run r,int tier){return compositionText(rewardComposition(r,tier));}
 static String compositionText(int[] counts){List<String> parts=new ArrayList<>();for(int i=3;i>=0;i--)if(counts[i]>0)parts.add(counts[i]+" "+rewardLabel(i));return String.join(" + ",parts);}
 /** what this offer really holds (the bonus can fall back to the base mix for one package) */
 static String offerCompositionText(Offer o,Cmd c,Run r,int tier){
  Map<String,CardRef> source=new HashMap<>();if(c!=null)for(CardRef card:offerSourceCards(o,c))if(card!=null)source.put(card.n,card);
  int[] n=new int[4];for(String name:o.cards){CardRef card=source.get(name);if(card==null)return compositionText(r,tier);
   n[card.tier==null?0:"rare".equals(card.tier)?1:"epic".equals(card.tier)?2:"legendary".equals(card.tier)?3:0]++;}
  return o.cards.isEmpty()?compositionText(r,tier):compositionText(n);
 }
 static double qualityShare(int level,int tier){return 1.0;}
 static String rewardRule(int tier,int level){   // only what the boxes do not show (user 2026-10-17: card counts stand in every box)
  if(level==11)return "";
  if(tier==3)return "Floors 10 and 14 are always Legendary.";
  if(tier==2)return "Floors 12 and 13 are always Epic.";
  if(level<EPIC_FROM)return "Epic rewards start at floor "+EPIC_FROM+" - until then skips and elite rooms lift to Rare.";
  return "Skip: one option of the next reward goes up a tier, one more per skip in a row.";
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
 static List<CardRef> packageTierPool(List<CardRef> pool,String tier){
  List<CardRef> out=new ArrayList<>();for(CardRef card:pool)if(card!=null&&Objects.equals(card.tier,tier))out.add(card);return out;
 }
 static Offer scaledOffer(Object source,Cmd c,Run run,Set<String> used,int tier,int level,Random random){
  Offer of=new Offer();of.rarity=offerRarity(tier);List<CardRef> pool;
  if(source instanceof Theme t){of.kind="Thema";of.name=t.name;of.packageKey=t.key;pool=Arrays.asList(t.cards);}
  else{Generic g=(Generic)source;of.kind="Allgemein";of.name=g.name;of.packageKey=g.key;pool=Arrays.asList(g.cards);}
  int[] bonus=rewardComposition(run,tier),base=baseComposition(tier);
  for(int[] counts:Arrays.equals(bonus,base)?List.of(base):List.of(bonus,base)){   // bonus mix first, the base mix if the package lacks the bonus card
   of.cards.clear();Set<String> exclude=new HashSet<>(used);boolean ok=true;
   for(int slot=3;slot>=0;slot--){int count=counts[slot];if(count==0)continue;
    List<CardRef> eligible=rewardPool(packageTierPool(pool,rarityKey(slot)),exclude,c);
    if(eligible.size()<count){ok=false;break;}
    of.cards.addAll(pickReward(eligible,1,0,random,count));exclude.addAll(of.cards);
   }
   if(ok&&of.cards.size()==REWARD_CARDS)return of;
  }
  of.cards.clear();return of;
 }
 static List<CardRef> offerSourceCards(Offer offer,Cmd c){
  if("Thema".equals(offer.kind))for(Theme t:c.themes)if(offer.packageKey!=null?offer.packageKey.equals(t.key):Objects.equals(offer.name,t.name))return Arrays.asList(t.cards);
  if("Allgemein".equals(offer.kind))for(Generic g:content().generic)if(offer.packageKey!=null?offer.packageKey.equals(g.key):Objects.equals(offer.name,g.name))return Arrays.asList(g.cards);
  return List.of();
 }
 /** Only a last resort for small curated pools: retain the five-card limit and
  * upper-tier caps, but allow the lower tiers to replace an exhausted slot.
  * Normal may use more Rare cards, never extra Epic/Legendary cards. */
 static boolean depletedComposition(Run run,int tier,int[] counts){
  if(Arrays.stream(counts).sum()!=REWARD_CARDS)return false;
  int[] wanted=rewardComposition(run,tier);
  if(counts[3]>wanted[3])return false;
  return tier>=2||counts[2]<=wanted[2];
 }
 static Offer depletedOffer(Object source,Cmd c,Run run,Set<String> owned,int tier,Random random){
  Offer of=new Offer();of.rarity=offerRarity(tier);of.depletedMix=true;
  if(source instanceof Theme t){of.kind="Thema";of.name=t.name;of.packageKey=t.key;}
  else{Generic g=(Generic)source;of.kind="Allgemein";of.name=g.name;of.packageKey=g.key;}
  List<CardRef> pool=rewardPool(offerSourceCards(of,c),owned,c);int[] wanted=rewardComposition(run,tier),actual=new int[4];
  // Preferred composition first, with fewer cards when a tier is depleted.
  for(int slot=3;slot>=0;slot--){List<CardRef> eligible=packageTierPool(pool,rarityKey(slot));
   for(int i=0;i<wanted[slot]&&!eligible.isEmpty();i++){CardRef x=drawReward(eligible,random);of.cards.add(x.n);pool.remove(x);actual[slot]++;}}
  // Fill gaps from the highest allowed remaining tier, respecting bonus caps.
  for(int slot=3;slot>=0&&of.cards.size()<REWARD_CARDS;slot--){List<CardRef> eligible=packageTierPool(pool,rarityKey(slot));
   while(!eligible.isEmpty()&&of.cards.size()<REWARD_CARDS){int cap=slot==3?wanted[3]:slot==2&&tier<2?wanted[2]:REWARD_CARDS;
    if(actual[slot]>=cap)break;CardRef x=drawReward(eligible,random);of.cards.add(x.n);actual[slot]++;}}
  if(of.cards.size()!=REWARD_CARDS||!depletedComposition(run,tier,actual))of.cards.clear();return of;
 }
 static boolean validSavedOffer(Offer offer,Cmd c,int tier,Run run){
  if(offer==null||offer.cards==null||offer.cards.size()!=REWARD_CARDS||new HashSet<>(offer.cards).size()!=REWARD_CARDS)return false;
  int own=offerRarityTier(offer);if(own>tier||!offerRarity(own).equals(offer.rarity))return false;tier=own;
  Map<String,CardRef> source=new HashMap<>();for(CardRef card:offerSourceCards(offer,c))if(card!=null)source.put(card.n,card);
  int[] actual=new int[4];Set<String> owned=new HashSet<>(run.deck);owned.addAll(bench(run));owned.add(run.commander);
  for(String name:offer.cards){CardRef card=source.get(name);if(card==null||owned.contains(name)||!fits(card.c,c.colors)||!rewardCompatible(card,c)||paper(name)==null)return false;
   int index=card.tier==null?0:"rare".equals(card.tier)?1:"epic".equals(card.tier)?2:"legendary".equals(card.tier)?3:-1;if(index<0)return false;actual[index]++;}
  return offer.depletedMix?depletedComposition(run,tier,actual):Arrays.equals(actual,rewardComposition(run,tier))||Arrays.equals(actual,baseComposition(tier));
 }
 static String offerCardTier(Offer offer,Cmd c,String name){for(CardRef card:offerSourceCards(offer,c))if(card!=null&&name.equals(card.n))return card.tier;return null;}
 static boolean refreshSavedOffers(Run run){
  Cmd c=cmd(run.commander);if(c==null)return false;
  boolean valid=run.rewardSchema==REWARD_SCHEMA&&run.offers!=null&&run.offers.size()==REWARD_OPTIONS;
  if(valid)for(int i=0;i<run.offers.size();i++)if(!validSavedOffer(run.offers.get(i),c,rewardOptionTier(run,i),run)){valid=false;break;}
  if(valid)return false;run.rewardTier=rewardTier(run);run.offers=makeOffers(run);return true;
 }
 static List<Offer> makeOffers(Run run){
  run.rewardSchema=REWARD_SCHEMA;Cmd c=cmd(run.commander);List<Offer> out=new ArrayList<>();if(c==null||run.wins>=15)return out;
  Random random=new Random(run.seed*977+run.round*13+run.rerollSalt*7919L);Set<String> owned=new HashSet<>(run.deck);owned.add(run.commander);owned.addAll(bench(run));
  List<Theme> picks=new ArrayList<>(),rest=new ArrayList<>();
  Profile prof=profile();
  for(Theme t:activeThemes(c,run))if(t.cards!=null&&t.cards.length>=REWARD_CARDS&&!isExcluded(prof,c,t))(run.chosenPackages!=null&&run.chosenPackages.contains(t.key)?picks:rest).add(t);   // "Never again" counts at once, also in this run (user 2026-10-18)
  Collections.shuffle(picks,random);Collections.shuffle(rest,random);List<Object> plan=new ArrayList<>();
  if(!picks.isEmpty())plan.add(picks.get(0));plan.addAll(rest);if(picks.size()>1)plan.addAll(picks.subList(1,picks.size()));
  if(run.rerollAvoid!=null&&!run.rerollAvoid.isEmpty()){List<Object> again=new ArrayList<>();   // after a reroll the packages just shown come last
   for(Object o:plan)if(o instanceof Theme t&&run.rerollAvoid.contains(t.key))again.add(o);plan.removeAll(again);plan.addAll(again);}
  // Shared neutral packages are themes too; generic fallback is not used for the fixed three thematic floor-11 options.
  {List<Generic> generic=new ArrayList<>(Arrays.asList(content().generic));Collections.shuffle(generic,random);plan.addAll(generic);}   // last resort, also on floor 11 (Claude 2026-10-16)
  avoid=new HashSet<>(profile().recentOffers==null?List.of():profile().recentOffers);seenThisRun=new HashSet<>(run.offered==null?List.of():run.offered);
  List<List<Offer>> candidates=new ArrayList<>();
  for(int index=0;index<REWARD_OPTIONS;index++){
   int tier=rewardOptionTier(run,index);List<Offer> options=new ArrayList<>();Set<Object> added=new HashSet<>();
   // These are mutually exclusive options: a card may occur in another option, but never twice inside one package.
   // Depleted pools (player took many rewards): fall back tier by tier so a screen is never empty (Claude 2026-10-16).
   for(int t=tier;t>=0;t--)for(Object source:plan){if(added.contains(source))continue;Offer candidate=scaledOffer(source,c,run,owned,t,run.wins,random);
    if(candidate.cards.size()==REWARD_CARDS){options.add(candidate);added.add(source);}}
   if(content().packageSchema>=1)for(Object source:plan){if(added.contains(source))continue;
    Offer candidate=depletedOffer(source,c,run,owned,tier,random);if(candidate.cards.size()==REWARD_CARDS){options.add(candidate);added.add(source);}}
   candidates.add(options);
  }
  // Match all three slots together. A greedy Epic choice on floor 11 can steal
  // the only source that still has enough lower-tier cards for a Rare option.
  if(!assignRewardSources(candidates,0,new HashSet<>(),out)){
   Set<String> used=new HashSet<>();for(List<Offer> options:candidates)for(Offer o:options)if(used.add(o.kind+":"+o.packageKey)){out.add(o);break;}
  }
  if(run.offered==null)run.offered=new ArrayList<>();for(Offer offer:out)for(String n:offer.cards)if(!run.offered.contains(n))run.offered.add(n);
  return out;
 }
 static boolean assignRewardSources(List<List<Offer>> choices,int slot,Set<String> used,List<Offer> out){
  if(slot==choices.size())return true;
  for(Offer o:choices.get(slot)){String key=o.kind+":"+o.packageKey;if(!used.add(key))continue;out.add(o);
   if(assignRewardSources(choices,slot+1,used,out))return true;out.remove(out.size()-1);used.remove(key);}
  return false;
 }
 private void buildOffers(Run r){maybeGuide("reward");subLift=-1;   // reward screen: Choose + Skip fill the bottom, the header centre is free
  if(refreshSavedOffers(r))save();
  background(r.commander,.68f);
  int tier=offerTier(r),up=rewardOptionTier(r,UPGRADED_OPTION);Color color=rewardColor(Math.max(tier,up));
  String head=r.wins==11?"EPIC + RARE":up>tier?rewardLabel(up).toUpperCase(Locale.ROOT)+" + "+rewardLabel(tier).toUpperCase(Locale.ROOT):rewardLabel(tier).toUpperCase(Locale.ROOT);
  root.addActor(new Painter((b,a)->{
   text(b,head+" REWARD",13,H()-7,15,Color.BLACK,a*.6f,W()-120,Align.left,true);
   text(b,head+" REWARD",12,H()-6,15,color,a,W()-120,Align.left,true);
   text(b,"Victory in round "+(r.round-1)+"! Choose one run-only package.  "+tx("Click","Tap")+" a card for a closer look.",12,H()-24,6.5f,DIM,a,W()-24,Align.left,false);
   String rule=rewardRule(tier,r.wins)+(skipBonusKept(r)?(rewardRule(tier,r.wins).isEmpty()?"":"   ·   ")+"Your skip bonus ("+upgradeCount(r)+" "+rewardLabel(rewardTier(r))+(upgradeCount(r)>1?" options":" option")+") is kept for the next reward.":"");
   float rw=W()-120-122-12-6;
   if(skipBonusKept(r)&&fitSingleLine(rule,5.8f,rw,false)<5f)rule="Your skip bonus ("+upgradeCount(r)+" "+rewardLabel(rewardTier(r))+(upgradeCount(r)>1?" options":" option")+") is kept for the next reward.";   // too long for one line: the kept bonus matters more
   text(b,rule,12,H()-34,fitSingleLine(rule,5.8f,rw,false),color,a,rw,Align.left,false);   // stops left of Buy reroll (one line, shrinks)
  }));
  float W=W(),H=H();int n=r.offers.size();float gap=8,pw=(W-24-gap*(n-1))/Math.max(1,n),ph=H-86,py=40;
  for(int i=0;i<n;i++){
   Offer o=r.offers.get(i);int optionTier=offerRarityTier(o);float px=12+i*(pw+gap);Color rc=rewardColor(optionTier);
   root.addActor(new Painter((b,a)->{Color old=b.getColor().cpy();b.setColor(1,1,1,a*.6f);panel().draw(b,px,py,pw,ph);b.setColor(old);   // semi-transparent (user 2026-10-17)
    String title="Legendär".equals(o.kind)?displayText(o.name):o.name;   // one line: long package names shrink instead of wrapping into the line below
    text(b,title,px+4,py+ph-6,fitSingleLine(title,9,pw-8,true),GOLD,a,pw-8,Align.center,true);
    Cmd hcmd=cmd(r.commander);long hc=hcmd==null?0:o.cards.stream().filter(huntList(profile(),hcmd)::contains).count();
    String offerHead=rewardLabel(optionTier).toUpperCase(Locale.ROOT)+"  ·  "+o.cards.size()+" CARDS"+(hc>0?"  ·  "+hc+" HUNTED":"");   // hunted count in the header, not over Choose (audit 2026-10-18)
    text(b,offerHead,px,py+ph-18,fitSingleLine(offerHead,6,pw-6,true),rc,a,pw,Align.center,true);
    text(b,offerCompositionText(o,cmd(r.commander),r,optionTier),px,py+ph-27,5.5f,DIM,a,pw,Align.center,false);}));
   int rows=(o.cards.size()+2)/3;
   Cmd oc=cmd(r.commander);Theme ot=oc==null||o.packageKey==null?null:findTheme(oc,o.packageKey);Profile pp=profile();
   float cw=Math.min((pw-16)/3,(ph-80-(rows-1)*4)/rows*.716f),chh=cw/.716f;   // room for Favorite / Never again
   float gx=px+(pw-(cw*3+8))/2,gy=py+26;
   for(int k=0;k<o.cards.size();k++){int col=k%3,row=k/3,inRow=Math.min(3,o.cards.size()-row*3);CardView v=new CardView(o.cards.get(k),gx+col*(cw+4)+(3-inRow)*(cw+4)/2,gy+(rows-1-row)*(chh+4),cw,chh);   /* a short last row is centred */String ct=offerCardTier(o,cmd(r.commander),o.cards.get(k));int cardTier="legendary".equals(ct)?3:"epic".equals(ct)?2:"rare".equals(ct)?1:0;v.tag=rewardLabel(cardTier).toUpperCase(Locale.ROOT);v.border=rewardColor(cardTier);
     if(oc!=null&&huntList(pp,oc).contains(o.cards.get(k))){v.tag="HUNTED  ·  "+v.tag;v.huntMark=true;}v.group=i;root.addActor(v);}
   if(ot!=null&&isNeutral(ot)){   // the package's state on the package itself, above its cards (user 2026-10-18)
    float cx0=gx-3,cy0=gy-3,cwAll=cw*3+8+6,chAll=rows*(chh+4)+2;
    root.addActor(new Painter((b,a)->{
     if(isExcluded(pp,oc,ot)){rect(b,cx0,cy0,cwAll,chAll,Color.valueOf("12060a"),a*.62f);frame(b,px,py,pw,ph,1.6f,NEVER,a);
      float bh=22,by=cy0+chAll/2-bh/2;rect(b,px+2,by,pw-4,bh,Color.valueOf("3a0d14"),a*.92f);frame(b,px+2,by,pw-4,bh,.8f,NEVER,a);
      centerText(b,"NEVER AGAIN",px,by+10f,pw,9,8,NEVER,a,true);centerText(b,"not offered again for this commander",px,by+2f,pw,6,4.6f,Color.valueOf("f3b8bf"),a,false);}
     else if(isFavorite(pp,oc,ot)){float pulse=.5f+.5f*(float)Math.sin(time*3);frame(b,px,py,pw,ph,1.6f,FAV,a*(.75f+.25f*pulse));
      float rw=58,rh=11,rx=px+pw-rw-4,ry=py+ph-rh-1;rect(b,rx,ry,rw,rh,Color.valueOf("3a2a06"),a*.95f);frame(b,rx,ry,rw,rh,.7f,FAV,a);
      centerText(b,"FAVORITE",rx,ry,rw,rh,5.4f,FAV,a,true);}}));
   }
   Btn pickB=button("Choose",px+pw/2-36,py+6,72,16,()->choose(o));pickB.gold=true;
   if(ot!=null&&isNeutral(ot)){   // neutral packages: remember for the next runs (user 2026-10-17)
    boolean fav=isFavorite(pp,oc,ot),never=isExcluded(pp,oc,ot);
    Btn f=button(fav?"Favorite: yes":"+ Favorite",px+4,py+ph-50,pw/2-6,12,()->{toggleFavorite(pp,oc,ot);save();toast(isFavorite(pp,oc,ot)?"Favorite - always available in your package choice from the next run on.":"Removed from favorites.");rebuild();});
    f.gold=fav;f.accent=FAV;
    Btn nv=button(never?"Never again: yes":"Never again",px+pw/2+2,py+ph-50,pw/2-6,12,()->neverAgain(pp,oc,ot));nv.gold=never;nv.accent=NEVER;}
  }
  button("Edit deck",W-100,H-21,90,15,()->showRunDeck(r));
  Profile rp=profile();if(r.packageRerolls>0){rp.packageRerolls+=r.packageRerolls;r.packageRerolls=0;save();}   // older runs: rerolls move to the profile
  Btn rr=button("Reroll packages ("+rp.packageRerolls+")",W-120,H-39,110,14,()->rerollOffers(r));rr.disabled=rp.packageRerolls<=0;rr.gold=rp.packageRerolls>0;
  Btn buy=button("Buy reroll - "+PACKAGE_REROLL_COST+" Favor",W-120-122,H-39,118,14,()->{Profile bp=profile();
   if(bp.goldBalance<PACKAGE_REROLL_COST){toast("Not enough Divine Favor ("+bp.goldBalance+" / "+PACKAGE_REROLL_COST+").");return;}
   bp.goldBalance-=PACKAGE_REROLL_COST;bp.packageRerolls++;save();toast("Reroll bought - "+bp.packageRerolls+" rerolls, "+bp.goldBalance+" Divine Favor left.");rebuild();});
  buy.disabled=rp.goldBalance<PACKAGE_REROLL_COST;
  root.addActor(new Painter((b,a)->{String fav="Divine Favor: "+profile().goldBalance;float fw=chipWidth(fav,6.4f)+10,fx=W-100-8-fw;   // what you can spend (user 2026-10-18), top row next to Edit deck
   rect(b,fx,H-21,fw,15,Color.valueOf("0b0f17"),a*.85f);frame(b,fx,H-21,fw,15,.6f,GOLD,a*.8f);centerText(b,fav,fx,H-21,fw,15,6.4f,Color.valueOf("ffd166"),a,true);}));   // earned by beating bosses (user 2026-10-18)
  int nb=nextRewardTier(r),carry=Math.min(tierCap(r.wins+1),nextSkipTier(r));
  String skipText=r.wins>=14?"Skip - final battle next (no further package reward)":r.wins+1==11?"Skip - next: 1 Epic + "+(REWARD_OPTIONS-1)+" Rare options"
   :carry>nb?"Skip - next reward: "+skipPreview(nextUpgradeCount(r),carry,nb):"Skip - next reward: "+rewardContents(nb)+(nextSkipTier(r)>Math.max(nb,carry)?" - Epic bonus from floor "+EPIC_FROM:"");
  float sw=Math.min(W-24,300);button(skipText,(W-sw)/2,12,sw,19,this::skipReward);
 }
 /** "2 Epic options + 1 Rare option" */
 static String skipPreview(int up,int carry,int nb){
  String s=up+" "+rewardLabel(carry)+(up>1?" options":" option");
  return up>=REWARD_OPTIONS?s:s+" + "+(REWARD_OPTIONS-up)+" "+rewardLabel(nb)+(REWARD_OPTIONS-up>1?" options":" option");
 }
 /** what a reward tier actually contains, for the skip button (user 2026-10-16: show "1 x Epic" etc.) */
 static String rewardContents(int tier){return switch(tier){
  case 1->"Rare (1 Normal + 3 Rare + 1 Epic, 5%: 2nd Epic)";case 2->"Epic (5 x Epic, 20%: 4 Epic + 1 Legendary)";
  case 3->"Legendary (4 x Legendary + 1 Epic)";default->"Normal (3 Normal + 2 Rare, 5%: 1 Epic)";};}
 static void recordRewardSkip(Run r){int count=nextUpgradeCount(r);r.rewardTier=nextSkipTier(r);r.rewardUpgrades=count;r.skippedThirteen=false;r.skippedRewards++;}
 static void recordRewardClaim(Run r){r.skippedThirteen=false;if(!skipBonusKept(r)){r.rewardTier=0;r.rewardUpgrades=0;}}
 /** a skip bonus that would not lift anything here (boss / guaranteed Epic+Legendary floors, elite rooms that are
  *  already as high) is kept for the next reward instead of being used up (user 2026-10-18) */
 static boolean skipBonusKept(Run r){
  if(rewardTier(r)<=0)return false;
  for(int i=0;i<upgradeCount(r);i++){int base=Math.min(tierCap(r.wins),Math.min(3,(r.wins==11?(i==0?2:1):baseRewardTier(r.wins))+r.eliteBonus));if(base<Math.min(tierCap(r.wins),rewardTier(r)))return false;}
  return true;   // every upgraded option is that high anyway (fixed / boss reward): the bonus waits for the next reward
 }
 private void skipReward(){
  Run r=profile().run;if(r==null||!"package".equals(r.phase))return;
  if(r.forcedTier<0)recordRewardSkip(r);
  afterReward(r);save();rebuild();
 }
 /** "Never again" on a reward package: excluded for this commander in all next runs. When the list is full, the
  *  player swaps: pick one excluded package to allow again (user 2026-10-17). */
 private void neverAgain(Profile p,Cmd c,Theme t){
  if(isExcluded(p,c,t)){excluded(p,c).remove(t.key);save();toast(t.name+" is allowed again.");rebuild();return;}   // second click undoes it (user 2026-10-18)
  if(excluded(p,c).size()<maxExcluded(c)){toggleExcluded(p,c,t);save();toast("Excluded for "+c.name.split(",")[0]+" - it is not offered again.");rebuild();return;}
  Group g=pushOverlay();List<String> ex=new ArrayList<>(excluded(p,c));
  float w=Math.min(W()-30,360),rowH=19,h=Math.min(H()-20,58+((ex.size()+1)/2)*rowH),x=(W()-w)/2,y=(H()-h)/2;
  g.addActor(new Painter((b,a)->{rect(b,0,0,W(),H(),Color.BLACK,a*.6f);rect(b,x,y,w,h,Color.valueOf("0d1220"),a*.96f);frame(b,x,y,w,h,1.2f,GOLD,a);
   text(b,"YOUR EXCLUDE LIST IS FULL ("+maxExcluded(c)+")",x,y+h-8,8,GOLD,a,w,Align.center,true);
   text(b,"Allow one of these again to exclude "+t.name+" instead:",x+8,y+h-22,5.8f,TEXT,a,w-16,Align.center,false);}));
  for(int i=0;i<ex.size();i++){final String key=ex.get(i);Theme old=findTheme(c,key);String label=old==null?key:old.name;
   Btn swap=new Btn(label,x+8+(i%2)*((w-20)/2+4),y+h-48-(i/2)*rowH,(w-20)/2,16,()->{excluded(p,c).remove(key);toggleExcluded(p,c,t);save();closeOverlay();toast("Swapped - "+t.name+" is excluded now.");rebuild();});g.addActor(swap);}
  Btn cancel=new Btn("Cancel",x+w/2-40,y+6,80,16,this::closeOverlay);g.addActor(cancel);
 }
 /** price of one package reroll in Divine Favor (rerolls are kept over runs) */
 static final int PACKAGE_REROLL_COST=4;
 /** new package options for this reward; costs one reroll (earned per boss or bought, kept over runs) */
 private void rerollOffers(Run r){
  Profile p=profile();if(p.packageRerolls<=0||!"package".equals(r.phase))return;
  List<String> shown=new ArrayList<>();for(Offer o:r.offers)if(o.packageKey!=null)shown.add(o.packageKey);
  if(r.offered!=null)for(Offer o:r.offers)r.offered.removeAll(o.cards);   // the cards may come back later
  p.packageRerolls--;r.rerollSalt++;r.rerollAvoid=shown;r.offers=makeOffers(r);
  if(r.offers.isEmpty())afterReward(r);
  save();toast("New package options - rerolls left: "+p.packageRerolls);rebuild();
 }
 private void choose(Offer o){
  Run r=profile().run;if(r==null||!"package".equals(r.phase)||!r.offers.contains(o))return;Cmd c=cmd(r.commander);
  // package cards go to the BENCH (user 2026-10-16): the player moves the ones they want into the deck with "Edit deck"
  int benched=0;
  for(String card:o.cards){
   if(r.deck.contains(card)||bench(r).contains(card))continue;
   bench(r).add(card);benched++;if(r.added==null)r.added=new ArrayList<>();r.added.add(card);cardStat(profile(),card).taken++;
  }
  toast(benched+" card(s) on your bench - open \"Edit deck\" and "+tx("right-click","long-press")+" to add them to your deck.");
  voice(r.commander,"package");
  if("Legendär".equals(o.rarity))r.legendary++;else if("Episch".equals(o.rarity))r.epic++;
  r.lastPackage=new ArrayList<>(o.cards);   // "Last package" sort in the deck editor shows these first
  r.picked.add(o.name+" ("+o.rarity+")");
  {Cmd oc=cmd(r.commander);if(oc!=null&&o.packageKey!=null)packageTaken(profile()).merge(oc.slug+":"+o.packageKey,1,Integer::sum);}   // statistics: taken as a reward
  if(r.forcedTier<0)recordRewardClaim(r);   // the fixed boss reward keeps the skip bonus
  afterReward(r);save();rebuild();
 }

 // ------------------------------------------------------------------ hidden dungeon encounters (user 2026-10-16)
 // Rare, out of nowhere, max 3 per run, only after floor 2 is cleared, never right before a boss:
 // Heirloom shop (Ancient Relics), Divine Favor shop, An Offer You Can't Refuse, Merchants from a Far-Away Land.
 static final int MAX_ENCOUNTERS=3;static final double ENCOUNTER_CHANCE=.24;
 private final Set<String> merchantPick=new LinkedHashSet<>();
 void afterReward(Run r){
  r.offers.clear();r.eliteBonus=0;r.rerollAvoid=null;
  if(r.forcedTier>=0)r.forcedTier=-1;                       // the bonus Epic screen is over
  else if(r.bonusEpicReady){                                // An Offer You Can't Refuse paid off: one extra Epic package
   r.bonusEpicReady=false;r.forcedTier=2;r.offers=makeOffers(r);
   if(!r.offers.isEmpty()){r.phase="package";toast("The bargain is kept: choose your BONUS Epic package.");return;}
   r.forcedTier=-1;
  }
  String enc=mapActive(r)?null:rollEncounter(r);   // with the Spire map, encounters sit on the map (? rooms)
  if(enc!=null){r.encounter=enc;r.encounters++;r.encounterTypes.add(enc);r.phase="encounter";merchantPick.clear();return;}
  r.phase="fight";resumeOpponent(r);
 }
 static String rollEncounter(Run r){
  if(r.wins<2||r.encounters>=MAX_ENCOUNTERS||r.round%5==0||r.round>=15)return null;
  Random rnd=new Random(r.seed^((long)r.round*0x51ED27L)^0x3E7L);if(rnd.nextDouble()>=ENCOUNTER_CHANCE)return null;
  List<String> kinds=new ArrayList<>(List.of("relics","favor","offer","merchant"));kinds.removeAll(r.encounterTypes);
  if(merchantCards(r).isEmpty())kinds.remove("merchant");
  if(kinds.isEmpty())return null;return kinds.get(rnd.nextInt(kinds.size()));
 }
 void leaveEncounter(Run r){r.encounter=null;r.phase="fight";resumeOpponent(r);merchantPick.clear();save();rebuild();}
 private void buildEncounter(Run r){
  String kind=r.encounter==null?"":r.encounter;artBackground(kind.isEmpty()?"favor":kind,.42f);
  switch(kind){
   case "relics"->buildHeirloomShop(r);
   case "offer"->buildOffer(r);
   case "merchant"->buildMerchant(r);
   default->{   // Divine Favor shop: the run shop, now only reachable through this encounter
    title("A SHRINE OF DIVINE FAVOR","A forgotten altar hums with power. Spend Divine Favor on cards for this run.");
    root.addActor(new Painter((b,a)->text(b,profile().goldBalance+" Divine Favor",0,H()*.55f,9,GOLD,a,W(),Align.center,true)));
    Btn open=button("Enter the shrine",W()/2-80,H()*.4f,160,22,()->{r.encounter=null;r.phase="fight";resumeOpponent(r);shopOpen=true;save();rebuild();});open.gold=true;
    button("Walk past",W()/2-60,H()*.4f-30,120,18,()->leaveEncounter(r));
   }
  }
 }
 // ---- Heirloom shop: permanent general unlocks (heirlooms) for Ancient Relics
 static int heirloomPrice(CardRef x){return "stark".equals(x.tier)?15:"gut".equals(x.tier)?10:6;}
 static List<CardRef> heirloomStock(Run r){
  Profile p=profile();Cmd c=cmd(r.commander);List<CardRef> pool=new ArrayList<>();
  for(CardRef x:content().unlocks)if(!p.general.contains(x.n)&&c!=null&&fits(x.c,c.colors)&&paper(x.n)!=null)pool.add(x);
  Collections.shuffle(pool,new Random(r.seed^((long)r.round*0x77L)));return pool.subList(0,Math.min(6,pool.size()));
 }
 private void buildHeirloomShop(Run r){
  Profile p=profile();title("THE HEIRLOOM VAULT · "+p.progressionPoints+" ANCIENT RELICS","Heirlooms are unlocked forever for every future run. Prices in Ancient Relics.");
  List<CardRef> stock=heirloomStock(r);int n=Math.max(1,stock.size());float gap=8,cw=Math.min((W()-24-gap*(n-1))/n,(H()-120)*.716f),ch=cw/.716f;
  float x0=(W()-(cw*n+gap*(n-1)))/2,y=H()-60-ch;
  for(int i=0;i<stock.size();i++){CardRef x=stock.get(i);float xx=x0+i*(cw+gap);
   root.addActor(new CardView(x.n,xx,y,cw,ch));int price=heirloomPrice(x);boolean owned=p.general.contains(x.n);
   Btn buy=button(owned?"Unlocked":price+" relics",xx,y-14,cw,12,()->{
    Profile q=profile();if(q.general.contains(x.n)||q.progressionPoints<price)return;
    q.progressionPoints-=price;q.general.add(x.n);noteUnlock(q,x.n);save();toast(x.n+" is now an heirloom - add it with \"Prepare deck\".");rebuild();});
   buy.disabled=owned||p.progressionPoints<price;buy.gold=!buy.disabled;}
  Btn go=button("Leave the vault",W()/2-70,12,140,22,()->leaveEncounter(r));go.gold=true;
 }
 // ---- An Offer You Can't Refuse: a penalty for the next fight, an extra Epic package after it
 private void buildOffer(Run r){
  title("AN OFFER YOU CAN'T REFUSE","A smiling stranger slides a contract across the table. Win the next fight under its terms and an extra Epic package is yours.");
  root.addActor(new Painter((b,a)->{
   text(b,"Refusing costs nothing.",0,H()*.62f,7,DIM,a,W(),Align.center,false);
   text(b,"Reward for winning the next fight: +1 Epic package (on top of the normal reward)",0,H()*.55f,6.5f,GOLD,a,W(),Align.center,false);}));
  float bw=Math.min(220,(W()-40)/2);
  Btn half=button("Sign: start the next fight with HALF your life",W()/2-bw-6,H()*.38f,bw,22,()->{r.nextFightPenalty="half";r.bonusEpicPending=true;toast("Signed. Next fight: half life.");leaveEncounter(r);});
  Btn hand=button("Sign: next fight opening hand of only 4 cards",W()/2+6,H()*.38f,bw,22,()->{r.nextFightPenalty="hand4";r.bonusEpicPending=true;toast("Signed. Next fight: 4-card opening hand.");leaveEncounter(r);});
  half.gold=true;hand.gold=true;
  button("Refuse",W()/2-60,H()*.38f-34,120,18,()->leaveEncounter(r));
 }
 // ---- Merchants from a Far-Away Land: cards used in the last battle (and still in the deck) for lands
 static List<String> merchantCards(Run r){
  List<String> out=new ArrayList<>();if(r.lastUsed==null)return out;
  for(String n:r.lastUsed)if(r.deck.contains(n)&&!isBasic(n)&&!n.equals(r.commander)&&!out.contains(n))out.add(n);return out;
 }
 static final String[][] MERCHANT_LANDS={   // name, colours, class (D dual, F fetch, S shock, G other very good land)
  {"Tundra","WU","D"},{"Underground Sea","UB","D"},{"Badlands","BR","D"},{"Taiga","RG","D"},{"Savannah","GW","D"},{"Scrubland","WB","D"},{"Volcanic Island","UR","D"},{"Bayou","BG","D"},{"Plateau","RW","D"},{"Tropical Island","GU","D"},
  {"Flooded Strand","WU","F"},{"Polluted Delta","UB","F"},{"Bloodstained Mire","BR","F"},{"Wooded Foothills","RG","F"},{"Windswept Heath","GW","F"},{"Marsh Flats","WB","F"},{"Scalding Tarn","UR","F"},{"Verdant Catacombs","BG","F"},{"Arid Mesa","RW","F"},{"Misty Rainforest","GU","F"},
  {"Hallowed Fountain","WU","S"},{"Watery Grave","UB","S"},{"Blood Crypt","BR","S"},{"Stomping Ground","RG","S"},{"Temple Garden","GW","S"},{"Godless Shrine","WB","S"},{"Steam Vents","UR","S"},{"Overgrown Tomb","BG","S"},{"Sacred Foundry","RW","S"},{"Breeding Pool","GU","S"},
  {"Seachrome Coast","WU","G"},{"Darkslick Shores","UB","G"},{"Blackcleave Cliffs","BR","G"},{"Copperline Gorge","RG","G"},{"Razorverge Thicket","GW","G"},{"Concealed Courtyard","WB","G"},{"Spirebluff Canal","UR","G"},{"Blooming Marsh","BG","G"},{"Inspiring Vantage","RW","G"},{"Botanical Sanctum","GU","G"},
  {"Silent Clearing","WB","G"},{"Fiery Islet","UR","G"},{"Nurturing Peatland","BG","G"},{"Sunbaked Canyon","RW","G"},{"Waterlogged Grove","GU","G"},{"Horizon Canopy","GW","G"},
  {"Prismatic Vista","","G"},{"Mana Confluence","","G"},{"City of Brass","","G"},{"Reliquary Tower","","G"},{"Cavern of Souls","","G"},
  {"Urborg, Tomb of Yawgmoth","B","G"},{"Boseiju, Who Endures","G","G"},{"Otawara, Soaring City","U","G"},{"Eiganjo, Seat of the Empire","W","G"},{"Sokenzan, Crucible of Defiance","R","G"},{"Takenuma, Abandoned Mire","B","G"}};
 static boolean epicCard(Cmd c,String name){
  if(c==null)return false;for(Theme t:c.themes)if(t.cards!=null)for(CardRef x:t.cards)if(x.n.equals(name)&&("epic".equals(x.tier)||"legendary".equals(x.tier)))return true;
  return rating(name)>=88;
 }
 static List<String> merchantTrade(Run r,List<String> given){
  Cmd c=cmd(r.commander);List<String> out=new ArrayList<>();if(c==null||given.isEmpty())return out;
  boolean epic=false;double value=0;for(String n:given){double q=Math.max(0,Math.min(1,rating(n)/100.0));value+=.35+.75*q;epic|=epicCard(c,n);}
  Random rnd=new Random(r.seed^((long)r.round*0x9F3L)^given.hashCode());
  int count=Math.min(10,Math.max(1,(int)Math.floor(value+rnd.nextDouble()*.9)));
  Set<String> owned=new HashSet<>(r.deck);owned.addAll(bench(r));
  for(int i=0;i<count;i++){
   double roll=rnd.nextDouble();String cls=epic&&roll<.06?"D":roll<.18?"F":roll<.62?"S":"G";
   List<String> pool=new ArrayList<>();
   for(String[] l:MERCHANT_LANDS)if(l[2].equals(cls)&&fits(l[1],c.colors)&&!owned.contains(l[0])&&!out.contains(l[0])&&paper(l[0])!=null&&(l[1].length()>1||cls.equals("G")))pool.add(l[0]);
   if(pool.isEmpty())for(String[] l:MERCHANT_LANDS)if(!l[2].equals("D")&&fits(l[1],c.colors)&&!owned.contains(l[0])&&!out.contains(l[0])&&paper(l[0])!=null)pool.add(l[0]);
   if(pool.isEmpty())break;out.add(pool.get(rnd.nextInt(pool.size())));
  }
  return out;
 }
 private void buildMerchant(Run r){
  title("MERCHANTS FROM A FAR-AWAY LAND","Under silk awnings a rat merchant counts coins: \"Cards you fought with, friend - I pay in land.\" Pick cards from your last battle.");
  List<String> cards=merchantCards(r);int n=cards.size();
  int cols=Math.max(1,Math.min(8,n)),rows=Math.max(1,(n+cols-1)/cols);float gap=5,cw=Math.min((W()-24-gap*(cols-1))/cols,(H()-110-(rows-1)*gap)/rows*.716f),ch=cw/.716f;
  float x0=(W()-(cw*cols+gap*(cols-1)))/2;
  for(int i=0;i<n;i++){String name=cards.get(i);int col=i%cols,row=i/cols;
   CardView v=new CardView(name,x0+col*(cw+gap),H()-58-ch-row*(ch+gap),cw,ch);v.selected=merchantPick.contains(name);
   v.action=()->{if(!merchantPick.remove(name))merchantPick.add(name);rebuild();};root.addActor(v);}
  int picked=merchantPick.size();
  root.addActor(new Painter((b,a)->text(b,picked==0?"Tap cards to offer them (they leave your deck). Better cards bring better and more lands, up to 10.":picked+" card(s) offered. Rare true duals only if you give at least one Epic-quality card.",12,40,6,GOLD,a,W()-24,Align.center,false)));
  Btn trade=button("Trade",W()-140,10,128,22,()->{
   List<String> given=new ArrayList<>(merchantPick);List<String> lands=merchantTrade(r,given);if(lands.isEmpty()){toast("The merchant shakes his head.");return;}
   Cmd c=cmd(r.commander);r.deck.removeAll(given);r.deck.addAll(lands);if(c!=null)r.deck=normalize(c,r.deck);
   if(r.added==null)r.added=new ArrayList<>();r.added.addAll(lands);r.lastPackage=new ArrayList<>(lands);
   toast("You receive: "+String.join(", ",lands));leaveEncounter(r);});
  trade.gold=true;trade.disabled=picked==0;
  button("Leave",12,10,90,22,()->leaveEncounter(r));
 }

 // ------------------------------------------------------------------ end of run: permanent unlocks
 static double completionRewardMultiplier(Run r){return r.completed&&r.wins>=15?1.5:1.0;}
 /** easy first unlocks: until a profile owns 12 heirlooms/signature cards, every run gives at least 2 (3 with 2+ wins,
  *  4 after reaching the first boss) - then the normal Glory-based pace takes over (user 2026-10-17) */
 static final int STARTER_UNLOCK_CAP=12;
 static int starterUnlocks(Profile p,Run r){
  int owned=p.general.size();for(List<String> l:p.signature.values())owned+=l.size();
  if(owned>=STARTER_UNLOCK_CAP)return 0;
  int n=r.wins>=5?4:r.wins>=2?3:2;return Math.min(n,STARTER_UNLOCK_CAP-owned);
 }
 static double unlockCredit(double carry,int glory,double pace,Run r){return carry+glory*pace*UNLOCK_PERCENT/100.0*completionRewardMultiplier(r);}
 private void endRun(){
  Profile p=profile();Run r=p.run;if(r==null)return;
  Cmd c=cmd(r.commander);Result res=new Result();res.commander=r.commander;res.wins=r.wins;res.ruhm=r.ruhm;
  double hours=p.activeMs/3_600_000.0,target=TARGET_SHARE*Math.pow(Math.min(1,hours/TARGET_HOURS),1.4),actual=generalShare();
  double pace=hours<.5?1:Math.max(.75,Math.min(1.33,(target+.03)/(actual+.03)));
  double points=unlockCredit(p.carry,r.ruhm,pace,r);int count=(int)Math.floor(points/COST);p.carry=points-count*COST;
  count=Math.max(count,starterUnlocks(p,r));   // a few easy first heirlooms (user 2026-10-17)
  Random rnd=new Random(r.seed^0x5eedL);
  for(int i=0;i<count;i++){
   p.counter++;
   if(p.counter%4==0&&c!=null){                     // every fourth unlock: a signature card of this commander
    List<String> have=p.signature.computeIfAbsent(c.name,k->new ArrayList<>());
    Set<String> anySig=unlockedSignatures(p);List<String> left=new ArrayList<>();for(CardRef x:c.specific)if(!have.contains(x.n)&&!anySig.contains(x.n))left.add(x.n);   // skip cards unlocked through another commander
    if(!left.isEmpty()){String s=left.get(rnd.nextInt(left.size()));have.add(s);noteUnlock(p,s);award(p,"signature:"+c.name+":"+s);res.signature.add(s);continue;}
   }
   String g=generalUnlock(c,r.wins,rnd);if(g==null)break;p.general.add(g);noteUnlock(p,g);award(p,"card:"+g);res.unlocked.add(g);
  }
  int beforeProgression=p.progressionPoints;progressionAfterRun(p,r);res.progressionEarned=p.progressionPoints-beforeProgression;
  res.completed=r.completed;scoreRun(p,r,res);
  int previousBest=p.best.getOrDefault(r.commander,0);
  syncDungeonUnlock(p);
  if(r.completed&&r.wins>=15)res.dungeonNotice=dungeonVictoryNotice(p,r);
  if(r.offered!=null){p.recentOffers=new ArrayList<>(r.offered.subList(Math.max(0,r.offered.size()-300),r.offered.size()));}
  p.runs++;p.totalWins+=r.wins;p.best.merge(r.commander,r.wins,Math::max);updateCommanderAchievements(p);
  for(Skin s:skins(r.commander))if(s.wins>previousBest&&s.wins<=r.wins)res.skins.add(s.artist+" ("+s.edition+" #"+s.number+")");
  p.lastRunFailed=!r.completed;   // the Portal Mage greets you differently after a failed run
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
  if(r.completed)l.add(new ScoreLine("Run abgeschlossen ("+runLimit(r)+" Runden)","",150));
  for(String id:r.curses){Curse c=curse(id);if(c!=null)l.add(new ScoreLine("Fluch: "+c.name(),c.major()?"groß":"klein",c.major()?80:40));}
  if(r.completed&&!r.curses.isEmpty())l.add(new ScoreLine("Verfluchter Sieg","",100));
  line(l,"Bosse besiegt",r.bosses,40,false);
  line(l,"Makellos (volles Leben)",r.perfect,15,false);
  line(l,"Lebensquelle (50+ Leben)",r.lifeSource,10,false);
  line(l,"Blitzsieg (bis Zug 7)",r.fast,15,false);
  line(l,"Marathon (20+ Züge)",r.marathon,10,false);
  line(l,"Schnellsieg (unter 4 Minuten)",r.quickWin,15,false);
  line(l,"Ausdauer (über 20 Minuten)",r.longWin,10,false);
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
  title((res.completed||res.wins>=MAX_ROUNDS?(res.completed&&res.wins>=15?"Run completed! · Rewards x1.5":"Run completed!"):"Run ended")+"  ·  "+wins(res.wins),res.ruhm+" Glory   ·   "+total+" new cards   ·   +"+res.progressionEarned+" progression points"+(res.skins.isEmpty()?"":"   ·   "+res.skins.size()+" new artwork")+"   ·   General "+p.general.size()+"/"+content().unlocks.length);
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
 static class Item{String name,tag,tier,rarity;Color tagColor;int count=1,recent=-1;boolean locked,added,heirloom;PaperCard illustration;}
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
 /** filter chip groups (all / card types / run status / rarity) and their colours: the filter bar was one colour and hard to read (user 2026-10-18) */
 static int filterGroup(String f){return f==null?0:switch(f){case "Kreaturen","Zauber","Artefakte","Verzauberungen","Planeswalker","Länder"->1;case "Legendär","Episch","Selten","Normal"->3;default->2;};}
 static Color filterAccent(String f){return f==null?null:Color.valueOf(switch(f){
  case "Kreaturen"->"6fd08f";case "Zauber"->"ef6f6c";case "Artefakte"->"b9c4d0";case "Verzauberungen"->"f08fd0";case "Planeswalker"->"f2b04a";case "Länder"->"c49a6c";
  case "Legendär"->"ff9a2e";case "Episch"->"b46cff";case "Selten"->"5aa7ff";case "Normal"->"c8c8c8";case "Gesperrt"->"8a93a7";
  default->"ffd166";});}
 /** browser search: card name or type line contains the text */
 static boolean searchHit(Item i,String q){
  if(q==null||q.isEmpty())return true;if(i.name.toLowerCase(Locale.ROOT).contains(q))return true;
  PaperCard p=paper(i.name);return p!=null&&p.getRules().getType().toString().toLowerCase(Locale.ROOT).contains(q);
 }
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
   case "Legendär"->"legendary".equals(i.rarity);
   case "Episch"->"epic".equals(i.rarity);
   case "Selten"->"rare".equals(i.rarity);
   case "Normal"->i.rarity==null;
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
   case "Seltenheit"->Comparator.comparingInt((Item i)->rarityOrder(i.rarity)).thenComparing(byCmc);
   case "Letztes Paket"->Comparator.comparingInt((Item i)->i.recent<0?9999:i.recent).thenComparing(byCmc);
   case "Neueste"->Comparator.comparingInt((Item i)->-unlockRank(profile(),i.name)).thenComparing(byCmc);   // newest unlock first
   case "Gespielt"->Comparator.comparingInt((Item i)->-cardStatOf(profile(),i.name).played).thenComparing(byCmc);   // card history: most played first
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
 /** card browser grid presets {columns, rows}, from many small cards to few big ones; default 12 x 3 */
 static final int[][] BROWSER_ZOOM={{16,4},{14,3},{12,3},{10,2},{8,2},{6,1}};
 static int browserZoom=2;
 /** set right before showBrowser: every card gets a "+ Hunt" / "Hunted" toggle for this hunting list (package cards, user 2026-10-18) */
 List<String> browserHunt;
 boolean browserStats;   // set right before showBrowser: the big preview shows the card history (deck views, user 2026-10-19)
 private void showBrowser(String title,java.util.function.Supplier<List<Item>> items,String[] filters,String[] sorts,java.util.function.Function<List<Item>,String> info,Runnable extra,String extraLabel,BrowserEdit edit){
  Group g=pushOverlay();
  if(edit!=null)overlayClosed.put(g,edit::closed);
  final List<String> hunt=browserHunt;browserHunt=null;boolean[] huntChanged={false};final boolean statsView=browserStats;browserStats=false;
  if(hunt!=null)overlayClosed.put(g,()->{if(huntChanged[0]&&overlays.isEmpty())rebuild();});   // package screen: Hunt n/m and the list count follow
  float W=W(),H=H();
  Set<String> active=new LinkedHashSet<>();String[] sort={sorts[0]};boolean[] reverse={false};int[] page={0};
  NameField search=new NameField("",0,0,10,10);search.placeholder="Search cards ...";   // name or type, filters while typing (user 2026-10-18)
  Group content=new Group();
  g.addActor(new Painter((b,a)->{rect(b,0,0,W,H,DARK,a*.97f);   // see-through (user 2026-10-17), but no readable text of the screen behind (2026-10-18)
   rect(b,0,H-76,W,76,DARK,a*.92f);rect(b,0,0,W,31,DARK,a*.92f);   // header and footer opaque: the screen behind showed through the text (user 2026-10-18, "clipping oben links")
   float room=edit==null?W-24:W-12-(edit.tabs().length>2?3*92+8:edit.tabs().length*108-4)-36;   // the deck / heirloom / signature tabs sit on the right of the title row
   text(b,title,12,H-8,fitSingleLine(title,12,room,true),GOLD,a,room,Align.left,true);}));
  g.addActor(content);
  float gap=3,top=H-72,bottom=32;
  Runnable[] refresh=new Runnable[1];
  refresh[0]=()->{
   content.clearChildren();
   // zoomable grid (user 2026-10-18): - / + buttons or Ctrl + mouse wheel; remembered for the session
   int cols=BROWSER_ZOOM[browserZoom][0],rows=BROWSER_ZOOM[browserZoom][1];
   float cw=Math.min((W-24-(cols-1)*gap)/cols,((top-bottom)-(rows-1)*(gap+5))/rows*.716f),ch=cw/.716f;
   int per=cols*rows;
   List<Item> list=new ArrayList<>();
   String q=search.entered().toLowerCase(Locale.ROOT);
   for(Item i:items.get()){boolean ok=active.isEmpty();for(String f:active)if(matches(i,f)){ok=true;break;}if(ok&&searchHit(i,q))list.add(i);}
   Comparator<Item> cmp=sorter(sort[0]);list.sort(reverse[0]?cmp.reversed():cmp);
   int pages=Math.max(1,(list.size()+per-1)/per);if(page[0]>=pages)page[0]=pages-1;
   String sub=info.apply(list)+(pages>1?"   ·   Page "+(page[0]+1)+"/"+pages:"");
   // filter bar, clearer (user 2026-10-18): each row on its own band, chips colour-coded per group (card types /
   // run status / rarity) with dividers between the groups, empty filters hidden, sort direction next to the sort
   // chips, zoom at the right end
   content.addActor(new Painter((b,a)->{text(b,sub,12,H-26,6.2f,DIM,a,W-24,Align.left,false);
    rect(b,8,H-51.5f,W-16,15,Color.valueOf("1a2034"),a*.55f);rect(b,8,H-68.5f,W-16,15,Color.valueOf("1a2034"),a*.55f);
    centerText(b,"Filter",10,H-50,36,12,5.2f,GOLD,a,false);centerText(b,"Sort",10,H-67,36,12,5.2f,GOLD,a,false);}));
   // every filter chip shows how many cards of that kind the current tab holds (user 2026-10-18)
   List<Item> allItems=items.get();List<String> labels=new ArrayList<>();List<String> keys=new ArrayList<>();
   int total=0;for(Item i:allItems)total+=Math.max(1,i.count);labels.add("All "+total);keys.add(null);
   for(String f:filters){if(f.equals("Alle"))continue;int c=0;for(Item i:allItems)if(matches(i,f))c+=Math.max(1,i.count);
    if(c==0&&!active.contains(f))continue;labels.add(displayText(f)+" "+c);keys.add(f);}
   float[] widths=new float[labels.size()];float sum=0;
   for(int k=0;k<widths.length;k++){widths[k]=Math.max(26,chipWidth(labels.get(k),5.2f)+12);sum+=widths[k]+2;if(k>0&&filterGroup(keys.get(k))!=filterGroup(keys.get(k-1)))sum+=8;}
   float shrink=Math.min(1,(W-14-50)/Math.max(1,sum));float x=50;
   for(int k=0;k<labels.size();k++){final String f=keys.get(k);float w=widths[k]*shrink;
    if(k>0&&filterGroup(f)!=filterGroup(keys.get(k-1))){float dx=x+3*shrink;content.addActor(new Painter((b,a)->rect(b,dx,H-49,.8f,9,DIM,a*.8f)));x+=8*shrink;}
    Btn c=new Btn(labels.get(k),x,H-50,w,12,()->{if(f==null)active.clear();else if(!active.remove(f))active.add(f);page[0]=0;refresh[0].run();});
    c.gold=f==null?active.isEmpty():active.contains(f);c.accent=filterAccent(f);content.addActor(c);x+=w+2*shrink;}
   x=50;float sortSum=0;for(String s:sorts)sortSum+=Math.max(26,displayText(s).length()*3.4f+10)+2;
   float sortShrink=Math.min(1,(W-12-50-6-54-10-100)/Math.max(1,sortSum));   // the search field keeps 100+ (user 2026-10-19: an 8th sort chip made it wrap)
   for(String s:sorts){
    float w=Math.max(26,displayText(s).length()*3.4f+10)*sortShrink;
    Btn c=new Btn(displayText(s),x,H-67,w,12,()->{if(sort[0].equals(s))reverse[0]=!reverse[0];else{sort[0]=s;reverse[0]=false;}refresh[0].run();});c.gold=s.equals(sort[0]);content.addActor(c);x+=w+2;
   }
   float rx=x+6;content.addActor(new Painter((b,a)->rect(b,rx-3,H-66,.8f,9,DIM,a*.8f)));
   Btn rev=new Btn(reverse[0]?"Descending":"Ascending",rx+2,H-67,52,12,()->{reverse[0]=!reverse[0];refresh[0].run();});content.addActor(rev);
   float sx=rx+2+52+10;search.setBounds(sx,H-67.5f,W-12-sx-6,13);   // search field: rest of the sort row
   float zx=188;   // zoom in the bottom bar, between the page buttons and Unlocked
   content.addActor(new Painter((b,a)->centerText(b,"Zoom",zx,8,24,18,5.2f,GOLD,a,false)));
   Btn zo=new Btn("-",zx+25,9,19,16,()->{browserZoom=Math.max(0,browserZoom-1);page[0]=0;refresh[0].run();});zo.disabled=browserZoom==0;content.addActor(zo);
   Btn zi=new Btn("+",zx+46,9,19,16,()->{browserZoom=Math.min(BROWSER_ZOOM.length-1,browserZoom+1);page[0]=0;refresh[0].run();});zi.disabled=browserZoom==BROWSER_ZOOM.length-1;content.addActor(zi);
   if(hunt!=null){String hint=tx("H: hunt / unhunt the card under the mouse","Hunt: tap a card, then Hunt");float hx=zx+72,hw=W-110-hx-8;   // the hunt key in the bottom bar (user 2026-10-19)
    content.addActor(new Painter((b,a)->centerText(b,hint,hx,8,hw,18,fitSingleLine(hint,6.2f,hw,false),Color.valueOf("ffb43c"),a,false)));}
   if(edit!=null){String[] tabs=edit.tabs();float span=tabs.length>2?3*92+8:tabs.length*104+(tabs.length-1)*4,letters=0;for(String tl:tabs)letters+=tl.length();
    float tx=W-12-span;   // same row width as before, each tab as wide as its label needs ("Signatures 12/32" was squeezed, audit 2026-10-18)
    for(int k=0;k<tabs.length;k++){final int t=k;float tw=(span-4*(tabs.length-1))*tabs[k].length()/letters;Btn tb=new Btn(tabs[k],tx,H-25,tw,17,()->{edit.tab(t);page[0]=0;refresh[0].run();});tb.gold=edit.tab()==k;content.addActor(tb);tx+=tw+4;}}
   for(int i=0;i<per;i++){
    int k=page[0]*per+i;if(k>=list.size())break;Item it=list.get(k);
    float cx=12+(i%cols)*(cw+gap),cy=top-ch-(i/cols)*(ch+gap+5);
    CardView v=new CardView(it.name,cx,cy,cw,ch);v.illustration=it.illustration;v.dim=it.locked;v.rating=rating(it.name);
    if(v.illustration==null&&isBasic(it.name)){Profile bp=profile();v.illustration=basicPaper(bp.run!=null?bp.run.commander:bp.selected,it.name,0);}   // same basics as in the duel (user 2026-10-17)
    v.tag=it.count>1?"× "+it.count:it.tag;v.border=it.tagColor;v.stats=statsView;content.addActor(v);
    if(hunt!=null&&!isBasic(it.name)){v.huntMark=hunt.contains(it.name);v.hunt=hunt;v.onHuntChange=()->{huntChanged[0]=true;refresh[0].run();};}   // H toggles it (user 2026-10-19: keybind, no button on the card)
    if(edit!=null)v.addListener(new ClickListener(com.badlogic.gdx.Input.Buttons.RIGHT){   // right-click moves the card
     @Override public void clicked(InputEvent e,float x,float y){if(blocked(v))return;try{edit.act(it);refresh[0].run();}catch(Throwable t){log(t);}}});
    if(edit!=null)onLongPress(v,()->{if(blocked(v))return;try{edit.act(it);refresh[0].run();}catch(Throwable t){log(t);}});   // touch: long press
   }
   if(list.isEmpty())content.addActor(new Painter((b,a)->text(b,q.isEmpty()?"No cards match these filters.":"No card matches \""+search.entered()+"\".",0,H/2,8,DIM,a,W,Align.center,true)));
   if(pages>1){
    content.addActor(new Btn("Previous page",12,8,84,18,()->{page[0]=(page[0]+pages-1)%pages;refresh[0].run();}));
    content.addActor(new Btn("Next page",100,8,84,18,()->{page[0]=(page[0]+1)%pages;refresh[0].run();}));
   }
  };
  refresh[0].run();
  search.onChange=()->{page[0]=0;refresh[0].run();};g.addActor(search);
  if(hunt!=null){stageMouse();focusOnReturn.add(g);
   g.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){   // H: the card under the mouse onto / off the hunting list (user 2026-10-19)
    @Override public boolean keyDown(InputEvent e,int key){if(key!=com.badlogic.gdx.Input.Keys.H||e.getTarget()==search)return false;
     CardView v=cardUnderMouse(g);if(v==null||v.hunt==null){toast("Point at a card and press H to hunt it.");return true;}toggleHunt(v);return true;}
    @Override public boolean touchDown(InputEvent e,float x,float y,int pointer,int button){if(e.getTarget()!=search&&g.getStage()!=null)g.getStage().setKeyboardFocus(g);return false;}});
   if(g.getStage()!=null)g.getStage().setKeyboardFocus(g);}
  g.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){   // Ctrl + wheel zooms, wheel alone turns pages
   @Override public boolean scrolled(InputEvent e,float x,float y,float ax,float ay){
    boolean ctrl=Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)||Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);
    if(ctrl){int z=Math.max(0,Math.min(BROWSER_ZOOM.length-1,browserZoom+(ay<0?1:-1)));if(z!=browserZoom){browserZoom=z;page[0]=0;refresh[0].run();}return true;}
    page[0]=Math.max(0,page[0]+(ay>0?1:-1));refresh[0].run();return true;}
   @Override public boolean mouseMoved(InputEvent e,float x,float y){if(e.getStage()!=null)e.getStage().setScrollFocus(g);return false;}});
  if(extra!=null)g.addActor(new Btn(extraLabel,W-222,8,106,20,extra));
  Btn close=new Btn("Close",W-110,8,98,20,this::closeOverlay);close.gold=true;g.addActor(close);
 }
 /** run deck, editable: tab Deck (a card goes to the bench, basic lands are just removed) and tab Bench (cards from
  *  packages and cards taken out, plus the basic lands of the commander's colours; + puts a card into the deck) */
 private void showRunDeck(Run r){showRunDeck0(r);maybeGuide("deck");}
 private void showRunDeck0(Run r){
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
    else if(i.recent>=0){i.added=true;i.tag="NEW";i.tagColor=Color.valueOf("ffd166");}   // only the newest cards are marked (user 2026-10-17)
    else if(added.contains(i.name)){i.added=true;}
    else if(heir.contains(i.name)){i.heirloom=true;   /* no "Heirloom" label/frame (user 2026-10-17); the Heirlooms filter still finds them */}
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
  browserStats=true;
  showBrowser("Run deck  ·  "+r.commander.split(",")[0],items,
   new String[]{"Alle","Kreaturen","Zauber","Artefakte","Verzauberungen","Planeswalker","Länder","Neu im Run","Erbstücke"},
   new String[]{"Letztes Paket","Mana","Typ","Farbe","Rating","Name","Neu","Gespielt"},   // run deck: newest package first; "Newest unlock" is for heirlooms / signatures (user 2026-10-18)
   list->{int lands=0;for(String n:r.deck)if(isLand(n))lands++;int n=r.deck.size();
    return n+" cards + commander   ·   "+lands+" lands   ·   "+(n-lands)+" others"+(n<DECK?"   ·   needs at least "+DECK:n>MAX_DECK?"   ·   max "+MAX_DECK:"")
     +tx("   ·   right-click a card: ","   ·   long-press a card: ")+(tab[0]==0?"remove":"add");},
   c==null?null:()->showCollection(c),"Unlocked",edit);
 }
 /** true when package number k of the run row is one of the packages chosen before the run */
 /** last mouse position in stage coordinates, from a listener on the stage (also sees the dev tool's injected moves) */
 private static final com.badlogic.gdx.math.Vector2 MOUSE=new com.badlogic.gdx.math.Vector2(-1,-1);private static com.badlogic.gdx.scenes.scene2d.Stage mouseStage;
 com.badlogic.gdx.math.Vector2 stageMouse(){
  var st=root.getStage();if(st==null)return null;
  if(mouseStage!=st){mouseStage=st;st.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){
   @Override public boolean mouseMoved(InputEvent e,float x,float y){MOUSE.set(e.getStageX(),e.getStageY());return false;}
   @Override public boolean touchDown(InputEvent e,float x,float y,int pointer,int button){if(touch())MOUSE.set(e.getStageX(),e.getStageY());return false;}});}   // touch: tooltips follow the last tap
  return MOUSE;
 }
 static boolean k0(Run r,int k){return r.chosenPackages!=null&&k<r.chosenPackages.size();}
 static int rarityOrder(String r){return switch(r==null?"":r){case "legendary"->0;case "epic"->1;case "rare"->2;default->3;};}
 static Color rewardRarityColor(String r){return switch(r==null?"":r){case "legendary"->Color.valueOf("ff9a2e");case "epic"->Color.valueOf("b46cff");case "rare"->Color.valueOf("5aa7ff");default->null;};}
 /** a package's cards with their reward rarity: coloured frame + filters, so the hard-to-get cards stand out (user 2026-10-17) */
 // ------------------------------------------------------------------ package compendium (user 2026-10-17)
 // Search a card (or package/tag) name and see every package of this commander that contains it - commander and
 // neutral packages, with their state. Tap a row for the card list, Select picks it right away.
 private String compendiumQuery="";private boolean compendiumOpen;private int compendiumPage;
 private void showCompendium(Cmd c){
  compendiumOpen=true;Profile p=profile();Group g=pushOverlay();
  float w=Math.min(W()-20,520),h=H()-16,x=(W()-w)/2,y=8;
  String q=compendiumQuery.trim().toLowerCase(Locale.ROOT);
  record Hit(Theme t,List<String> cards,boolean byName){}
  List<Hit> hits=new ArrayList<>();
  List<String> hunt=huntList(p,c);boolean huntView=q.length()<2&&!hunt.isEmpty();   // empty search + hunting list: rank packages by hunted cards
  List<String> matchCards=new ArrayList<>();   // distinct matching card names, for "+ hunt" chips
  if(q.length()>=2||huntView)for(Theme t:c.themes){
   if(t.cards==null)continue;List<String> found=new ArrayList<>();Set<String> seen=new HashSet<>();
   for(CardRef r:t.cards)if(r!=null&&r.n!=null&&(huntView?hunt.contains(r.n):r.n.toLowerCase(Locale.ROOT).contains(q))&&seen.add(r.n)){found.add(r.n);if(!huntView&&!matchCards.contains(r.n))matchCards.add(r.n);}
   if(huntView){if(!found.isEmpty())hits.add(new Hit(t,found,false));continue;}
   PackageArt art=packageArt(c,t);String label=art!=null&&art.shortName!=null?art.shortName:t.name;
   boolean byName=label.toLowerCase(Locale.ROOT).contains(q)||t.name.toLowerCase(Locale.ROOT).contains(q)||(art!=null&&art.tags!=null&&String.join(" ",art.tags).toLowerCase(Locale.ROOT).contains(q));
   if(!found.isEmpty()||byName)hits.add(new Hit(t,found,byName));}
  hits.sort(Comparator.comparingInt((Hit hh)->-hh.cards().size()));
  matchCards.sort(Comparator.comparingInt(String::length));
  float rowH=24,listTop=y+h-86;int perPage=Math.max(1,(int)((listTop-(y+30))/(rowH+3)));
  int pages=Math.max(1,(hits.size()+perPage-1)/perPage);compendiumPage=Math.max(0,Math.min(compendiumPage,pages-1));
  final int shownFrom=compendiumPage*perPage;
  g.addActor(new Painter((b,a)->{
   rect(b,0,0,W(),H(),Color.BLACK,a*.62f);rect(b,x,y,w,h,Color.valueOf("0d1220"),a*.96f);frame(b,x,y,w,h,1.2f,GOLD,a);
   text(b,"PACKAGE COMPENDIUM  ·  "+c.name.split(",")[0].toUpperCase(Locale.ROOT),x,y+h-7,9,GOLD,a,w,Align.center,true);
   // search box
   float bx=x+10,by=y+h-44,bw=w-20,bh=18;rect(b,bx,by,bw,bh,Color.valueOf("05070d"),a);frame(b,bx,by,bw,bh,.8f,Color.valueOf("8a72ff"),a);
   boolean caret=(int)(time*2)%2==0;
   text(b,compendiumQuery.isEmpty()?"Type a card name, e.g. Skullclamp, Sol Ring, Rhystic...":compendiumQuery+(caret?"|":""),bx+6,by+13,6.6f,compendiumQuery.isEmpty()?DIM:TEXT,a,bw-12,Align.left,false);
   text(b,huntView?"HUNTING LIST ("+hunt.size()+")  -  tap a card to remove it":q.length()>=2&&!matchCards.isEmpty()?"Add to your hunting list:":"",x+10,y+h-50,5.6f,GOLD,a,w-20,Align.left,false);
   if(q.length()<2&&!huntView){text(b,"Search every package of this commander - commander and neutral - for a card, a package name or a tag. Results show which packages contain it, so you can build around a strategy.",x+20,y+h/2+10,6.2f,DIM,a,w-40,Align.center,false);return;}
   if(hits.isEmpty()){text(b,"No package of "+c.name.split(",")[0]+" contains \""+compendiumQuery.trim()+"\".",x+20,y+h/2,6.6f,DIM,a,w-40,Align.center,false);return;}
   text(b,hits.size()+" package(s)",x+10,y+h-50,5.6f,DIM,a,w-20,Align.right,false);
   if(huntView)text(b,"Packages ranked by how many of your hunted cards they contain.",x+10,listTop+4,5.2f,DIM,a,w-20,Align.left,false);
   for(int i=shownFrom;i<Math.min(hits.size(),shownFrom+perPage);i++){Hit hh=hits.get(i);float ry=listTop-(i-shownFrom+1)*(rowH+3);
    Theme t=hh.t();boolean neutral=isNeutral(t),open=packageOpen(p,c,t),ex=isExcluded(p,c,t),fav=isFavorite(p,c,t),sel=packageSelection(p,c).contains(t.key);
    rect(b,x+10,ry,w-20,rowH,sel?Color.valueOf("231d4a"):Color.valueOf("141a2a"),a*.9f);frame(b,x+10,ry,w-20,rowH,.6f,sel?Color.valueOf("a59ae8"):Color.valueOf("3a4152"),a);
    PackageArt art=packageArt(c,t);String label=art!=null&&art.shortName!=null?art.shortName:t.name;
    String state=(neutral?"Neutral":"Commander")+"  ·  "+(ex?"excluded":!open?"locked":sel?"selected":fav?"favorite":neutral&&!neutralOffered(p,c,t)?"not offered this run":"available");
    text(b,label,x+14,ry+rowH-3,fitSingleLine(label,7,w*.42f,true),open&&!ex?GOLD:DIM,a,w*.42f,Align.left,true);
    text(b,state,x+14,ry+8,fitSingleLine(state,5.2f,w*.29f,false),ex||!open?Color.valueOf("ff9a9a"):Color.valueOf("a8bcef"),a,w*.29f,Align.left,false);
    if(!hunt.isEmpty()){int hc=huntCount(p,c,t),total=packageSize(t);if(hc>0)text(b,"Hunt "+hc+"/"+total,x+14+w*.30f,ry+8,5.2f,Color.valueOf("ffb43c"),a,w*.13f,Align.right,true);}
    String cards=hh.cards().isEmpty()?"(package name / tag)":String.join(", ",hh.cards().subList(0,Math.min(4,hh.cards().size())))+(hh.cards().size()>4?"  +"+(hh.cards().size()-4)+" more":"");
    text(b,cards,x+14+w*.44f,ry+rowH-6,fitSingleLine(cards,5.8f,w*.56f-100,false),TEXT,a,w*.56f-100,Align.left,false);}
  }));
  for(int i=shownFrom;i<Math.min(hits.size(),shownFrom+perPage);i++){Theme t=hits.get(i).t();float ry=listTop-(i-shownFrom+1)*(rowH+3);
   g.addActor(new Btn("Cards",x+w-86,ry+5,36,14,()->showPackageCards(c,t)));
   boolean can=packageOpen(p,c,t)&&!isExcluded(p,c,t)&&(!isNeutral(t)||neutralOffered(p,c,t));
   Btn sel=new Btn(packageSelection(p,c).contains(t.key)?"Unpick":"Pick",x+w-46,ry+5,34,14,()->{togglePackage(p,c,t);save();rebuild();});sel.disabled=!can;g.addActor(sel);}
  // chips: "+ card" adds to the hunting list (search), hunted cards remove (hunting-list view)
  List<String> chips=huntView?hunt:matchCards.subList(0,Math.min(8,matchCards.size()));float cx=x+10,cy=y+h-70;
  for(String card:chips){boolean has=hunt.contains(card);String label=(huntView?"x  ":has?"Hunted: ":"+ ")+card;float cw=Math.min(w-20,chipWidth(label,5.6f)+8);
   if(cx+cw>x+w-10){if(huntView)break;continue;}
   Btn chip=new Btn(label,cx,cy,cw,12,()->{if(hunt.contains(card))hunt.remove(card);else hunt.add(card);save();closeOverlay();compendiumOpen=false;showCompendium(c);});chip.gold=has&&!huntView;g.addActor(chip);cx+=cw+4;}
  if(pages>1){g.addActor(new Btn("<",x+w/2-70,y+8,40,16,()->{compendiumPage--;closeOverlay();compendiumOpen=false;showCompendium(c);}){{disabled=compendiumPage==0;}});
   g.addActor(new Painter((b,a)->centerText(b,(compendiumPage+1)+" / "+pages,x+w/2-26,y+8,52,16,6,DIM,a,false)));
   g.addActor(new Btn(">",x+w/2+30,y+8,40,16,()->{compendiumPage++;closeOverlay();compendiumOpen=false;showCompendium(c);}){{disabled=compendiumPage>=pages-1;}});}
  g.addActor(new Btn("Close",x+10,y+8,70,16,()->{compendiumOpen=false;closeOverlay();}));
  // keyboard: type to search, Backspace deletes, Escape clears / closes
  Actor keys=new Actor();keys.setBounds(0,0,0,0);
  keys.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){
   @Override public boolean keyTyped(InputEvent e,char ch){
    if(ch=='\b'){if(!compendiumQuery.isEmpty())compendiumQuery=compendiumQuery.substring(0,compendiumQuery.length()-1);}
    else if(ch==27){if(compendiumQuery.isEmpty()){compendiumOpen=false;closeOverlay();return true;}compendiumQuery="";}
    else if(ch>=32&&ch!=127&&compendiumQuery.length()<40)compendiumQuery+=ch;else return false;
    compendiumPage=0;closeOverlay();compendiumOpen=false;showCompendium(c);return true;}});
  g.addActor(keys);if(keys.getStage()!=null)keys.getStage().setKeyboardFocus(keys);
 }
 int huntPage;
 /** the hunting list as pictures (user 2026-10-18: "mehr intuitiv"): which packages hold each card, remove, add, reset */
 private void showHuntList(Cmd c){
  Profile p=profile();List<String> hunt=huntList(p,c);Group g=pushOverlay();
  float w=Math.min(W()-20,520),h=H()-16,x=(W()-w)/2,y=8;
  int cols=5,per=10,pages=Math.max(1,(hunt.size()+per-1)/per);huntPage=Math.max(0,Math.min(huntPage,pages-1));
  float gap=8,cw=(w-20-(cols-1)*gap)/cols,ch=Math.min(cw*1.39f,(h-84)/2-26);cw=ch/1.39f;float gx=x+(w-(cols*cw+(cols-1)*gap))/2,top=y+h-44;
  g.addActor(new Painter((b,a)->{
   rect(b,0,0,W(),H(),Color.BLACK,a*.62f);rect(b,x,y,w,h,Color.valueOf("0d1220"),a*.96f);frame(b,x,y,w,h,1.2f,GOLD,a);
   text(b,"HUNTING LIST  ·  "+c.name.split(",")[0].toUpperCase(Locale.ROOT)+"  ("+hunt.size()+")",x,y+h-7,9,GOLD,a,w,Align.center,true);
   text(b,"Combo pieces and the strongest synergy cards for this commander. Packages show Hunt 2/6 - how many of these they hold; in reward offers they carry a gold HUNTED tag.",x+14,y+h-25,5.6f,TEXT,a,w-28,Align.center,false);
   if(hunt.isEmpty())text(b,"Your hunting list is empty - add cards, paste a list with Bulk add or reset it to the suggested list.",x+20,y+h/2,6.6f,DIM,a,w-40,Align.center,false);}));
  for(int i=huntPage*per;i<Math.min(hunt.size(),huntPage*per+per);i++){final String card=hunt.get(i);int k=i-huntPage*per;
   float cx=gx+(k%cols)*(cw+gap),cy=top-(k/cols+1)*(ch+24)+18;
   g.addActor(new CardView(card,cx,cy,cw,ch));
   List<String> where=new ArrayList<>();for(Theme t:c.themes)if(t.cards!=null)for(CardRef r:t.cards)if(r!=null&&card.equals(r.n)){PackageArt art=packageArt(c,t);where.add(art!=null&&art.shortName!=null?art.shortName:t.name);break;}
   String in=where.isEmpty()?"in no package":"in "+where.get(0)+(where.size()>1?" +"+(where.size()-1):"");
   final float cardW=cw;g.addActor(new Painter((b,a)->text(b,in,cx-2,cy-2,fitSingleLine(in,4.6f,cardW+4,false),Color.valueOf("a8bcef"),a,cardW+4,Align.center,false)));
   g.addActor(new Btn("Remove",cx+cw*.05f,cy-17,cw*.9f,11,()->{hunt.remove(card);save();rebuild();showHuntList(c);}));}
  g.addActor(new Btn("Close",x+10,y+8,52,16,this::closeOverlay));
  Btn add=new Btn("+ Add cards",x+68,y+8,84,16,()->{closeOverlay();compendiumQuery="";compendiumPage=0;showCompendium(c);});add.gold=true;g.addActor(add);
  Btn bulk=new Btn("Bulk add",x+158,y+8,74,16,()->{closeOverlay();showHuntBulk(c);});bulk.gold=true;g.addActor(bulk);   // paste a list of names (user 2026-10-19)
  g.addActor(new Btn("Reset to suggested",x+238,y+8,116,16,()->{hunt.clear();hunt.addAll(huntDefaults(c));save();rebuild();showHuntList(c);}));
  if(pages>1){g.addActor(new Btn("<",x+w-96,y+8,36,16,()->{huntPage--;closeOverlay();showHuntList(c);}){{disabled=huntPage==0;}});
   g.addActor(new Btn(">",x+w-56,y+8,36,16,()->{huntPage++;closeOverlay();showHuntList(c);}){{disabled=huntPage>=pages-1;}});}
 }
 /** bulk add (user 2026-10-19: "Karten einfach reinschreiben, du erkennst welche gemeint sind und fuegst alle hinzu"):
  *  names one per line or separated by , ; - typos, partial names and decklist lines ("1x Name (SET) 12") are fine */
 private void showHuntBulk(Cmd c){
  Group g=pushOverlay();float w=Math.min(W()-20,520),h=Math.min(H()-16,300),x=(W()-w)/2,y=(H()-h)/2;
  NameField field=new NameField("",x+14,y+34,w-28,h-96);field.multiline=true;field.maxLen=6000;
  field.placeholder="Frantic Search\nMizzix's Mastery, Thousand-Year Storm\n1x Jeska's Will (CMR) 187";
  g.addActor(new Painter((b,a)->{rect(b,0,0,W(),H(),Color.BLACK,a*.62f);rect(b,x,y,w,h,Color.valueOf("0d1220"),a*.96f);frame(b,x,y,w,h,1.2f,GOLD,a);
   text(b,"BULK ADD  ·  "+c.name.split(",")[0].toUpperCase(Locale.ROOT),x,y+h-7,9,GOLD,a,w,Align.center,true);
   text(b,"Type or paste card names - one per line or separated by commas. Typos and partial names are fine: the closest card from this commander's packages is picked. "+tx("Ctrl+V pastes, Ctrl+Enter adds.","Long-press the field to paste copied text."),x+14,y+h-25,5.4f,TEXT,a,w-28,Align.center,false);}));
  g.addActor(field);
  Runnable go=()->{String txt=field.value.toString();closeOverlay();bulkAddHunt(c,txt);};field.onEnter=go;
  Btn add=new Btn("Add all",x+w-124,y+8,110,18,go);add.gold=true;g.addActor(add);
  g.addActor(new Btn("Cancel",x+14,y+8,80,18,()->{closeOverlay();showHuntList(c);}));
  if(g.getStage()!=null)g.getStage().setKeyboardFocus(field);
 }
 private void bulkAddHunt(Cmd c,String txt){
  Profile p=profile();List<String> hunt=huntList(p,c);BulkResult res=matchHuntNames(c,txt);
  int added=0;List<String> already=new ArrayList<>();
  for(String n:res.found())if(hunt.contains(n))already.add(n);else{hunt.add(n);added++;}
  save();rebuild();huntPage=Math.max(0,(hunt.size()-1)/10);showHuntList(c);
  StringBuilder sb=new StringBuilder(added==0?"No new card added.":"Added "+added+(added==1?" card":" cards")+" to the hunting list.");
  if(!res.guessed().isEmpty())sb.append("\n\nRecognised as:  ").append(String.join("   ·   ",res.guessed()));
  if(!already.isEmpty())sb.append("\n\nAlready on the list:  ").append(String.join(", ",already));
  if(!res.outside().isEmpty())sb.append("\n\nNot in "+c.name.split(",")[0]+"'s packages (never offered, not added):  ").append(String.join(", ",res.outside()));
  if(!res.unknown().isEmpty())sb.append("\n\nNot recognised:  ").append(String.join(", ",res.unknown()));
  showNotice("Bulk add",sb.toString());
 }
 private void showNotice(String title,String body){
  Group g=pushOverlay();float w=Math.min(W()-40,440),x=(W()-w)/2;
  BitmapFont f=font(false);f.getData().setScale(6f/FONT_PX);float bh=new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,body,TEXT,w-28,Align.left,true).height;
  float h=Math.min(H()-20,bh+60),y=(H()-h)/2;
  g.addActor(new Painter((b,a)->{rect(b,0,0,W(),H(),Color.BLACK,a*.55f);rect(b,x,y,w,h,Color.valueOf("0d1220"),a*.97f);frame(b,x,y,w,h,1.2f,GOLD,a);
   text(b,title.toUpperCase(Locale.ROOT),x,y+h-7,8,GOLD,a,w,Align.center,true);text(b,body,x+14,y+h-24,6f,TEXT,a,w-28,Align.left,false);}));
  Btn ok=new Btn("OK",x+w/2-40,y+8,80,18,this::closeOverlay);ok.gold=true;g.addActor(ok);
 }
 record BulkResult(List<String> found,List<String> guessed,List<String> outside,List<String> unknown){}
 record NameMatch(String name,double score){}
 static String normName(String s){
  s=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace('\u2019','\'').replace("'","");
  return s.replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();}
 /** "1x Name", "2 Name", "Name (SET) 123", "Name [SET]", "Name *F*" -> "Name" */
 static String cleanEntry(String s){
  s=s.trim().replaceAll("^\\d+\\s*[xX]?\\s+","").replaceAll("^[xX]\\d+\\s+","");
  return s.replaceAll("\\s*[\\(\\[].*$","").replaceAll("\\s+\\*[A-Za-z]+\\*.*$","").replaceAll("\\s+#.*$","").trim();}
 static int lev(String a,String b){int[] p=new int[b.length()+1],q=new int[b.length()+1];for(int j=0;j<=b.length();j++)p[j]=j;
  for(int i=1;i<=a.length();i++){q[0]=i;for(int j=1;j<=b.length();j++)q[j]=Math.min(Math.min(q[j-1]+1,p[j]+1),p[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));int[] t=p;p=q;q=t;}
  return p[b.length()];}
 /** 1 = same name; whole-name prefix and whole-word hits next; otherwise edit distance (full name, or the name's start for typos in partial names) */
 static double nameScore(String q,String n){
  if(q.isEmpty())return 0;if(q.equals(n))return 1;
  if(n.startsWith(q))return q.length()>=3?.92+.06*q.length()/n.length():0;
  if((" "+n).contains(" "+q))return q.length()>=4?.86+.06*q.length()/n.length():0;
  double full=1-lev(q,n)/(double)Math.max(q.length(),n.length());
  double pre=q.length()<n.length()?(1-lev(q,n.substring(0,q.length()))/(double)q.length())*.9:0;
  return Math.max(full,pre);}
 static NameMatch bestMatch(Map<String,String> pool,String entry){
  String q=normName(entry);String exact=pool.get(q);if(exact!=null)return new NameMatch(exact,1);
  String best=null;double bs=0;for(var e:pool.entrySet()){double sc=nameScore(q,e.getKey());if(sc>bs){bs=sc;best=e.getValue();}}
  return new NameMatch(best,bs);}
 static Map<String,String> allCardNamesNorm;
 static Map<String,String> allCardNames(){
  if(allCardNamesNorm==null){Map<String,String> m=new HashMap<>();
   try{for(PaperCard pc:FModel.getMagicDb().getCommonCards().getUniqueCards())m.putIfAbsent(normName(pc.getName()),pc.getName());}catch(Throwable t){log(t);}
   allCardNamesNorm=m;}
  return allCardNamesNorm;}
 /** splits the typed text and finds the meant card in the commander's packages; a comma inside a name ("Jace, the Mind Sculptor") is kept */
 static BulkResult matchHuntNames(Cmd c,String txt){
  Map<String,String> pool=new LinkedHashMap<>();
  for(Theme t:c.themes)if(t.cards!=null)for(CardRef r:t.cards)if(r!=null&&r.n!=null){pool.putIfAbsent(normName(r.n),r.n);int sl=r.n.indexOf(" // ");
   if(sl>0){pool.putIfAbsent(normName(r.n.substring(0,sl)),r.n);pool.putIfAbsent(normName(r.n.substring(sl+4)),r.n);}}
  List<String> found=new ArrayList<>(),guessed=new ArrayList<>(),outside=new ArrayList<>(),unknown=new ArrayList<>();
  for(String seg:txt.split("[\\n;|]+")){
   String[] parts=seg.split(",");int i=0;
   while(i<parts.length){
    String one=cleanEntry(parts[i]);if(one.isEmpty()){i++;continue;}
    String two=i+1<parts.length?cleanEntry(parts[i]+","+parts[i+1]):null;
    NameMatch m=bestMatch(pool,one),m2=two==null?null:bestMatch(pool,two);String entry=one;
    if(m2!=null&&m2.score()>=.9&&m2.score()>=m.score()){m=m2;entry=two;i+=2;}else i++;
    if(m.name()!=null&&m.score()>=.74){if(!found.contains(m.name()))found.add(m.name());if(m.score()<1)guessed.add(entry+" -> "+m.name());}
    else{String real=allCardNames().get(normName(entry));if(real!=null){if(!outside.contains(real))outside.add(real);}else unknown.add(entry);}
   }
  }
  return new BulkResult(found,guessed,outside,unknown);}
 /** hunting list per commander: cards you are looking for; packages show how many of them they hold (user 2026-10-17) */
 static List<String> huntList(Profile p,Cmd c){if(p.huntLists==null)p.huntLists=new HashMap<>();if(p.huntSeeded==null)p.huntSeeded=new HashSet<>();
  List<String> l=p.huntLists.computeIfAbsent(c.name,k->new ArrayList<>());
  if(l.isEmpty()&&!p.huntSeeded.contains(c.name)){List<String> d=huntDefaults(c);if(!d.isEmpty()){l.addAll(d);p.huntSeeded.add(c.name);}}   // once per commander (only when suggestions exist); emptied by hand it stays empty
  return l;}
 static Map<String,List<String>> huntDefaultsFile;
 /** suggested hunted cards (hunt_defaults.json: combo pieces + strong synergy for this commander) that its packages still hold */
 static List<String> huntDefaults(Cmd c){
  if(huntDefaultsFile==null){huntDefaultsFile=new HashMap<>();try{File f=new File(DIR+"hunt_defaults.json");if(f.isFile()){
   Map<String,List<String>> m=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,List<String>>>(){}.getType());if(m!=null)huntDefaultsFile.putAll(m);}}catch(Exception e){log(e);}}
  Set<String> inPkg=new HashSet<>();for(Theme t:c.themes)if(t.cards!=null)for(CardRef r:t.cards)if(r!=null)inPkg.add(r.n);
  List<String> out=new ArrayList<>();for(String n:huntDefaultsFile.getOrDefault(c.slug,List.of()))if(inPkg.contains(n)&&!Arrays.asList(c.starter).contains(n))out.add(n);
  return out;
 }
 static int huntCount(Profile p,Cmd c,Theme t){if(t.cards==null)return 0;List<String> h=huntList(p,c);if(h.isEmpty())return 0;
  Set<String> seen=new HashSet<>();for(CardRef r:t.cards)if(r!=null&&r.n!=null&&h.contains(r.n))seen.add(r.n);return seen.size();}
 static int packageSize(Theme t){if(t.cards==null)return 0;Set<String> s=new HashSet<>();for(CardRef r:t.cards)if(r!=null&&r.n!=null)s.add(r.n);return s.size();}
 private void showPackageCards(Cmd c,Theme t){
  List<Item> items=new ArrayList<>();Set<String> seen=new HashSet<>();int[] n=new int[4];
  for(CardRef r:t.cards){
   if(r==null||r.n==null||!seen.add(r.n))continue;
   Item i=new Item();i.name=r.n;i.rarity="legendary".equals(r.tier)||"epic".equals(r.tier)||"rare".equals(r.tier)?r.tier:null;
   i.tagColor=rewardRarityColor(i.rarity);i.tag=i.rarity==null?null:displayText(i.rarity.substring(0,1).toUpperCase(Locale.ROOT)+i.rarity.substring(1));
   n[rarityOrder(i.rarity)]++;items.add(i);
  }
  PackageArt art=packageArt(c,t);String name=art!=null&&art.shortName!=null&&!art.shortName.isBlank()?art.shortName:t.name;
  Profile hp=profile();browserHunt=huntList(hp,c);
  showBrowser(name+"  ·  "+c.name.split(",")[0],items,new String[]{"Alle","Legendär","Episch","Selten","Normal","Kreaturen","Zauber","Länder"},
   new String[]{"Seltenheit","Mana","Typ","Farbe","Rating","Name"},
   list->n[0]+" Legendary  ·  "+n[1]+" Epic  ·  "+n[2]+" Rare  ·  "+n[3]+" Normal   ·   shown: "+list.size()+"   ·   hunted: "+huntCount(hp,c,t),null,null);
 }
 private void showDeck(String commander,List<String> cards,String title){
  Run r=profile().run;Cmd c=cmd(commander);
  Set<String> added=new HashSet<>(r!=null&&r.added!=null?r.added:List.of());
  Set<String> heir=new HashSet<>(c==null?List.of():unlockedFor(c));
  Map<String,Integer> counts=new LinkedHashMap<>();for(String n:cards)if(!n.equals(commander))counts.merge(n,1,Integer::sum);
  List<Item> items=new ArrayList<>();
  for(var e:counts.entrySet()){
   Item i=new Item();i.name=e.getKey();i.count=e.getValue();
   if(added.contains(i.name)){i.added=true;if(r!=null&&r.lastPackage!=null&&r.lastPackage.contains(i.name)){i.tag="NEW";i.tagColor=Color.valueOf("ffd166");}}
   else if(heir.contains(i.name)){i.heirloom=true;   /* no "Heirloom" label/frame (user 2026-10-17); the Heirlooms filter still finds them */}
   items.add(i);
  }
  int lands=0;for(String n:cards)if(isLand(n))lands++;
  final int landCount=lands;
  browserStats=true;
  showBrowser(title+"  ·  "+commander.split(",")[0],items,new String[]{"Alle","Kreaturen","Zauber","Artefakte","Verzauberungen","Planeswalker","Länder","Neu im Run","Erbstücke"},
   new String[]{"Mana","Typ","Farbe","Rating","Name","Neu","Gespielt"},
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

 /** Native SoundSystem owns playback, volume, focus/pause and track disposal. */
 private static boolean bossMusicActive;private static long bossMusicAttempt;
 private static forge.sound.MusicPlaylist beforeBossMusic;
 static boolean bossMusicEncounter(Profile p){return p!=null&&p.run!=null&&"fight".equals(p.run.phase)&&p.run.duelStart>0&&p.run.round%5==0;}
 public static File bossMusicDirectory(){
  if(!bossMusicEncounter(profile))return null;
  File folder=new File(DIR+"music/"+(profile.run.round>=15?"final":"regular"));
  File[] tracks=folder.listFiles(forge.sound.SoundSystem.PLAYABLE_AUDIO);
  return tracks!=null&&tracks.length>0?folder:null;
 }
 static void updateBossMusic(){
  boolean active=bossMusicEncounter(profile);if(active==bossMusicActive)return;
  if(active){if(bossMusicAttempt==profile.run.duelStart)return;bossMusicAttempt=profile.run.duelStart;if(bossMusicDirectory()==null)return;}
  try{
   var system=forge.sound.SoundSystem.instance;
   if(active){beforeBossMusic=system.getCurrentPlaylist();bossMusicActive=true;forge.sound.MusicPlaylist.invalidateMusicPlaylist();system.setBackgroundMusic(forge.sound.MusicPlaylist.BOSS,true);if(beforeBossMusic==forge.sound.MusicPlaylist.BOSS)system.changeBackgroundTrack();}
   else{bossMusicActive=false;forge.sound.MusicPlaylist.invalidateMusicPlaylist();system.setBackgroundMusic(beforeBossMusic==null?forge.sound.MusicPlaylist.MENUS:beforeBossMusic,false);if(beforeBossMusic==forge.sound.MusicPlaylist.BOSS)system.changeBackgroundTrack();beforeBossMusic=null;}
  }catch(Throwable t){bossMusicActive=false;log(t);}
 }
 // ------------------------------------------------------------------ voice lines (voices/<slug>/<event>_*.wav|ogg|mp3)
 private static final Map<String,com.badlogic.gdx.audio.Sound> voiceCache=new HashMap<>();
 private static com.badlogic.gdx.audio.Sound currentVoice;private static long lastVoice,currentVoiceId=-1;
 /** plays a random clip for the event; own clips can simply be added to the commander's voice folder */
 /** true if this commander has at least one recorded line for the event */
 static boolean hasVoice(String commander,String event){
  Cmd c=cmd(commander);if(c==null)return false;
  File[] files=new File(DIR+"voices/"+c.slug).listFiles((d,n)->n.startsWith(event+"_")&&(n.endsWith(".wav")||n.endsWith(".ogg")||n.endsWith(".mp3")));
  return files!=null&&files.length>0;
 }
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
   float vol=voicePlayback();
   if(vol<=0)return;
   currentVoiceId=s.play(Math.min(1f,vol));currentVoice=s;lastVoice=System.currentTimeMillis();voiceEnds=lastVoice+wavMs(f);
  }catch(Throwable t){log(t);}
 }
 /** when the clip that is playing now ends (boss lines wait for it instead of cutting it off) */
 static volatile long voiceEnds;
 static void voiceLater(String commander,String event,long ms){
  com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task(){@Override public void run(){Gdx.app.postRunnable(()->voice(commander,event));}},ms/1000f);
 }
 /** voices/bosses/<id> for a boss enemy (Ghalta, Nahiri, Niv-Mizzet, Sorin, Teferi, Lathliss, Chandra, Sliver Queen, Emrakul) */
 static String bossVoiceId(String enemy){
  if(enemy==null)return null;String id=enemy.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-+|-+$","");
  return new File(DIR+"voices/bosses/"+id).isDirectory()?id:null;
 }
 private static Map<String,Map<String,List<String>>> bossLines;
 static String bossLineText(String id,String file){
  if(bossLines==null){bossLines=new HashMap<>();
   try{File f=new File(DIR+"voices/bosses/lines.json");if(f.exists()){
    Map<String,Object> all=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,Object>>(){}.getType());
    for(Map.Entry<String,Object> e:all.entrySet())if(e.getValue() instanceof Map<?,?> events){Map<String,List<String>> per=new HashMap<>();
     for(Map.Entry<?,?> x:events.entrySet())if(x.getValue() instanceof List<?> l){List<String> t=new ArrayList<>();for(Object o:l)t.add(String.valueOf(o));per.put(String.valueOf(x.getKey()),t);}
     bossLines.put(e.getKey(),per);}}}catch(Exception e){log(e);}}
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(.+)_(\\d+)\\.[A-Za-z0-9]+$").matcher(file);if(!m.matches())return null;
  List<String> l=bossLines.getOrDefault(id,Map.of()).get(m.group(1));int i=Integer.parseInt(m.group(2))-1;
  return l!=null&&i>=0&&i<l.size()?l.get(i):null;
 }
 /** a boss line (Codex, original synthetic voices; nonverbal bosses have no subtitle) */
 // waits for a line that is still playing; returns the ms until this boss line has finished, so a reply can follow
 // without cutting it off (boss clips run up to 4.7 s, the old fixed 3.2 s reply cut them, user 2026-10-17)
 static long bossVoice(String enemy,String event,long delayMs){
  String id=bossVoiceId(enemy);if(id==null)return 0;
  File[] files=new File(DIR+"voices/bosses/"+id).listFiles((d,n)->n.startsWith(event+"_")&&(n.endsWith(".wav")||n.endsWith(".ogg")||n.endsWith(".mp3")));
  if(files==null||files.length==0)return 0;File f=files[new Random().nextInt(files.length)];
  delayMs=Math.max(delayMs,voiceEnds-System.currentTimeMillis()+250);
  final long clip=wavMs(f);
  Runnable play=()->{try{
   String said=bossLineText(id,f.getName());
   if(said!=null&&!said.isBlank()){subSpeaker=enemy;subText=said;subStart=System.currentTimeMillis();subMs=Math.max(2400,Math.min(8000,wavMs(f)+900));}
   if(currentVoice!=null)currentVoice.stop();
   com.badlogic.gdx.audio.Sound snd=voiceCache.computeIfAbsent(f.getPath(),q->Gdx.audio.newSound(Gdx.files.absolute(q)));
   float vol=voicePlayback();if(vol<=0)return;
   currentVoiceId=snd.play(Math.min(1f,vol));currentVoice=snd;lastVoice=System.currentTimeMillis();voiceEnds=lastVoice+clip;
  }catch(Throwable t){log(t);}};
  if(delayMs<=0)Gdx.app.postRunnable(play);
  else com.badlogic.gdx.utils.Timer.schedule(new com.badlogic.gdx.utils.Timer.Task(){@Override public void run(){Gdx.app.postRunnable(play);}},delayMs/1000f);
  return Math.max(0,delayMs)+clip;
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
 // ------------------------------------------------------------------ standalone game (desktop shortcut "Rogue Commanders")
 /** started by the "Rogue Commanders" shortcut (-Dforge.autocontinue=rogue): behaves like its own game (user 2026-10-16) */
 /** protected static fields of forge.Forge (this scene lives in another package) */
 static Object forgeField(String name){try{java.lang.reflect.Field f=Forge.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(Throwable e){return null;}}
 public static boolean standalone(){return "rogue".equalsIgnoreCase(System.getProperty("forge.autocontinue"));}
 private static boolean coverDone,titleSet;private static long coverEnd,coverStart;private static Texture coverBg;private static Texture coverLogo;
 private static com.badlogic.gdx.graphics.g2d.SpriteBatch coverBatch;
 /** drawn at the end of every Forge.render(): hides Forge's splash (logo, version), the update prompt, the Adventure
  *  start menu while the shortcut skips them, and Forge's closing screen with the old logo */
 public static void drawStandaloneCover(){
  if(!standalone())return;
  try{
   if(!titleSet){titleSet=true;coverStart=System.currentTimeMillis();try{Gdx.graphics.setTitle("Rogue Commanders");}catch(Throwable ignored){}}
   boolean closing=forgeField("closingScreen")!=null;
   long now=System.currentTimeMillis();
   if(!coverDone&&Forge.getCurrentScene() instanceof RogueCommandersScene&&forgeField("transitionScreen")==null&&forgeField("splashScreen")==null){coverDone=true;coverEnd=now;}
   float a;
   if(closing)a=1;
   else if(!coverDone)a=now-coverStart>120_000?0:1;          // safety: never hide the game forever
   else{a=1-(now-coverEnd)/450f;if(a<=0)return;}
   int w=Gdx.graphics.getWidth(),h=Gdx.graphics.getHeight();if(w<=0||h<=0)return;
   if(coverBatch==null)coverBatch=new com.badlogic.gdx.graphics.g2d.SpriteBatch();
   if(coverBg==null){var f=Gdx.files.absolute(DIR+"backgrounds/default.jpg");if(f.exists()){coverBg=new Texture(f);coverBg.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);}}
   coverBatch.getProjectionMatrix().setToOrtho2D(0,0,w,h);
   Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
   coverBatch.begin();
   coverBatch.setColor(.02f,.02f,.03f,a);coverBatch.draw(white(),0,0,w,h);
   if(coverBg!=null){float sc=Math.max(w/(float)coverBg.getWidth(),h/(float)coverBg.getHeight()),bw=coverBg.getWidth()*sc,bh=coverBg.getHeight()*sc;
    coverBatch.setColor(.55f,.55f,.6f,a);coverBatch.draw(coverBg,(w-bw)/2,(h-bh)/2,bw,bh);}
   coverBatch.setColor(0,0,0,.45f*a);coverBatch.draw(white(),0,0,w,h);
   float size=Math.min(w,h)*.085f;
   BitmapFont title=font(true);title.getData().setScale(size/FONT_PX);
   if(coverLogo==null){var lf=Gdx.files.absolute(DIR+"logo.png");if(lf.exists()){coverLogo=new Texture(lf,true);coverLogo.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);}}
   if(coverLogo!=null){float lh=h*.36f,lw=lh*coverLogo.getWidth()/coverLogo.getHeight();if(lw>w*.85f){lw=w*.85f;lh=lw*coverLogo.getHeight()/coverLogo.getWidth();}
    coverBatch.setColor(1,1,1,a);coverBatch.draw(coverLogo,(w-lw)/2,h*.47f,lw,lh);}
   else{
   title.setColor(0,0,0,.7f*a);title.draw(coverBatch,"ROGUE COMMANDERS",3,h*.58f-3,w,com.badlogic.gdx.utils.Align.center,false);
   title.setColor(GOLD.r,GOLD.g,GOLD.b,a);title.draw(coverBatch,"ROGUE COMMANDERS",0,h*.58f,w,com.badlogic.gdx.utils.Align.center,false);}
   BitmapFont body=font(false);body.getData().setScale(size*.28f/FONT_PX);
   int dots=(int)((now/400)%4);String status=(closing?"Closing":"Loading")+".".repeat(dots);
   body.setColor(TEXT.r,TEXT.g,TEXT.b,.9f*a);body.draw(coverBatch,status,0,h*.42f,w,com.badlogic.gdx.utils.Align.center,false);
   float bw=w*.28f,bx=(w-bw)/2,by=h*.36f,bh=Math.max(3,h*.006f),t=(now%1600)/1600f;
   coverBatch.setColor(1,1,1,.12f*a);coverBatch.draw(white(),bx,by,bw,bh);
   coverBatch.setColor(GOLD.r,GOLD.g,GOLD.b,.9f*a);coverBatch.draw(white(),bx+bw*Math.max(0,t-.25f),by,bw*Math.min(.25f,Math.min(t,1-t+.25f)),bh);
   coverBatch.end();coverBatch.setColor(Color.WHITE);title.setColor(Color.WHITE);body.setColor(Color.WHITE);
   Gdx.graphics.requestRendering();
  }catch(Throwable t){log(t);}
 }
 /** bottom margin of the voice subtitle (fraction of the screen height): screens with a bottom button row lift it */
 static volatile float subLift=.025f;
 public static void drawSubtitle(){
  String text=subText,speaker=subSpeaker;if(text==null)return;
  long t=System.currentTimeMillis()-subStart;
  if(t>subMs+400){subText=null;return;}
  try{
   float a=Math.max(0,Math.min(1,Math.min(t/180f,(subMs+400-t)/400f)));
   int w=Gdx.graphics.getWidth(),h=Gdx.graphics.getHeight();if(w<=0||h<=0)return;
   if(subBatch==null)subBatch=new com.badlogic.gdx.graphics.g2d.SpriteBatch();
   subBatch.getProjectionMatrix().setToOrtho2D(0,0,w,h);
   float size=Math.max(14,Math.min(48,Math.min(w,h)*.026f)),maxW=Math.min(w*.86f,size*48);
   BitmapFont body=font(false),title=font(true);com.badlogic.gdx.graphics.g2d.GlyphLayout said=null,who=null;
   float padX=0,padY=0,gap=0,boxW=0,boxH=0;
   for(int tries=0;tries<24;tries++){
    maxW=Math.min(w*.86f,size*48);body.getData().setScale(size/FONT_PX);
    said=new com.badlogic.gdx.graphics.g2d.GlyphLayout(body,"\u201C"+text+"\u201D",new Color(TEXT.r,TEXT.g,TEXT.b,a),maxW,Align.center,true);
    float headingSize=fitSingleLine(speaker.toUpperCase(),size*.72f,maxW,true);title.getData().setScale(headingSize/FONT_PX);
    who=new com.badlogic.gdx.graphics.g2d.GlyphLayout(title,speaker.toUpperCase(),new Color(GOLD.r,GOLD.g,GOLD.b,a),maxW,Align.center,false);
    padX=size*.7f;padY=size*.4f;gap=size*.25f;boxW=Math.max(said.width,who.width)+2*padX;boxH=who.height+gap+said.height+2*padY;
    if(boxH<=h*.16f&&boxW<=w-16)break;size*=.9f;
   }
   float bx=(w-boxW)/2,by=subLift<0?h-boxH-h*.015f:h*subLift;   // subLift<0: top centre (screens whose bottom is full of buttons)
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
   if(url==null||!(url.startsWith("https://cards.scryfall.io/")||url.startsWith("https://api.scryfall.com/cards/"))||!FETCHING.add(path))return;
   Thread t=new Thread(()->{
    try{
     java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();
     c.setRequestProperty("User-Agent","ForgeRogueCommanders/1.0");c.setRequestProperty("Accept","*/*");c.setConnectTimeout(15000);c.setReadTimeout(30000);
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
 /** the real illustration of the selected commander printing (Scryfall art_crop, cached in rogue-commanders/art_crops,
  *  downloaded once in the background like enemy art); null while missing or for custom skins -> card-box fallback */
 static Texture commanderArtCrop(String commander){
  try{
   Skin sk=selectedSkin(profile(),commander);if(sk!=null&&sk.custom())return null;
   PaperCard p=commanderPaper(commander);if(p==null)return null;
   forge.card.CardEdition ed=FModel.getMagicDb().getEditions().get(p.getEdition());
   String code=(ed!=null&&ed.getScryfallCode()!=null&&!ed.getScryfallCode().isEmpty()?ed.getScryfallCode():p.getEdition()).toLowerCase(Locale.ROOT);
   String num=p.getCollectorNumber();if(num==null||num.isEmpty())return null;
   String path=DIR+"art_crops/"+code+"-"+num.replaceAll("[^A-Za-z0-9]","_")+".jpg";
   if(new File(path).exists())return fileTexture(path);
   EnemyArtFetch.fetch(path,"https://api.scryfall.com/cards/"+code+"/"+java.net.URLEncoder.encode(num,StandardCharsets.UTF_8)+"?format=image&version=art_crop");
   return null;
  }catch(Exception e){return null;}
 }
 static final Map<String,Texture> ownPortraits=new HashMap<>();static final Set<String> missingPortraits=new HashSet<>();
 /** original portrait painted for Rogue Commanders (no card art, user 2026-10-16); null until Codex delivered it */
 static Texture enemyPortrait(String enemy){
  if(enemy==null)return null;String slug=enemy.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-+|-+$","");
  if(missingPortraits.contains(slug))return null;Texture t=ownPortraits.get(slug);if(t!=null)return t;
  var f=Gdx.files.absolute(DIR+"enemy-portraits/"+slug+".png");if(!f.exists()){missingPortraits.add(slug);return null;}
  try{t=new Texture(f,true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);ownPortraits.put(slug,t);return t;}
  catch(Exception ex){log(ex);missingPortraits.add(slug);return null;}
 }
 static Texture enemyArtTexture(EnemySprite e){
  try{
   if(e==null||e.getData()==null)return null;
   Texture own=enemyPortrait(e.getData().getName());if(own!=null)return own;
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
   if(p==null)return null;Texture t=fileTexture(p);if(t!=null&&!artTrim.containsKey(t))artTrim.put(t,darkBorderTrim(p));return t;
  }catch(Exception ex){log(ex);return null;}
 }
 /** inner rectangle {x,y,w,h} without uniform dark bars (letterbox/pillarbox baked into some upscaled art files) */
 static final Map<Texture,int[]> artTrim=new HashMap<>();
 static int[] darkBorderTrim(String path){
  com.badlogic.gdx.graphics.Pixmap px=null;
  try{px=new com.badlogic.gdx.graphics.Pixmap(Gdx.files.absolute(path));int w=px.getWidth(),h=px.getHeight();
   int left=0,right=w-1,top=0,bottom=h-1;
   while(left<w/4&&darkLine(px,left,true))left++;while(right>w*3/4&&darkLine(px,right,true))right--;
   while(top<h/4&&darkLine(px,top,false))top++;while(bottom>h*3/4&&darkLine(px,bottom,false))bottom--;
   if(left<3)left=0;if(w-1-right<3)right=w-1;if(top<3)top=0;if(h-1-bottom<3)bottom=h-1;   // ignore hairline edges
   return new int[]{left,top,right-left+1,bottom-top+1};
  }catch(Exception e){return null;}finally{if(px!=null)px.dispose();}
 }
 static boolean darkLine(com.badlogic.gdx.graphics.Pixmap px,int i,boolean column){
  int n=column?px.getHeight():px.getWidth(),dark=0,samples=0;
  for(int k=0;k<n;k+=Math.max(1,n/64)){int c=column?px.getPixel(i,k):px.getPixel(k,i);
   int r=(c>>>24)&255,g=(c>>>16)&255,b=(c>>>8)&255;samples++;if(r+g+b<3*40)dark++;}
  return dark>=samples*0.95;
 }
 // ------------------------------------------------------------------ versus screen before every duel
 /** commander art (selected illustration) against the opponent, round, life and boss badge; tap or wait to fight */
 /** VS screen v2 (Codex design 2026-10-17, versus-design-20261017/HANDOFF.md; user 2026-10-18 "Versus-Screen ist noch
  *  der alte"): the opponent's room fills the screen, commander and enemy stand in the outer thirds with soft edges
  *  (the room is painted back over their borders - no boxes), round on top, a restrained VS, names + live stats low
  *  left/right, one short opponent cue and a FIGHT button. Tap anywhere still starts the fight. */
 private void showVersus(Run r,EnemySprite enemy,boolean boss,Runnable fight){
  Group g=pushOverlay();
  float W=W(),H=H();long start=System.currentTimeMillis();boolean[] done={false};
  final Texture foeArt=enemyArtTexture(enemy);   // full artwork (upscaled) instead of the small sprite avatar
  TextureRegion foe=null;try{foe=enemy.getAvatar();}catch(Exception ignored){}
  final TextureRegion enemyAvatar=foe;List<String> startCards=boons(r);
  Cmd c=cmd(r.commander);String colors=c==null?"":c.colors;
  OpponentSpec spec=opponentSpec(r);final Texture room=spec==null||spec.room==null?null:roomTexture(spec.room);
  Runnable go=()->{if(done[0])return;done[0]=true;closeOverlay();fight.run();};
  final Tip tip=randomTip();
  String foeName=enemy.getName();String[] cue=choiceLine(spec!=null&&spec.enemy!=null?spec.enemy:foeName);
  Color silver=Color.valueOf("dfe3ee"),navy=Color.valueOf("0a0f1c"),violet=Color.valueOf("8a72ff");
  g.addActor(new Painter((b,a)->{
   float t=(System.currentTimeMillis()-start)/1000f;
   float in=Math.min(1,t/.45f),ease=1-(1-in)*(1-in)*(1-in);
   // 1. the opponent's room, slowly drifting (gentle ambient movement)
   float BX=-W*.04f+(float)Math.sin(t*.12f)*W*.015f,BY=-H*.04f,BW=W*1.08f,BH=H*1.08f;
   if(room!=null)drawCoverAt(b,room,BX,BY,BW,BH,a);else rect(b,0,0,W,H,DARK,a);
   rect(b,0,0,W,H,navy,a*.18f);
   // 2. the two figures in the outer thirds, borders melted back into the room
   float fw=W*.40f,fh=H*.86f,fy=H*.04f;
   float lx=-(1-ease)*(fw*.6f),rx=W-fw+(1-ease)*(fw*.6f);
   Texture artCrop=commanderArtCrop(r.commander),tex=artCrop!=null?artCrop:cardTexture(r.commander);
   Skin skin=selectedSkin(profile(),r.commander);boolean customSkin=artCrop==null&&skin!=null&&skin.custom();
   if(tex!=null){int[] crop;
    if(artCrop!=null)crop=artSourceBounds(tex.getWidth(),tex.getHeight(),false,fw,fh);
    else if(customSkin){int th=Math.round(tex.getHeight()*.48f),ty=Math.round(tex.getHeight()*.07f);crop=artSourceBounds(tex.getWidth(),th,false,fw,fh);crop[1]+=ty;}
    else crop=artSourceBounds(tex.getWidth(),tex.getHeight(),true,fw,fh);
    Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(tex,lx,fy,fw,fh,crop[0],crop[1],crop[2],crop[3],false,false);b.setColor(o);
    if(room!=null)melt(b,room,BX,BY,BW,BH,lx,fy,fw,fh,false,a);}
   if(foeArt!=null){Color o=b.getColor().cpy();b.setColor(1,1,1,a);
    int[] tr=artTrim.get(foeArt);if(tr==null)tr=new int[]{0,0,foeArt.getWidth(),foeArt.getHeight()};
    int[] crop=artSourceBounds(tr[2],tr[3],tr[3]>tr[2]*1.1f,fw,fh);crop[0]+=tr[0];crop[1]+=tr[1];
    b.draw(foeArt,rx,fy,fw,fh,crop[0],crop[1],crop[2],crop[3],false,false);b.setColor(o);
    if(room!=null)melt(b,room,BX,BY,BW,BH,rx,fy,fw,fh,true,a);}
   else if(enemyAvatar!=null){float side=Math.min(fw,fh)*.8f;Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(enemyAvatar,rx+(fw-side)/2,fy+(fh-side)/2,side,side);b.setColor(o);
    if(room!=null)melt(b,room,BX,BY,BW,BH,rx+(fw-side)/2,fy+(fh-side)/2,side,side,true,a);}
   // foreground mist + readable navy at the bottom and the top
   int n=40;for(int k=0;k<n;k++){float f=1-k/(float)n;rect(b,0,0,W,H*.42f*f,navy,a*.042f);rect(b,0,H-H*.16f*f,W,H*.16f*f,navy,a*.02f);}
   // 3. round on top with thin rules
   String header=(boss?"BOSS  ·  ":"")+"ROUND "+r.round+" / "+runLimit(r)+(bestOfThree(r)?"  ·  BEST OF 3":"");
   float hy=H-9;text(b,header,0,hy,8,boss?RED:silver,a*ease,W,Align.center,true);
   float hw=chipWidth(header,8)*1.15f;line(b,W/2-hw/2-70,hy-4.5f,W/2-hw/2-8,hy-4.5f,.7f,silver,a*.55f*ease,false);line(b,W/2+hw/2+8,hy-4.5f,W/2+hw/2+70,hy-4.5f,.7f,silver,a*.55f*ease,false);
   if(tip!=null){   // tips from real players stay on every VS screen (user 2026-10-17) - compact under the round
    float ta=a*Math.max(0,Math.min(1,(t-.6f)/.5f)),tw=Math.min(W*.5f,250),tx=(W-tw)/2;
    text(b,"\u201c"+tip.tip+"\u201d  - "+tip.name,tx,hy-15,5.3f,Color.valueOf("c9cfdd"),ta*.95f,tw,Align.center,false);}
   // 4. restrained VS with a violet slash
   float vs=Math.min(1,Math.max(0,(t-.3f)/.35f)),size=Math.min(24,W*.05f)*(1.15f-.15f*vs),vy=H*.55f;
   line(b,W/2-size*.35f,vy-size*1.05f,W/2+size*.35f,vy+size*.55f,1.1f,violet,a*vs*.8f,false);
   text(b,"VS",0,vy+size*.35f-.8f,size,Color.BLACK,a*vs*.55f,W+2,Align.center,true);
   text(b,"VS",0,vy+size*.35f,size,silver,a*vs,W,Align.center,true);
   // 5. names and live stats, low left / right
   String myName=r.commander.split(",")[0];float nw=W*.36f,ny=H*.33f;
   text(b,myName,W*.04f,ny,fitSingleLine(myName,13,nw,true),silver,a,nw,Align.left,true);
   line(b,W*.04f,ny-17,W*.04f+nw*.8f,ny-17,.7f,silver,a*.5f,false);
   text(b,wins(r.wins)+"   ·   Glory "+r.ruhm+lifeTag(r),W*.04f,ny-21,6.4f,TEXT,a,nw,Align.left,false);
   if(!colors.isEmpty())pips(b,colors,W*.04f+2,ny-40,4.6f,a);
   float ex=W*.96f-nw;
   text(b,foeName,ex,ny,fitSingleLine(foeName,13,nw,true),boss?RED:silver,a,nw,Align.right,true);
   line(b,ex+nw*.2f,ny-17,ex+nw,ny-17,.7f,boss?RED:silver,a*.5f,false);
   String label=spec==null?"":spec.label;
   String stats=(label.isEmpty()?"":label+"   ·   ")+"Life "+enemyStartingLife(r)+"   ·   Hand "+opponentStartCards(r);
   text(b,stats,ex,ny-21,Math.min(6.4f,fitSingleLine(stats,6.4f,nw,false)),TEXT,a,nw,Align.right,false);
   float sy=ny-33;
   if(!startCards.isEmpty()){String sc="Starts with: "+String.join(", ",startCards);text(b,sc,ex,sy,Math.min(5.6f,fitSingleLine(sc,5.6f,nw,false)),Color.valueOf("ffb43c"),a,nw,Align.right,false);sy-=11;}
   String foeColors=specColors(spec);
   if(!foeColors.isEmpty())pips(b,foeColors,ex+nw-foeColors.length()*(4.6f*2+2.5f)+2.5f-2,sy-7,4.6f,a);
   // 6. one short opponent cue above FIGHT (animals: nonverbal, no quotes)
   if(cue!=null){boolean speech=!"nonverbal".equals(cue[1]);float ca=a*Math.max(0,Math.min(1,(t-.4f)/.5f)),cw=Math.min(W*.2f,150);   /* narrower: clear of the stats lines left and right */
    text(b,speech?"\u201c"+cue[0]+"\u201d":cue[0],(W-cw)/2,H*.29f,5.8f,Color.valueOf("e9e3d4"),ca,cw,Align.center,false);}
   if(t>(tip!=null?6.5f:3.2f))Gdx.app.postRunnable(go);   // a little longer with a tip, so it can be read (tap skips)
  }));
  Actor tap=new Actor();tap.setBounds(0,0,W,H);
  tap.addListener(new ClickListener(){@Override public void clicked(InputEvent e,float x,float y){if(System.currentTimeMillis()-start>500)go.run();}});
  g.addActor(tap);
  Btn fightBtn=new Btn("FIGHT",(W-112)/2,H*.11f,112,22,()->{if(System.currentTimeMillis()-start>300)go.run();});fightBtn.gold=true;g.addActor(fightBtn);
  {String specific=boss?"boss_"+opponentSpec(r).id:null;voice(r.commander,specific!=null&&hasVoice(r.commander,specific)?specific:boss?"boss":"battle");
   if(boss)bossVoice(r.opponent,"battle",3400);}
 }
 /** drawCover into an arbitrary rectangle (the VS background is a little larger than the screen and drifts) */
 static void drawCoverAt(Batch b,Texture t,float x,float y,float w,float h,float a){drawCover(b,t,x,y,w,h,a);}
 /** paint the cover-mapped background (bx,by,bw,bh) back over the borders of a figure at (x,y,w,h), in strips of rising
  *  opacity: soft edges without masks or shaders. inner edge = towards the screen centre (wide), top and bottom too. */
 static void melt(Batch b,Texture bg,float bx,float by,float bw,float bh,float x,float y,float w,float h,boolean onRight,float a){
  int n=40;float inner=w*.34f,outer=w*.05f,top=h*.16f,bottom=h*.30f;
  for(int i=0;i<n;i++){float f0=i/(float)n,f1=(i+1f)/n,al=a*(float)Math.pow(1-f0,1.6f);   // strip 0 at the edge = opaque background
   // inner side
   float iw=inner/n,ix=onRight?x+inner*f0:x+w-inner*f1;coverPart(b,bg,bx,by,bw,bh,ix,y,iw,h,al);
   // outer side
   float ow=outer/n,ox=onRight?x+w-outer*f1:x+outer*f0;coverPart(b,bg,bx,by,bw,bh,ox,y,ow,h,al);
   // top and bottom
   float th=top/n;coverPart(b,bg,bx,by,bw,bh,x,y+h-top*f1,w,th,al);
   float bh2=bottom/n;coverPart(b,bg,bx,by,bw,bh,x,y+bottom*f0,w,bh2,al);
  }
 }
 /** the part of a cover-mapped background (drawn with drawCover into bx,by,bw,bh) that lies under a screen rectangle */
 static void coverPart(Batch b,Texture t,float bx,float by,float bw,float bh,float rx,float ry,float rw,float rh,float a){
  if(rw<=0||rh<=0||a<=.003f)return;
  // the same integer source window as drawCover, then exact float texture coordinates (no 1-texel seams)
  float ta=t.getWidth()/(float)t.getHeight(),sa=bw/bh;int sw=t.getWidth(),sh=t.getHeight(),sx=0,sy=0;
  if(ta>sa){sw=Math.round(t.getHeight()*sa);sx=(t.getWidth()-sw)/2;}else{sh=Math.round(t.getWidth()/sa);sy=(t.getHeight()-sh)/2;}
  float kx=sw/bw,ky=sh/bh,u=(sx+(rx-bx)*kx)/t.getWidth(),u2=(sx+(rx+rw-bx)*kx)/t.getWidth();
  float v=(sy+(by+bh-(ry+rh))*ky)/t.getHeight(),v2=(sy+(by+bh-ry)*ky)/t.getHeight();
  Color o=b.getColor().cpy();b.setColor(1,1,1,a);b.draw(t,rx,ry,rw,rh,u,v2,u2,v);b.setColor(o);
 }

 // ------------------------------------------------------------------ big card preview
 private void preview(String name){preview(name,null);}
 /** big preview of a card in a grid; arrow keys step through the other cards on the same page (user 2026-10-17) */
 // ------------------------------------------------------------------ double-faced cards in the big preview (user 2026-10-18:
 // "kann solche Karten in der Deckansicht nicht flippen"): Flip button / F key shows the other face (Forge: getImageKey(true))
 static PaperCard previewPaper(String name,PaperCard illustration){return illustration!=null?illustration:paper(name);}
 static boolean hasBack(String name,PaperCard illustration){PaperCard p=previewPaper(name,illustration);return p!=null&&p.hasBackFace();}
 static Texture backTexture(PaperCard p){
  if(p==null)return null;String key=p.getImageKey(true);
  Texture sh=sharpTexture(key,()->{try{File f=forge.ImageKeys.getImageFile(forge.util.ImageUtil.getImageKey(p,"back",true));if(f!=null&&f.exists())return f;}catch(Throwable ignored){}
   try{return forge.ImageKeys.getImageFile(key);}catch(Throwable t){return null;}});
  return sh!=null?sh:textureForKey(key);
 }
 /** the big preview picture: front, or the back face / the upside-down half of a flip card */
 static void drawPreviewFace(Batch b,String name,PaperCard illustration,boolean back,float x,float y,float cw,float ch,float a){
  PaperCard p=previewPaper(name,illustration);
  boolean flipCard=back&&p!=null&&p.getRules().getSplitType()==forge.card.CardSplitType.Flip;
  Texture t=back&&!flipCard?backTexture(p):illustration==null?cardTexture(name):cardTexture(illustration);
  if(t==null){rect(b,x,y,cw,ch,Color.valueOf("1c1a22"),a);text(b,back?name+" (back)":name,x+6,y+ch/2+10,10,TEXT,a,cw-12,Align.center,true);return;}
  Color o=b.getColor().cpy();b.setColor(1,1,1,a);
  if(flipCard)b.draw(t,x,y,cw,ch,0,0,t.getWidth(),t.getHeight(),true,true);   // flip cards: the other half is the picture upside down
  else b.draw(t,x,y,cw,ch);
  b.setColor(o);
 }
 private void preview(CardView from){
  List<CardView> page=new ArrayList<>();
  if(from.getParent()!=null)for(Actor x:from.getParent().getChildren())if(x instanceof CardView v&&v.name!=null&&!v.mystery&&v.action==null)page.add(v);
  page.sort(Comparator.comparingInt((CardView v)->v.group).thenComparingDouble(v->-Math.round(v.getY())).thenComparingDouble(Actor::getX));   // package by package, then row by row
  if(page.size()<2){preview(from.name,from.illustration,from.stats);return;}
  int[] at={Math.max(0,page.indexOf(from))};boolean[] back={false};
  Group g=pushOverlay();
  float ch=H()-30,cw=ch*.716f;
  g.addActor(new Painter((b,a)->{CardView v=page.get(at[0]);rect(b,0,0,W(),H(),Color.BLACK,a*.78f);
   float x=(W()-cw)/2,y=15;drawPreviewFace(b,v.name,v.illustration,back[0],x,y,cw,ch,a);if(v.stats)drawCardHistory(b,v.name,x+cw+34,y+ch-4,W()-(x+cw+34)-10,a);
   if(v.hunt!=null&&v.hunt.contains(v.name)){Color hc=Color.valueOf("ffb43c");frame(b,x-3,y-3,cw+6,ch+6,2f,hc,a);text(b,"HUNTED",x+cw+34,y+ch-4,9,hc,a,120,Align.left,true);}
   drawOracle(b,v.name,back[0],10,y+ch,x-44,a);   // English rules text (tester 2026-10-19)
   boolean dfc=hasBack(v.name,v.illustration);
   text(b,(v.group>=0?"Package "+(v.group+1)+"   ·   ":"")+(at[0]+1)+" / "+page.size()+tx("   ·   < > buttons, arrow keys or mouse wheel: previous / next","   ·   < > buttons or swipe: previous / next")+(dfc&&!touch()?"   ·   F: flip":"")+(v.hunt!=null&&!touch()?"   ·   H: hunt":"")+tx("   ·   click anywhere else to close","   ·   tap next to the card to close"),0,10,5.5f,DIM,a,W(),Align.center,false);}));
  Actor catcher=new Actor();catcher.setBounds(0,0,W(),H());
  // paging only with the arrow buttons / keys / wheel - a click next to the card closes like everywhere else (tester 2026-10-19)
  catcher.addListener(new ClickListener(){@Override public void clicked(InputEvent e,float x,float y){closeOverlay();}});
  catcher.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){
   @Override public boolean scrolled(InputEvent e,float x,float y,float ax,float ay){if(ay>0&&at[0]<page.size()-1){at[0]++;back[0]=false;}else if(ay<0&&at[0]>0){at[0]--;back[0]=false;}return true;}
   @Override public boolean mouseMoved(InputEvent e,float x,float y){if(e.getStage()!=null)e.getStage().setScrollFocus(catcher);return false;}
   @Override public boolean keyDown(InputEvent e,int key){
   if(key==com.badlogic.gdx.Input.Keys.RIGHT||key==com.badlogic.gdx.Input.Keys.DOWN){if(at[0]<page.size()-1){at[0]++;back[0]=false;}return true;}
   if(key==com.badlogic.gdx.Input.Keys.LEFT||key==com.badlogic.gdx.Input.Keys.UP){if(at[0]>0){at[0]--;back[0]=false;}return true;}
   if(key==com.badlogic.gdx.Input.Keys.F){CardView v=page.get(at[0]);if(hasBack(v.name,v.illustration))back[0]=!back[0];return true;}
   if(key==com.badlogic.gdx.Input.Keys.H){CardView v=page.get(at[0]);if(v.hunt!=null){toggleHunt(v);return true;}}
   if((key==com.badlogic.gdx.Input.Keys.ESCAPE||key==com.badlogic.gdx.Input.Keys.BACK)||key==com.badlogic.gdx.Input.Keys.ENTER||key==com.badlogic.gdx.Input.Keys.SPACE){closeOverlay();return true;}
   return false;}});
  if(touch())catcher.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener(){   // touch: swipe pages
   @Override public void fling(InputEvent e,float vx,float vy,int button){if(Math.abs(vx)<Math.abs(vy)||Math.abs(vx)<300)return;
    if(vx<0&&at[0]<page.size()-1){at[0]++;back[0]=false;}else if(vx>0&&at[0]>0){at[0]--;back[0]=false;}}});
  g.addActor(catcher);
  if(touch()){float bx=(W()+cw)/2+8,by=46;   // touch: Hunt button instead of the H key
   Btn hb=new Btn("Hunt",bx,by,64,18,()->{CardView v=page.get(at[0]);if(v.hunt!=null)toggleHunt(v);}){
    @Override public void draw(Batch b,float a){CardView v=page.get(at[0]);if(v.hunt!=null)super.draw(b,a);}
    @Override public Actor hit(float x,float y,boolean touchable){return page.get(at[0]).hunt!=null?super.hit(x,y,touchable):null;}};
   g.addActor(hb);}
  {float bx=(W()+cw)/2+8,by=24;   // Flip button next to the card, only for double-faced cards
   Btn flip=new Btn(tx("Flip  (F)","Flip"),bx,by,64,18,()->{CardView v=page.get(at[0]);if(hasBack(v.name,v.illustration))back[0]=!back[0];}){
    @Override public void draw(Batch b,float a){CardView v=page.get(at[0]);if(hasBack(v.name,v.illustration))super.draw(b,a);}
    @Override public Actor hit(float x,float y,boolean touchable){CardView v=page.get(at[0]);return hasBack(v.name,v.illustration)?super.hit(x,y,touchable):null;}};
   flip.gold=true;g.addActor(flip);}
  {float ax=(W()-cw)/2,ay=15+ch/2-22;   // the arrows as real buttons
   g.addActor(new Btn("<",ax-32,ay,26,44,()->{if(at[0]>0){at[0]--;back[0]=false;}}){@Override public void draw(Batch b,float a){disabled=at[0]==0;super.draw(b,a);}});
   g.addActor(new Btn(">",ax+cw+6,ay,26,44,()->{if(at[0]<page.size()-1){at[0]++;back[0]=false;}}){@Override public void draw(Batch b,float a){disabled=at[0]>=page.size()-1;super.draw(b,a);}});}
  if(catcher.getStage()!=null){catcher.getStage().setKeyboardFocus(catcher);catcher.getStage().setScrollFocus(catcher);}
 }
 static float wrappedHeight(String s,float size,float width,boolean title){BitmapFont f=font(title);f.getData().setScale(size/FONT_PX);return new com.badlogic.gdx.graphics.g2d.GlyphLayout(f,s,TEXT,width,Align.left,true).height;}
 /** the card's English rules text left of the big preview: readable even for old or foreign pictures (tester 2026-10-19) */
 void drawOracle(Batch b,String name,boolean back,float x,float top,float w,float a){
  if(w<70||name==null)return;PaperCard pc=paper(name);if(pc==null)return;
  w=Math.min(w,200);
  forge.card.ICardFace f=back&&pc.getRules().getOtherPart()!=null?pc.getRules().getOtherPart():pc.getRules().getMainPart();if(f==null)return;
  String cost=f.getManaCost()==null||f.getManaCost().isNoCost()?"":f.getManaCost().toString();
  String type=f.getType()==null?"":f.getType().toString();
  String oracle=f.getOracleText()==null?"":f.getOracleText().replace("\\n","\n").replace("\r","").trim();
  String pt=f.getPower()!=null&&!f.getPower().isEmpty()?f.getPower()+" / "+f.getToughness():f.getInitialLoyalty()!=null&&!f.getInitialLoyalty().isEmpty()?"Loyalty "+f.getInitialLoyalty():"";
  float iw=w-16,hName=wrappedHeight(f.getName(),7.4f,iw,true),hType=wrappedHeight(type+(cost.isEmpty()?"":"   "+cost),5.4f,iw,false),hText=oracle.isEmpty()?0:wrappedHeight(oracle,5.8f,iw,false);
  float h=Math.min(top-8,10+hName+4+hType+(hText>0?7+hText:0)+(pt.isEmpty()?0:12)+8),py=top-h;
  rect(b,x,py,w,h,Color.valueOf("0d1220"),a*.92f);frame(b,x,py,w,h,1f,GOLD,a*.8f);
  float yy=top-6;text(b,f.getName(),x+8,yy,7.4f,GOLD,a,iw,Align.left,true);yy-=hName+4;
  text(b,type+(cost.isEmpty()?"":"   "+cost),x+8,yy,5.4f,Color.valueOf("a8bcef"),a,iw,Align.left,false);yy-=hType+7;
  if(hText>0){text(b,oracle,x+8,yy,5.8f,TEXT,a,iw,Align.left,false);yy-=hText+6;}
  if(!pt.isEmpty())text(b,pt,x+8,Math.max(py+12,yy),6.4f,GOLD,a,iw,Align.left,true);
 }
 private void preview(String name,PaperCard illustration){preview(name,illustration,false);}
 private void preview(String name,PaperCard illustration,boolean stats){
  Group g=pushOverlay();
  float ch=H()-30,cw=ch*.716f;
  boolean[] back={false};boolean dfc=hasBack(name,illustration);
  g.addActor(new Painter((b,a)->{rect(b,0,0,W(),H(),Color.BLACK,a*.78f);float x=(W()-cw)/2,y=15;
   drawPreviewFace(b,name,illustration,back[0],x,y,cw,ch,a);if(stats)drawCardHistory(b,name,x+cw+34,y+ch-4,W()-(x+cw+34)-10,a);drawOracle(b,name,back[0],10,y+ch,x-44,a);
   text(b,dfc?"F: flip   ·   tap to close":"Tap to close",0,10,5.5f,DIM,a,W(),Align.center,false);}));
  Actor catcher=new Actor();catcher.setBounds(0,0,W(),H());
  catcher.addListener(new ClickListener(){@Override public void clicked(InputEvent e,float x,float y){closeOverlay();}});
  catcher.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener(){@Override public boolean keyDown(InputEvent e,int key){
   if(key==com.badlogic.gdx.Input.Keys.F&&dfc){back[0]=!back[0];return true;}
   if((key==com.badlogic.gdx.Input.Keys.ESCAPE||key==com.badlogic.gdx.Input.Keys.BACK)||key==com.badlogic.gdx.Input.Keys.ENTER||key==com.badlogic.gdx.Input.Keys.SPACE){closeOverlay();return true;}
   return false;}});
  g.addActor(catcher);
  if(dfc){Btn flip=new Btn(tx("Flip  (F)","Flip"),(W()+cw)/2+8,24,64,18,()->back[0]=!back[0]);flip.gold=true;g.addActor(flip);}
  if(catcher.getStage()!=null)catcher.getStage().setKeyboardFocus(catcher);
 }
 /** the card's history over all runs next to the big preview in the deck views (user 2026-10-19) */
 private void drawCardHistory(Batch b,String name,float x,float top,float w,float a){
  if(w<60)return;CardStat s=cardStatOf(profile(),name);w=Math.min(w,190);
  String[] rows={"Taken from rewards:  "+s.taken+"x","In your deck:  "+s.runs+(s.runs==1?" run":" runs")+"  ·  "+s.games+(s.games==1?" duel":" duels"),
   "Played:  "+s.played+"x"+(s.games>0?"  ("+String.format(Locale.ROOT,"%.1f",s.played/(double)s.games)+" per duel)":""),
   "Won with it:  "+s.wins+" of "+s.games+(s.games>0?"  ("+Math.round(100.0*s.wins/s.games)+" %)":"")};
  String foot=s.games==0&&s.taken==0?"Nothing yet - counted from this update on.":"Over all runs, counted since this update.";
  float rh=11,h=24+rows.length*rh+14;
  rect(b,x,top-h,w,h,Color.valueOf("0d1220"),a*.92f);frame(b,x,top-h,w,h,1f,GOLD,a*.8f);
  text(b,"YOUR HISTORY",x+8,top-6,7,GOLD,a,w-16,Align.left,true);
  for(int i=0;i<rows.length;i++)text(b,rows[i],x+8,top-22-i*rh,fitSingleLine(rows[i],5.6f,w-16,false),TEXT,a,w-16,Align.left,false);
  text(b,foot,x+8,top-h+11,fitSingleLine(foot,4.8f,w-16,false),DIM,a,w-16,Align.left,false);
 }
 /** H in a package's card view / big preview: the card onto / off the hunting list (user 2026-10-19: keybind instead of a button) */
 private void toggleHunt(CardView v){
  if(v==null||v.hunt==null||v.name==null||isBasic(v.name))return;
  boolean now=!v.hunt.remove(v.name);if(now)v.hunt.add(v.name);v.huntMark=now;save();
  toast(now?"Hunting "+v.name+"  (H again removes it)":v.name+" is no longer hunted");
  if(v.onHuntChange!=null)v.onHuntChange.run();
 }
 private CardView cardUnderMouse(Group g){
  var st=g.getStage();var m=stageMouse();if(st==null||m==null||m.x<0)return null;
  Actor a=st.hit(m.x,m.y,true);while(a!=null&&!(a instanceof CardView))a=a.getParent();return (CardView)a;
 }
 /** overlays that take the keyboard back when the overlay above them closes (H in the package card view) */
 private final Set<Group> focusOnReturn=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
 private final Map<Group,Runnable> overlayClosed=new HashMap<>();
 private void closeOverlay(){
  if(overlays.isEmpty())return;
  Group g=overlays.remove(overlays.size()-1);g.remove();
  if(!overlays.isEmpty()){Group top=overlays.get(overlays.size()-1);var st=top.getStage();if(st!=null&&focusOnReturn.contains(top)&&(st.getKeyboardFocus()==null||st.getKeyboardFocus().getStage()==null))st.setKeyboardFocus(top);}
  Runnable done=overlayClosed.remove(g);if(done!=null)try{done.run();}catch(Throwable t){log(t);}
 }
}
