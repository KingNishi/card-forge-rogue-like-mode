import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
/** DeckFormat.Commander: main deck Range.is(99) -> Range.of(MIN,MAX) (e.g. 59-99 cards + commander).
 *  args: in.class out.class MIN MAX; exit 2 when the range is already MIN-MAX. Works on Forge's original (99) and on the
 *  older byte patch (59). */
public class PatchDeckFormatRange {
 public static void main(String[] args)throws Exception{
  int min=Integer.parseInt(args[2]),max=Integer.parseInt(args[3]);
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);
  for(MethodNode m:c.methods){
   if(!m.name.equals("<clinit>"))continue;
   for(AbstractInsnNode n:m.instructions.toArray()){
    if(!(n instanceof LdcInsnNode l)||!"Commander".equals(l.cst))continue;
    // ldc "Commander", iconst_3, bipush N, Integer.valueOf, [bipush M, Integer.valueOf, Range.of | Range.is]
    AbstractInsnNode q=n.getNext();
    while(q!=null&&!(q instanceof IntInsnNode))q=q.getNext();
    if(!(q instanceof IntInsnNode first))break;
    AbstractInsnNode valueOf=first.getNext();
    AbstractInsnNode after=valueOf==null?null:valueOf.getNext();
    if(after instanceof MethodInsnNode is&&is.name.equals("is")&&is.owner.equals("org/apache/commons/lang3/Range")){
     first.operand=min;
     InsnList a=new InsnList();
     a.add(new IntInsnNode(max<=127?Opcodes.BIPUSH:Opcodes.SIPUSH,max));
     a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false));
     a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"org/apache/commons/lang3/Range","of","(Ljava/lang/Comparable;Ljava/lang/Comparable;)Lorg/apache/commons/lang3/Range;",false));
     m.instructions.insertBefore(is,a);m.instructions.remove(is);
     ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
     System.out.println("Commander main deck: "+min+"-"+max);return;
    }
    if(after instanceof IntInsnNode second&&first.operand==min&&second.operand==max){System.exit(2);return;}
    if(after instanceof IntInsnNode second){   // a different range: set ours
     first.operand=min;second.operand=max;
     ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
     System.out.println("Commander main deck: "+min+"-"+max);return;
    }
    break;
   }
  }
  System.err.println("Commander deck size pattern not found");System.exit(1);
 }
}
