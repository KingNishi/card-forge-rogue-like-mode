package forge.screens.match.views;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;
import forge.Graphics;
import forge.assets.FImage;
import forge.assets.FSkinFont;
import forge.game.player.PlayerView;
import forge.screens.match.MatchController;
import java.lang.reflect.Field;

/**
 * The match menu's "Players" view in the modern battle style (user 2026-10-16: "immer noch das alte Design"): a dark
 * glass card per player with a framed portrait, the name in gold, life with a heart and the other details in two
 * readable columns. VPlayers$PlayerInfoPanel.drawBackground starts with "if(PlayersStyleMod.draw(this,g))return;"
 * (PatchPlayersStyle); on any problem Forge's own drawing is used.
 */
public final class PlayersStyleMod {
 private PlayersStyleMod(){}
 static final Color GLASS=new Color(.055f,.068f,.095f,.92f),GOLD=Color.valueOf("e0b24f"),ENEMY=Color.valueOf("ef6b6b"),
  TEXT=Color.valueOf("efe6d2"),DIM=Color.valueOf("b9b2a2"),HEART=Color.valueOf("ee4549");
 private static Field playerField;

 public static boolean draw(Object panel,Graphics g){
  try{
   if(playerField==null){playerField=panel.getClass().getDeclaredField("player");playerField.setAccessible(true);}
   PlayerView player=(PlayerView)playerField.get(panel);if(player==null)return false;
   var obj=(forge.toolbox.FDisplayObject)panel;float w=obj.getWidth(),h=obj.getHeight();if(h<20)return false;
   boolean ai=player.isAI();Color accent=ai?ENEMY:GOLD;
   float pad=Math.max(6,h*.06f),r=Math.min(14,h*.08f);
   g.fillRoundRect(new Color(accent.r*.45f,accent.g*.45f,accent.b*.45f,.95f),pad*.5f,pad*.5f,w-pad,h-pad,r);
   g.fillRoundRect(GLASS,pad*.5f+2,pad*.5f+2,w-pad-4,h-pad-4,Math.max(0,r-2));
   // portrait
   float as=h-pad*3,ax=pad*1.5f,ay=pad*1.5f;
   FImage avatar=MatchController.getPlayerAvatar(player);
   g.fillRect(Color.BLACK,ax-2,ay-2,as+4,as+4);
   if(avatar!=null)g.drawImage(avatar,ax,ay,as,as);
   g.drawRect(2,accent,ax-1,ay-1,as+2,as+2);
   // name + life
   String[] lines=player.getDetails()==null?new String[0]:player.getDetails().split("\n");
   String name=player.getName()==null?(lines.length>0?lines[0]:""):player.getName();
   float tx=ax+as+pad*1.6f,tw=w-tx-pad*1.5f,row=Math.max(14,h*.13f);
   g.drawText(name,FSkinFont.forHeight(row*1.25f),accent,tx,ay,tw,row*1.4f,false,Align.left,true);
   float ly=ay+row*1.55f,hs=row*.9f;
   heart(g,tx,ly+(row-hs)/2,hs);
   g.drawText(String.valueOf(player.getLife()),FSkinFont.forHeight(row),TEXT,tx+hs+row*.35f,ly,tw*.4f,row,false,Align.left,true);
   // the remaining details (without the name and life lines) in two columns
   java.util.List<String> rest=new java.util.ArrayList<>();
   for(String l:lines){String t=l.trim();if(t.isEmpty()||t.equals(name)||t.toLowerCase(java.util.Locale.ROOT).startsWith("life"))continue;rest.add(t);}
   float cy=ly+row*1.35f,cw=tw/2;FSkinFont f=FSkinFont.forHeight(row*.78f);int half=(rest.size()+1)/2;
   for(int i=0;i<rest.size();i++){float x=tx+(i<half?0:cw),y=cy+(i<half?i:i-half)*row*.95f;
    if(y+row>h-pad)break;
    g.drawText(rest.get(i),f,i<half?TEXT:DIM,x,y,cw-pad,row,false,Align.left,true);}
   return true;
  }catch(Throwable t){return false;}
 }
 static void heart(Graphics g,float x,float y,float s){
  float r=s*.27f;g.fillCircle(HEART,x+r,y+r*1.05f,r);g.fillCircle(HEART,x+s-r,y+r*1.05f,r);
  g.fillTriangle(HEART,x+.02f*s,y+r*1.35f,x+s*.98f,y+r*1.35f,x+s/2,y+s*.98f);
 }
}
