import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
public class BuildHeroSprites {
 public static void main(String[] args)throws Exception{
  Gdx.files=new Lwjgl3Files();Path root=Paths.get("D:/forge/res/adventure/common/sprites/heroes"),out=Paths.get("D:/forge/_mods/hero-sprites4x");Files.createDirectories(out);
  int count=0;List<String> entries=new ArrayList<>();
  try(var files=Files.walk(root)){
   for(Path atlas:files.filter(p->p.toString().endsWith(".atlas")).toList()){
    try{
     TextureAtlas.TextureAtlasData data=new TextureAtlas.TextureAtlasData(Gdx.files.absolute(atlas.toString()),Gdx.files.absolute(atlas.getParent().toString()),false);
     for(TextureAtlas.TextureAtlasData.Page page:data.getPages()){
      Path source=Paths.get(page.textureFile.path());String name=source.getFileName().toString().toLowerCase();
      if(!name.endsWith(".png")||name.contains("avatar")||name.contains("_art")||name.contains("@")||name.startsWith("elf_f")||name.contains("custom"))continue;
      Path rel=root.relativize(source),target=out.resolve(rel);Files.createDirectories(target.getParent());
      if(Files.exists(target)&&Files.getLastModifiedTime(target).compareTo(Files.getLastModifiedTime(source))>=0){count++;continue;}
      BufferedImage image=ImageIO.read(source.toFile()),scaled=new BufferedImage(image.getWidth()*4,image.getHeight()*4,BufferedImage.TYPE_INT_ARGB);
      for(int y=0;y<scaled.getHeight();y++)for(int x=0;x<scaled.getWidth();x++)scaled.setRGB(x,y,image.getRGB(x/4,y/4));
      Set<String> seen=new HashSet<>();
      for(TextureAtlas.TextureAtlasData.Region r:data.getRegions()){
       if(r.page!=page||r.rotate||r.name.equals("Avatar"))continue;
       String key=r.left+":"+r.top+":"+r.width+":"+r.height;if(!seen.add(key))continue;
       if(r.left<0||r.top<0||r.left+r.width>image.getWidth()||r.top+r.height>image.getHeight())continue;
       BufferedImage frame=image.getSubimage(r.left,r.top,r.width,r.height);
       BufferedImage detailed=UpscaleAvatars.xbr2x(UpscaleAvatars.xbr2x(frame));
       int[] pixels=detailed.getRGB(0,0,detailed.getWidth(),detailed.getHeight(),null,0,detailed.getWidth());
       scaled.setRGB(r.left*4,r.top*4,detailed.getWidth(),detailed.getHeight(),pixels,0,detailed.getWidth());
      }
      ImageIO.write(scaled,"png",target.toFile());entries.add(rel.toString());count++;
     }
    }catch(Exception e){System.err.println("Skipped malformed hero atlas "+atlas.getFileName()+": "+e.getMessage());}
   }
  }
  Files.write(out.resolve("last-built.txt"),entries);System.out.println("Prepared "+count+" NPC/hero sprite sheets at 4x (player sprite excluded), frame-isolated xBR; original atlases and identities unchanged.");
 }
}
