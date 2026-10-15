package forge.adventure.scene;

import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import com.github.tommyettinger.textra.TypingLabel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import forge.Forge;
import forge.adventure.character.EnemySprite;
import forge.adventure.data.AdventureEventData;
import forge.adventure.data.EnemyData;
import forge.adventure.data.RewardData;
import forge.adventure.data.WorldData;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.stage.IAfterMatch;
import forge.adventure.stage.WorldStage;
import forge.adventure.util.AdventureEventController;
import forge.adventure.util.Controls;
import forge.adventure.util.Current;
import forge.adventure.util.Reward;
import forge.adventure.world.WorldSave;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.gui.FThreads;
import forge.screens.TransitionScreen;
import forge.util.ScreenUtil;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;

/**
 * Dungeon Run (inspired by Hearthstone): replaces the Draft/Sealed/Jumpstart events of every inn.
 * Pick one of three themed commanders, then fight six opponents of rising difficulty (round 6 is a mini boss); one loss
 * ends the run. After every win one of three themed 8-card packages upgrades the deck (its cards replace starter
 * cards, so the deck stays a legal 59+1 commander deck). Rewards: 100 gold per win; 5+ wins add the arena prize
 * (an item and three mythic rares); 6 wins add two boosters and three rares.
 * Content: _mods/dungeon-run/content.json (built by content.py). State: one JSON per character in the save folder.
 */
public class DungeonRunScene extends MenuScene implements IAfterMatch {
 static final String CONTENT="D:/forge/_mods/dungeon-run/content.json";
 static final int ROUNDS=6,ENTRY_GOLD=200,LIFE=30;
 static final String[] ARENA_ITEMS={"Gold Armor","Gold Shield","Gold Boots","Bronze Sword"};
 static final Set<String> TOO_BIG=Set.of("Emrakul","Kozilek","Elesh Norn","Jin-Gitaxias","Urabrask","Vorinclex","Valyx Feaster of Torment","Chandra","Liliana","Nissa","Sorin");

 // ------------------------------------------------------------------ content and state
 static class Pack{String name,theme;String[] cards;}
 static class Commander{String name,theme,colors,blurb;String[] lands,starter;Pack[] packages;}
 static class Content{int version;Commander[] commanders;}
 static class State{
  String phase="none";   // none, commander, fight, package, done
  long seed;String[] commanderOptions=new String[0];String commander;
  List<String> deck=new ArrayList<>(),starterLeft=new ArrayList<>(),picked=new ArrayList<>(),pickedThemes=new ArrayList<>();
  String[] packageOptions=new String[0];String[] opponents=new String[0];
  int round=1,wins;boolean lost;Map<String,Long> cooldown=new HashMap<>();
 }
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static Content content;private static State state;private static String stateOwner;

 static Content content(){
  if(content==null){try{content=GSON.fromJson(Files.readString(new File(CONTENT).toPath(),StandardCharsets.UTF_8),Content.class);}catch(Exception e){System.err.println("[DungeonRun] content: "+e);content=new Content();content.commanders=new Commander[0];}}
  return content;
 }
 static Commander commander(String name){for(Commander c:content().commanders)if(c.name.equals(name))return c;return null;}
 private static File stateFile(){
  String who=Current.player()==null?"player":Current.player().getName();
  return new File(WorldSave.getSaveDir(),"dungeonrun_"+who.replaceAll("[^A-Za-z0-9_-]","_")+".json");
 }
 static State state(){
  String who=Current.player()==null?"":Current.player().getName();
  if(state==null||!who.equals(stateOwner)){
   stateOwner=who;state=new State();
   try{File f=stateFile();if(f.exists())state=GSON.fromJson(Files.readString(f.toPath(),StandardCharsets.UTF_8),State.class);}catch(Exception e){System.err.println("[DungeonRun] state: "+e);state=new State();}
   if(state.cooldown==null)state.cooldown=new HashMap<>();
  }
  return state;
 }
 static void save(){try{File f=stateFile();f.getParentFile().mkdirs();Files.writeString(f.toPath(),GSON.toJson(state),StandardCharsets.UTF_8);}catch(Exception e){System.err.println("[DungeonRun] save: "+e);}}

