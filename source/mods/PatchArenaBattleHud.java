import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;
public class PatchArenaBattleHud {
 public static void main(String[] args)throws Exception{
  ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(c,0);String H="forge/adventure/util/ArenaBattleHudMod";int count=0;
  for(MethodNode m:c.methods){
   String method=c.name.endsWith("VPlayerPanel")&&m.name.equals("updateTabLayout")?"layout":c.name.endsWith("VPrompt")&&m.name.equals("doLayout")?"prompt":null;
   if(method!=null){boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H))exists=true;
    if(!exists)for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.FLOAD,1));a.add(new VarInsnNode(Opcodes.FLOAD,2));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,method,"(L"+c.name+";FF)V",false));m.instructions.insertBefore(n,a);count++;}
   }
   if(c.name.endsWith("VZoneDisplay")&&m.name.equals("layoutAndGetScrollBounds"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.name.equals("layoutVerticallyForLandscapeMode")){
    if(n.getNext() instanceof VarInsnNode a&&a.getOpcode()==Opcodes.ALOAD)continue;
    InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"zoneVertical","(ZLforge/screens/match/views/VZoneDisplay;)Z",false));m.instructions.insert(n,a);count++;
   }
  }
  for(MethodNode m:c.methods){
   // Flashback tab: leave command-zone cards in the command zone (one shared card panel per card)
   if(c.name.endsWith("VZoneDisplay")&&m.name.equals("update")&&m.desc.equals("()V"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.name.equals("refreshCardPanels")){
    if(n.getPrevious() instanceof MethodInsnNode prev&&prev.owner.equals(H))continue;
    InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"zoneCards","(Ljava/lang/Iterable;Lforge/screens/match/views/VZoneDisplay;)Ljava/lang/Iterable;",false));m.instructions.insertBefore(n,a);count++;
   }
   // turn plaque / turn banner on top of the board
   if(c.name.endsWith("VPlayerPanel")&&m.name.equals("drawOverlay")&&m.desc.equals("(Lforge/Graphics;)V")){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("overlay"))exists=true;
    if(!exists)for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.RETURN){InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new VarInsnNode(Opcodes.ALOAD,1));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"overlay","(Lforge/screens/match/views/VPlayerPanel;Lforge/Graphics;)V",false));m.instructions.insertBefore(n,a);count++;}
   }
  }
  for(MethodNode m:c.methods){
   // slimmer top menu bar
   if(c.name.equals("forge/menu/FMenuBar")&&m.name.equals("getPreferredHeight")&&m.desc.equals("()F"))for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==Opcodes.FRETURN){
    if(n.getPrevious() instanceof MethodInsnNode prev&&prev.owner.equals(H))continue;
    m.instructions.insertBefore(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"headerHeight","(F)F",false));count++;
   }
   // slimmer bottom prompt bar
   if(c.name.equals("forge/screens/match/MatchScreen"))for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&f.owner.equals("forge/screens/match/views/VPrompt")&&f.name.equals("HEIGHT")){
    if(n.getNext() instanceof MethodInsnNode next&&next.owner.equals(H))continue;
    m.instructions.insert(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"promptHeight","(F)F",false));count++;
   }
   // one-line prompt message with a font that fits
   if(c.name.equals("forge/screens/match/views/VPrompt$MessageLabel")&&m.name.equals("draw")){
    AbstractInsnNode[] all=m.instructions.toArray();
    for(AbstractInsnNode n:all)if(n instanceof MethodInsnNode call&&call.name.equals("drawText")&&call.owner.equals("forge/assets/TextRenderer")){
     AbstractInsnNode font=null,text=null;
     for(AbstractInsnNode q=n.getPrevious();q!=null;q=q.getPrevious()){
      if(font==null&&q instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&f.name.equals("FONT"))font=q;
      if(text==null&&q instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.name.equals("message")){text=q;break;}
     }
     if(font==null||text==null||text.getNext() instanceof MethodInsnNode t&&t.owner.equals(H))continue;
     m.instructions.insert(text,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"promptText","(Ljava/lang/String;)Ljava/lang/String;",false));
     InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"promptFont","(Lforge/assets/FSkinFont;Lforge/toolbox/FDisplayObject;)Lforge/assets/FSkinFont;",false));m.instructions.insert(font,a);count+=2;
    }
   }
   // equal battlefield heights for both players
   if(c.name.equals("forge/screens/match/MatchScreen$FieldScroller")&&m.name.equals("layoutAndGetScrollBounds")){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("split"))exists=true;
    if(!exists)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW&&t.desc.equals("forge/toolbox/FScrollPane$ScrollBounds")){
     InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.FLOAD,1));a.add(new VarInsnNode(Opcodes.FLOAD,3));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"split","(FF)V",false));m.instructions.insertBefore(n,a);count++;break;
    }
   }
   // the scroller (board) reaches the bottom edge: MatchScreen.doLayout's scroller.setBounds(..., height-promptHeight-startY)
   if(c.name.equals("forge/screens/match/MatchScreen")&&m.name.equals("doLayout")&&m.desc.equals("(FFF)V")){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("scrollerGap"))exists=true;
    boolean inScroller=false;
    if(!exists)for(AbstractInsnNode n:m.instructions.toArray()){
     if(n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.name.equals("scroller"))inScroller=true;
     else if(inScroller&&n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("promptHeight")){
      m.instructions.insert(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"scrollerGap","(F)F",false));count++;break;}
     else if(inScroller&&n instanceof MethodInsnNode call&&call.name.equals("setBounds"))inScroller=false;
    }
   }
   // portrait: VAvatar.draw reads its image through H.avatarImage (commander art for the human commander player)
   if(c.name.equals("forge/screens/match/views/VAvatar")&&m.name.equals("draw")&&m.desc.equals("(Lforge/Graphics;)V")){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("avatarImage"))exists=true;
    if(!exists)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.owner.equals("forge/screens/match/views/VAvatar")&&f.name.equals("image")){
     InsnList a=new InsnList();a.add(new VarInsnNode(Opcodes.ALOAD,0));
     a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"avatarImage","(Lforge/assets/FImage;Lforge/screens/match/views/VAvatar;)Lforge/assets/FImage;",false));
     m.instructions.insert(n,a);count++;
    }
   }
   // own portrait: a tap casts your commander from the command zone (user 2026-11-14) - H.avatarTap decides, else Forge's selectPlayer
   if(c.name.equals("forge/screens/match/views/VAvatar")&&m.name.equals("tap")&&m.desc.equals("(FFI)Z")){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("avatarTap"))exists=true;
    if(!exists){InsnList a=new InsnList();LabelNode go=new LabelNode();
     a.add(new VarInsnNode(Opcodes.ALOAD,0));a.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"avatarTap","(Lforge/screens/match/views/VAvatar;)Z",false));
     a.add(new JumpInsnNode(Opcodes.IFEQ,go));a.add(new InsnNode(Opcodes.ICONST_1));a.add(new InsnNode(Opcodes.IRETURN));a.add(go);a.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
     m.instructions.insert(a);count++;}
   }
   // no divider line between the two player panels: FieldScroller.drawOverlay's Graphics.drawLine -> H.divider
   if(c.name.equals("forge/screens/match/MatchScreen$FieldScroller")&&m.name.equals("drawOverlay")&&m.desc.equals("(Lforge/Graphics;)V"))
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.equals("forge/Graphics")
       &&call.name.equals("drawLine")&&call.desc.equals("(FLforge/assets/FSkinColor;FFFF)V")){
     m.instructions.set(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"divider","(Lforge/Graphics;FLforge/assets/FSkinColor;FFFF)V",false));count++;
    }
   // board image from the very top: Graphics.drawImage(FImage,x,y,w,h) in FieldScroller.drawBackground -> H.boardImage
   if(c.name.equals("forge/screens/match/MatchScreen$FieldScroller")&&m.name.equals("drawBackground")&&m.desc.equals("(Lforge/Graphics;)V"))
    for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.equals("forge/Graphics")
       &&call.name.equals("drawImage")&&call.desc.equals("(Lforge/assets/FImage;FFFF)V")){
     m.instructions.set(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"boardImage","(Lforge/Graphics;Lforge/assets/FImage;FFFF)V",false));count++;
    }
   // board image under the whole width: Forge starts it at the battlefield's left edge, the command-zone column stayed bare
   if(c.name.equals("forge/screens/match/MatchScreen$FieldScroller")&&m.name.equals("drawBackground")&&m.desc.equals("(Lforge/Graphics;)V")){
    boolean exists=false;for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.owner.equals(H)&&call.name.equals("boardLeft"))exists=true;
    if(!exists)for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode call&&call.name.equals("getLeft")&&call.desc.equals("()F")){
     m.instructions.insert(n,new MethodInsnNode(Opcodes.INVOKESTATIC,H,"boardLeft","(F)F",false));count++;break;
    }
   }
  }
  if(c.name.endsWith("VPlayerPanel")&&c.methods.stream().noneMatch(m->m.name.equals("arenaOriginalBackground"))){
   MethodNode old=c.methods.stream().filter(m->m.name.equals("drawBackground")).findFirst().orElseThrow();old.name="arenaOriginalBackground";
   MethodNode bridge=new MethodNode(Opcodes.ACC_PUBLIC,"drawBackground",old.desc,null,null);bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,H,"background","(Lforge/screens/match/views/VPlayerPanel;Lforge/Graphics;)V",false));bridge.instructions.add(new InsnNode(Opcodes.RETURN));c.methods.add(bridge);count++;
  }
  if(count==0){System.exit(2);return;}ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Paths.get(args[1]),w.toByteArray());
 }
}
