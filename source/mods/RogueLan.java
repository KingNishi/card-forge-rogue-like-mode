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
 static final Map<String,String> cmdArt=new java.util.concurrent.ConcurrentHashMap<>();
 static final String[][] BOARDS={{"AKH_Mobile","Amonkhet"},{"WOE_Mobile","Eldraine"},{"KHM_Mobile","Kaldheim"},{"FRA","FRA"},{"Fallback","Klassisch"},
  {"MID_Mobile","Innistrad"},{"AFR_Mobile","Forgotten Realms"},{"AKR_Mobile","Amonkhet Remastered"},{"BLB","Bloomburrow"},{"DAR_Mobile","Dominaria"},
  {"XLN_Mobile","Ixalan"},{"OTJ","Thunder Junction"}};
 static final String BOARD_DIR="D:/forge/_mods/arena-boards/";
 /** {name, label, preview file} of every board on this PC; first entry = random */
 public static List<String[]> boards(){
  List<String[]> out=new ArrayList<>();out.add(new String[]{"","Zufall",null});
  for(String[] b:BOARDS){java.io.File small=new java.io.File(BOARD_DIR+"boards/"+b[0]+".jpg"),hd=new java.io.File(BOARD_DIR+"boards-hd/"+b[0]+".jpg");
   if(small.isFile()||hd.isFile())out.add(new String[]{b[0],b[1],(small.isFile()?small:hd).getPath()});}
  return out;
 }
 public static void setBoard(String name){board=name==null?"":name;save();if(BattleUiSettings.german)BattleUiSettings.lanBoard=board;}
 /** the Rogue scene sets this: switches back to its LAN screen after a match */
 public static Runnable backToRogue;

 private static ServerGameLobby hostLobby;private static FGameClient client;private static ClientGameLobby clientLobby;
 private static Deck deck;private static boolean isHost,sentDeck,setDuo;
 private static java.util.Timer timer;private static boolean winLoseSeen;private static long overSince;
 private static int myWins,theirWins;private static String opponent="";

 static void set(State s,String msg,boolean err){state=s;status=msg;error=err;version++;}
 /** a LAN match is on screen: own (German) controls tour */
 public static boolean inMatch(){return state==State.MATCH||state==State.STARTING;}
 public static boolean tourDue(){return inMatch()&&!tourDone;}
 public static void setTourDone(){if(!tourDone){tourDone=true;save();}}
 public static boolean busy(){return state!=State.IDLE&&state!=State.AFTER;}
 public static String score(){return myWins+theirWins==0?"":"Stand: du "+myWins+" - "+theirWins+" "+opponent;}

 // ------------------------------------------------------------------ saved fields
 private static Path file(){return java.nio.file.Paths.get(forge.localinstance.properties.ForgeConstants.USER_PREFS_DIR,"rogue_lan.json");}
 public static void load(String defaultName){
  try{Path f=file();if(Files.isRegularFile(f)){JsonObject o=JsonParser.parseString(Files.readString(f)).getAsJsonObject();
   if(o.has("name"))name=o.get("name").getAsString();if(o.has("deck"))deckText=o.get("deck").getAsString();if(o.has("ip"))hostIp=o.get("ip").getAsString();if(o.has("tourDone"))tourDone=o.get("tourDone").getAsBoolean();if(o.has("board"))board=o.get("board").getAsString();if(o.has("online"))online=o.get("online").getAsBoolean();
   if(o.has("cmdArt"))for(var e:o.getAsJsonObject("cmdArt").entrySet())cmdArt.put(e.getKey(),e.getValue().getAsString());}}
  catch(Exception e){System.err.println("[RogueLan] "+e);}
  if(name.isBlank()&&defaultName!=null)name=defaultName;
 }
 public static void save(){
  try{JsonObject o=new JsonObject();o.addProperty("name",name);o.addProperty("deck",deckText);o.addProperty("ip",hostIp);o.addProperty("tourDone",tourDone);o.addProperty("board",board);o.addProperty("online",online);JsonObject ca=new JsonObject();for(var e:cmdArt.entrySet())ca.addProperty(e.getKey(),e.getValue());o.add("cmdArt",ca);
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
 static final Pattern LINE=Pattern.compile("^(\\d+)\\s*[xX]?\\s+(.+?)(?:\\s+\\([A-Za-z0-9_]{2,8}\\)(?:\\s+[^\\s]+)?)?(?:\\s+\\*[A-Za-z]+\\*)*\\s*$");
 static final Map<String,String> HEADERS=Map.ofEntries(Map.entry("commander","cmd"),Map.entry("commanders","cmd"),Map.entry("kommandeur","cmd"),Map.entry("hauptdeck","main"),Map.entry("deck","main"),Map.entry("main","main"),
  Map.entry("mainboard","main"),Map.entry("sideboard","side"),Map.entry("companion","side"),Map.entry("maybeboard","skip"),Map.entry("considering","skip"),Map.entry("tokens","skip"));
 record Entry(int n,String card){}

 public static Deck buildDeck(String input)throws Exception{
  String text=input==null?"":input.trim();
  if(text.isEmpty())throw new IllegalArgumentException("Füge zuerst einen Moxfield-Link oder eine Deckliste ein.");
  List<Entry> cmd=new ArrayList<>(),main=new ArrayList<>();String deckName="LAN deck";
  Matcher m=MOX.matcher(text);
  if(m.find()&&text.split("\\s+").length<=2){deckName=moxfield(m.group(1),cmd,main);}
  else parseText(text,cmd,main);
  List<String> unknown=new ArrayList<>();
  Deck d=new Deck(deckName);
  for(Entry e:cmd){PaperCard pc=card(e.card());if(pc==null)unknown.add(e.card());else d.getOrCreate(DeckSection.Commander).add(pc,1);}
  for(Entry e:main){PaperCard pc=card(e.card());if(pc==null)unknown.add(e.card());else d.getMain().add(pc,e.n());}
  if(!d.has(DeckSection.Commander)||d.get(DeckSection.Commander).isEmpty()){   // no commander line: the first card that can lead the deck
   for(Map.Entry<PaperCard,Integer> e:d.getMain()){if(e.getKey().getRules().canBeCommander()){PaperCard c=e.getKey();d.getMain().remove(c,1);d.getOrCreate(DeckSection.Commander).add(c,1);break;}}
  }
  if(!d.has(DeckSection.Commander)||d.get(DeckSection.Commander).isEmpty())
   throw new IllegalArgumentException("Kein Commander gefunden. Schreib eine Zeile \"Commander\" über deinen Commander in der Liste.");
  if(d.getMain().countAll()<20)throw new IllegalArgumentException("Nur "+d.getMain().countAll()+" Karten erkannt - ist das das ganze Deck?");
  if(!unknown.isEmpty())warning=unknown.size()+" Karte(n) nicht gefunden und weggelassen: "+String.join(", ",unknown.subList(0,Math.min(4,unknown.size())))+(unknown.size()>4?" ...":"");
  return d;
 }
 static String warning;
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
 static void parseText(String text,List<Entry> cmd,List<Entry> main){
  List<List<Entry>> blocks=new ArrayList<>();List<Entry> cur=new ArrayList<>();
  Map<String,List<Entry>> sec=new HashMap<>();for(String k:new String[]{"cmd","main","side","skip"})sec.put(k,new ArrayList<>());
  String section=null;boolean header=false;
  for(String raw:text.replace("\r","").split("\n")){
   String line=raw.trim(),key=line.toLowerCase(Locale.ROOT).replaceAll("[:\\s]+$","").replaceAll("\\s*\\(\\d+\\)$","");
   if(HEADERS.containsKey(key)){section=HEADERS.get(key);header=true;continue;}
   if(line.isEmpty()){if(!cur.isEmpty()){blocks.add(cur);cur=new ArrayList<>();}continue;}
   if(line.startsWith("//")||line.startsWith("#"))continue;
   Matcher m=LINE.matcher(line);Entry e;
   if(m.matches())e=new Entry(Integer.parseInt(m.group(1)),m.group(2).trim());
   else if(!line.matches(".*\\d.*")&&line.length()<60)e=new Entry(1,line);   // "Sol Ring" without a count
   else continue;
   if(header&&section!=null)sec.get(section).add(e);else cur.add(e);
  }
  if(!cur.isEmpty())blocks.add(cur);
  if(header){cmd.addAll(sec.get("cmd"));main.addAll(sec.get("main"));
   if(cmd.isEmpty()&&!sec.get("side").isEmpty()&&sec.get("side").size()<=2)cmd.addAll(sec.get("side"));}
  else if(blocks.size()>=2&&blocks.get(blocks.size()-1).size()<=2){   // MTGO export: commander after an empty line at the end
   cmd.addAll(blocks.get(blocks.size()-1));for(int i=0;i<blocks.size()-1;i++)main.addAll(blocks.get(i));}
  else for(List<Entry> b:blocks)main.addAll(b);
 }
 static String moxfield(String id,List<Entry> cmd,List<Entry> main)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL("https://api2.moxfield.com/v3/decks/all/"+id).openConnection();
  c.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) RogueCommanders/1.0");c.setRequestProperty("Accept","application/json");
  c.setConnectTimeout(10000);c.setReadTimeout(15000);
  int code;try{code=c.getResponseCode();}catch(Exception e){throw new IllegalArgumentException("Moxfield ist nicht erreichbar. Füge stattdessen die Deckliste ein (Moxfield: Export > Copy for MTGO).");}
  if(code==404)throw new IllegalArgumentException("Moxfield: Deck nicht gefunden. Stell es auf Public oder Unlisted.");
  if(code!=200)throw new IllegalArgumentException("Moxfield hat den Abruf abgelehnt (HTTP "+code+"). Füge stattdessen die Deckliste ein (Export > Copy for MTGO).");
  JsonObject o;try(InputStream in=c.getInputStream()){o=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}
  JsonObject boards=o.getAsJsonObject("boards");
  for(String[] b:new String[][]{{"commanders","cmd"},{"mainboard","main"}}){
   if(boards==null||!boards.has(b[0]))continue;JsonObject cards=boards.getAsJsonObject(b[0]).getAsJsonObject("cards");if(cards==null)continue;
   for(Map.Entry<String,JsonElement> e:cards.entrySet()){JsonObject v=e.getValue().getAsJsonObject();
    Entry en=new Entry(v.has("quantity")?v.get("quantity").getAsInt():1,v.getAsJsonObject("card").get("name").getAsString());
    (b[1].equals("cmd")?cmd:main).add(en);}
  }
  return o.has("name")?o.get("name").getAsString():"Moxfield deck";
 }

 // ------------------------------------------------------------------ start / stop
 /** Start Game: empty host IP = host the game, otherwise join that IP. Runs in the background. */
 public static void start(){
  if(busy())return;
  String n=name.isBlank()?"Commander":name.trim(),ip=hostIp.trim(),text=deckText;
  save();warning=null;
  set(State.LOADING,"Dein Deck wird geladen...",false);
  new Thread(()->{
   try{Deck d=buildDeck(text);deck=d;
    int count=d.getMain().countAll()+d.get(DeckSection.Commander).countAll();
    String info=d.get(DeckSection.Commander).toFlatList().get(0).getName()+" ("+count+" Karten)";
    prefetchGerman(d);
    if(ip.isEmpty())host(n,info);else join(n,ip,info);
   }catch(IllegalArgumentException e){set(State.IDLE,e.getMessage(),true);}
   catch(Throwable t){t.printStackTrace();set(State.IDLE,"Start fehlgeschlagen: "+t,true);stop();}
  },"RogueLan-start").start();
 }

 static void duoOn(){if(System.getProperty("forge.duo")==null){System.setProperty("forge.duo","1");setDuo=true;}german(true);devMode(true);startTimer();}
 private static String prevLang,prevCardLang;
 /** Forge's own texts (OK, End Turn, Keep, prompts) and the card names / rules texts in German while the LAN mode runs;
  *  the deck is still pasted in English or as a Moxfield link (user 2026-11-14). Card pictures stay as cached. */
 static void german(boolean on){
  BattleUiSettings.german=on;BattleUiSettings.lanBoard=on?board:"";
  try{var loc=forge.util.Localizer.getInstance();String dir=forge.localinstance.properties.ForgeConstants.LANG_DIR;
   if(on){if(prevLang==null){prevLang=FModel.getPreferences().getPref(forge.localinstance.properties.ForgePreferences.FPref.UI_LANGUAGE);if(prevLang==null||!prevLang.contains("-"))prevLang="en-US";}loc.setLanguage("de-DE",dir);}
   else if(prevLang!=null){loc.setLanguage(prevLang,dir);prevLang=null;}}
  catch(Throwable t){System.err.println("[RogueLan] language "+t);}
  try{String dir=forge.localinstance.properties.ForgeConstants.LANG_DIR;
   if(on){if(prevCardLang==null){prevCardLang=forge.util.CardTranslation.getLanguageSelected();forge.util.CardTranslation.preloadTranslation("de-DE",dir);}}
   else if(prevCardLang!=null){forge.util.CardTranslation.preloadTranslation(prevCardLang,dir);prevCardLang=null;}}
  catch(Throwable t){System.err.println("[RogueLan] card language "+t);}
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
  server.updateSlot(0,UpdateLobbyPlayerEvent.nameUpdate(n));
  server.updateSlot(0,UpdateLobbyPlayerEvent.deckUpdate(deck));
  server.updateSlot(0,UpdateLobbyPlayerEvent.isReadyUpdate(true));
  duoOn();
  set(State.HOSTING,"Dein Deck: "+info+(online?"\nOnline: Port "+PORT+" wird per UPnP im Router geoeffnet. Schick deinem Gegner deine Internet-IP "+internetIp()+"\nKommt er nicht rein: Port "+PORT+" (TCP) im Router auf diesen PC weiterleiten."
   :"\nWarte auf deinen Gegner - schick ihm deine IP "+lanIp())+(warning!=null?"\n"+warning:""),false);
 }
 /** host: both seats taken and ready -> start the match */
 static synchronized void hostCheck(){
  ServerGameLobby l=hostLobby;if(l==null||state==State.STARTING||state==State.MATCH||l.isMatchActive())return;
  LobbySlot me=l.getSlot(0),them=l.getNumberOfSlots()>1?l.getSlot(1):null;
  if(them==null||them.getType()!=LobbySlotType.REMOTE){if(state==State.LOBBY)set(State.HOSTING,"Dein Gegner ist weg. Warte - deine IP ist "+lanIp(),true);return;}
  opponent=them.getName()==null?"":them.getName();
  if(!me.isReady()){if(state!=State.AFTER)set(State.LOBBY,opponent+" ist da. Drück Nochmal spielen, wenn du bereit bist.",false);return;}
  if(!them.isReady()||them.getDeck()==null){set(State.LOBBY,opponent+" ist verbunden - warte auf sein Deck...",false);return;}
  set(State.STARTING,"Das Spiel gegen "+opponent+" startet...",false);
  FThreads.invokeInBackgroundThread(()->{
   // casual games between friends: Forge's "deck not legal - play anyway?" question cannot show in the Rogue app
   var prefs=FModel.getPreferences();boolean legality=prefs.getPrefBoolean(forge.localinstance.properties.ForgePreferences.FPref.ENFORCE_DECK_LEGALITY);
   Runnable go;
   try{prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.ENFORCE_DECK_LEGALITY,false);go=l.startGame();}
   finally{prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.ENFORCE_DECK_LEGALITY,legality);}
   if(go==null){set(State.LOBBY,"Das Spiel konnte nicht starten - siehe Meldung.",true);return;}
   Forge.setLoadingaMatch(true);
   Runnable start=go;FThreads.invokeInEdtLater(start);   // no LoadingOverlay: Forge overlays do not draw over a Rogue scene
  });
 }

 static void join(String n,String address,String info){
  isHost=false;sentDeck=false;myWins=theirWins=0;opponent="";
  String hostName=address;int port=PORT;
  int c=address.lastIndexOf(':');if(c>0&&address.indexOf(':')==c){try{port=Integer.parseInt(address.substring(c+1).trim());hostName=address.substring(0,c).trim();}catch(NumberFormatException ignored){}}
  set(State.CONNECTING,"Verbinde mit "+hostName+" ...",false);
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
    for(int i=0;i<lobby.getNumberOfSlots();i++)if(i!=slot&&lobby.getSlot(i)!=null&&lobby.getSlot(i).getName()!=null)opponent=lobby.getSlot(i).getName();
    if(!sentDeck){sentDeck=true;cl.send(UpdateLobbyPlayerEvent.deckUpdate(deck));cl.send(UpdateLobbyPlayerEvent.isReadyUpdate(true));
     set(State.LOBBY,"Verbunden mit "+(opponent.isEmpty()?hn:opponent)+". Dein Deck: "+info+"\nDas Spiel startet gleich..."+(warning!=null?"\n"+warning:""),false);}}
   @Override public void close(){
    boolean wasMatch=state==State.MATCH;client=null;
    set(State.IDLE,"Die Verbindung zum Host ist abgebrochen.",true);
    if(wasMatch)Gdx.app.postRunnable(RogueLan::leaveMatch);}
   @Override public ClientGameLobby getLobby(){return lobby;}});
  duoOn();
  try{cl.connect();}
  catch(Exception e){client=null;
   set(State.IDLE,"Keine Verbindung zu "+hn+". Prüf die IP, ob der Host zuerst SPIEL STARTEN gedrückt hat und ob die Windows-Firewall Rogue Commanders erlaubt (private Netzwerke).",true);}
 }

 /** after a match: ready again (host and friend both press it) */
 public static void playAgain(){
  if(state!=State.AFTER&&state!=State.LOBBY)return;
  if(!deckText.isBlank())try{Deck d=buildDeck(deckText);deck=d;}catch(Exception e){set(state,e.getMessage(),true);return;}
  if(isHost&&hostLobby!=null){FServerManager s=FServerManager.getInstance();s.updateSlot(0,UpdateLobbyPlayerEvent.deckUpdate(deck));s.updateSlot(0,UpdateLobbyPlayerEvent.isReadyUpdate(true));
   set(State.LOBBY,"Bereit - warte, bis "+(opponent.isEmpty()?"dein Gegner":opponent)+" Nochmal spielen drückt.",false);hostCheck();}
  else if(client!=null){client.send(UpdateLobbyPlayerEvent.deckUpdate(deck));client.send(UpdateLobbyPlayerEvent.isReadyUpdate(true));
   set(State.LOBBY,"Bereit - der Host startet das nächste Spiel.",false);}
  else set(State.IDLE,"Nicht mehr verbunden - drück SPIEL STARTEN.",true);
 }

 public static void stop(){
  try{if(client!=null){client.close();}}catch(Throwable t){System.err.println("[RogueLan] "+t);}
  client=null;clientLobby=null;
  try{if(hostLobby!=null)FServerManager.getInstance().stopServer();}catch(Throwable t){System.err.println("[RogueLan] "+t);}
  hostLobby=null;deck=null;
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
 public static java.io.File germanImage(java.io.File file,String key){
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
  if(cmdLoading)return;cmdLoading=true;cmdError="";cmdPrints=List.of();
  new Thread(()->{
   try{Deck d=buildDeck(text);String n=d.get(DeckSection.Commander).toFlatList().get(0).getName();cmdName=n;
    List<String[]> list=queryPrints(n,true);if(list.isEmpty())list=queryPrints(n,false);
    if(list.isEmpty())cmdError="Keine Drucke gefunden.";
    int idx=0;String saved=cmdArt.get(n);
    if(saved!=null){for(int i=0;i<list.size();i++)if(list.get(i)[1].equals(saved))idx=i;}
    else{int best=Integer.MIN_VALUE;for(int i=0;i<list.size();i++){int sc=Integer.parseInt(list.get(i)[3]);if(sc>best){best=sc;idx=i;}}}
    cmdIndex=idx;cmdPrints=list;
   }catch(IllegalArgumentException e){cmdError=e.getMessage();}
   catch(Throwable t){cmdError="Fehler beim Laden: "+t.getMessage();}
   finally{cmdLoading=false;}
  },"RogueLan-cmdart").start();
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
   if(cur instanceof MatchScreen&&!(Forge.getCurrentScene() instanceof MatchScene)&&state!=State.MATCH){
    if(scene==null)scene=new MatchScene();
    winLoseSeen=false;overSince=0;
    set(State.MATCH,"",false);
    try{forge.assets.ImageCache.getInstance().clear();}catch(Throwable ignored){}   // German pictures instead of the cached English ones
    Forge.switchScene(scene);return;}
   if(state!=State.MATCH)return;
   MatchScreen v=MatchController.getView();
   forge.game.GameView g=MatchController.instance==null?null:MatchController.instance.getGameView();
   boolean over=g!=null&&g.isGameOver();
   boolean wl=v!=null&&v.getViewWinLose()!=null&&v.getViewWinLose().isVisible();
   if(over){if(overSince==0){overSince=System.currentTimeMillis();count(g);}if(wl)winLoseSeen=true;}
   boolean gone=!(cur instanceof MatchScreen);
   if(gone||over&&winLoseSeen&&!wl||over&&!winLoseSeen&&System.currentTimeMillis()-overSince>20000)leaveMatch();
  }catch(Throwable t){System.err.println("[RogueLan] tick "+t);}
 }
 static void count(forge.game.GameView g){
  try{String w=g.getWinningPlayerName();if(w==null)return;if(w.equals(name.isBlank()?"Commander":name.trim()))myWins++;else theirWins++;}catch(Throwable ignored){}
 }
 static void leaveMatch(){
  boolean connected=client!=null||hostLobby!=null;
  if(state==State.MATCH||state==State.STARTING)set(connected?State.AFTER:State.IDLE,connected?"Gutes Spiel! Drück Nochmal spielen für eine Revanche.":status,!connected&&error);
  try{Forge.clearCurrentScreen();}catch(Throwable ignored){}
  if(backToRogue!=null)backToRogue.run();
 }
}
