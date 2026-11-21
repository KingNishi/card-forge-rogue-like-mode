package forge.adventure.util;

import com.badlogic.gdx.Gdx;
import com.google.gson.*;
import forge.Forge;
import forge.StaticData;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.gamemodes.match.GameLobby;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.ChatMessage;
import forge.gamemodes.net.client.ClientGameLobby;
import forge.gamemodes.net.client.FGameClient;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import forge.gamemodes.net.server.FServerManager;
import forge.gamemodes.net.server.ServerGameLobby;
import forge.gui.FThreads;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.interfaces.ILobbyListener;
import forge.interfaces.IUpdateable;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeNetPreferences.FNetPref;
import forge.model.FModel;
import forge.screens.FScreen;
import forge.screens.match.MatchController;
import forge.screens.match.MatchScreen;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Rogue Commanders 1v1 LAN (user 2026-11-14: "ganz simpel - Name, Moxfield-Link oder Deckliste, IP kopieren, Start Game").
 *  Uses Forge's own network play (FServerManager / FGameClient) without the Forge lobby screen; the match is shown
 *  through a small ForgeScene so the Rogue battle HUD (forge.duo switch) and the input work inside the Rogue app. */
public final class RogueLan {
 private RogueLan(){}
 public static final int PORT=36743;
 public enum State{IDLE,LOADING,HOSTING,CONNECTING,LOBBY,STARTING,MATCH,AFTER}
 public static volatile State state=State.IDLE;
 public static volatile String status="";
 public static volatile boolean error;
 /** bumps whenever the screen should rebuild its buttons */
 public static volatile int version;
 /** fields of the LAN screen, kept while the app runs and saved in rogue_lan.json */
 public static String name="",deckText="",hostIp="";
 /** the battle controls tour (German) runs once, in the first LAN match */
 public static boolean tourDone;
 /** battlefield for LAN games: "" = random, else a board name (user 2026-11-14 "battle field chooser") */
 public static String board="";
 /** Online mode (user 2026-11-15 "1v1 Online Mode"): the host asks the router via UPnP to open port 36743 while hosting and
  *  shows the internet IP; false = home network only */
 public static boolean online;
 /** 1v1 vs AI (user 2026-11-15): same screen without IP; the AI plays a pasted deck or a random Moxfield deck of the chosen
  *  bracket (2 Core .. 5 cEDH) that was updated in the last 2 years */
 /** language of 1v1 / vs AI games (user 2026-11-15 "auswählen ... englisch oder deutsch"): false = German texts, card names
  *  and German card pictures; true = Forge in English with the normal (English) pictures */
 public static boolean english=true;   // default English (user 2026-11-15 "wir bleiben einfach bei englisch")
 /** text in the chosen language */
 public static String t(String de,String en){return english?en:de;}
 public static boolean vsAi;public static int bracket=3;public static String aiDeckText="";
 /** players at the table, 2..4 (user 2026-11-15 "erweitere den 1v1 mode auf bis zu 4 spieler"): vs AI = 1..3 AI opponents,
  *  LAN/online = the host waits for up to 3 friends and may fill the free seats with AI ("Jetzt starten") */
 public static int players=2;
 /** pre-game option (user 2026-11-15 "unendlich viele free mulligans als pre game option"): false = the first mulligan is
  *  free (Commander rule), true = every mulligan is free (BattleZoomMod.tuck). In LAN the host's choice counts. */
 public static boolean allMulls;
 /** game mode (user 2026-11-16 "koop ai mode ... und ffa 1v1v1 und 1v1v1v1"): false = free for all (everyone alone),
  *  true = co-op: LAN - all human players are one team against the AI seats; vs AI - only with 4 players: you + an AI
  *  partner against 2 AIs. In LAN the host's choice counts. */
 public static boolean coop;
 public static boolean allMulligansFree(){return allMulls&&inMatch();}
 private static final List<Deck> aiDecks=new ArrayList<>();private static final List<String> aiNames=new ArrayList<>();
 private static final Set<String> usedRandom=new HashSet<>();
 private static volatile boolean filling;
 public static final String[] BRACKETS={"","Exhibition","Core","Upgraded","Optimized","cEDH"};
 private static forge.gamemodes.match.LocalLobby aiLobby;private static Deck aiDeck;private static String aiInfo="";
 private static volatile String extIp;private static volatile boolean extLoading;
 /** the internet IP of this connection (asked once in the background), "..." until known */
 public static String internetIp(){
  String ip=extIp;if(ip!=null)return ip;
  if(!extLoading){extLoading=true;new Thread(()->{String r=null;try{r=FServerManager.getExternalAddress();}catch(Throwable t){}
   extIp=r==null||r.isBlank()?"unbekannt":r.trim();extLoading=false;version++;},"RogueLan-extip").start();}
  return "...";
 }
 public static String shownIp(){return online?internetIp():lanIp();}
 /** chosen commander artwork per commander name -> picture URL (user 2026-11-15) */
 static final Map<String,String> cmdArt=new java.util.concurrent.ConcurrentHashMap<>();   // no longer used: online prints look bad (user 2026-11-16)
 /** own commander artwork per commander (canon name -> picture file on this PC): Rogue fan-art skins or pictures in
  *  _mods/rogue-commanders/custom-art (user 2026-11-16 "fuer die commander auch custom artworks zulassen", no online prints) */
 static final boolean PARALLEL_MULLIGAN=false;
 public static final Map<String,String> cmdCustom=new java.util.concurrent.ConcurrentHashMap<>();
 static final String[][] BOARDS={{"SG_Forest","Feenwald"},{"SG_Deepsea","Tiefsee-Tempel"},{"SG_Necropolis","Nekropole"},{"SG_Crystal","Kristallhöhle"},{"SG_Hoard","Drachenhort"},{"SG_Ice","Eis-Ebene"},{"SG_Library","Arkane Bibliothek"},{"SG_Shrine","Herbst-Schrein"},
  // benched (user 2026-11-15 "nur die animierten"): AKH_Mobile Amonkhet, WOE_Mobile Eldraine, KHM_Mobile Kaldheim, FRA, Fallback Klassisch,
  // MID_Mobile Innistrad, AFR_Mobile Forgotten Realms, AKR_Mobile Amonkhet Remastered, BLB Bloomburrow, DAR_Mobile Dominaria, XLN_Mobile Ixalan, OTJ
  };
 static final Map<String,String> BOARD_EN=Map.of("Feenwald","Fae Forest","Tiefsee-Tempel","Deep Sea Temple","Nekropole","Necropolis","Kristallhöhle","Crystal Cave","Drachenhort","Dragon Hoard","Eis-Ebene","Frozen Lake","Arkane Bibliothek","Arcane Library","Herbst-Schrein","Autumn Shrine");
 static final String BOARD_DIR="D:/forge/_mods/arena-boards/",SOGNI_DIR="D:/forge/_mods/modern-battlefield/boards/";
 /** {name, label, preview file} of every board on this PC; first entry = random */
 public static List<String[]> boards(){
  List<String[]> out=new ArrayList<>();out.add(new String[]{"",t("Zufall","Random"),null});
  for(String[] b:BOARDS){java.io.File small=new java.io.File(BOARD_DIR+"boards/"+b[0]+".jpg"),hd=new java.io.File(BOARD_DIR+"boards-hd/"+b[0]+".jpg");
   java.io.File sg=new java.io.File(SOGNI_DIR+b[0]+"-preview.jpg");   // own Sogni boards (public)
   if(!sg.isFile())sg=new java.io.File(SOGNI_DIR+b[0]+".jpg");   // releases up to 0.2.12.6 shipped no previews (user 2026-11-18)
   String label=t(b[1],BOARD_EN.getOrDefault(b[1],b[1]));
   if(small.isFile()||hd.isFile())out.add(new String[]{b[0],label,(small.isFile()?small:hd).getPath()});else if(sg.isFile())out.add(new String[]{b[0],label,sg.getPath()});}
  return out;
 }
 public static void setBoard(String name){board=name==null?"":name;save();if(System.getProperty("forge.duo")!=null)BattleUiSettings.lanBoard=board;}
 /** the Rogue scene sets this: switches back to its LAN screen after a match */
 public static Runnable backToRogue;

 private static ServerGameLobby hostLobby;private static FGameClient client;private static ClientGameLobby clientLobby;
 private static Deck deck;private static boolean isHost,sentDeck,setDuo;
 private static java.util.Timer timer;private static boolean winLoseSeen;private static long overSince;private static Object lastGame;private static long hiddenSince;
 private static int myWins,theirWins;private static String opponent="";

 static void set(State s,String msg,boolean err){state=s;status=msg;error=err;version++;}
 /** a LAN match is on screen: own (German) controls tour */
 public static boolean inMatch(){return state==State.MATCH||state==State.STARTING;}
 public static boolean tourDue(){return inMatch()&&!tourDone;}
 public static void setTourDone(){if(!tourDone){tourDone=true;save();}}
 public static boolean busy(){return state!=State.IDLE&&state!=State.AFTER;}
 public static String score(){return myWins+theirWins==0?"":forge.adventure.util.RogueLan.t("Stand: du ","Score: you ")+myWins+" - "+theirWins+" "+opponent;}

 // ------------------------------------------------------------------ saved fields
 private static Path file(){return java.nio.file.Paths.get(forge.localinstance.properties.ForgeConstants.USER_PREFS_DIR,"rogue_lan.json");}
 public static void load(String defaultName){
  try{Path f=file();if(Files.isRegularFile(f)){JsonObject o=JsonParser.parseString(Files.readString(f)).getAsJsonObject();
   if(o.has("name"))name=o.get("name").getAsString();if(o.has("deck"))deckText=o.get("deck").getAsString();if(o.has("ip"))hostIp=o.get("ip").getAsString();if(o.has("tourDone"))tourDone=o.get("tourDone").getAsBoolean();if(o.has("board")){board=o.get("board").getAsString();boolean known=false;for(String[] b:BOARDS)known|=b[0].equals(board);if(!known)board="";}if(o.has("online"))online=o.get("online").getAsBoolean();if(o.has("english"))english=o.get("english").getAsBoolean();if(o.has("bracket"))bracket=Math.max(2,Math.min(5,o.get("bracket").getAsInt()));if(o.has("aiDeck"))aiDeckText=o.get("aiDeck").getAsString();if(o.has("players"))players=Math.max(2,Math.min(4,o.get("players").getAsInt()));if(o.has("allMulls"))allMulls=o.get("allMulls").getAsBoolean();if(o.has("coop"))coop=o.get("coop").getAsBoolean();
   if(o.has("cmdArt"))for(var e:o.getAsJsonObject("cmdArt").entrySet())cmdArt.put(e.getKey(),e.getValue().getAsString());
   if(o.has("cmdCustom"))for(var e:o.getAsJsonObject("cmdCustom").entrySet())cmdCustom.put(e.getKey(),e.getValue().getAsString());}}
  catch(Exception e){System.err.println("[RogueLan] "+e);}
  if(name.isBlank()&&defaultName!=null)name=defaultName;
 }
 public static void save(){
  try{JsonObject o=new JsonObject();o.addProperty("name",name);o.addProperty("deck",deckText);o.addProperty("ip",hostIp);o.addProperty("tourDone",tourDone);o.addProperty("board",board);o.addProperty("online",online);o.addProperty("english",english);o.addProperty("bracket",bracket);o.addProperty("aiDeck",aiDeckText);o.addProperty("players",players);o.addProperty("allMulls",allMulls);o.addProperty("coop",coop);JsonObject ca=new JsonObject();for(var e:cmdArt.entrySet())ca.addProperty(e.getKey(),e.getValue());o.add("cmdArt",ca);
   JsonObject cc=new JsonObject();for(var e:cmdCustom.entrySet())cc.addProperty(e.getKey(),e.getValue());o.add("cmdCustom",cc);
   Path f=file();Files.createDirectories(f.getParent());Files.writeString(f,new GsonBuilder().setPrettyPrinting().create().toJson(o));}
  catch(Exception e){System.err.println("[RogueLan] "+e);}
 }

 // ------------------------------------------------------------------ own IP (home network, not the VPN)
 private static String ipCache;private static long ipAt;
 /** cached: the screen draws it every frame */
 public static String lanIp(){long now=System.currentTimeMillis();if(ipCache==null||now-ipAt>10000){ipCache=findIp();ipAt=now;}return ipCache;}
 static String findIp(){
  List<String> home=new ArrayList<>(),lan=new ArrayList<>(),other=new ArrayList<>();
  try{for(NetworkInterface ni:Collections.list(NetworkInterface.getNetworkInterfaces())){
    if(!ni.isUp()||ni.isLoopback()||ni.isVirtual())continue;
    String n=(ni.getName()+" "+ni.getDisplayName()).toLowerCase(Locale.ROOT);
    boolean vpn=Pattern.compile("vpn|surfshark|nord|tap-|wireguard|wintun|hyper-v|vmware|virtualbox|vbox|vethernet|hamachi|zerotier|tailscale|bluetooth|npcap|loopback|pseudo").matcher(n).find();
    for(InetAddress a:Collections.list(ni.getInetAddresses())){
     if(!(a instanceof Inet4Address)||a.isLoopbackAddress()||a.isLinkLocalAddress())continue;
     String ip=a.getHostAddress();
     if(vpn)other.add(ip);else if(ip.startsWith("192.168."))home.add(ip);else if(a.isSiteLocalAddress())lan.add(ip);else other.add(ip);}}}
  catch(Exception e){System.err.println("[RogueLan] "+e);}
  if(!home.isEmpty())return home.get(0);if(!lan.isEmpty())return lan.get(0);if(!other.isEmpty())return other.get(0);
  return "127.0.0.1";
 }

 // ------------------------------------------------------------------ deck: Moxfield link or pasted list
 static final Pattern MOX=Pattern.compile("moxfield\\.com/decks/([A-Za-z0-9_\\-]+)");
 static final Pattern LINE=Pattern.compile("^(\\d+)\\s*[xX]?\\s+(.+?)(?:\\s+\\(([A-Za-z0-9_]{2,8})\\)(?:\\s+([^\\s*]+))?)?(?:\\s+\\*[A-Za-z]+\\*)*\\s*$");
 static final Map<String,String> HEADERS=Map.ofEntries(Map.entry("commander","cmd"),Map.entry("commanders","cmd"),Map.entry("kommandeur","cmd"),Map.entry("hauptdeck","main"),Map.entry("deck","main"),Map.entry("main","main"),
  Map.entry("mainboard","main"),Map.entry("sideboard","side"),Map.entry("companion","side"),Map.entry("maybeboard","skip"),Map.entry("considering","skip"),Map.entry("tokens","skip"));
 /** set + collector number: the printing (artwork) chosen on Moxfield / in the list (user 2026-11-17) */
 record Entry(int n,String card,String set,String cn){Entry(int n,String card){this(n,card,null,null);}}

