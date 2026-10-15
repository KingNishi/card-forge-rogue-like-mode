import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** PointOfInterestMapRenderer: wrap render() with MapLightMod.begin/end (map + sprites drawn off-screen, then lit) and
 *  add a renderMapLayer override that lets MapLightMod switch the ground layers to the material shader. */
public class PatchMapLight {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);String H="forge/adventure/util/MapLightMod";int count=0;
  String desc="(Lcom/badlogic/gdx/maps/tiled/renderers/OrthogonalTiledMapRenderer;)V";
  for(MethodNode m:c.methods){
   if(!m.name.equals("render")||!m.desc.equals("()V"))continue;
   boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H))exists=true;
   if(exists)continue;
   InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"begin",desc,false));m.instructions.insert(a);count++;
   for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList e=new InsnList();e.add(new VarInsnNode(Opcodes.ALOAD,0));e.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"end",desc,false));m.instructions.insertBefore(n,e);count++;}
  }
  String layerDesc="(Lcom/badlogic/gdx/maps/MapLayer;)V",hookDesc="(Lcom/badlogic/gdx/maps/tiled/renderers/OrthogonalTiledMapRenderer;Lcom/badlogic/gdx/maps/MapLayer;)V";
  if(c.methods.stream().noneMatch(m->m.name.equals("renderMapLayer")&&m.desc.equals(layerDesc))){
   MethodNode m=new MethodNode(Opcodes.ACC_PUBLIC,"renderMapLayer",layerDesc,null,null);
   m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
   m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"layerBegin",hookDesc,false));
   m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
   m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,c.superName,"renderMapLayer",layerDesc,false));
   m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
   m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"layerEnd",hookDesc,false));
   m.instructions.add(new InsnNode(Opcodes.RETURN));m.maxStack=2;m.maxLocals=2;c.methods.add(m);count++;
  }
  if(count==0){System.exit(2);return;}ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