 // ------------------------------------------------------------------ inn integration (InnScene is patched to call these)
 private static String innPoint="";private static PointOfInterestChanges innChanges;private static Scene innLast;
 /** InnScene.startEvent() */
 public static void openFromInn(InnScene inn){
  try{Field f=InnScene.class.getDeclaredField("localPointOfInterestId");f.setAccessible(true);innPoint=String.valueOf(f.get(null));}catch(ReflectiveOperationException e){innPoint="";}
  innChanges=InnScene.changes;innLast=inn;
  Forge.switchScene(instance(),true);
 }
 /** end of InnScene.refreshStatus(): describe the Dungeon Run instead of the old events */
 public static void innStatus(InnScene inn){
  try{
   Field f=InnScene.class.getDeclaredField("eventDescription");f.setAccessible(true);TextraLabel label=(TextraLabel)f.get(inn);
   State s=state();String text;
   switch(s.phase){
    case "commander"->text="Dungeon Run [GREEN]Commander wählen";
    case "fight"->text="Dungeon Run [GREEN]Runde "+s.round+"/"+ROUNDS;
    case "package"->text="Dungeon Run [GREEN]Paket wählen";
    case "done"->text="Dungeon Run [GREEN]Belohnung abholen";
    default->text=onCooldown(currentPoint())?"Dungeon Run [GREY]morgen wieder":"Dungeon Run verfügbar";
   }
   if(label!=null)label.setText(text);
   if(inn.event!=null)inn.event.setDisabled(false);
  }catch(ReflectiveOperationException e){System.err.println("[DungeonRun] inn: "+e);}
 }
 private static String currentPoint(){
  try{Field f=InnScene.class.getDeclaredField("localPointOfInterestId");f.setAccessible(true);return String.valueOf(f.get(null));}catch(ReflectiveOperationException e){return innPoint;}
 }
 private static boolean onCooldown(String point){Long day=state().cooldown.get(point);return day!=null&&day>=LocalDate.now().toEpochDay();}

 // ------------------------------------------------------------------ scene
 private static DungeonRunScene object;
 public static DungeonRunScene instance(){if(object==null)object=new DungeonRunScene();return object;}
 private final TextraButton advance,back,editDeck;private final Table list,headerTable;private final ScrollPane info;
 private AdventureEventData event;private AdventureEventData.AdventureEventParticipant opponent;

 private DungeonRunScene(){
  super(Forge.isLandscapeMode()?"ui/event.json":"ui/event_portrait.json");
  ui.onButtonPress("return",this::back);ui.onButtonPress("advance",this::advance);ui.onButtonPress("editDeck",this::showDeck);
  back=ui.findActor("return");advance=ui.findActor("advance");editDeck=ui.findActor("editDeck");
  for(String hidden:new String[]{"nextPage","previousPage","metaDraftTable"}){var a=ui.findActor(hidden);if(a!=null)a.setVisible(false);}
  Window window=ui.findActor("scrollWindow");list=ui.findActor("enemies");window.add(list);
  Window header=ui.findActor("header");header.toFront();headerTable=new Table(Controls.getSkin());header.add(headerTable).expand();
  info=ui.findActor("blessingInfo");
 }
 @Override public void enter(){super.enter();refresh();}
 @Override public boolean back(){Forge.switchScene(innLast!=null?innLast:GameScene.instance());return true;}

