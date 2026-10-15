import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchModernBattlefield {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);String helper="forge/adventure/util/ModernBattlefieldMod";int changes=0;
  if(c.name.equals("forge/assets/FSkinTexture")){
   if(c.methods.stream().anyMatch(m->m.name.equals("modernOriginalDraw"))){System.exit(2);return;}
   MethodNode old=c.methods.stream().filter(m->m.name.equals("draw")&&m.desc.equals("(Lforge/Graphics;FFFF)V")).findFirst().orElseThrow();old.name="modernOriginalDraw";
   MethodNode bridge=new MethodNode(Opcodes.ACC_PUBLIC,"draw",old.desc,null,null);bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));for(int i=2;i<=5;i++)bridge.instructions.add(new VarInsnNode(Opcodes.FLOAD,i));bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,helper,"background","(Lforge/assets/FSkinTexture;Lforge/Graphics;FFFF)V",false));bridge.instructions.add(new InsnNode(Opcodes.RETURN));c.methods.add(bridge);changes++;
  }else{
   for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray()){
    if(n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&f.owner.equals("com/badlogic/gdx/graphics/Color")&&f.desc.equals("Lcom/badlogic/gdx/graphics/Color;")){
     String method=c.name.endsWith("VPhaseIndicator$PhaseLabel")&&m.name.equals("draw")?"phaseInk":"literal";
     if(n.getNext() instanceof MethodInsnNode call&&call.owner.equals(helper)){if(!call.name.equals(method)){call.name=method;changes++;}continue;}
     m.instructions.insert(n,new MethodInsnNode(Opcodes.INVOKESTATIC,helper,method,"(Lcom/badlogic/gdx/graphics/Color;)Lcom/badlogic/gdx/graphics/Color;",false));changes++;
    }
    if(c.name.equals("forge/toolbox/FButton")&&n instanceof MethodInsnNode call&&call.owner.equals("forge/Graphics")&&call.name.equals("drawImage")&&call.desc.equals("(Lforge/assets/FImage;FFFF)V")){
     call.setOpcode(Opcodes.INVOKESTATIC);call.owner=helper;call.name="drawControl";call.desc="(Lforge/Graphics;Lforge/assets/FImage;FFFF)V";changes++;
    }
    if((c.name.endsWith("VPhaseIndicator$PhaseLabel")||c.name.endsWith("FMenuTab")||c.name.endsWith("VPlayerPanel$InfoTab"))&&n instanceof MethodInsnNode call&&call.owner.equals("forge/Graphics")&&call.name.equals("fillRect")&&call.desc.equals("(Lforge/assets/FSkinColor;FFFF)V")){
     call.setOpcode(Opcodes.INVOKESTATIC);call.owner=helper;call.name="roundPanel";call.desc="(Lforge/Graphics;Lforge/assets/FSkinColor;FFFF)V";changes++;
    }
    if(c.name.endsWith("FCardPanel$CardDestroyedAnimation")&&n instanceof LdcInsnNode ldc&&ldc.cst instanceof Float value&&value==.6f){ldc.cst=.001f;changes++;}
   }
  }
  if(changes==0){System.exit(2);return;}ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
