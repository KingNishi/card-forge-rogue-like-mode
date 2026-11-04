package forge.player;

import forge.game.ability.ApiType;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementType;
import forge.game.spellability.SpellAbility;
import java.util.List;
import java.util.Set;

/** Forge asks for the order of simultaneous replacement effects even when the order cannot matter, e.g. a planeswalker's
 *  starting loyalty plus Oath of Gideon's extra loyalty counter (user, 2026-10-15). When every candidate only puts
 *  counters on the entering permanent or makes it enter tapped, all orders give the same result: no question. */
public final class ReplacementAutoMod {
 private static final Set<ApiType> ORDER_FREE=Set.of(ApiType.PutCounter,ApiType.Tap);
 private ReplacementAutoMod(){}
 public static boolean orderIrrelevant(List<ReplacementEffect> list){
  try{
   if(list==null||list.size()<2)return false;
   for(ReplacementEffect re:list){
    if(re.getMode()!=ReplacementType.Moved||re.hasParam("Optional"))return false;
    SpellAbility sa=re.ensureAbility();if(sa==null)return false;
    for(SpellAbility s=sa;s!=null;s=s.getSubAbility())if(s.getApi()==null||!ORDER_FREE.contains(s.getApi()))return false;
   }
   return true;
  }catch(RuntimeException e){return false;}
 }
}