 private void setHeader(String text){headerTable.clear();headerTable.add(Controls.newTextraLabel(text)).expand();}
 private void setInfo(String text){
  TypingLabel label=Controls.newTypingLabel("[BLACK]"+text);label.skipToTheEnd();label.setAlignment(10);label.setWrap(true);
  info.setActor(label);label.setWidth(info.getWidth()-5);info.layout();
 }
 private void refresh(){
  State s=state();list.clear();list.top();
  editDeck.setDisabled(s.commander==null);advance.setDisabled(false);
  switch(s.phase){
   case "commander"->showCommanderChoice(s);
   case "package"->showPackageChoice(s);
   case "fight","done"->showProgress(s);
   default->showIntro(s);
  }
 }
 private void showIntro(State s){
  setHeader("Dungeon Run");
  boolean cooldown=onCooldown(innPoint);int fee=fee();
  advance.setText(cooldown?"Morgen":"Starten");advance.setDisabled(cooldown||Current.player().getGold()<fee);
  list.add(Controls.newTextraLabel("[%120]Dungeon Run")).left().pad(4).row();
  list.add(wrap("Wähle einen von drei Commandern und besiege sechs Gegner nacheinander. Nach jedem Sieg wählst du eins von drei Paketen mit 8 Karten, das dein Deck verstärkt. Gegner 6 ist ein Mini-Boss. Eine Niederlage beendet den Run.")).width(240).left().pad(4).row();
  setInfo("Teilnahme: "+fee+" Gold\n\nBelohnung:\n- 100 Gold pro Sieg\n- ab 5 Siegen: Arena-Preis (Gegenstand + 3 mythische Karten)\n- 6 Siege: zusätzlich 2 Booster + 3 seltene Karten"
   +(cooldown?"\n\n[RED]Hier gibt es heute keinen weiteren Run.":"")+(Current.player().getGold()<fee?"\n\n[RED]Nicht genug Gold.":""));
 }
 private int fee(){return Math.round(ENTRY_GOLD*(innChanges==null?1f:innChanges.getTownPriceModifier()));}
 private TextraLabel wrap(String text){TextraLabel l=Controls.newTextraLabel(text);l.setWrap(true);return l;}

 private void showCommanderChoice(State s){
  setHeader("Wähle deinen Commander");advance.setText("Wählen ...");advance.setDisabled(true);
  StringBuilder sb=new StringBuilder();
  for(String name:s.commanderOptions){
   Commander c=commander(name);if(c==null)continue;
   list.add(Controls.newTextraLabel("[GOLD]"+c.name)).left().padTop(4).colspan(2).row();
   list.add(wrap("[LIGHT_GRAY]"+c.theme+" ("+colorNames(c.colors)+")")).width(130).left();
   Table buttons=new Table();
   buttons.add(Controls.newTextButton("Ansehen",()->Forge.switchScene(DeckPreviewScene.getInstance(starterDeck(c))))).pad(2);
   buttons.add(Controls.newTextButton("Wählen",()->chooseCommander(c))).pad(2);
   list.add(buttons).right().row();
   sb.append("[BLACK]").append(c.name).append(" - ").append(c.theme).append("\n").append(c.blurb).append("\n\n");
  }
  setInfo(sb.toString());
 }
 private void showPackageChoice(State s){
  Commander c=commander(s.commander);
  setHeader("Sieg! Wähle ein Paket");advance.setText("Wählen ...");advance.setDisabled(true);
  for(String name:s.packageOptions){
   Pack p=pack(c,name);if(p==null)continue;
   list.add(Controls.newTextraLabel("[GOLD]"+p.name)).left().padTop(4).colspan(2).row();
   list.add(wrap("[LIGHT_GRAY]8 Karten, "+p.theme+(s.pickedThemes.contains(p.theme)?" [GREEN](passt)":""))).width(130).left();
   Table buttons=new Table();
   buttons.add(Controls.newTextButton("Ansehen",()->Forge.switchScene(DeckPreviewScene.getInstance(packDeck(p))))).pad(2);
   buttons.add(Controls.newTextButton("Wählen",()->choosePackage(p))).pad(2);
   list.add(buttons).right().row();
  }
  setInfo("Runde "+(s.round-1)+" gewonnen ("+s.wins+" Siege).\n\nDas gewählte Paket ersetzt 8 Starterkarten deines Decks.\n\nBisher gewählt:\n"+(s.picked.isEmpty()?"-":String.join("\n",s.picked)));
 }
 private void showProgress(State s){
  Commander c=commander(s.commander);boolean done="done".equals(s.phase);
  setHeader(done?"Run beendet":"Runde "+s.round+" von "+ROUNDS);
  advance.setText(done?"Belohnung":"Kämpfen");
  for(int i=0;i<s.opponents.length;i++){
   int round=i+1;EnemyData data=WorldData.getEnemy(s.opponents[i]);
   String status=round<s.round||(done&&!s.lost&&round<=s.wins)?"[GREEN]besiegt":round==s.round&&!done?"[GOLD]nächster Gegner":done&&s.lost&&round==s.round?"[RED]verloren":"[GRAY]wartet";
   Image avatar=new Image();try{if(data!=null)avatar=new Image(new EnemySprite(data).getAvatar());}catch(Exception ignored){}
   avatar.setScaling(Scaling.fit);
   list.add(avatar).size(16).pad(3);
   list.add(Controls.newTextraLabel((round==ROUNDS?"[RED]Mini-Boss[]: ":"Runde "+round+": ")+(data!=null?data.getName():s.opponents[i]))).left().expandX();
   list.add(Controls.newTextraLabel(status)).right().padRight(4).row();
  }
  setInfo((c!=null?c.name+"\n"+c.theme+"\n\n":"")+"Siege: "+s.wins+"/"+ROUNDS+"\n\nPakete:\n"+(s.picked.isEmpty()?"-":String.join("\n",s.picked))
   +(done?"\n\n"+rewardText(s.wins):""));
 }
 private static String rewardText(int wins){
  StringBuilder sb=new StringBuilder("Belohnung: "+wins*100+" Gold");
  if(wins>=5)sb.append("\n+ Arena-Preis");if(wins>=6)sb.append("\n+ 2 Booster + 3 seltene Karten");
  return sb.toString();
 }
 private static String colorNames(String colors){
  List<String> out=new ArrayList<>();
  for(char ch:colors.toCharArray())out.add(switch(ch){case 'W'->"Weiß";case 'U'->"Blau";case 'B'->"Schwarz";case 'R'->"Rot";case 'G'->"Grün";default->"?";});
  return String.join("/",out);
 }

