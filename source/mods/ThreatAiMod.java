package forge.ai;

import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.player.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Threat-aware AI for 1v1 vs AI / LAN games with 3-4 players (user 2026-11-16: "die AI erkennt nicht wer grade der
 *  threat ist ... player angreifen die nicht blocken koennen, player mit removal fokusen die highest mana value creature
 *  haben"). Only active while a RogueLan match runs; roguelite fights and normal Forge stay native.
 *  - defender(): which opponent the AI attacks. Lethal first, then free damage (few blockers), then the biggest threat.
 *    Replaces Forge's random pick among similar opponents; keeps the last target when it is almost as good.
 *  - bestCreature(): removal on opponents' creatures prefers high mana value, commanders and the biggest threat.
 *  Both return null to fall back to Forge's own logic (target legality, timing and costs always stay native). */
public final class ThreatAiMod {
 private ThreatAiMod(){}
 private static Method inMatch;
 private static final Map<Player,Player> LAST=new WeakHashMap<>();
 private static final Map<Player,Integer> LOGGED=new WeakHashMap<>();

 static boolean active(){
  try{
   if(inMatch==null)inMatch=Class.forName("forge.adventure.util.RogueLan").getMethod("inMatch");   // RogueLan is installed by another step
   return (Boolean)inMatch.invoke(null);
  }catch(Throwable t){return false;}
 }

 /** how dangerous a player is right now (board, mana value, commander, walkers, monarch/initiative, life) */
 static double threat(Player p){
  double s=0;int maxCmc=0;
  for(Card c:p.getCreaturesInPlay()){
   s+=ComputerUtilCard.evaluateCreature(c)/12.0;
   maxCmc=Math.max(maxCmc,c.getCMC());
   if(c.isCommander())s+=15;
  }
  s+=maxCmc*3;
  for(Card w:p.getPlaneswalkersInPlay())s+=25+2*w.getCurrentLoyalty();
  if(p.isMonarch())s+=20;
  if(p.hasInitiative())s+=15;
  return s+p.getLife()/4.0;
 }

 static boolean evades(Card attacker,List<Card> blockers){
  if(!attacker.hasKeyword(Keyword.FLYING))return false;
  for(Card b:blockers)if(b.hasKeyword(Keyword.FLYING)||b.hasKeyword(Keyword.REACH))return false;
  return true;
 }

 public static Player defender(Player ai,boolean nextTurn){
  try{
   if(ai==null||!active())return null;
   List<Player> opps=new ArrayList<>();
   for(Player p:ai.getOpponents())if(!p.hasLost())opps.add(p);
   if(opps.size()<2)return null;   // a single opponent: nothing to choose
   List<Card> attackers=new ArrayList<>();
   for(Card c:ai.getCreaturesInPlay())if(CombatUtil.canAttack(c)||nextTurn&&!c.hasKeyword(Keyword.DEFENDER))attackers.add(c);
   attackers.sort((a,b)->Integer.compare(Math.max(0,b.getNetPower()),Math.max(0,a.getNetPower())));
   Player best=null;double bestScore=-1e9;StringBuilder why=new StringBuilder();
   double[] scores=new double[opps.size()];
   for(int i=0;i<opps.size();i++){
    Player p=opps.get(i);
    List<Card> blockers=new ArrayList<>();
    for(Card c:p.getCreaturesInPlay())if(CombatUtil.canBlock(c,nextTurn))blockers.add(c);
    // blockers stop the biggest ground attackers; evasive attackers and the rest get through
    int through=0,ground=0;
    for(Card a:attackers){
     int pw=Math.max(0,a.getNetPower());
     if(evades(a,blockers))through+=pw;
     else if(ground++>=blockers.size())through+=pw;
    }
    double score=threat(p);
    if(through>=p.getLife()&&p.canLoseLife()&&!p.cantLoseForZeroOrLessLife())score+=1000;   // lethal: always
    score+=through*3;
    if(blockers.isEmpty()&&!attackers.isEmpty())score+=25;          // cannot block at all
    score+=Math.max(0,p.getStartingLife()-p.getLife())*0.5;         // finish weakened players
    scores[i]=score;
    why.append(p.getName()).append('=').append(Math.round(score)).append("(threat ").append(Math.round(threat(p)))
       .append(", through ").append(through).append(", blockers ").append(blockers.size()).append(") ");
    if(score>bestScore){bestScore=score;best=p;}
   }
   Player last=LAST.get(ai);   // no flip-flopping: keep the old target when it is within 10 %
   if(last!=null&&last!=best&&opps.contains(last)&&bestScore<1000){
    double lastScore=scores[opps.indexOf(last)];
    if(lastScore>=bestScore*0.9)best=last;
   }
   LAST.put(ai,best);
   log(ai,"attack "+best.getName()+" | "+why);
   return best;
  }catch(Throwable t){return null;}
 }

 public static Card bestCreature(Iterable<Card> list){
  try{
   if(list==null||!active())return null;
   Player me=null;
   for(Card c:list){me=c.getGame().getPhaseHandler().getPriorityPlayer();break;}
   if(me==null||!me.getController().isAI())return null;
   List<Card> cands=new ArrayList<>();
   for(Card c:list){
    if(!c.isCreature())continue;
    if(!c.getController().isOpponentOf(me))return null;   // own or mixed lists (pump, sacrifice...) stay native
    cands.add(c);
   }
   if(cands.isEmpty())return null;
   Player top=null;double topThreat=-1;int opps=0;
   for(Player p:me.getOpponents()){if(p.hasLost())continue;opps++;double t=threat(p);if(t>topThreat){topThreat=t;top=p;}}
   Card best=null;double bestScore=-1e9;
   for(Card c:cands){
    double s=ComputerUtilCard.evaluateCreature(c)+c.getCMC()*10+(c.isCommander()?50:0);
    if(opps>=2&&c.getController()==top)s+=60;
    if(s>bestScore){bestScore=s;best=c;}
   }
   log(me,"removal "+best.getName()+" ("+best.getController().getName()+", mv "+best.getCMC()+")");
   return best;
  }catch(Throwable t){return null;}
 }

 /** test instance only: one line per AI and turn into the log */
 static void log(Player ai,String msg){
  if(System.getProperty("forge.devmode")==null)return;
  try{
   int turn=ai.getGame().getPhaseHandler().getTurn();
   String key=msg.substring(0,Math.min(7,msg.length()));
   Integer seen=LOGGED.get(ai);
   if(seen!=null&&seen==turn*2+(key.startsWith("attack")?0:1))return;
   LOGGED.put(ai,turn*2+(key.startsWith("attack")?0:1));
   System.out.println("[threat] T"+turn+" "+ai.getName()+": "+msg);
  }catch(Throwable ignored){}
 }
}