 public static Deck buildDeck(String input)throws Exception{
  java.io.File saved=savedFile(input);
  if(saved!=null&&isTxt(saved)){   // a list in the game's Decks folder (user 2026-11-20)
   if(!saved.isFile())throw new IllegalArgumentException(t("Gespeichertes Deck nicht gefunden: ","Saved deck not found: ")+saved.getName());
   String txt=readText(saved);if(savedFile(txt)!=null)throw new IllegalArgumentException(t("Die Deckdatei verweist nur auf ein anderes Deck: ","The deck file only points to another deck: ")+saved.getName());
   Deck d=buildDeck(txt);d.setName(baseName(saved));return d;}
  if(saved!=null){   // a deck copy saved in the game (user 2026-11-17): exact printings, no Moxfield call
   if(!saved.isFile())throw new IllegalArgumentException(t("Gespeichertes Deck nicht gefunden: ","Saved deck not found: ")+saved.getName());
   Deck d=forge.deck.io.DeckSerializer.fromFile(saved);
   if(d==null||!d.has(DeckSection.Commander)||d.get(DeckSection.Commander).isEmpty())throw new IllegalArgumentException(t("Das gespeicherte Deck hat keinen Commander.","The saved deck has no commander."));
   return d;}
  String text=input==null?"":input.trim();
  if(text.isEmpty())throw new IllegalArgumentException(forge.adventure.util.RogueLan.t("Füge zuerst einen Moxfield-Link oder eine Deckliste ein.","Paste a Moxfield link or a deck list first."));
  List<Entry> cmd=new ArrayList<>(),main=new ArrayList<>();String deckName="LAN deck";
  Matcher m=MOX.matcher(text);
  if(m.find()&&text.split("\\s+").length<=2){deckName=moxfield(m.group(1),cmd,main);}
  else parseText(text,cmd,main);
  List<String> unknown=new ArrayList<>();
  Deck d=new Deck(deckName);
  for(Entry e:cmd){PaperCard pc=print(e);if(pc==null)unknown.add(e.card());else d.getOrCreate(DeckSection.Commander).add(pc,1);}
  for(Entry e:main){PaperCard pc=print(e);if(pc==null)unknown.add(e.card());else d.getMain().add(pc,e.n());}
  if(!d.has(DeckSection.Commander)||d.get(DeckSection.Commander).isEmpty()){   // no commander line: the first card that can lead the deck
   for(Map.Entry<PaperCard,Integer> e:d.getMain()){if(e.getKey().getRules().canBeCommander()){PaperCard c=e.getKey();d.getMain().remove(c,1);d.getOrCreate(DeckSection.Commander).add(c,1);break;}}
  }
  if(!d.has(DeckSection.Commander)||d.get(DeckSection.Commander).isEmpty())
   throw new IllegalArgumentException(forge.adventure.util.RogueLan.t("Kein Commander gefunden. Schreib eine Zeile \"Commander\" über deinen Commander in der Liste.","No commander found. Put a line \"Commander\" above your commander in the list."));
  if(d.getMain().countAll()<20)throw new IllegalArgumentException(forge.adventure.util.RogueLan.t("Nur ","Only ")+d.getMain().countAll()+forge.adventure.util.RogueLan.t(" Karten erkannt - ist das das ganze Deck?"," cards recognised - is that the whole deck?"));
  if(!unknown.isEmpty())warning=unknown.size()+forge.adventure.util.RogueLan.t(" Karte(n) nicht gefunden und weggelassen: "," card(s) not found and left out: ")+String.join(", ",unknown.subList(0,Math.min(4,unknown.size())))+(unknown.size()>4?" ...":"");
  return d;
 }
 static String warning;
 /** the exact printing (set + collector number), else the same set, else Forge's default print (user 2026-11-17:
  *  "beachtet er die artworks beim mox import") */
 static PaperCard print(Entry e){
  PaperCard pc=card(e.card());if(pc==null||e.set()==null)return pc;
  try{var db=StaticData.instance().getCommonCards();String set=e.set().toUpperCase(Locale.ROOT);
   PaperCard p=e.cn()!=null?db.getCard(pc.getName(),set,e.cn()):null;
   if(p==null)p=db.getCard(pc.getName(),set);
   if(p!=null)return p;
  }catch(Exception x){System.err.println("[RogueLan] print "+e+": "+x);}
  return pc;
 }
 /** English or German card name (German: Forge's cardnames-de-DE.txt, user 2026-11-14 "deutsche Liste") */
 static PaperCard card(String name){
  PaperCard pc=cardEn(name);if(pc!=null)return pc;
  String en=english(name);return en==null?null:cardEn(en);
 }
 private static Map<String,String> german;
 /** German name -> English name, keys normalised; basic lands added by hand (the file keeps them English) */
 static synchronized String english(String name){
  if(german==null){Map<String,String> m=new HashMap<>();
   try(var r=Files.newBufferedReader(java.nio.file.Paths.get(forge.localinstance.properties.ForgeConstants.LANG_DIR,"cardnames-de-DE.txt"),StandardCharsets.UTF_8)){
    String l;while((l=r.readLine())!=null){int a=l.indexOf('|');if(a<1)continue;int b=l.indexOf('|',a+1);
     String de=(b<0?l.substring(a+1):l.substring(a+1,b)).trim();if(!de.isEmpty())m.putIfAbsent(norm(de),l.substring(0,a).trim());}}
   catch(Exception e){System.err.println("[RogueLan] german card names: "+e);}
   for(String[] bl:new String[][]{{"wald","Forest"},{"ebene","Plains"},{"insel","Island"},{"sumpf","Swamp"},{"gebirge","Mountain"},{"ödnis","Wastes"}})m.put(bl[0],bl[1]);
   german=m;}
  return german.get(norm(name));
 }
 static String norm(String s){return s.toLowerCase(Locale.ROOT).replace('\u2019','\'').trim();}
 static PaperCard cardEn(String name){
  try{var db=StaticData.instance().getCommonCards();
   PaperCard pc=db.getCard(name);if(pc!=null)return pc;
   int i=name.indexOf(" // ");if(i>0){pc=db.getCard(name.substring(0,i));if(pc!=null)return pc;}
   i=name.indexOf(" / ");if(i>0)return db.getCard(name.substring(0,i));
  }catch(Exception e){System.err.println("[RogueLan] card "+name+": "+e);}
  return null;
 }
 /** headers a list may use (user 2026-11-20: "(commander)", "(enchantments)", "Artifacts", "Lands (34):", "## Creatures") */
 static final Set<String> MAIN_HEADERS=Set.of("creature","creatures","artifact","artifacts","enchantment","enchantments","instant","instants",
  "sorcery","sorceries","planeswalker","planeswalkers","land","lands","battle","battles","other","others","spell","spells","nonland","nonlands",
  "ramp","removal","draw","card draw","interaction","protection","utility","synergy","theme","wincon","wincons","win condition","win conditions",
  "board wipes","wipes","tutors","counterspells","library","kreaturen","artefakte","verzauberungen","spontanzauber","hexereien","länder",
  "zauber","sonstiges","decklist","deck list","main deck","the 99","cards","list","nonland cards","mana","mana rocks","rocks","mana dorks","dorks","recursion","combo","finisher","finishers");
 /** the section of a header line, else null */
 static String header(String line){
  String k=line.toLowerCase(Locale.ROOT).trim();
  for(int i=0;i<3;i++)k=k.replaceAll("[:\\s]+$","").replaceAll("\\s*[(\\[]\\s*\\d+(?:\\s*/\\s*\\d+)?\\s*[)\\]]$","")
   .replaceAll("\\s*[-\u2013]\\s*\\d+$","").replaceAll("^[#=~*\\-\\s]+|[=~*\\-\\s]+$","").trim();
  if(k.length()>2&&(k.startsWith("(")&&k.endsWith(")")||k.startsWith("[")&&k.endsWith("]")))k=k.substring(1,k.length()-1).trim();
  if(HEADERS.containsKey(k))return HEADERS.get(k);
  if(k.startsWith("commander")||k.startsWith("kommandeur"))return "cmd";
  if(k.startsWith("sideboard")||k.startsWith("companion")||k.startsWith("begleiter"))return "side";
  if(k.startsWith("maybe")||k.startsWith("consider")||k.equals("token"))return "skip";
  if(MAIN_HEADERS.contains(k))return "main";
  return null;
 }
 static final Pattern TAG=Pattern.compile("\\s*(?:\\[([^\\[\\]]*)\\]|\\(([^()]*)\\)|\\^[^^]*\\^)\\s*$");
 static final Pattern SET_CODE=Pattern.compile("[A-Za-z0-9_]{2,8}");
 static void parseText(String text,List<Entry> cmd,List<Entry> main){
  List<List<Entry>> blocks=new ArrayList<>();List<Entry> cur=new ArrayList<>();
  Map<String,List<Entry>> sec=new HashMap<>();for(String k:new String[]{"cmd","main","side","skip"})sec.put(k,new ArrayList<>());
  String section=null;boolean header=false,notes=false;List<Entry> tagged=new ArrayList<>();
  for(String raw:text.replace("﻿","").replace("\r","").split("\n")){
   String line=raw.trim();
   if(line.isEmpty()){if(!cur.isEmpty()){blocks.add(cur);cur=new ArrayList<>();}
    if("cmd".equals(section)&&!sec.get("cmd").isEmpty())section="main";   // a blank line ends the commander section
    continue;}
   if(line.startsWith("//"))continue;
   if(line.startsWith("#")){String h=header(line);   // markdown: "## Creatures" / "## Decklist" are headers, "## Notes" starts a part to skip
    if(h!=null){section=h;header=true;notes=false;}else if(line.startsWith("##")){section="skip";header=true;notes=true;}
    continue;}
   if(line.regionMatches(true,0,"SB:",0,3))continue;
   if(!Character.isDigit(line.charAt(0))){String h=header(line);   // "Lands (34):", "(commander)", "Artifacts"
    if(h!=null&&card(line)==null){section=h;header=true;notes=false;continue;}}
   boolean isCmd=false,drop=false;String l=line;
   for(int i=0;i<4;i++){Matcher t=TAG.matcher(l);if(!t.find())break;   // trailing tags: "(commander)", "[Ramp]", "[Commander{top}]", "^Have^"
    String c=t.group(1)!=null?t.group(1):t.group(2);
    if(t.group(2)!=null&&SET_CODE.matcher(c.trim()).matches()&&header(c)==null)break;   // "(CMR)": the set, keep it
    if(c!=null){String lc=c.toLowerCase(Locale.ROOT);if(lc.contains("{nodeck}"))drop=true;   // Archidekt maybeboard / not in deck
     for(String item:lc.replaceAll("\\{[^}]*\\}","").split(",")){item=item.trim();
      if(item.equals("commander"))isCmd=true;
      if(item.startsWith("maybeboard")||item.startsWith("sideboard")||item.startsWith("considering"))drop=true;}}
    l=l.substring(0,t.start()).trim();}
   Matcher m=LINE.matcher(l);Entry e;
   if(m.matches())e=new Entry(Integer.parseInt(m.group(1)),m.group(2).trim(),m.group(3),m.group(4));
   else if(!l.isEmpty()&&!l.matches(".*\\d.*")&&l.length()<60&&card(l)!=null)e=new Entry(1,l);   // "Sol Ring" without a count
   else{if(!Character.isDigit(line.charAt(0))&&header&&!notes&&line.length()<=60)section="main";   // unknown heading ("Shrines (18):"): the deck
    continue;}
   if(drop)continue;
   if(isCmd){tagged.add(e);continue;}
   if(header&&section!=null)sec.get(section).add(e);else cur.add(e);
  }
  if(!cur.isEmpty())blocks.add(cur);
  cmd.addAll(tagged);
  if(header){if(cmd.isEmpty())cmd.addAll(sec.get("cmd"));else main.addAll(sec.get("cmd"));main.addAll(sec.get("main"));
   for(List<Entry> bl:blocks)main.addAll(bl);
   if(cmd.isEmpty()&&!sec.get("side").isEmpty()&&sec.get("side").size()<=2)cmd.addAll(sec.get("side"));}
  else if(cmd.isEmpty()&&blocks.size()>=2&&blocks.get(blocks.size()-1).size()<=2){   // MTGO export: commander after an empty line at the end
   cmd.addAll(blocks.get(blocks.size()-1));for(int i=0;i<blocks.size()-1;i++)main.addAll(blocks.get(i));}
  else for(List<Entry> bl:blocks)main.addAll(bl);
  while(cmd.size()>2)main.add(cmd.remove(cmd.size()-1));   // a commander section holds 1-2 cards
  if(cmd.size()==2){PaperCard a=card(cmd.get(0).card()),b=card(cmd.get(1).card());   // a second commander only as a partner
   try{if(a!=null&&b!=null&&!a.getRules().canBePartnerCommanders(b.getRules()))main.add(cmd.remove(1));}catch(Exception x){}}
 }
 static String moxfield(String id,List<Entry> cmd,List<Entry> main)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL("https://api2.moxfield.com/v3/decks/all/"+id).openConnection();
  c.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) RogueCommanders/1.0");c.setRequestProperty("Accept","application/json");
  c.setConnectTimeout(10000);c.setReadTimeout(15000);
  int code;try{code=c.getResponseCode();}catch(Exception e){throw new IllegalArgumentException(forge.adventure.util.RogueLan.t("Moxfield ist nicht erreichbar. Füge stattdessen die Deckliste ein (Moxfield: Export > Copy for MTGO).","Moxfield is not reachable. Paste the deck list instead (Moxfield: Export > Copy for MTGO)."));}
  if(code==404)throw new IllegalArgumentException(forge.adventure.util.RogueLan.t("Moxfield: Deck nicht gefunden. Stell es auf Public oder Unlisted.","Moxfield: deck not found. Set it to Public or Unlisted."));
  if(code!=200)throw new IllegalArgumentException(forge.adventure.util.RogueLan.t("Moxfield hat den Abruf abgelehnt (HTTP ","Moxfield refused the request (HTTP ")+code+forge.adventure.util.RogueLan.t("). Füge stattdessen die Deckliste ein (Export > Copy for MTGO).","). Paste the deck list instead (Export > Copy for MTGO)."));
  JsonObject o;try(InputStream in=c.getInputStream()){o=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
  JsonObject boards=o.getAsJsonObject("boards");
  for(String[] b:new String[][]{{"commanders","cmd"},{"mainboard","main"}}){
   if(boards==null||!boards.has(b[0]))continue;JsonObject cards=boards.getAsJsonObject(b[0]).getAsJsonObject("cards");if(cards==null)continue;
   for(Map.Entry<String,JsonElement> e:cards.entrySet()){JsonObject v=e.getValue().getAsJsonObject();
    JsonObject cd=v.getAsJsonObject("card");
    Entry en=new Entry(v.has("quantity")?v.get("quantity").getAsInt():1,cd.get("name").getAsString(),
     cd.has("set")&&!cd.get("set").isJsonNull()?cd.get("set").getAsString():null,cd.has("cn")&&!cd.get("cn").isJsonNull()?cd.get("cn").getAsString():null);
    (b[1].equals("cmd")?cmd:main).add(en);}
  }
  return o.has("name")?o.get("name").getAsString():"Moxfield deck";
 }

 // ------------------------------------------------------------------ start / stop
 /** Start Game: empty host IP = host the game, otherwise join that IP. Runs in the background. */
 public static void start(){
  if(busy())return;portBusy=false;
  String n=name.isBlank()?"Commander":name.trim(),ip=hostIp.trim(),text=deckText;
  save();warning=null;
  set(State.LOADING,forge.adventure.util.RogueLan.t("Dein Deck wird geladen...","Loading your deck..."),false);
  new Thread(()->{
   try{Deck d=buildDeck(text);deck=d;remember(text,d);
    int count=d.getMain().countAll()+d.get(DeckSection.Commander).countAll();
    String info=d.get(DeckSection.Commander).toFlatList().get(0).getName()+" ("+count+" Karten)";
    if(ip.isEmpty())host(n,info);else join(n,ip,info);
   }catch(IllegalArgumentException e){set(State.IDLE,e.getMessage(),true);}
   catch(Throwable t){t.printStackTrace();set(State.IDLE,startError(t),true);stop();}
  },"RogueLan-start").start();
 }

 // ------------------------------------------------------------------ 1v1 vs AI (user 2026-11-15)
 /** Start Game in the AI mode: own deck + the AI's deck (pasted, or random of the bracket), then a local Forge match */
 public static void startAi(){
  if(busy())return;portBusy=false;
  String n=name.isBlank()?"Commander":name.trim(),text=deckText,aiText=aiDeckText;int b=bracket;
  save();warning=null;myWins=theirWins=0;
  set(State.LOADING,forge.adventure.util.RogueLan.t("Dein Deck wird geladen...","Loading your deck..."),false);
  new Thread(()->{
   try{Deck d=buildDeck(text);deck=d;String myWarn=warning;warning=null;remember(text,d);
    List<String> texts=splitDecks(aiText);int count=Math.max(1,Math.min(3,players-1));
    aiDecks.clear();aiNames.clear();usedRandom.clear();StringBuilder info=new StringBuilder();String aiWarn=null;
    for(int k=0;k<count;k++){
     Deck ai;String head=count>1?(t("KI ","AI ")+(k+1)+": "):"";
     if(k<texts.size()){set(State.LOADING,head+forge.adventure.util.RogueLan.t("Das Deck der KI wird geladen...","Loading the AI deck..."),false);ai=buildDeck(texts.get(k));remember(texts.get(k),ai);head+=forge.adventure.util.RogueLan.t("Gegner-Deck: ","Opponent deck: ");}
     else{set(State.LOADING,head+forge.adventure.util.RogueLan.t("Suche ein zufälliges Bracket-","Looking for a random bracket ")+b+forge.adventure.util.RogueLan.t("-Deck ("," deck (")+BRACKETS[b]+forge.adventure.util.RogueLan.t(") auf Moxfield...",") on Moxfield..."),false);ai=randomDeck(b);head+=forge.adventure.util.RogueLan.t("Zufallsdeck Bracket ","Random deck bracket ")+b+": ";}
     if(warning!=null&&aiWarn==null)aiWarn=warning;warning=null;
     int pacts=replacePacts(ai);if(pacts>0)head="("+pacts+forge.adventure.util.RogueLan.t(" Pakt durch Land ersetzt) "," pact replaced by a land) ")+head;
     String cmd=ai.get(DeckSection.Commander).toFlatList().get(0).getName();
     aiDecks.add(ai);aiNames.add(count>1?t("KI ","AI ")+(k+1)+" ("+cmd+")":t("KI (","AI (")+cmd+")");
     if(info.length()>0)info.append('\n');info.append(head).append(cmd).append(" - ").append(ai.getName());
    }
    warning=myWarn!=null?myWarn:aiWarn!=null?forge.adventure.util.RogueLan.t("KI-Deck: ","AI deck: ")+aiWarn:null;
    aiDeck=aiDecks.get(0);aiInfo=info.toString();opponent=String.join(", ",aiNames);
    duoOn();
    aiGo(n);
   }catch(IllegalArgumentException e){set(State.IDLE,e.getMessage(),true);}
   catch(Throwable t){t.printStackTrace();set(State.IDLE,startError(t),true);stop();}
  },"RogueLan-ai").start();
 }
 /** pacts (pay next upkeep or lose) are a trap for the AI: each one becomes a basic land - the deck's most played basic,
  *  else one of the commander's colours (user 2026-11-15 "pakt soll einfach dann durch ein land ersetzt werden") */
 static final Set<String> PACTS=Set.of("Pact of Negation","Slaughter Pact","Summoner's Pact","Intervention Pact","Pact of the Titan");
 static int replacePacts(Deck d){
  int n=0;
  for(PaperCard c:new ArrayList<>(d.getMain().toFlatList()))if(PACTS.contains(c.getName())){d.getMain().remove(c,1);n++;}
  if(n==0)return 0;
  Map<String,Integer> basics=new HashMap<>();
  for(Map.Entry<PaperCard,Integer> e:d.getMain())if(e.getKey().getRules().getType().isBasicLand())basics.merge(e.getKey().getName(),e.getValue(),Integer::sum);
  String land=basics.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
  if(land==null){var ci=d.get(DeckSection.Commander).toFlatList().get(0).getRules().getColorIdentity();
   land=ci.hasGreen()?"Forest":ci.hasBlack()?"Swamp":ci.hasRed()?"Mountain":ci.hasBlue()?"Island":ci.hasWhite()?"Plains":"Wastes";}
  PaperCard l=card(land);if(l!=null)d.getMain().add(l,n);
  return n;
 }
 /** the AI deck field for several AIs: decks separated by a line "---", or several Moxfield links (one per line) */
 static List<String> splitDecks(String text){
  List<String> out=new ArrayList<>();if(text==null||text.isBlank())return out;
  for(String part:text.replace("\r","").split("\n\\s*-{3,}\\s*(\n|$)")){
   String p=part.trim();if(p.isEmpty())continue;
   String[] lines=p.split("\n");boolean links=lines.length>1;
   for(String l:lines)if(!l.isBlank()&&!MOX.matcher(l).find())links=false;
   if(links){for(String l:lines)if(!l.isBlank())out.add(l.trim());}else out.add(p);
  }
  return out;
 }
 /** co-op vs AI needs 4 seats (2 against 2) */
 public static boolean coopVsAiPossible(){return players==4;}
 static final java.util.regex.Pattern TEAM_TAG=java.util.regex.Pattern.compile("^\\[(Team [AB]|Team)\\] ");
 /** seats -> teams right before the start: free for all = every seat its own team; co-op = humans (and in vs AI the first
  *  AI as partner) team 0, the other AIs team 1. Names get a "[Team A]" / "[Team B]" tag so everybody sees who is with whom. */
 static void applyTeams(GameLobby l,boolean vsAiGame){
  int ai=0;for(int i=0;i<l.getNumberOfSlots();i++){LobbySlot s=l.getSlot(i);if(s!=null&&s.getType()==LobbySlotType.AI)ai++;}
  boolean on=coop&&(vsAiGame?l.getNumberOfSlots()==4:ai>0);   // co-op without any AI opponent would leave nobody to fight
  int aiSeen=0;
  for(int i=0;i<l.getNumberOfSlots();i++){LobbySlot s=l.getSlot(i);if(s==null||s.getType()==LobbySlotType.OPEN)continue;
   String nm=s.getName()==null?"":TEAM_TAG.matcher(s.getName()).replaceFirst("");
   if(!on){s.setTeam(i);s.setName(nm);continue;}
   boolean isAi=s.getType()==LobbySlotType.AI;
   boolean teamA=!isAi||(vsAiGame&&aiSeen==0);
   if(isAi)aiSeen++;
   s.setTeam(teamA?0:1);s.setName((teamA?"[Team A] ":"[Team B] ")+nm);
  }
 }
 /** a new local lobby (you + AI) with both decks, started like the LAN host does */
 static void aiGo(String n){
  forge.gamemodes.match.LocalLobby l=new forge.gamemodes.match.LocalLobby();aiLobby=l;
  l.setListener(new IUpdateable(){@Override public void update(boolean full){}@Override public void update(int slot,LobbySlotType type){}});
  l.applyVariant(GameType.Commander);
  while(l.getNumberOfSlots()<aiDecks.size()+1)l.addSlot();   // local lobby: new seats are AI seats, each its own team (free for all)
  LobbySlot me=l.getSlot(0);
  me.setName(n);me.setDeck(deck);me.setIsReady(true);
  for(int k=0;k<aiDecks.size();k++){LobbySlot ai=l.getSlot(k+1);ai.setType(LobbySlotType.AI);ai.setName(aiNames.get(k));ai.setDeck(aiDecks.get(k));ai.setIsReady(true);}
  applyTeams(l,true);
  set(State.STARTING,aiInfo+forge.adventure.util.RogueLan.t("\nDas Spiel gegen die KI startet...","\nThe game against the AI is starting...")+(warning!=null?"\n"+warning:""),false);
  FThreads.invokeInBackgroundThread(()->{
   var prefs=FModel.getPreferences();boolean legality=prefs.getPrefBoolean(forge.localinstance.properties.ForgePreferences.FPref.ENFORCE_DECK_LEGALITY);
   Runnable go;
   try{prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.ENFORCE_DECK_LEGALITY,false);go=l.startGame();}
   finally{prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.ENFORCE_DECK_LEGALITY,legality);}
   if(go==null){set(State.IDLE,forge.adventure.util.RogueLan.t("Das Spiel konnte nicht starten.","The game could not start."),true);return;}
   Forge.setLoadingaMatch(true);
   FThreads.invokeInEdtLater(go);
  });
 }
 /** a random public Moxfield commander deck of exactly this bracket, among the most viewed ones, updated in the last
  *  2 years (user 2026-11-15 "nicht älter als 2 jahre"); offline: one of Forge's commander precons */
 static Deck randomDeck(int b)throws Exception{
  Random rnd=new Random();long cutoff=System.currentTimeMillis()-730L*86_400_000L;
  List<Integer> pages=new ArrayList<>();for(int i=1;i<=12;i++)pages.add(i);Collections.shuffle(pages,rnd);
  boolean reached=false;
  for(int t=0;t<5;t++){
   JsonObject o;
   try{HttpURLConnection c=(HttpURLConnection)new URL("https://api2.moxfield.com/v2/decks/search?pageNumber="+pages.get(t)+"&pageSize=50&sortType=views&sortDirection=Descending&fmt=commander&minBracket="+b+"&maxBracket="+b).openConnection();
    c.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) RogueCommanders/1.0");c.setRequestProperty("Accept","application/json");
    c.setConnectTimeout(10000);c.setReadTimeout(15000);
    if(c.getResponseCode()!=200)continue;
    try(InputStream in=c.getInputStream()){o=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    reached=true;}
   catch(Exception e){System.err.println("[RogueLan] moxfield search "+e);continue;}
   List<String> ids=new ArrayList<>();
   for(JsonElement e:o.getAsJsonArray("data")){JsonObject x=e.getAsJsonObject();
    try{if(x.get("bracket").getAsInt()!=b||!x.get("isLegal").getAsBoolean()||x.get("mainboardCount").getAsInt()<90)continue;
     if(java.time.Instant.parse(x.get("lastUpdatedAtUtc").getAsString()).toEpochMilli()<cutoff)continue;
     String id=x.get("publicId").getAsString();if(!usedRandom.contains(id))ids.add(id);}catch(Exception ignored){}}   // several AIs: never the same deck twice
   Collections.shuffle(ids,rnd);
   for(int k=0;k<Math.min(3,ids.size());k++){
    try{warning=null;Deck d=buildDeck("https://moxfield.com/decks/"+ids.get(k));usedRandom.add(ids.get(k));return d;}catch(Exception e){System.err.println("[RogueLan] random deck "+ids.get(k)+": "+e);}}
  }
  // Moxfield not reachable: a Forge commander precon (about bracket 2)
  java.io.File[] precons=new java.io.File(forge.localinstance.properties.ForgeConstants.COMMANDER_PRECON_DIR).listFiles((dir,f)->f.endsWith(".dck"));
  if(precons!=null&&precons.length>0){Deck d=forge.deck.io.DeckSerializer.fromFile(precons[rnd.nextInt(precons.length)]);
   if(d!=null&&d.has(DeckSection.Commander)&&!d.get(DeckSection.Commander).isEmpty()){if(!reached)warning=forge.adventure.util.RogueLan.t("Moxfield nicht erreichbar - die KI spielt ein Forge-Precon.","Moxfield not reachable - the AI plays a Forge precon.");return d;}}
  throw new IllegalArgumentException(reached?forge.adventure.util.RogueLan.t("Kein passendes Bracket-","No fitting bracket ")+b+forge.adventure.util.RogueLan.t("-Deck gefunden - versuch es nochmal oder füge ein Deck für die KI ein."," deck found - try again or paste a deck for the AI."):forge.adventure.util.RogueLan.t("Moxfield ist nicht erreichbar - füge ein Deck für die KI ein.","Moxfield is not reachable - paste a deck for the AI."));
 }

 /** GameRules.warnAboutAICards (patched): Forge's "AI has problems with these cards" list stays off in 1v1 / vs AI games */
 public static boolean aiWarn(boolean warn){return warn&&!inMatch()&&state!=State.LOADING;}

 static void duoOn(){if(System.getProperty("forge.duo")==null){System.setProperty("forge.duo","1");setDuo=true;}german(true);devMode(true);startTimer();}
 private static String prevLang,prevCardLang;
 /** Forge's own texts (OK, End Turn, Keep, prompts) and the card names / rules texts in German while the LAN mode runs;
  *  the deck is still pasted in English or as a Moxfield link (user 2026-11-14). Card pictures stay as cached. */
 /** host/join error for the screen; a taken LAN port means an old game still runs in the background (user 2026-11-16) */
 /** true after a start failed because the LAN port is taken: the screen offers "close old instance" (user 2026-11-16
  *  "kommt eine meldung alte instanz beenden ? damit man ein double fail switch hat") */
 public static volatile boolean portBusy;
 /** ends every OTHER process of this same game program (same javaw.exe path) - an invisible old game that holds the port.
  *  Other Forge installs, the dev instance (JDK java.exe) and this window are never touched. Returns how many ended. */
 public static int killStale(){
  int n=0;
  try{ProcessHandle me=ProcessHandle.current();String exe=me.info().command().orElse(null);if(exe==null)return 0;
   for(ProcessHandle p:(Iterable<ProcessHandle>)ProcessHandle.allProcesses()::iterator){
    if(p.pid()==me.pid()||!exe.equalsIgnoreCase(p.info().command().orElse("")))continue;
    p.destroyForcibly();n++;
    try{p.onExit().get(4,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception ignored){}
   }}catch(Throwable t){System.err.println("[RogueLan] kill stale: "+t);}
  portBusy=false;return n;
 }
 static String startError(Throwable t){
  for(Throwable c=t;c!=null;c=c.getCause())if(c instanceof java.net.BindException){portBusy=true;
   return t("Ein altes Spiel läuft noch im Hintergrund und belegt den LAN-Port - \"Alte Instanz beenden\" drücken.",
            "An old game is still running in the background and holds the LAN port - press \"Close old instance\".");}
  return t("Start fehlgeschlagen: ","Start failed: ")+t;
 }
 static void german(boolean on){
  BattleUiSettings.lanBoard=on?board:"";
  boolean inMode=on;
  on=on&&!english;   // English chosen: Forge stays English, the normal pictures are used
  BattleUiSettings.german=on;
  // always set the wanted language outright (user 2026-11-16: after switching DE -> EN some card names stayed German):
  // German game -> de-DE, English game -> en-US, leaving the mode -> Forge's own setting. No remembered "previous"
  // language that could get lost; when the card language really changes, the cached card pictures/texts are dropped.
  String base=null;
  try{base=FModel.getPreferences().getPref(forge.localinstance.properties.ForgePreferences.FPref.UI_LANGUAGE);}catch(Throwable ignored){}
  if(base==null||!base.contains("-"))base="en-US";
  String want=on?"de-DE":inMode?"en-US":base;
  String dir=forge.localinstance.properties.ForgeConstants.LANG_DIR;
  try{forge.util.Localizer.getInstance().setLanguage(want,dir);}
  catch(Throwable t){System.err.println("[RogueLan] language "+t);}
  boolean changed=false;
  try{if(!want.equals(forge.util.CardTranslation.getLanguageSelected())){forge.util.CardTranslation.preloadTranslation(want,dir);changed=true;}}
  catch(Throwable t){System.err.println("[RogueLan] card language "+t);}
  prevLang=prevCardLang=null;
  if(changed)try{Gdx.app.postRunnable(()->{
   try{forge.assets.ImageCache.getInstance().clear();}catch(Throwable ignored){}
   try{forge.card.CardRenderer.clearcardArtCache();}catch(Throwable ignored){}});}catch(Throwable ignored){}
 }

 static void host(String n,String info){
  isHost=true;myWins=theirWins=0;opponent="";
  try{FModel.getNetPreferences().setPref(FNetPref.UPnP,online?"ALWAYS":"NEVER");FModel.getNetPreferences().save();}catch(Throwable ignored){}   // online: the router opens the port via UPnP while hosting; LAN: no UPnP
  FServerManager server=FServerManager.getInstance();
  ServerGameLobby lobby=new ServerGameLobby();hostLobby=lobby;
  server.startServer(PORT);
  server.setLobby(lobby);
  lobby.setListener(new IUpdateable(){
   @Override public void update(boolean full){server.updateLobbyState();hostCheck();}
   @Override public void update(int slot,LobbySlotType type){}});
  server.setLobbyListener(new ILobbyListener(){
   @Override public void message(String source,String message,ChatMessage.MessageType type){
    if(message!=null&&message.startsWith(CHEAT)){hostCheat(source,message);return;}
    if(message!=null&&message.startsWith("Warning"))set(state,message,true);}
   @Override public void update(GameLobby.GameLobbyData data,int slot){}
   @Override public void close(){}
   @Override public ClientGameLobby getLobby(){return null;}});
  lobby.applyVariant(GameType.Commander);
  for(int i=lobby.getNumberOfSlots();i<players;i++)lobby.addSlot();   // open seats for up to 3 friends
  aiDecks.clear();aiNames.clear();usedRandom.clear();
  server.updateSlot(0,UpdateLobbyPlayerEvent.nameUpdate(n));
  server.updateSlot(0,UpdateLobbyPlayerEvent.deckUpdate(deck));
  server.updateSlot(0,UpdateLobbyPlayerEvent.isReadyUpdate(true));
  duoOn();
  set(State.HOSTING,forge.adventure.util.RogueLan.t("Dein Deck: ","Your deck: ")+info+(online?forge.adventure.util.RogueLan.t("\nOnline: Port ","\nOnline: port ")+PORT+forge.adventure.util.RogueLan.t(" wird per UPnP im Router geoeffnet. Schick deinem Gegner deine Internet-IP "," is opened in the router via UPnP. Send your opponent your internet IP ")+internetIp()+forge.adventure.util.RogueLan.t("\nKommt er nicht rein: Port ","\nIf they cannot join: forward port ")+PORT+forge.adventure.util.RogueLan.t(" (TCP) im Router auf diesen PC weiterleiten."," (TCP) in the router to this PC.")
   :forge.adventure.util.RogueLan.t("\nWarte auf deinen Gegner - schick ihm deine IP ","\nWaiting for your opponent - send them your IP ")+lanIp())+(warning!=null?"\n"+warning:""),false);
 }
 /** host: both seats taken and ready -> start the match */
 static synchronized void hostCheck(){
  ServerGameLobby l=hostLobby;if(l==null||state==State.STARTING||state==State.MATCH||l.isMatchActive())return;
  LobbySlot me=l.getSlot(0);
  List<String> names=new ArrayList<>();int open=0,remote=0,ai=0;String waiting=null;
  for(int i=1;i<l.getNumberOfSlots();i++){LobbySlot s=l.getSlot(i);if(s==null)continue;
   if(s.getType()==LobbySlotType.OPEN){open++;continue;}
   String nm=s.getName()==null?"?":s.getName();names.add(nm);
   if(s.getType()==LobbySlotType.REMOTE){remote++;if(!s.isReady()||s.getDeck()==null)waiting=nm;}else if(s.getType()==LobbySlotType.AI)ai++;}
  opponent=String.join(", ",names);
  if(remote==0){if(state==State.LOBBY)set(State.HOSTING,forge.adventure.util.RogueLan.t("Dein Gegner ist weg. Warte - deine IP ist ","Your opponent left. Waiting - your IP is ")+lanIp(),true);return;}
  if(!me.isReady()){if(state!=State.AFTER)set(State.LOBBY,opponent+t(" ist da. Drück Nochmal spielen, wenn du bereit bist."," joined. Press Play again when you are ready."),false);return;}
  if(waiting!=null){set(State.LOBBY,waiting+t(" ist verbunden - warte auf sein Deck..."," is connected - waiting for the deck..."),false);return;}
  if(open>0&&!filling){   // seats left: wait for more friends, or the host presses "Jetzt starten" (free seats -> AI)
   set(State.LOBBY,t("Am Tisch: ","At the table: ")+opponent+" ("+(remote+ai+1)+"/"+l.getNumberOfSlots()+t(" Spieler). Warte auf weitere Freunde oder drück JETZT STARTEN - freie Plätze spielt die KI."," players). Wait for more friends or press START NOW - the AI takes the free seats."),false);return;}
  if(open>0)return;   // the AI decks are still loading (hostFill)
  set(State.STARTING,forge.adventure.util.RogueLan.t("Das Spiel gegen ","The game against ")+opponent+forge.adventure.util.RogueLan.t(" startet..."," is starting..."),false);
  FThreads.invokeInBackgroundThread(()->{
   // casual games between friends: Forge's "deck not legal - play anyway?" question cannot show in the Rogue app
   var prefs=FModel.getPreferences();boolean legality=prefs.getPrefBoolean(forge.localinstance.properties.ForgePreferences.FPref.ENFORCE_DECK_LEGALITY);
   Runnable go;
   try{applyTeams(l,false);prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.ENFORCE_DECK_LEGALITY,false);go=l.startGame();}
   finally{prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.ENFORCE_DECK_LEGALITY,legality);}
   if(go==null){set(State.LOBBY,forge.adventure.util.RogueLan.t("Das Spiel konnte nicht starten - siehe Meldung.","The game could not start - see the message."),true);return;}
   Forge.setLoadingaMatch(true);
   Runnable start=go;FThreads.invokeInEdtLater(start);   // no LoadingOverlay: Forge overlays do not draw over a Rogue scene
  });
 }

 /** host with free seats: "Jetzt starten" - each free seat gets an AI with a random deck of the chosen bracket, then the
  *  match starts with everybody who is at the table */
 public static boolean canStartNow(){
  ServerGameLobby l=hostLobby;if(l==null||filling||!isHost||state==State.STARTING||state==State.MATCH)return false;
  boolean open=false,remote=false;for(int i=1;i<l.getNumberOfSlots();i++){LobbySlot s=l.getSlot(i);if(s==null)continue;open|=s.getType()==LobbySlotType.OPEN;remote|=s.getType()==LobbySlotType.REMOTE;}
  return open&&remote;
 }
 public static void startNow(){
  if(!canStartNow())return;
  ServerGameLobby l=hostLobby;filling=true;int b=bracket;
  new Thread(()->{
   try{
    for(int i=1;i<l.getNumberOfSlots();i++){LobbySlot s=l.getSlot(i);if(s==null||s.getType()!=LobbySlotType.OPEN)continue;
     set(State.LOBBY,t("Suche ein Deck für die KI auf Platz ","Looking for a deck for the AI in seat ")+(i+1)+" (Bracket "+b+")...",false);
     Deck ai=randomDeck(b);warning=null;replacePacts(ai);
     String nm=t("KI ","AI ")+(i+1)+" ("+ai.get(DeckSection.Commander).toFlatList().get(0).getName()+")";
     if(s.getType()!=LobbySlotType.OPEN)continue;   // a friend took the seat meanwhile
     s.setType(LobbySlotType.AI);s.setName(nm);s.setDeck(ai);s.setIsReady(true);}
    FServerManager.getInstance().updateLobbyState();
   }catch(Throwable t){System.err.println("[RogueLan] start now: "+t);set(state,t instanceof IllegalArgumentException?t.getMessage():t("Kein KI-Deck gefunden: ","No AI deck found: ")+t,true);}
   finally{filling=false;}
   hostCheck();
  },"RogueLan-startnow").start();
 }

 static void join(String n,String address,String info){
  isHost=false;sentDeck=false;myWins=theirWins=0;opponent="";
  String hostName=address;int port=PORT;
  int c=address.lastIndexOf(':');if(c>0&&address.indexOf(':')==c){try{port=Integer.parseInt(address.substring(c+1).trim());hostName=address.substring(0,c).trim();}catch(NumberFormatException ignored){}}
  set(State.CONNECTING,forge.adventure.util.RogueLan.t("Verbinde mit ","Connecting to ")+hostName+" ...",false);
  final String hn=hostName;
  IGuiGame gui=GuiBase.getInterface().getNewGuiGame();
  FGameClient cl=new FGameClient(n,gui,hn,port);client=cl;
  ClientGameLobby lobby=new ClientGameLobby();clientLobby=lobby;
  lobby.setListener(new IUpdateable(){@Override public void update(boolean full){}@Override public void update(int slot,LobbySlotType type){}});
  if(gui instanceof forge.gamemodes.match.AbstractGuiGame agg)agg.setClientLobby(lobby);
  cl.addLobbyListener(new ILobbyListener(){
   @Override public void message(String source,String message,ChatMessage.MessageType type){if(message!=null&&message.startsWith("Warning"))set(state,message,true);}
   @Override public void update(GameLobby.GameLobbyData data,int slot){
    lobby.setLocalPlayer(slot);lobby.setData(data);
    List<String> others=new ArrayList<>();
    for(int i=0;i<lobby.getNumberOfSlots();i++){LobbySlot s=lobby.getSlot(i);if(i!=slot&&s!=null&&s.getType()!=LobbySlotType.OPEN&&s.getName()!=null&&!s.getName().isBlank())others.add(s.getName());}
    opponent=String.join(", ",others);
    if(!sentDeck){sentDeck=true;cl.send(UpdateLobbyPlayerEvent.deckUpdate(deck));cl.send(UpdateLobbyPlayerEvent.isReadyUpdate(true));
     set(State.LOBBY,forge.adventure.util.RogueLan.t("Verbunden mit ","Connected to ")+(opponent.isEmpty()?hn:opponent)+forge.adventure.util.RogueLan.t(". Dein Deck: ",". Your deck: ")+info+forge.adventure.util.RogueLan.t("\nDas Spiel startet gleich...","\nThe game starts in a moment...")+(warning!=null?"\n"+warning:""),false);}}
   @Override public void close(){
    boolean wasMatch=state==State.MATCH;client=null;
    set(State.IDLE,forge.adventure.util.RogueLan.t("Die Verbindung zum Host ist abgebrochen.","The connection to the host was lost."),true);
    if(wasMatch)Gdx.app.postRunnable(RogueLan::leaveMatch);}
   @Override public ClientGameLobby getLobby(){return lobby;}});
  duoOn();
  try{cl.connect();}
  catch(Exception e){client=null;
   set(State.IDLE,forge.adventure.util.RogueLan.t("Keine Verbindung zu ","No connection to ")+hn+forge.adventure.util.RogueLan.t(". Prüf die IP, ob der Host zuerst SPIEL STARTEN gedrückt hat und ob die Windows-Firewall Rogue Commanders erlaubt (private Netzwerke).",". Check the IP, whether the host pressed START GAME first and whether the Windows firewall allows Rogue Commanders (private networks)."),true);}
 }

 /** after a match: ready again (host and friend both press it) */
 public static void playAgain(){
  if(state!=State.AFTER&&state!=State.LOBBY)return;
  if(!deckText.isBlank())try{Deck d=buildDeck(deckText);deck=d;}catch(Exception e){set(state,e.getMessage(),true);return;}
  if(aiLobby!=null&&aiDeck!=null){aiGo(name.isBlank()?"Commander":name.trim());return;}   // vs AI: rematch with the same AI deck
  if(isHost&&hostLobby!=null){FServerManager s=FServerManager.getInstance();s.updateSlot(0,UpdateLobbyPlayerEvent.deckUpdate(deck));s.updateSlot(0,UpdateLobbyPlayerEvent.isReadyUpdate(true));
   set(State.LOBBY,forge.adventure.util.RogueLan.t("Bereit - warte, bis ","Ready - waiting until ")+(opponent.isEmpty()?forge.adventure.util.RogueLan.t("dein Gegner","your opponent"):opponent)+forge.adventure.util.RogueLan.t(" Nochmal spielen drückt."," presses Play again."),false);hostCheck();}
  else if(client!=null){client.send(UpdateLobbyPlayerEvent.deckUpdate(deck));client.send(UpdateLobbyPlayerEvent.isReadyUpdate(true));
   set(State.LOBBY,forge.adventure.util.RogueLan.t("Bereit - der Host startet das nächste Spiel.","Ready - the host starts the next game."),false);}
  else set(State.IDLE,forge.adventure.util.RogueLan.t("Nicht mehr verbunden - drück SPIEL STARTEN.","No longer connected - press START GAME."),true);
 }

 public static void stop(){
  try{if(client!=null){client.close();}}catch(Throwable t){System.err.println("[RogueLan] "+t);}
  client=null;clientLobby=null;
  try{if(hostLobby!=null)FServerManager.getInstance().stopServer();}catch(Throwable t){System.err.println("[RogueLan] "+t);}
  hostLobby=null;deck=null;aiLobby=null;aiDeck=null;
  if(setDuo){System.clearProperty("forge.duo");setDuo=false;}
  german(false);devMode(false);
  if(timer!=null){timer.cancel();timer=null;}
  if(state!=State.IDLE)set(State.IDLE,"",false);
 }


 // ------------------------------------------------------------------ same game for host and joining player (user 2026-11-14)
 /** joining player's card click (NetGameController.selectCard, patched): true = send it to the host, false = Forge opens
  *  the big view - wanted targets / choices and your own cards are sent, everything else is just looked at */
 public static boolean netSelect(forge.game.card.CardView c){
  try{
   if(!inMatch()||c==null)return true;
   var ui=MatchController.instance;if(ui==null)return true;
   if(ui.isSelectable(c))return true;
   var me=ui.getCurrentPlayer();var z=c.getZone();if(me==null||z==null)return true;
   boolean mine=me.equals(c.getController())||me.equals(c.getOwner());
   return mine&&(z==forge.game.zone.ZoneType.Hand||z==forge.game.zone.ZoneType.Battlefield||z==forge.game.zone.ZoneType.Command
    ||z==forge.game.zone.ZoneType.Graveyard||z==forge.game.zone.ZoneType.Exile);
  }catch(Throwable t){return true;}
 }
 static final String CHEAT="~rogue:cheat:";
 /** joining player's dev menu (NetGameController.cheat, patched): each command goes to the host, which runs it */
 public static forge.interfaces.IDevModeCheats netCheat(forge.interfaces.IDevModeCheats none){
  FGameClient cl=client;if(!inMatch()||cl==null)return none;
  return (forge.interfaces.IDevModeCheats)java.lang.reflect.Proxy.newProxyInstance(RogueLan.class.getClassLoader(),new Class<?>[]{forge.interfaces.IDevModeCheats.class},(proxy,m,args)->{
   if(m.getDeclaringClass()==Object.class)return m.invoke(none,args);
   if(m.getParameterCount()==0&&m.getReturnType()==void.class){cl.send(new forge.gamemodes.net.event.MessageEvent(CHEAT+m.getName()));return null;}
   Class<?> r=m.getReturnType();return r==boolean.class?false:r==int.class?0:null;});
 }
 /** host: a dev command from the joining player - run it on the host's game; choices appear at the player who asked.
  *  Rollback uses the host's saved phase state (it is one game for both). */
 static void hostCheat(String source,String message){
  try{
   String name=message.substring(CHEAT.length()).trim();ServerGameLobby l=hostLobby;if(l==null)return;
   java.lang.reflect.Method m=forge.interfaces.IDevModeCheats.class.getMethod(name);if(m.getParameterCount()!=0)return;
   int slot=-1;for(int i=0;i<l.getNumberOfSlots();i++){LobbySlot s=l.getSlot(i);if(s!=null&&source!=null&&source.startsWith(String.valueOf(s.getName()))&&s.getType()==LobbySlotType.REMOTE)slot=i;}
   var ctrl=l.getController(name.equals("rollbackPhase")||slot<0?0:slot);if(ctrl==null)return;
   forge.util.ThreadUtil.invokeInGameThread(()->{try{m.invoke(ctrl.cheat());}catch(Throwable t){System.err.println("[RogueLan] cheat "+name+": "+t);}});
  }catch(Throwable t){System.err.println("[RogueLan] cheat: "+t);}
 }
 private static Boolean prevDev;
 /** Forge's dev menu (rollback, add cards, life ...) in LAN games on both PCs, back to the old setting afterwards */
 static void devMode(boolean on){
  try{if(on){if(prevDev==null)prevDev=forge.localinstance.properties.ForgePreferences.DEV_MODE;forge.localinstance.properties.ForgePreferences.DEV_MODE=true;}
   else if(prevDev!=null){forge.localinstance.properties.ForgePreferences.DEV_MODE=prevDev;prevDev=null;}}catch(Throwable t){System.err.println("[RogueLan] dev mode "+t);}
 }


 // ------------------------------------------------------------------ German card pictures (user 2026-11-14)
 /** own folder next to Forge's picture cache; the English pictures for Rogue stay untouched */
 static java.io.File germanDir(){return new java.io.File(new java.io.File(forge.localinstance.properties.ForgeConstants.CACHE_CARD_PICS_DIR).getParentFile(),"cards-de");}
 private static final Set<String> deQueued=java.util.concurrent.ConcurrentHashMap.newKeySet(),deMissing=java.util.concurrent.ConcurrentHashMap.newKeySet();
 private static java.util.concurrent.ExecutorService deLoader;private static final java.util.concurrent.atomic.AtomicInteger dePending=new java.util.concurrent.atomic.AtomicInteger();
 private static volatile boolean deFresh;
 /** letters and digits only, no accents: card names and Forge's picture file names meet here */
 static String canon(String s){return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
 static String deFile(String name){return canon(name)+"-v2.png";}   // v2: borderless preferred, no textless / full art / renamed prints   // full-size png (user 2026-11-14: jpg quality too low)
 private static volatile Map<String,String> realNames;
 /** simplified name -> real card name (for the Scryfall search), built once from Forge's card database */
 static String realName(String c){
  Map<String,String> m=realNames;
  if(m==null){m=new HashMap<>();for(PaperCard pc:StaticData.instance().getCommonCards().getUniqueCards())m.putIfAbsent(canon(pc.getName()),pc.getName());realNames=m;}
  return m.get(c);
 }
 /** ImageKeys.getImageFile (patched): during a LAN game a German scan replaces the picture once it is on disk;
  *  a missing one is fetched in the background (the English picture shows until then) */
 /** ImageKeys.getImageFile (patched): German scan in German games, then the sharp AI copy when there is one (CardUpscaler) */
 public static java.io.File germanImage(java.io.File file,String key){
  java.io.File f=file;   // card pictures stay English in every language (user 2026-11-15: German scans were too poor);
                         // German players read the German text with arrow down in the big view (CardZoom details)
  try{   // own commander artwork chosen on the 1v1 / LAN screen (local picture, never an online print)
   if(!cmdCustom.isEmpty()&&inMatch()){String n=keyName(key);String p=n==null?null:cmdCustom.get(n);
    if(p!=null){java.io.File c=new java.io.File(p);if(c.isFile())return c;}}
  }catch(Throwable ignored){}
  try{return CardUpscaler.hd(f);}catch(Throwable t){return f;}
 }
 /** key of cmdCustom for a commander name (front face, same form as keyName) */
 public static String cmdKey(String name){String n=name==null?"":name.split(" // ")[0];return canon(n).replaceAll("\\d+$","");}
 /** canon card name of a Forge picture key ("c:SET/Name1.fullborder", "Name|SET|1"), null for tokens and others */
 static String keyName(String key){
  if(key==null||key.isEmpty())return null;
  if(key.length()>2&&key.charAt(1)==':'){if(!key.startsWith("c:"))return null;key=key.substring(2);}
  String name=key;int bar=name.indexOf('|');if(bar>0)name=name.substring(0,bar);
  int slash=Math.max(name.lastIndexOf('/'),name.lastIndexOf('\\'));if(slash>=0)name=name.substring(slash+1);
  name=name.replaceAll("(?i)\\.(fullborder|full|jpg|jpeg|png)$","").replaceAll("(?i)\\.(fullborder|full)$","");
  String c=canon(name).replaceAll("\\d+$","");return c.isEmpty()?null:c;
 }
 static java.io.File germanImage0(java.io.File file,String key){
  try{
   if(!BattleUiSettings.german||key==null||key.isEmpty())return file;
   if(key.length()>2&&key.charAt(1)==':'){if(!key.startsWith("c:"))return file;key=key.substring(2);}
   // Forge keys look like "TDM/Plains1.fullborder" (set folder, art number) or "Plains|TDM|1"
   String name=key;int bar=name.indexOf('|');if(bar>0)name=name.substring(0,bar);
   int slash=Math.max(name.lastIndexOf('/'),name.lastIndexOf('\\'));if(slash>=0)name=name.substring(slash+1);
   name=name.replaceAll("(?i)\\.(fullborder|full|jpg|jpeg|png)$","").replaceAll("(?i)\\.(fullborder|full)$","");
   String c=canon(name),plain=c.replaceAll("\\d+$","");if(plain.isEmpty())return file;
   for(String k:new String[]{plain,c}){java.io.File de=new java.io.File(germanDir(),k+"-v2.png");if(de.isFile()&&de.length()>1000)return de;}
   String real=realName(plain);if(real==null)real=realName(c);
   if(real!=null)fetchGerman(real);
  }catch(Throwable t){}
  return file;
 }
 static void fetchGerman(String name){
  if(deMissing.contains(name)||!deQueued.add(name))return;
  dePending.incrementAndGet();
  synchronized(RogueLan.class){if(deLoader==null)deLoader=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"RogueLan-de-pics");t.setDaemon(true);return t;});}
  deLoader.submit(()->{
   try{
    java.io.File out=new java.io.File(germanDir(),deFile(name));if(out.isFile())return;
    String chosen=cmdArt.get(name);if(chosen!=null){download(chosen,out);deFresh=true;return;}   // commander artwork picked on the LAN screen
    String q=java.net.URLEncoder.encode("!\""+name+"\" lang:de",StandardCharsets.UTF_8);
    HttpURLConnection c=(HttpURLConnection)new URL("https://api.scryfall.com/cards/search?q="+q+"&unique=prints&order=released&dir=desc").openConnection();
    c.setRequestProperty("User-Agent","RogueCommanders/1.0");c.setRequestProperty("Accept","application/json");c.setConnectTimeout(8000);c.setReadTimeout(12000);
    int code=c.getResponseCode();
    for(int tries=1;(code==429||code>=500)&&tries<=4;tries++){   // Scryfall busy / rate limit: wait and ask again
     Thread.sleep(1500L*tries);c=(HttpURLConnection)new URL("https://api.scryfall.com/cards/search?q="+q+"&unique=prints&order=released&dir=desc").openConnection();
     c.setRequestProperty("User-Agent","RogueCommanders/1.0");c.setRequestProperty("Accept","application/json");c.setConnectTimeout(8000);c.setReadTimeout(12000);code=c.getResponseCode();}
    if(code==404){deMissing.add(name);Thread.sleep(120);return;}   // no German print
    if(code!=200){deQueued.remove(name);System.err.println("[RogueLan] German picture "+name+": HTTP "+code);Thread.sleep(500);return;}
    JsonObject o;try(InputStream in=c.getInputStream()){o=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
    String img=null;
    // pick the print (user 2026-11-14): no textless / full art / other printed name (Captain Kirk for Sisay); borderless first, else the newest
    int bestScore=Integer.MIN_VALUE;
    for(JsonElement e:o.getAsJsonArray("data")){JsonObject card=e.getAsJsonObject();
     JsonObject face=card.has("card_faces")?card.getAsJsonArray("card_faces").get(0).getAsJsonObject():null;
     JsonObject iu=card.has("image_uris")?card.getAsJsonObject("image_uris"):face!=null&&face.has("image_uris")?face.getAsJsonObject("image_uris"):null;
     if(iu==null||!(iu.has("png")||iu.has("large")))continue;
     boolean textless=card.has("textless")&&card.get("textless").getAsBoolean(),fullArt=card.has("full_art")&&card.get("full_art").getAsBoolean();
     boolean renamed=card.has("flavor_name")||face!=null&&face.has("flavor_name");
     boolean borderless=card.has("border_color")&&"borderless".equals(card.get("border_color").getAsString());
     int score=(textless?-100:0)+(fullArt?-100:0)+(renamed?-200:0)+(borderless?10:0);
     if(score>bestScore){bestScore=score;img=(iu.has("png")?iu.get("png"):iu.get("large")).getAsString();}}   // newest first: ties keep the newer print
    Thread.sleep(300);   // Scryfall asks for at most ~10 requests per second - both LAN PCs share one home IP
    if(img==null){deMissing.add(name);return;}
    HttpURLConnection d=(HttpURLConnection)new URL(img).openConnection();d.setRequestProperty("User-Agent","RogueCommanders/1.0");d.setConnectTimeout(8000);d.setReadTimeout(20000);
    if(d.getResponseCode()!=200){deMissing.add(name);return;}
    out.getParentFile().mkdirs();java.io.File tmp=new java.io.File(out.getPath()+".tmp");
    try(InputStream in=d.getInputStream()){Files.copy(in,tmp.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    Files.move(tmp.toPath(),out.toPath(),StandardCopyOption.REPLACE_EXISTING);
    deFresh=true;Thread.sleep(300);
   }catch(Throwable t){deQueued.remove(name);System.err.println("[RogueLan] German picture "+name+": "+t);}   // network hiccup: may be asked again later
   finally{if(dePending.decrementAndGet()==0&&deFresh){deFresh=false;   // batch done: pick the new pictures up once
    Gdx.app.postRunnable(()->{try{forge.assets.ImageCache.getInstance().clear();}catch(Throwable ignored){}});}}
  });
 }
 /** your own deck: fetch its German pictures while the game is being set up */
 static void prefetchGerman(Deck d){
  try{if(d==null)return;for(var e:d.getMain())fetchGerman(e.getKey().getName());if(d.has(DeckSection.Commander))for(var e:d.get(DeckSection.Commander))fetchGerman(e.getKey().getName());}catch(Throwable ignored){}
 }


 // ------------------------------------------------------------------ commander artwork chooser (user 2026-11-15)
 /** {label, png url, preview url} of every print of the commander (German first, else English) */
 public static volatile List<String[]> cmdPrints=List.of();
 public static volatile int cmdIndex;public static volatile boolean cmdLoading;public static volatile String cmdError="",cmdName="";
 public static void loadCommanderPrints(String text){
  // only the commander's name: the artwork chooser lists own pictures, no Scryfall prints any more (user 2026-11-16
  // "diese online artworks ... sehen scheisse aus"); queryPrints stays unused
  if(cmdLoading)return;cmdLoading=true;cmdError="";cmdPrints=List.of();cmdName="";
  new Thread(()->{
   try{Deck d=buildDeck(text);cmdName=d.get(DeckSection.Commander).toFlatList().get(0).getName();
   }catch(IllegalArgumentException e){cmdError=e.getMessage();}
   catch(Throwable t){cmdError="Fehler beim Laden: "+t.getMessage();}
   finally{cmdLoading=false;}
  },"RogueLan-cmdart").start();
 }
 // ------------------------------------------------------------------ deck copies + artwork picker (user 2026-11-17: "moxfield decks
 // erstmal als kopie ins game importen aber nur wenn man das will, anschliessend eine deck auswahl wo man sein deck nochmal mit
 // artworks bearbeiten kann"). Copies are normal Forge .dck files in decks/commander (Forge's deck editor sees them too).
 public static final String DECK_PREFIX="deck: ";
 /** the game's own Decks folder (user 2026-11-20 "ordner innerhalb der game files der decks als txt speichert"): plain lists,
  *  one card per line, readable by hand and written by the MTG commander builder. "D:/forge/" is relocated to the install
  *  folder on every PC and on the phone. Every deck that was played once is stored here automatically. */
 public static java.io.File txtDir(){
  java.io.File d=new java.io.File("D:/forge/Decks/");
  if(!d.isDirectory()&&d.mkdirs())try{Files.writeString(new java.io.File(d,"_LIESMICH.md").toPath(),
   "# Decks\n\nJede .txt-Datei in diesem Ordner ist ein Deck fuer Rogue Commanders (1v1 vs KI und LAN, Knopf \"Meine Decks\").\n"+
   "Eine Karte pro Zeile: Anzahl, Name, optional (SET) Sammlernummer. Ueberschriften wie Commander, (commander), Artifacts,\n"+
   "Lands (34): oder ## Creatures werden erkannt; Zeilen mit // oder # sind Kommentare. Gespielte Decks landen hier automatisch.\n\n"+
   "Every .txt file here is a deck. One card per line: count, name, optional (SET) collector number. Headers like Commander,\n"+
   "(commander), Artifacts or Lands (34): are understood. Every deck you play once is saved here automatically.\n",StandardCharsets.UTF_8);}
   catch(Exception e){System.err.println("[RogueLan] decks readme: "+e);}
  return d;
 }
 static boolean isTxt(java.io.File f){return f.getName().toLowerCase(Locale.ROOT).endsWith(".txt");}
 static String baseName(java.io.File f){String n=f.getName();int i=n.lastIndexOf('.');return i>0?n.substring(0,i):n;}
 /** UTF-8, else Windows-1252 (Notepad lists with umlauts) */
 static String readText(java.io.File f)throws java.io.IOException{
  byte[] b=Files.readAllBytes(f.toPath());
  try{return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString();}
  catch(java.nio.charset.CharacterCodingException e){return new String(b,java.nio.charset.Charset.forName("windows-1252"));}
 }
 static final Pattern SRC_LINE=Pattern.compile("(?im)^\\s*(?://|#)\\s*(?:source|quelle|moxfield)\\s*:\\s*(https?://\\S+)");
 static String deckBase(Deck d){
  String b=d.getName()==null||d.getName().isBlank()||d.getName().equals("LAN deck")?d.get(DeckSection.Commander).toFlatList().get(0).getName():d.getName();
  b=b.replaceAll("[\\\\/:*?\"<>|]","").trim();return b.isEmpty()?"Deck":b;
 }
 static String txtLine(int n,PaperCard p){String cn=p.getCollectorNumber();
  return n+" "+p.getName()+" ("+p.getEdition()+")"+(cn==null||cn.isBlank()||cn.equals("0")?"":" "+cn)+"\n";}
 /** writes the deck as a plain list with its printings */
 static void writeTxt(Deck d,java.io.File f,String src)throws java.io.IOException{
  StringBuilder sb=new StringBuilder("// Rogue Commanders - eine Karte pro Zeile: Anzahl Name (SET) Nummer\n");
  if(src!=null&&!src.isBlank())sb.append("// Source: ").append(src).append('\n');
  sb.append("\nCommander\n");if(d.has(DeckSection.Commander))for(var e:d.get(DeckSection.Commander))sb.append(txtLine(e.getValue(),e.getKey()));
  List<Map.Entry<PaperCard,Integer>> l=new ArrayList<>();for(var e:d.getMain())l.add(e);l.sort(Comparator.comparing(e->e.getKey().getName()));
  sb.append("\nDeck\n");for(var e:l)sb.append(txtLine(e.getValue(),e.getKey()));
  f.getParentFile().mkdirs();Files.writeString(f.toPath(),sb.toString(),StandardCharsets.UTF_8);
 }
 static void writeAny(Deck d,java.io.File f)throws java.io.IOException{if(isTxt(f))writeTxt(d,f,d.getSourceUrl());else forge.deck.io.DeckSerializer.writeDeck(d,f);}
 /** a deck that was just played (own list/link or a pasted AI deck) -> Decks/<name>.txt, once; an existing file is kept as it is */
 static void remember(String text,Deck d){
  try{if(d==null||text==null||text.isBlank()||savedFile(text)!=null)return;
   java.io.File f=new java.io.File(txtDir(),deckBase(d)+".txt");if(f.exists())return;
   Matcher m=MOX.matcher(text.trim());writeTxt(d,f,m.find()?"https://moxfield.com/decks/"+m.group(1):null);
   System.err.println("[RogueLan] deck saved: "+f);
  }catch(Throwable e){System.err.println("[RogueLan] remember deck: "+e);}
 }
 public static java.io.File deckDir(){return new java.io.File(forge.localinstance.properties.ForgeConstants.DECK_COMMANDER_DIR);}
 /** "deck: Name" in the deck field -> that file in decks/commander, else null */
 public static java.io.File savedFile(String text){
  String s=text==null?"":text.trim();if(!s.toLowerCase(Locale.ROOT).startsWith("deck:"))return null;
  String n=s.substring(5).trim();if(n.isEmpty())return null;
  if(n.toLowerCase(Locale.ROOT).endsWith(".txt"))return new java.io.File(txtDir(),n);
  return new java.io.File(deckDir(),n.endsWith(".dck")?n:n+".dck");
 }
 public static String deckLabel(java.io.File f){
  if(isTxt(f))return txtDir().toPath().relativize(f.toPath()).toString().replace('\\','/');
  String r=deckDir().toPath().relativize(f.toPath()).toString().replace('\\','/');return r.endsWith(".dck")?r.substring(0,r.length()-4):r;
 }
 /** every commander deck in decks/commander (also sub folders), newest first */
 public static List<java.io.File> savedDecks(){
  List<java.io.File> out=new ArrayList<>();
  try(var st=Files.walk(txtDir().toPath(),3)){st.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".txt")&&!p.getFileName().toString().startsWith("_")).forEach(p->out.add(p.toFile()));}
  catch(Exception e){System.err.println("[RogueLan] txt decks: "+e);}
  if(deckDir().isDirectory())try(var st=Files.walk(deckDir().toPath(),3)){st.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".dck")).forEach(p->out.add(p.toFile()));}
  catch(Exception e){System.err.println("[RogueLan] decks: "+e);}
  out.sort(Comparator.comparingLong(java.io.File::lastModified).reversed());return out;
 }
 public static Deck readDeck(java.io.File f){
  if(isTxt(f))try{String txt=readText(f),w=warning;Deck d=buildDeck(txt);warning=w;d.setName(baseName(f));
   Matcher m=SRC_LINE.matcher(txt);if(m.find())d.setSourceUrl(m.group(1).trim());return d;}
   catch(Exception e){System.err.println("[RogueLan] read "+f+": "+e);return null;}
  try{return forge.deck.io.DeckSerializer.fromFile(f);}catch(Exception e){System.err.println("[RogueLan] read "+f+": "+e);return null;}}
 public static volatile boolean saving;
 /** saves the deck of the field (Moxfield link or list) as a copy with its printings and selects it; result text for a toast */
 public static void saveCopy(String text,java.util.function.Consumer<String> done){
  if(saving)return;saving=true;
  new Thread(()->{String msg;
   try{if(savedFile(text)!=null)throw new IllegalArgumentException(t("Das ist schon eine gespeicherte Kopie.","This already is a saved copy."));
    Deck d=buildDeck(text);String w=warning;
    String base=deckBase(d);   // user 2026-11-20: copies are plain lists in the game's Decks folder
    java.io.File f=new java.io.File(txtDir(),base+".txt");for(int k=2;f.exists();k++)f=new java.io.File(txtDir(),base+" ("+k+").txt");
    d.setName(baseName(f));
    Matcher m=MOX.matcher(text.trim());if(m.find())d.setSourceUrl("https://moxfield.com/decks/"+m.group(1));
    writeTxt(d,f,d.getSourceUrl());
    deckText=DECK_PREFIX+deckLabel(f);save();
    msg=t("Als Kopie gespeichert: ","Saved as a copy: ")+d.getName()+(w==null?"":" - "+w);}
   catch(IllegalArgumentException e){msg=e.getMessage();}
   catch(Throwable e){msg=t("Speichern fehlgeschlagen: ","Saving failed: ")+e.getMessage();}
   finally{saving=false;}
   done.accept(msg);
  },"RogueLan-savecopy").start();
 }
 /** newer list from Moxfield, keeping the chosen printing of every card that is still in the deck */
 public static void refreshCopy(java.io.File f,java.util.function.Consumer<String> done){
  if(saving)return;saving=true;
  new Thread(()->{String msg;
   try{Deck old=readDeck(f);if(old==null||old.getSourceUrl()==null)throw new IllegalArgumentException(t("Dieses Deck hat keinen Moxfield-Link.","This deck has no Moxfield link."));
    Deck d=buildDeck(old.getSourceUrl());Map<String,PaperCard> keep=new HashMap<>();
    for(var sec:old)for(var e:sec.getValue())keep.put(e.getKey().getName(),e.getKey());
    Deck n=new Deck(old.getName());n.setSourceUrl(old.getSourceUrl());
    for(var sec:d)for(var e:sec.getValue()){PaperCard p=keep.getOrDefault(e.getKey().getName(),e.getKey());n.getOrCreate(sec.getKey()).add(p,e.getValue());}
    writeAny(n,f);msg=t("Von Moxfield neu geladen: ","Reloaded from Moxfield: ")+n.getName();}
   catch(IllegalArgumentException e){msg=e.getMessage();}
   catch(Throwable e){msg=t("Neu laden fehlgeschlagen: ","Reload failed: ")+e.getMessage();}
   finally{saving=false;}
   done.accept(msg);
  },"RogueLan-refresh").start();
 }

 public static volatile List<PaperCard> artCards=List.of();public static volatile java.io.File artFile;
 /** the copy's distinct cards (commander first, then A-Z) with their printing */
 public static void loadArtDeck(java.io.File f){
  artFile=f;List<PaperCard> l=new ArrayList<>(),rest=new ArrayList<>();Set<String> seen=new HashSet<>();Deck d=readDeck(f);
  if(d!=null){if(d.has(DeckSection.Commander))for(var e:d.get(DeckSection.Commander))if(seen.add(e.getKey().getName()))l.add(e.getKey());
   for(var e:d.getMain())if(seen.add(e.getKey().getName()))rest.add(e.getKey());}
  rest.sort(Comparator.comparing(PaperCard::getName));l.addAll(rest);artCards=l;
 }
 /** every printing Forge knows of a card, newest first, one per set + collector number */
 public static List<PaperCard> printings(String name){
  List<PaperCard> out=new ArrayList<>();Set<String> seen=new HashSet<>();
  try{for(PaperCard p:StaticData.instance().getCommonCards().getAllCards(name))if(seen.add(p.getEdition()+"|"+p.getCollectorNumber()))out.add(p);
   var eds=StaticData.instance().getEditions();
   out.sort(Comparator.comparing((PaperCard p)->{var ed=eds.get(p.getEdition());return ed==null||ed.getDate()==null?new Date(0):ed.getDate();}).reversed());}
  catch(Exception e){System.err.println("[RogueLan] printings "+name+": "+e);}
  return out;
 }
 /** all copies of this card in the saved deck get printing p */
 public static boolean pick(String card,PaperCard p){
  java.io.File f=artFile;Deck d=f==null?null:readDeck(f);if(d==null||p==null)return false;
  for(var sec:d){var pool=sec.getValue();int n=0;
   for(var e:pool)if(e.getKey().getName().equals(card))n+=e.getValue();
   if(n>0){for(PaperCard c:new ArrayList<>(pool.toFlatList()))if(c.getName().equals(card))pool.remove(c,1);pool.add(p,n);}}
  try{writeAny(d,f);}catch(java.io.IOException x){System.err.println("[RogueLan] pick: "+x);return false;}
  List<PaperCard> l=new ArrayList<>(artCards);for(int i=0;i<l.size();i++)if(l.get(i).getName().equals(card))l.set(i,p);artCards=l;
  return true;
 }

 // ------------------------------------------------------------------ deck tags (user 2026-11-20: "der deck auswaehler soll auch tags
 // erkennen wie +1 counter, lotr theme deck, nur die 2 dominantesten tags, automatisch", "auf englisch")
 /** {tag, weight}: a card counts for a tag when its rules text / types match; score = matching cards x weight */
 static final Object[][] TAG_RULES={
  {"+1/+1 Counters",1.4,Pattern.compile("(?i)\\+1/\\+1 counter|proliferate")},
  {"Tokens",1.2,Pattern.compile("(?i)create[^.]*token")},
  {"Graveyard",1.1,Pattern.compile("(?i)from (?:your|a) graveyard|reanimate|return target creature card")},
  {"Sacrifice",1.0,Pattern.compile("(?i)sacrifice (?:a|another) (?:creature|permanent|artifact)|whenever (?:a|another) creature you control dies")},
  {"Landfall",1.3,Pattern.compile("(?i)landfall|whenever a land (?:you control )?enters|play an additional land|put a land card")},
  {"Lifegain",1.2,Pattern.compile("(?i)gain(?:s)? \\d+ life|gain life|lifelink|whenever you gain life")},
  {"Spellslinger",1.0,Pattern.compile("(?i)whenever you cast (?:an|your) (?:instant|sorcery|noncreature)|copy target (?:instant|sorcery)|magecraft|prowess")},
  {"Mill",1.4,Pattern.compile("(?i)\\bmills?\\b|into (?:his or her|their) graveyard from the top")},
  {"Control",0.9,Pattern.compile("(?i)counter target|destroy all|exile all|return all .* to their owners")},
  {"Voltron",1.2,Pattern.compile("(?i)equipped creature|enchanted creature gets|equip \\{")},
  {"Flying",0.8,Pattern.compile("(?i)\\bflying\\b")},
  {"Card Draw",0.5,Pattern.compile("(?i)draw (?:a|two|three|x) cards?")},
  {"Ramp",0.6,Pattern.compile("(?i)add \\{|search your library for (?:a|up to \\w+) (?:basic )?lands?")},
  {"Blink",1.4,Pattern.compile("(?i)exile (?:target|another) [^.]*return (?:it|that card) to the battlefield")},
  {"Theft",1.4,Pattern.compile("(?i)gain control of|you may cast (?:it|spells) from among|cards your opponents own")},
  {"Discard",1.2,Pattern.compile("(?i)(?:each opponent|target player|target opponent) discards")},
  {"Burn",1.1,Pattern.compile("(?i)deals? \\d+ damage to (?:each opponent|any target|target player)")}};
 /** {tag, words in the set name}: decks whose printings mostly come from one Universes Beyond world */
 static final String[][] THEME_SETS={{"LOTR Theme","lord of the rings"},{"Final Fantasy Theme","final fantasy"},{"Warhammer 40K Theme","warhammer 40"},
  {"Doctor Who Theme","doctor who"},{"Fallout Theme","fallout"},{"Marvel Theme","marvel"},{"Avatar Theme","avatar"},{"Assassin's Creed Theme","assassin"},
  {"Secret Lair Theme","secret lair"}};
 private static final Map<String,List<String>> tagCache=new HashMap<>();
 /** the two most dominant themes of a deck, e.g. ["+1/+1 Counters","LOTR Theme"] */
 public static List<String> deckTags(java.io.File f,Deck d){
  String key=f==null?null:f.getPath()+"|"+f.lastModified();
  if(key!=null)synchronized(tagCache){List<String> c=tagCache.get(key);if(c!=null)return c;}
  Map<String,Double> score=new HashMap<>();Map<String,Integer> tribes=new HashMap<>(),themes=new HashMap<>();int cards=0,arts=0,ench=0,spells=0;
  if(d!=null)for(var sec:d){if(sec.getKey()!=DeckSection.Main&&sec.getKey()!=DeckSection.Commander)continue;
   for(var e:sec.getValue()){PaperCard p=e.getKey();var r=p.getRules();if(r.getType().isLand()&&r.getType().isBasicLand())continue;
    int n=sec.getKey()==DeckSection.Commander?4:1;cards++;   // the commander counts like several cards: it defines the deck
    String text=(r.getOracleText()==null?"":r.getOracleText())+" "+r.getName();
    for(Object[] t:TAG_RULES)if(((Pattern)t[2]).matcher(text).find())score.merge((String)t[0],n*(double)t[1],Double::sum);
    var type=r.getType();if(type.isArtifact()&&!type.isCreature())arts+=n;if(type.isEnchantment()&&!type.isCreature())ench+=n;if(type.isInstant()||type.isSorcery())spells+=n;
    if(type.isCreature())for(String st:type.getCreatureTypes())tribes.merge(st,n,Integer::sum);
    try{forge.card.CardEdition ed=StaticData.instance().getEditions().get(p.getEdition());String en=ed==null||ed.getName()==null?"":ed.getName().toLowerCase(Locale.ROOT);
     for(String[] th:THEME_SETS)if(en.contains(th[1]))themes.merge(th[0],n,Integer::sum);}catch(Exception x){}}}
  if(arts>=14)score.merge("Artifacts",arts*1.1,Double::sum);if(ench>=12)score.merge("Enchantments",ench*1.2,Double::sum);
  if(spells>=24)score.merge("Spellslinger",(spells-18)*1.0,Double::sum);
  for(var t:tribes.entrySet())if(t.getValue()>=(t.getKey().equals("Human")?18:10))score.merge(t.getKey()+" Tribal",t.getValue()*1.5,Double::sum);
  for(var t:themes.entrySet())if(t.getValue()>=Math.max(12,cards/5))score.merge(t.getKey(),t.getValue()*2.0,Double::sum);   // printings pick a world
  List<String> out=new ArrayList<>();score.entrySet().stream().filter(e->e.getValue()>=6).sorted(Map.Entry.<String,Double>comparingByValue().reversed()).limit(2).forEach(e->out.add(e.getKey()));
  if(key!=null)synchronized(tagCache){tagCache.put(key,out);}
  return out;
 }

 // ------------------------------------------------------------------ deck editor (user 2026-11-20 "erweitere den deckeditor
 // so das ich karten ingame auch aendern kann" + "artwork packages ... use fullart borderless when available, use lotr when available")
 /** card name (English or German) -> its default printing, else null */
 public static PaperCard findCard(String name){return name==null||name.isBlank()?null:card(name.trim());}
 static boolean saveArt(Deck d){java.io.File f=artFile;try{
  if(isTxt(f)){java.io.File bak=new java.io.File(f.getPath()+".bak");if(!bak.exists())Files.copy(f.toPath(),bak.toPath());}   // the hand-written list stays as .txt.bak
  writeAny(d,f);}catch(java.io.IOException e){System.err.println("[RogueLan] deck edit: "+e);return false;}loadArtDeck(f);return true;}
 /** one more copy of a card in the open deck */
 public static boolean addCard(String name){PaperCard p=findCard(name);Deck d=artFile==null?null:readDeck(artFile);if(p==null||d==null)return false;d.getMain().add(p,1);return saveArt(d);}
 /** every copy of a card leaves the open deck */
 public static boolean isCommander(String card){
  Deck d=artFile==null?null:readDeck(artFile);if(d==null||!d.has(DeckSection.Commander))return false;
  for(var e:d.get(DeckSection.Commander))if(e.getKey().getName().equals(card))return true;return false;
 }
 public static boolean removeCard(String card){
  Deck d=artFile==null?null:readDeck(artFile);if(d==null||isCommander(card))return false;   // the commander can only be replaced
  for(var sec:d){var pool=sec.getValue();for(PaperCard c:new ArrayList<>(pool.toFlatList()))if(c.getName().equals(card))pool.remove(c,1);}
  return saveArt(d);
 }
 /** every copy of a card becomes another card (same count, same section - also the commander) */
 public static boolean replaceCard(String card,String name){
  PaperCard p=findCard(name);Deck d=artFile==null?null:readDeck(artFile);if(p==null||d==null)return false;
  for(var sec:d){var pool=sec.getValue();int n=0;
   for(PaperCard c:new ArrayList<>(pool.toFlatList()))if(c.getName().equals(card)){pool.remove(c,1);n++;}
   if(n>0)pool.add(p,n);}
  return saveArt(d);
 }
 /** {key, German, English}; biggest alternative-art groups Forge knows (section of the set, or the Universes Beyond set name) */
 public static final String[][] ART_PACKS={
  {"fullart","Full Art & Borderless","Full art & borderless"},{"extended","Extended Art","Extended art"},
  {"showcase","Showcase","Showcase"},{"retro","Retro-Rahmen","Retro frame"},
  {"sld","Secret Lair","Secret Lair"},{"lotr","Herr der Ringe","Lord of the Rings"},
  {"ff","Final Fantasy","Final Fantasy"},{"40k","Warhammer 40.000","Warhammer 40,000"},
  {"who","Doctor Who","Doctor Who"},{"fallout","Fallout","Fallout"},
  {"marvel","Marvel","Marvel"},{"avatar","Avatar","Avatar"},
  {"oldest","Älteste Drucke","Oldest printings"},{"newest","Neueste Drucke","Newest printings"}};
 static boolean packMatch(String pack,PaperCard p){
  forge.card.CardEdition ed=StaticData.instance().getEditions().get(p.getEdition());if(ed==null)return false;
  String sec=null;try{sec=ed.getSectionForCollectorNumber(p.getCollectorNumber());}catch(Exception e){}
  sec=sec==null?"":sec.toLowerCase(Locale.ROOT);String n=ed.getName()==null?"":ed.getName().toLowerCase(Locale.ROOT);
  return switch(pack){
   case "fullart"->sec.equals("borderless")||sec.equals("full art");
   case "extended"->sec.equals("extended art");
   case "showcase"->sec.equals("showcase");
   case "retro"->sec.equals("retro frame");
   case "sld"->n.startsWith("secret lair");
   case "lotr"->n.contains("lord of the rings");
   case "ff"->n.contains("final fantasy");
   case "40k"->n.contains("warhammer 40");
   case "who"->n.contains("doctor who");
   case "fallout"->n.contains("fallout");
   case "marvel"->n.contains("marvel")||n.contains("spider-man");
   case "avatar"->n.contains("avatar");
   default->false;};
 }
 private static final Map<String,List<PaperCard>> printCache=new HashMap<>();
 /** the printing a package gives a card (newest match), else null */
 static PaperCard packPrint(String pack,String card){
  List<PaperCard> all;synchronized(printCache){all=printCache.computeIfAbsent(card,RogueLan::printings);}
  if(all.isEmpty())return null;
  if(pack.equals("newest"))return all.get(0);
  if(pack.equals("oldest"))return all.get(all.size()-1);
  for(PaperCard p:all)if(packMatch(pack,p))return p;
  return null;
 }
 /** the open deck's cards without its commander(s): packs never touch the commander, its picture is chosen on its own
  *  (user 2026-11-20 "die auswahl fuer den commander kann man aber immernoch separat machen") */
 public static List<PaperCard> packCards(){
  Set<String> cmd=new HashSet<>();Deck d=artFile==null?null:readDeck(artFile);
  if(d!=null&&d.has(DeckSection.Commander))for(var e:d.get(DeckSection.Commander))cmd.add(e.getKey().getName());
  List<PaperCard> l=new ArrayList<>();for(PaperCard c:artCards)if(!cmd.contains(c.getName()))l.add(c);return l;
 }
 /** how many different cards (commander excluded) a package covers */
 public static int packCount(String pack){int n=0;for(PaperCard c:packCards())if(packPrint(pack,c.getName())!=null)n++;return n;}
 /** packs in priority order (user 2026-11-20 "1 wird zu 100% angewendet, 2. nur die die bei 1 nicht ueberschrieben werden"):
  *  how many different cards the whole selection covers */
 public static int packsCoverage(List<String> order){
  int n=0;for(PaperCard c:packCards())for(String p:order)if(packPrint(p,c.getName())!=null){n++;break;}return n;
 }
 /** applies the packs in priority order: each card takes the first pack that has a printing for it; result = changed cards */
 public static int applyPacks(List<String> order){
  Deck d=artFile==null?null:readDeck(artFile);if(d==null)return -1;int changed=0;
  for(var sec:d){if(sec.getKey()==DeckSection.Commander)continue;var pool=sec.getValue();
   Map<PaperCard,Integer> cur=new LinkedHashMap<>();for(var e:pool)cur.put(e.getKey(),e.getValue());
   for(var e:cur.entrySet()){PaperCard old=e.getKey(),p=null;
    for(String pk:order){p=packPrint(pk,old.getName());if(p!=null)break;}
    if(p==null||p.getEdition().equals(old.getEdition())&&Objects.equals(p.getCollectorNumber(),old.getCollectorNumber()))continue;
    pool.remove(old,e.getValue());pool.add(p,e.getValue());changed++;}}
  return saveArt(d)?changed:-1;
 }
 /** the pack order chosen in the artwork pack dialog (this session) */
 public static final List<String> packOrder=new ArrayList<>();
 /** applies a package to the open deck; cards without a matching printing keep their picture; result = changed cards, -1 = error */
 public static int applyPack(String pack){
  Deck d=artFile==null?null:readDeck(artFile);if(d==null)return -1;int changed=0;
  for(var sec:d){var pool=sec.getValue();Map<PaperCard,Integer> cur=new LinkedHashMap<>();for(var e:pool)cur.put(e.getKey(),e.getValue());
   for(var e:cur.entrySet()){PaperCard old=e.getKey(),p=packPrint(pack,old.getName());
    if(p==null||p.getEdition().equals(old.getEdition())&&Objects.equals(p.getCollectorNumber(),old.getCollectorNumber()))continue;
    pool.remove(old,e.getValue());pool.add(p,e.getValue());changed++;}}
  return saveArt(d)?changed:-1;
 }

 static List<String[]> queryPrints(String name,boolean german)throws Exception{
  List<String[]> out=new ArrayList<>();
  String q=java.net.URLEncoder.encode("!\""+name+"\""+(german?" lang:de":""),StandardCharsets.UTF_8);
  HttpURLConnection c=(HttpURLConnection)new URL("https://api.scryfall.com/cards/search?q="+q+"&unique=prints&order=released&dir=desc").openConnection();
  c.setRequestProperty("User-Agent","RogueCommanders/1.0");c.setRequestProperty("Accept","application/json");c.setConnectTimeout(8000);c.setReadTimeout(12000);
  if(c.getResponseCode()!=200)return out;
  JsonObject o;try(InputStream in=c.getInputStream()){o=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
  for(JsonElement e:o.getAsJsonArray("data")){JsonObject card=e.getAsJsonObject();
   JsonObject face=card.has("card_faces")?card.getAsJsonArray("card_faces").get(0).getAsJsonObject():null;
   JsonObject iu=card.has("image_uris")?card.getAsJsonObject("image_uris"):face!=null&&face.has("image_uris")?face.getAsJsonObject("image_uris"):null;
   if(iu==null||!iu.has("png"))continue;
   boolean textless=card.has("textless")&&card.get("textless").getAsBoolean(),fullArt=card.has("full_art")&&card.get("full_art").getAsBoolean();
   String flavor=card.has("flavor_name")?card.get("flavor_name").getAsString():face!=null&&face.has("flavor_name")?face.get("flavor_name").getAsString():null;
   boolean borderless=card.has("border_color")&&"borderless".equals(card.get("border_color").getAsString());
   String label=(card.has("set_name")?card.get("set_name").getAsString():"?")+" #"+(card.has("collector_number")?card.get("collector_number").getAsString():"")
    +(borderless?" - randlos":"")+(fullArt?" - Full Art":"")+(textless?" - ohne Text":"")+(flavor!=null?" - \""+flavor+"\"":"");
   int score=(textless?-100:0)+(fullArt?-100:0)+(flavor!=null?-200:0)+(borderless?10:0);
   out.add(new String[]{label,iu.get("png").getAsString(),iu.has("normal")?iu.get("normal").getAsString():iu.get("png").getAsString(),String.valueOf(score)});}
  return out;
 }
 private static final Set<String> thumbsLoading=java.util.concurrent.ConcurrentHashMap.newKeySet();
 /** preview picture of one print: the file once it is on disk, else null (download started) */
 public static java.io.File thumb(String[] print){
  java.io.File f=new java.io.File(new java.io.File(germanDir(),"previews"),Integer.toHexString(print[2].hashCode())+".jpg");
  if(f.isFile()&&f.length()>1000)return f;
  if(thumbsLoading.add(print[2]))new Thread(()->{try{download(print[2],f);}catch(Throwable t){System.err.println("[RogueLan] preview "+t);}finally{thumbsLoading.remove(print[2]);}},"RogueLan-preview").start();
  return null;
 }
 /** "Uebernehmen": this print becomes the commander's picture in LAN games */
 public static void pickCommanderPrint(int i){
  List<String[]> list=cmdPrints;if(i<0||i>=list.size()||cmdName.isEmpty())return;
  String n=cmdName,url=list.get(i)[1];cmdArt.put(n,url);cmdIndex=i;save();
  new Thread(()->{try{download(url,new java.io.File(germanDir(),deFile(n)));Gdx.app.postRunnable(()->{try{forge.assets.ImageCache.getInstance().clear();}catch(Throwable ignored){}});}
   catch(Throwable t){System.err.println("[RogueLan] commander art "+t);}},"RogueLan-cmdart-pick").start();
 }
 // ------------------------------------------------------------------ MPC Autofill custom art for the commander (user 2026-11-20
 // "fuer die artwork auswahl von commander auch eine api ... die custom arts hat wie mpc proxy"): community proxy renders from
 // the public mpcfill.com backend, fetched at runtime only into the user's own custom-art folder - never part of a release
 public static volatile boolean mpcLoading;
 static String mpcCall(String method,String path,String body)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL("https://mpcfill.com/2/"+path).openConnection();
  c.setRequestMethod(method);c.setRequestProperty("User-Agent","RogueCommanders/1.0");c.setRequestProperty("Accept","application/json");
  c.setConnectTimeout(10000);c.setReadTimeout(25000);
  if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(var o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}
  if(c.getResponseCode()!=200)throw new java.io.IOException("MPC HTTP "+c.getResponseCode());
  try(InputStream in=c.getInputStream()){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}
 }
 /** up to max MPC pictures of a card -> dir/"<MPC name> - <source>.jpg" (the file name starts with the card name, so the
  *  commander artwork chooser lists them); done gets a message for a toast */
 public static void fetchMpc(String card,String dir,int max,java.util.function.Consumer<String> done){
  if(mpcLoading||card==null||card.isBlank())return;mpcLoading=true;
  new Thread(()->{String msg;
   try{JsonObject src=JsonParser.parseString(mpcCall("GET","sources/",null)).getAsJsonObject().getAsJsonObject("results");
    JsonArray sources=new JsonArray();for(var e:src.entrySet()){JsonArray p=new JsonArray();p.add(e.getValue().getAsJsonObject().get("pk").getAsInt());p.add(true);sources.add(p);}
    JsonObject q=new JsonObject();q.addProperty("query",card);q.addProperty("cardType","CARD");JsonArray qs=new JsonArray();qs.add(q);
    JsonObject st=new JsonObject(),type=new JsonObject(),ss=new JsonObject(),fs=new JsonObject();
    type.addProperty("fuzzySearch",false);type.addProperty("filterCardbacks",false);ss.add("sources",sources);
    fs.addProperty("minimumDPI",0);fs.addProperty("maximumDPI",1500);fs.addProperty("maximumSize",30);fs.add("languages",new JsonArray());fs.add("includesTags",new JsonArray());
    JsonArray ex=new JsonArray();ex.add("NSFW");fs.add("excludesTags",ex);
    st.add("searchTypeSettings",type);st.add("sourceSettings",ss);st.add("filterSettings",fs);
    JsonObject body=new JsonObject();body.add("queries",qs);body.add("searchSettings",st);
    JsonObject res=JsonParser.parseString(mpcCall("POST","editorSearch/",body.toString())).getAsJsonObject().getAsJsonObject("results");
    List<String> ids=new ArrayList<>();
    for(var e:res.entrySet()){JsonObject r=e.getValue().getAsJsonObject();if(r.has("CARD"))for(JsonElement id:r.getAsJsonArray("CARD"))ids.add(id.getAsString());}
    if(ids.isEmpty())throw new IllegalArgumentException(t("Keine MPC-Bilder für ","No MPC pictures for ")+card);
    JsonArray want=new JsonArray();for(String id:ids.subList(0,Math.min(ids.size(),max*2)))want.add(id);
    JsonObject cb=new JsonObject();cb.add("cardIdentifiers",want);
    JsonObject cards=JsonParser.parseString(mpcCall("POST","cards/",cb.toString())).getAsJsonObject().getAsJsonObject("results");
    int got=0,have=0;new java.io.File(dir).mkdirs();
    for(JsonElement idEl:want){if(got+have>=max)break;String id=idEl.getAsString();JsonObject cd=cards.has(id)?cards.getAsJsonObject(id):null;
     String nm=cd!=null&&cd.has("name")?cd.get("name").getAsString():card,srcName=cd!=null&&cd.has("sourceName")?cd.get("sourceName").getAsString():"MPC";
     if(!nm.toLowerCase(Locale.ROOT).startsWith(card.toLowerCase(Locale.ROOT)))nm=card+" "+nm;
     String file=(nm+" - "+srcName+" "+id.substring(0,Math.min(5,id.length()))).replaceAll("[\\\\/:*?\"<>|]","").trim()+".jpg";
     java.io.File out=new java.io.File(dir,file);if(out.isFile()){have++;continue;}
     try{mpcImage("https://lh3.googleusercontent.com/d/"+id+"=w1200",out);got++;Thread.sleep(150);}
     catch(Exception x){System.err.println("[RogueLan] MPC "+id+": "+x);}}   // Google Drive throttles busy files: take the next one
    msg=got+have==0?t("MPC-Bilder konnten nicht geladen werden.","MPC pictures could not be loaded."):
     got+t(" MPC-Bilder geladen"," MPC pictures loaded")+(have>0?" ("+have+t(" schon da)"," already there)"):"")+t(" - mit < > durchblättern."," - browse with < >.");}
   catch(IllegalArgumentException e){msg=e.getMessage();}
   catch(Throwable e){msg=t("MPC nicht erreichbar: ","MPC not reachable: ")+e.getMessage();}
   finally{mpcLoading=false;}
   done.accept(msg);
  },"RogueLan-mpc").start();
 }
 /** one MPC render: the print bleed around the card is cut off so it looks like a normal card picture */
 static void mpcImage(String url,java.io.File out)throws Exception{
  HttpURLConnection d=(HttpURLConnection)new URL(url).openConnection();d.setRequestProperty("User-Agent","RogueCommanders/1.0");d.setConnectTimeout(10000);d.setReadTimeout(30000);
  if(d.getResponseCode()!=200)throw new java.io.IOException("HTTP "+d.getResponseCode());
  String ct=d.getContentType();if(ct==null||!ct.startsWith("image/"))throw new java.io.IOException("no image: "+ct);
  byte[] data;try(InputStream in=d.getInputStream()){data=in.readAllBytes();}
  try{java.awt.image.BufferedImage im=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
   if(im!=null&&(double)im.getWidth()/im.getHeight()>0.724){   // 2.75 x 3.75 in print file with 1/8 in bleed -> 2.5 x 3.5 in card
    int mx=(int)Math.round(im.getWidth()*0.0455),my=(int)Math.round(im.getHeight()*0.0333);
    javax.imageio.ImageIO.write(im.getSubimage(mx,my,im.getWidth()-2*mx,im.getHeight()-2*my),"jpg",out);return;}
  }catch(Throwable t){System.err.println("[RogueLan] MPC crop: "+t);}   // no ImageIO (phone): keep the full picture
  Files.write(out.toPath(),data);
 }
 static void download(String url,java.io.File out)throws Exception{
  HttpURLConnection d=(HttpURLConnection)new URL(url).openConnection();d.setRequestProperty("User-Agent","RogueCommanders/1.0");d.setConnectTimeout(8000);d.setReadTimeout(20000);
  if(d.getResponseCode()!=200)throw new java.io.IOException("HTTP "+d.getResponseCode());
  out.getParentFile().mkdirs();java.io.File tmp=new java.io.File(out.getPath()+".tmp");
  try(InputStream in=d.getInputStream()){Files.copy(in,tmp.toPath(),StandardCopyOption.REPLACE_EXISTING);}
  Files.move(tmp.toPath(),out.toPath(),StandardCopyOption.REPLACE_EXISTING);
 }

 // ------------------------------------------------------------------ simultaneous mulligan (user 2026-11-14)
 /** MulliganService.runPlayerMulligans (patched): in a LAN game the host asks every player at the same time, then
  *  applies keep / mulligan for all of them - nobody waits for the other's decision. false = Forge's own order. */
 @SuppressWarnings("unchecked")
 public static boolean parallelMulligan(forge.game.mulligan.MulliganService service){
  // OFF (user 2026-11-16 "Mulligan war bei mir bugged"): asking two players at once races inside Forge's view tracker
  // (NPE TrackableObject.unregisterConsumer in one ask thread -> that hand counted as kept, its Keep/Mulligan prompt
  // stayed on screen into turn 4). Forge's own one-after-the-other order is safe; the wait is a few seconds.
  if(!PARALLEL_MULLIGAN)return false;
  if(hostLobby==null||!inMatch())return false;
  try{
   Class<?> k=forge.game.mulligan.MulliganService.class;
   java.lang.reflect.Field fm=k.getDeclaredField("mulligans"),ff=k.getDeclaredField("firstPlayer"),fg=k.getDeclaredField("game");
   fm.setAccessible(true);ff.setAccessible(true);fg.setAccessible(true);
   List<forge.game.mulligan.AbstractMulligan> ms=(List<forge.game.mulligan.AbstractMulligan>)fm.get(service);
   forge.game.player.Player first=(forge.game.player.Player)ff.get(service);forge.game.Game game=(forge.game.Game)fg.get(service);
   boolean allKept;
   do{
    allKept=true;
    List<forge.game.mulligan.AbstractMulligan> open=new ArrayList<>();for(var m:ms)if(!m.hasKept())open.add(m);
    Map<forge.game.mulligan.AbstractMulligan,Boolean> keep=new java.util.concurrent.ConcurrentHashMap<>();List<Thread> asks=new ArrayList<>();
    for(var m:open){
     if(!m.canMulligan()){keep.put(m,true);continue;}
     Thread t=new Thread(()->{boolean k2=true;try{k2=m.getPlayer().getController().mulliganKeepHand(first,m.tuckCardsDuringMulligan());}catch(Throwable e){System.err.println("[RogueLan] mulligan "+e);}keep.put(m,k2);},"RogueLan-mulligan");
     t.setDaemon(true);asks.add(t);t.start();}
    for(Thread t:asks)t.join();
    if(game.isGameOver())return true;
    for(var m:open){if(Boolean.TRUE.equals(keep.get(m)))m.keep();else{allKept=false;m.mulligan();}}
   }while(!allKept);
   return true;
  }catch(Throwable t){System.err.println("[RogueLan] parallel mulligan: "+t);return false;}
 }

 // ------------------------------------------------------------------ match shown inside the Rogue app
 static final class MatchScene extends forge.adventure.scene.ForgeScene{
  @Override public FScreen getScreen(){return MatchController.getView();}
 }
 private static MatchScene scene;
 static void startTimer(){
  if(timer!=null)return;
  timer=new java.util.Timer("RogueLan-watch",true);
  timer.schedule(new TimerTask(){@Override public void run(){try{Gdx.app.postRunnable(RogueLan::tick);}catch(Throwable ignored){}}},300,300);
 }
 /** GL thread: a match screen opened -> show it through our ForgeScene; the match ended and the result was closed -> back */
 static void tick(){
  try{
   FScreen cur=Forge.getCurrentScreen();
   // also in the middle of a match (user 2026-11-20 "nach mehreren Runden kann man nichts mehr machen"): "Next game" of a
   // best-of-3 empties Forge's screen stack, Forge re-enters the Rogue scene and gives it the input while the next game's
   // match screen is drawn - so whenever a match screen is current, the match scene (with Forge's input) comes back
   if(cur instanceof MatchScreen&&!(Forge.getCurrentScene() instanceof MatchScene)){
    if(scene==null)scene=new MatchScene();
    if(state!=State.MATCH){
     winLoseSeen=false;overSince=0;lastGame=null;
     set(State.MATCH,"",false);
     try{forge.assets.ImageCache.getInstance().clear();}catch(Throwable ignored){}}   // German pictures instead of the cached English ones
    Forge.switchScene(scene);return;}
   if(state!=State.MATCH)return;
   MatchScreen v=MatchController.getView();
   forge.game.GameView g=MatchController.instance==null?null:MatchController.instance.getGameView();
   if(g!=lastGame){lastGame=g;winLoseSeen=false;overSince=0;hiddenSince=0;}   // the next game of the same match: its own result
   boolean over=g!=null&&g.isGameOver();
   boolean wl=v!=null&&v.getViewWinLose()!=null&&v.getViewWinLose().isVisible();
   if(over){if(overSince==0){overSince=System.currentTimeMillis();count(g);}if(wl)winLoseSeen=true;}
   boolean gone=!(cur instanceof MatchScreen);
   forge.gamemodes.match.HostedMatch hm=null;try{hm=MatchController.getHostedMatch();}catch(Throwable ignored){}
   boolean ended=hm==null||hm.getGame()==null;   // "Show battlefield" only hides the result screen: stay until Quit / Next really ran
   if(over&&winLoseSeen&&!wl&&!ended){   // ... and bring the result screen back after a look at the board (adventure menus have no "show result")
    if(hiddenSince==0)hiddenSince=System.currentTimeMillis();
    else if(System.currentTimeMillis()-hiddenSince>6000){hiddenSince=0;Gdx.app.postRunnable(()->{try{if(MatchController.instance!=null)MatchController.instance.showWinlose();}catch(Throwable x){System.err.println("[RogueLan] show result "+x);}});}}
   else hiddenSince=0;
   if(gone||over&&winLoseSeen&&!wl&&ended||over&&!winLoseSeen&&System.currentTimeMillis()-overSince>20000)leaveMatch();
  }catch(Throwable t){System.err.println("[RogueLan] tick "+t);}
 }
 static void count(forge.game.GameView g){
  try{String w=g.getWinningPlayerName();if(w==null)return;if(w.equals(name.isBlank()?"Commander":name.trim()))myWins++;else theirWins++;}catch(Throwable ignored){}
 }
 static void leaveMatch(){
  boolean connected=client!=null||hostLobby!=null||aiLobby!=null;
  if(state==State.MATCH||state==State.STARTING)set(connected?State.AFTER:State.IDLE,connected?forge.adventure.util.RogueLan.t("Gutes Spiel! Drück Nochmal spielen für eine Revanche.","Good game! Press Play again for a rematch."):status,!connected&&error);
  try{Forge.clearScreenStack();}catch(Throwable ignored){}   // no old match screen stays on the stack (rematch freeze 2026-11-20)
  if(backToRogue!=null)backToRogue.run();
 }
}