 // ------------------------------------------------------------------ actions
 public void advance(){
  State s=state();
  switch(s.phase){
   case "none"->{
    int fee=fee();if(onCooldown(innPoint)||Current.player().getGold()<fee)return;
    showDialog(createGenericDialog("Dungeon Run","Run für "+fee+" Gold starten?","Starten","Abbrechen",()->{
     Current.player().takeGold(fee);startRun();removeDialog();refresh();},this::removeDialog));
   }
   case "fight"->startRound();
   case "done"->collectRewards();
   default->{}
  }
 }
 private void startRun(){
  State s=new State();s.cooldown=state().cooldown;s.seed=new Random().nextLong();Random r=new Random(s.seed);
  List<Commander> all=new ArrayList<>(Arrays.asList(content().commanders));Collections.shuffle(all,r);
  s.commanderOptions=all.stream().limit(3).map(c->c.name).toArray(String[]::new);
  s.phase="commander";s.opponents=pickOpponents(r);state=s;save();
 }
 private void chooseCommander(Commander c){
  State s=state();s.commander=c.name;s.deck=new ArrayList<>();s.deck.addAll(Arrays.asList(c.lands));s.deck.addAll(Arrays.asList(c.starter));
  s.starterLeft=new ArrayList<>(Arrays.asList(c.starter));s.phase="fight";s.round=1;s.wins=0;save();refresh();
 }
 private void choosePackage(Pack p){
  State s=state();Commander c=commander(s.commander);
  for(String card:p.cards){
   if(s.deck.contains(card))continue;   // singleton
   String out=null;
   if(!s.starterLeft.isEmpty())out=s.starterLeft.remove(s.starterLeft.size()-1);
   else for(String basic:new String[]{"Plains","Island","Swamp","Mountain","Forest"})if(Collections.frequency(s.deck,basic)>6){out=basic;break;}
   if(out==null)continue;
   s.deck.remove(out);s.deck.add(card);
  }
  s.picked.add(p.name);s.pickedThemes.add(p.theme);s.packageOptions=new String[0];s.phase="fight";save();refresh();
 }
 private static Pack pack(Commander c,String name){if(c!=null)for(Pack p:c.packages)if(p.name.equals(name))return p;return null;}
 /** three package offers; themes already chosen are twice as likely (like Hearthstone's buckets) */
 private static String[] offerPackages(State s){
  Commander c=commander(s.commander);if(c==null)return new String[0];
  Random r=new Random(s.seed*31+s.round);List<Pack> pool=new ArrayList<>();
  for(Pack p:c.packages)if(!s.picked.contains(p.name)){pool.add(p);if(s.pickedThemes.contains(p.theme))pool.add(p);}
  List<String> out=new ArrayList<>();
  while(out.size()<3&&!pool.isEmpty()){Pack p=pool.get(r.nextInt(pool.size()));if(!out.contains(p.name))out.add(p.name);pool.removeIf(q->q==p);}
  return out.toArray(new String[0]);
 }
 /** five regular enemies of rising strength (by life), then a mini boss */
 private static String[] pickOpponents(Random r){
  List<EnemyData> normal=new ArrayList<>(),bosses=new ArrayList<>();
  for(EnemyData e:WorldData.getAllEnemies()){
   if(e==null||e.copyPlayerDeck||e.nextEnemy!=null||e.deck==null||e.deck.length==0)continue;
   if(e.boss){if(e.life<=50&&TOO_BIG.stream().noneMatch(n->e.getName().startsWith(n)))bosses.add(e);}
   else normal.add(e);
  }
  normal.sort(Comparator.comparingInt(e->e.life));
  String[] out=new String[ROUNDS];
  for(int i=0;i<ROUNDS-1;i++){
   int from=normal.size()*i/(ROUNDS-1),to=Math.max(from+1,normal.size()*(i+1)/(ROUNDS-1));
   out[i]=normal.isEmpty()?"":normal.get(from+r.nextInt(Math.max(1,to-from))).getName();
  }
  out[ROUNDS-1]=bosses.isEmpty()?(normal.isEmpty()?"":normal.get(normal.size()-1).getName()):bosses.get(r.nextInt(bosses.size())).getName();
  return out;
 }
 static Deck runDeck(State s){
  Deck d=new Deck("Dungeon Run - "+s.commander);
  d.getOrCreate(DeckSection.Commander).add(s.commander,1);
  for(String card:s.deck)d.getMain().add(card,1);
  return d;
 }
 static Deck starterDeck(Commander c){
  Deck d=new Deck(c.name+" (Start)");d.getOrCreate(DeckSection.Commander).add(c.name,1);
  for(String card:c.lands)d.getMain().add(card,1);for(String card:c.starter)d.getMain().add(card,1);return d;
 }
 static Deck packDeck(Pack p){Deck d=new Deck(p.name);for(String card:p.cards)d.getMain().add(card,1);return d;}
 private void showDeck(){State s=state();if(s.commander!=null)Forge.switchScene(DeckPreviewScene.getInstance(runDeck(s)));}

