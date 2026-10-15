package forge.localinstance.achievements;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.card.Card;
import forge.game.zone.ZoneType;
public final class AdventureRewardAchievements {
 static boolean satisfied(int index,int life,int hand,int tokens,int artifacts,int creatures,int maxPower,int totalPower){
  return switch(index){case 0->true;case 1->tokens>=10;case 2->artifacts>=10;case 3->maxPower>=20;case 4->life==1;case 5->hand>=15;case 6->creatures>=15;case 7->totalPower>=100;default->false;};
 }
 public static void add(AchievementCollection collection){
  String[] names={"First Victory","Token Legion","Artifact Engine","Colossus","Last Breath","Deep Reserves","Menagerie","Overwhelming Force"};
  String[] descriptions={"Win a duel.","Win with at least 10 tokens on your battlefield.","Win with at least 10 artifacts on your battlefield.","Win with a creature of power 20 or greater.","Win with exactly 1 life remaining.","Win with at least 15 cards in hand.","Win with at least 15 creatures on your battlefield.","Win with total creature power of at least 100."};
  for(int i=0;i<names.length;i++)collection.add(new Goal(i,names[i],descriptions[i]));
 }
 public static final class Goal extends Achievement {
  final int index;
  Goal(int i,String name,String text){super("AdventureBonus"+i,name,text+" Reward: one "+AchievementBoosterMod.qualityForBonus(i)+" booster (15 cards), once.","Complete this objective.",0);index=i;}
  protected int evaluate(Player p,Game game){
   if(p.getOutcome()==null||!p.getOutcome().hasWon())return 0;
   if(index==0)return 1;
   if(index==4)return p.getLife()==1?1:0;
   if(index==5)return p.getCardsIn(ZoneType.Hand).size()>=15?1:0;
   int tokens=0,artifacts=0,creatures=0,maxPower=0,totalPower=0;
   for(Card c:p.getCardsIn(ZoneType.Battlefield)){
    if(c.isToken())tokens++;
    if(c.isArtifact())artifacts++;
    if(c.isCreature()){creatures++;maxPower=Math.max(maxPower,c.getNetPower());totalPower+=Math.max(0,c.getNetPower());}
   }
   return satisfied(index,p.getLife(),p.getCardsIn(ZoneType.Hand).size(),tokens,artifacts,creatures,maxPower,totalPower)?1:0;
  }
  protected String getNoun(){return null;}
 }
}
