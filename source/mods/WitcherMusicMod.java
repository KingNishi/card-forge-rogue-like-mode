package forge.adventure.util;
import java.io.File;
import forge.sound.MusicPlaylist;
import forge.sound.SoundSystem;
public final class WitcherMusicMod {
    public static File directory(MusicPlaylist playlist) {
        File folder = new File("D:/forge/_mods/witcher-music/playlists", playlist.getSubDir());
        File[] tracks = folder.listFiles((dir,name)->name.toLowerCase().endsWith(".mp3"));
        return tracks != null && tracks.length > 0 ? folder : SoundSystem.findMusicDirectory(playlist);
    }
}