 private void startRound(){
  State s=state();EnemyData data=WorldData.getEnemy(s.opponents[s.round-1]);
  if(data==null){data=(EnemyData)WorldData.getAllEnemies().first();}
  try{
   Constructor<AdventureEventData.AdventureEventParticipant> ctor=AdventureEventData.AdventureEventParticipant.class.getDeclaredConstructor();ctor.setAccessible(true);
   opponent=ctor.newInstance().generate(data);
  }catch(ReflectiveOperationException e){System.err.println("[DungeonRun] opponent: "+e);return;}
  opponent.setDeck(data.generateDeck(Current.player().isFantasyMode(),false));
  event=new AdventureEventData(s.seed,AdventureEventController.EventFormat.Constructed,AdventureEventController.EventStyle.Bracket,null);
  event.eventRules=new AdventureEventData.AdventureEventRules(AdventureEventController.EventFormat.Constructed,AdventureEventData.PairingStyle.SingleElimination,1f);
  event.eventRules.gameType=GameType.Commander;event.eventRules.startingLife=LIFE;event.eventRules.gamesPerMatch=1;
  event.eventRules.allowsShards=false;event.eventRules.allowsItems=false;event.eventRules.allowsBlessings=false;
  event.registeredDeck=runDeck(s);event.rounds=ROUNDS;event.currentRound=s.round;event.nextOpponent=opponent;
  event.eventStatus=AdventureEventController.EventStatus.Started;
  DuelScene duelScene=DuelScene.instance();EnemySprite enemy=opponent.getSprite();AdventureEventData eventData=event;
  advance.setDisabled(true);
  TransitionScreen transition=new TransitionScreen(()->{
    duelScene.initDuels(WorldStage.getInstance().getPlayerSprite(),enemy,false,eventData);
    advance.setDisabled(false);Forge.switchScene(duelScene);
   },ScreenUtil.getInstance().takeScreenshot(),true,false,false,false,"",Current.player().avatar(),enemy.getAtlasPath(),
   Current.player().getName(),enemy.getName(),s.wins+"-0",(s.round==ROUNDS?"Mini-Boss":"Runde "+s.round));
  transition.eventDuel=true;
  FThreads.invokeInEdtNowOrLater(()->Forge.setTransitionScreen(transition));
 }
 @Override public void setWinner(boolean winner,boolean isArena){
  State s=state();if(!"fight".equals(s.phase))return;
  if(winner){
   s.wins++;
   if(s.round>=ROUNDS){s.phase="done";}
   else{s.round++;s.phase="package";s.packageOptions=offerPackages(s);if(s.packageOptions.length==0)s.phase="fight";}
  }else{s.lost=true;s.phase="done";}
  save();refresh();
 }
 private void collectRewards(){
  State s=state();Commander c=commander(s.commander);
  Array<Reward> rewards=new Array<>();
  if(s.wins>0){RewardData gold=new RewardData();gold.type="gold";gold.count=100*s.wins;gold.probability=1;rewards.addAll(gold.generate(false,true));}
  String[] colors=c==null?null:lowerColors(c.colors);
  if(s.wins>=5){
   RewardData item=new RewardData();item.type="item";item.count=1;item.probability=1;item.itemNames=ARENA_ITEMS;rewards.addAll(item.generate(false,true));
   RewardData mythic=new RewardData();mythic.type="card";mythic.count=3;mythic.probability=1;mythic.rarity=new String[]{"Mythic Rare"};mythic.colors=colors;rewards.addAll(mythic.generate(false,true));
  }
  if(s.wins>=ROUNDS){
   RewardData boosters=new RewardData();boosters.type="cardPackShop";boosters.count=2;boosters.probability=1;
   if(colors!=null&&colors.length>0)boosters.colors=new String[]{Character.toUpperCase(colors[0].charAt(0))+colors[0].substring(1)};
   rewards.addAll(boosters.generate(false,true));
   RewardData rare=new RewardData();rare.type="card";rare.count=3;rare.probability=1;rare.rarity=new String[]{"Rare"};rare.colors=colors;rewards.addAll(rare.generate(false,true));
  }
  State next=new State();next.cooldown=s.cooldown;next.cooldown.put(innPoint,LocalDate.now().toEpochDay());state=next;save();
  if(rewards.size>0){RewardScene.instance().loadRewards(rewards,RewardScene.Type.EventReward,null);Forge.switchScene(RewardScene.instance());}
  else refresh();
 }
 private static String[] lowerColors(String colors){
  List<String> out=new ArrayList<>();
  for(char ch:colors.toCharArray())out.add(switch(ch){case 'W'->"white";case 'U'->"blue";case 'B'->"black";case 'R'->"red";case 'G'->"green";default->"colorless";});
  return out.toArray(new String[0]);
 }
}
