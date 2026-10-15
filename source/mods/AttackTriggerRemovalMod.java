package forge.ai;

import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

/** The last priority window before attackers: do not discount valuable attack engines.
 * This only changes the native removal timing decision, after normal target selection.
 * Target legality, mana, costs, destruction protection and spell timing remain native. */
public final class AttackTriggerRemovalMod {
 private AttackTriggerRemovalMod(){}
 public static boolean beforeAttack(SpellAbility removal,Card target,int ignored,ZoneType destination){
  if(removal==null||target==null||removal.getActivatingPlayer()==null)return false;
  var ai=removal.getActivatingPlayer();var game=ai.getGame();var phases=game.getPhaseHandler();
  if(phases.getPhase()!=PhaseType.COMBAT_BEGIN||!game.getStack().isEmpty())return false;
  if(target.getController()!=phases.getPlayerTurn()||!target.getController().isOpponentOf(ai))return false;
  if(!target.isInZone(ZoneType.Battlefield)||!target.isCreature()||!CombatUtil.canAttack(target))return false;
  return valuableAttackTrigger(target);
 }
 static boolean valuableAttackTrigger(Card target){
  if(target.getCMC()<4&&target.getNetPower()<4)return false;
  for(var trigger:target.getTriggers()){
   if(trigger.isSuppressed()||trigger.getMode()!=TriggerType.Attacks)continue;
   String valid=trigger.getParamOrDefault("ValidCard","");
   if(!valid.contains("Self"))continue;
   SpellAbility effect=trigger.ensureAbility();
   for(int depth=0;effect!=null&&depth<16;depth++,effect=effect.getSubAbility()){
    if("Curse".equals(effect.getParam("AILogic")))continue;
    if(effect.getApi()==null)continue;
    switch(effect.getApi().name()){
     case "Dig":case "DigUntil":case "Draw":case "Token":case "CopyPermanent":
     case "DealDamage":case "DamageAll":case "AddTurn":case "AddPhase":case "Play":case "Mana":case "PumpAll":return true;
     case "ChangeZone":
      String to=effect.getParamOrDefault("Destination","");
      if(to.equals("Battlefield")||to.equals("Hand"))return true;
      break;
     default:break;
    }
   }
  }
  return false;
 }
}
