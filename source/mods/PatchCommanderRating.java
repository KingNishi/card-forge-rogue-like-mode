import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** Commander rating in card views (see CommanderRatingMod):
 *  ImageView$ItemInfo / ColumnDef: CardRanker.getRawScore -> CommanderRatingMod.score
 *  ImageView$ItemInfo.draw: big draft badge + centered number -> small corner badge; showPriceInfo -> showPrice
 *  AdventureDeckEditor$AdventureCardManager$1 (list rows): showPriceInfo -> showPrice */
public class PatchCommanderRating {
 static final String M="forge/adventure/util/CommanderRatingMod";
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);int count=0;
  for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(M)){System.exit(2);return;}
  for(MethodNode m:c.methods){
   boolean afterBadge=false;
   for(AbstractInsnNode n:m.instructions.toArray()){
    if(n instanceof FieldInsnNode f&&f.name.equals("draftRankImage")&&n.getOpcode()==Opcodes.GETFIELD)afterBadge=true;
    if(!(n instanceof MethodInsnNode call))continue;
    if(call.owner.equals("forge/gamemodes/limited/CardRanker")&&call.name.equals("getRawScore")){call.owner=M;call.name="score";count++;}
    else if(call.name.equals("showPriceInfo")&&call.desc.equals("()Z")&&(call.owner.equals("forge/itemmanager/ItemManager")||call.owner.endsWith("AdventureCardManager"))){
     call.setOpcode(Opcodes.INVOKESTATIC);call.owner=M;call.name="showPrice";call.desc="(Lforge/itemmanager/ItemManager;)Z";call.itf=false;count++;}
    else if(afterBadge&&call.owner.equals("forge/Graphics")&&call.name.equals("drawImage")&&call.desc.equals("(Lforge/assets/FImage;FFFF)V")){
     call.setOpcode(Opcodes.INVOKESTATIC);call.owner=M;call.name="badge";call.desc="(Lforge/Graphics;Lforge/assets/FImage;FFFF)V";count++;}
    else if(afterBadge&&call.owner.equals("forge/Graphics")&&call.name.equals("drawText")&&call.desc.equals("(Ljava/lang/String;Lforge/assets/FSkinFont;Lcom/badlogic/gdx/graphics/Color;FFFFZIZ)V")){
     call.setOpcode(Opcodes.INVOKESTATIC);call.owner=M;call.name="badgeText";
     call.desc="(Lforge/Graphics;Ljava/lang/String;Lforge/assets/FSkinFont;Lcom/badlogic/gdx/graphics/Color;FFFFZIZ)V";count++;afterBadge=false;}
   }
  }
  if(count==0){System.exit(2);return;}
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
  System.out.println("patched "+count);
 }
}
