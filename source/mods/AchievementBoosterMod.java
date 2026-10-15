package forge.localinstance.achievements;
import forge.Forge;
import forge.adventure.util.*;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.world.WorldSave;
import forge.game.GameType;
import forge.game.player.Player;
import forge.deck.Deck;
import forge.item.PaperCard;
import forge.card.CardRarity;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.lang.reflect.Field;
/** One persistent claim per trophy, shared across saves like Forge achievements. */
public final class AchievementBoosterMod {
 private static final Path FILE=java.nio.file.Paths.get(System.getProperty("forge.achievement.rewards","D:/forge/_mods/achievement-rewards/claims.bin"));
 private static Map<String,Claim> claims;
 private static long retryAt;
 private static Map<CardRarity,List<PaperCard>> pool;
 private static final Random random=new Random();
 public static final int[][] CONTENTS={{11,3,1,0},{8,5,2,0},{5,6,3,1},{3,5,5,2}};
 public static final String[] QUALITY={"Standard","Enhanced","Premium","Legendary"};
 static final class Claim implements Serializable {
  private static final long serialVersionUID=1L;
  String owner,flag,label;Deck pack;boolean delivered;
 }
 public static int bonusTier(int i){return new int[]{0,2,1,2,3,2,2,3}[i];}
 public static String qualityForBonus(int i){return QUALITY[bonusTier(i)];}
 public static synchronized int unlockedSleeveCount(){
  try{load();return 3+claims.size();}catch(Exception e){System.err.println("[Achievement sleeve unlocks] "+e);return 3;}
 }
 public static boolean earned(Achievement a){return a.isSpecial()?a.getBest()>0:a.earnedCommon()||a.earnedUncommon()||a.earnedRare()||a.earnedMythic();}
 public static int tier(Achievement a){
  if(a.getKey().startsWith("AdventureBonus"))return bonusTier(Integer.parseInt(a.getKey().substring(14)));
  int baseline=switch(a.getKey()){
   case "TotalGameWins","TotalMatchWins","GameWinStreak","MatchWinStreak","RagsToRiches"->3;
   case "Poisoned","DeckedOut","ManaScrewed"->2;
   case "LifeToSpare","ManaFlooded","Hellbent","StormChaser"->1;
   default->2;
  };
  return Math.max(baseline,a.earnedMythic()?3:a.earnedRare()?2:a.earnedUncommon()?1:0);
 }
 public static synchronized int update(Achievement a,Player p,AchievementCollection collection){
  int result=a.update(p);
  try{
   GameType type=p.getGame().getRules().getGameType();
   if((type==GameType.Adventure||type==GameType.AdventureEvent)&&earned(a)){
    load();String key=collection.getClass().getSimpleName()+":"+a.getKey();
    if(!claims.containsKey(key)){
     recordClaim(key,owner(Current.player()),a.getDisplayName(),makePack(tier(a),a.getDisplayName()));
    }
   }
  }catch(Exception e){System.err.println("[Achievement rewards] "+e);}
  return result;
 }
 static boolean recordClaim(String key,String owner,String label,Deck pack)throws Exception{
  load();if(claims.containsKey(key))return false;
  Claim claim=new Claim();claim.owner=owner;claim.flag="achievementReward:"+key;claim.label=label;claim.pack=pack;
  claims.put(key,claim);try{persist();}catch(Exception e){claims.remove(key);throw e;}return true;
 }
 private static String owner(AdventurePlayer p)throws Exception{
  Field seed=Current.world().getClass().getDeclaredField("seed");seed.setAccessible(true);
  return p.getName()+":"+seed.getLong(Current.world());
 }
 @SuppressWarnings("unchecked") private static void load()throws Exception{
  if(claims!=null)return;
  if(Files.exists(FILE))try(ObjectInputStream in=new ObjectInputStream(Files.newInputStream(FILE))){claims=(Map<String,Claim>)in.readObject();}
  else claims=new LinkedHashMap<>();
 }
 private static void persist()throws Exception{
  Files.createDirectories(FILE.getParent());Path temp=FILE.resolveSibling(FILE.getFileName()+".tmp");
  try(ObjectOutputStream out=new ObjectOutputStream(Files.newOutputStream(temp))){out.writeObject(claims);}
  try{Files.move(temp,FILE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}
 }
 private static void cardPool(){
  if(pool!=null)return;
  Map<CardRarity,List<PaperCard>> all=new EnumMap<>(CardRarity.class);
  Map<CardRarity,Set<String>> seen=new EnumMap<>(CardRarity.class);
  for(PaperCard c:forge.adventure.data.RewardData.getAllCards()){
   if(c.getRules().getType().isBasicLand())continue;
   CardRarity r=c.getRarity();
   if(r!=CardRarity.Common&&r!=CardRarity.Uncommon&&r!=CardRarity.Rare&&r!=CardRarity.MythicRare)continue;
   if(seen.computeIfAbsent(r,k->new HashSet<>()).add(c.getName()))all.computeIfAbsent(r,k->new ArrayList<>()).add(c);
  }
  pool=all;
 }
 public static Deck makePack(int tier,String label){cardPool();return makePack(tier,label,pool,random);}
 public static Deck makePack(int tier,String label,Map<CardRarity,List<PaperCard>> cards,Random rng){
  Deck pack=new Deck(QUALITY[tier]+" Achievement Booster - "+label);
  CardRarity[] rarities={CardRarity.Common,CardRarity.Uncommon,CardRarity.Rare,CardRarity.MythicRare};
  Set<String> used=new HashSet<>();
  for(int slot=0;slot<4;slot++)for(int n=0;n<CONTENTS[tier][slot];n++){
   List<PaperCard> choices=cards.get(rarities[slot]);if(choices==null||choices.isEmpty())throw new IllegalStateException("Missing reward rarity "+rarities[slot]);
   PaperCard c=choices.get(rng.nextInt(choices.size()));
   for(int attempt=0;attempt<40&&used.contains(c.getName());attempt++)c=choices.get(rng.nextInt(choices.size()));
   used.add(c.getName());pack.getMain().add(c,1);
  }
  return pack;
 }
 public static synchronized void deliver(){
  if(!Forge.isMobileAdventureMode||!(Forge.getCurrentScene() instanceof forge.adventure.scene.GameScene
     ||Forge.getCurrentScene() instanceof forge.adventure.scene.TileMapScene)
     ||Forge.advFreezePlayerControls||System.currentTimeMillis()<retryAt)return;
  retryAt=System.currentTimeMillis()+5000;
  try{
   load();AdventurePlayer player=Current.player();String owner=owner(player);
   for(Claim claim:claims.values()){
    if(claim.delivered||!owner.equals(claim.owner))continue;
    if(!player.checkCharacterFlag(claim.flag)){
     if(!player.addBooster(claim.pack))throw new IllegalStateException("Cannot add reward booster");
     player.setCharacterFlag(claim.flag,1);
     if(!WorldSave.getCurrentSave().autoSave()){
      player.removeBooster(claim.pack);player.setCharacterFlag(claim.flag,0);throw new IOException("Reward autosave failed; will retry");
     }
     player.addStatusMessage("Achievement reward",claim.pack.getName(),null,0,0);
    }
    claim.delivered=true;claim.pack=null;persist();
   }
  }catch(Exception e){System.err.println("[Achievement rewards delivery] "+e);}
 }
}
