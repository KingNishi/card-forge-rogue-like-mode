package forge.adventure.util;
import forge.itemmanager.*;
import java.util.Map;
public final class DraftRatingMod {
 private static boolean adventure(CardManager manager){return manager.getClass().getName().equals("forge.adventure.scene.AdventureDeckEditor$AdventureCardManager");}
 public static void columns(ItemManagerConfig config){
  if(!config.getCols().containsKey(ColumnDef.RANKING)){
   ItemColumnConfig column=new ItemColumnConfig(ColumnDef.RANKING);
   column.setIndex(config.getCols().size());column.setVisible(true);column.setSortPriority(0);column.establishDefaults();
   config.getCols().put(ColumnDef.RANKING,column);
  }
  config.getCols().get(ColumnDef.RANKING).setVisible(true);
 }
 public static void setup(CardManager manager,ItemManagerConfig config,Map<ColumnDef,ItemColumn> overrides){
  if(adventure(manager))columns(config);
  manager.setup(config,overrides);
 }
 public static void showRanking(CardManager manager,boolean show){manager.setShowRanking(show||adventure(manager));}
}
