import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Launcher for the itch.io app channel (auto-updates). The itch app copies the game into a folder of its choice and
 * patches the files on every update, so nothing may be relocated in place like the Setup.exe does. Instead:
 *  - the game JAR is copied to rogue-run.jar and that copy is relocated to the real folder (Relocate.java),
 *  - the few text files in _mods that carry the author's folder get the real folder,
 *  - all of this runs again whenever the JAR changed (an update) or the folder moved,
 * then the game starts with the same arguments as "Rogue Commanders.cmd". Pristine files stay pristine, so the itch app
 * can keep patching small diffs.
 *
 *   .itch.toml: path = "runtime/bin/javaw.exe", args = ["-cp", "installer;../../installer", "ItchLaunch"]
 */
public class ItchLaunch {
    static final String JAR = "forge-gui-mobile-dev-2.0.16-SNAPSHOT-jar-with-dependencies.jar", RUN = "rogue-run.jar",
                        MARK = ".itch-relocated";

    public static void main(String[] args) throws Exception {
        Path installer = Path.of(ItchLaunch.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path root = installer.getParent().toAbsolutePath().normalize();
        Path log = root.resolve("itch-launch.log");
        try {
            Path jar = root.resolve(JAR), run = root.resolve(RUN), mark = root.resolve(MARK);
            String slash = root.toString().replace('\\', '/') + "/";
            String stamp = slash + "|" + Files.size(jar) + "|" + Files.getLastModifiedTime(jar).toMillis();
            String old = Files.exists(mark) ? Files.readString(mark, StandardCharsets.UTF_8).trim() : "";
            if (!old.equals(stamp) || !Files.exists(run)) {
                Files.copy(jar, run, StandardCopyOption.REPLACE_EXISTING);
                Relocate.main(new String[]{root.toString(), run.toString()});
                String previous = old.contains("|") ? old.substring(0, old.indexOf('|')) : null;
                relocateTexts(root.resolve("_mods"), slash, previous);
                Files.writeString(mark, stamp, StandardCharsets.UTF_8);
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(root.resolve("runtime/bin/javaw.exe").toString());
            cmd.addAll(launchArgs(root));
            new ProcessBuilder(cmd).directory(root.toFile()).redirectErrorStream(true)
                    .redirectOutput(root.resolve("forge-output.log").toFile()).start();
        } catch (Throwable t) {
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(log))) { t.printStackTrace(w); }
            throw t;
        }
    }

    /** arguments of the javaw line in "Rogue Commanders.cmd", with the game JAR swapped for the relocated copy */
    static List<String> launchArgs(Path root) throws IOException {
        String line = Files.readAllLines(root.resolve("Rogue Commanders.cmd"), StandardCharsets.UTF_8).stream()
                .filter(l -> l.contains("javaw.exe")).findFirst().orElseThrow();
        String rest = line.substring(line.indexOf("javaw.exe\"") + "javaw.exe\"".length()).trim();
        List<String> out = new ArrayList<>(Arrays.asList(rest.split("\\s+")));
        int i = out.indexOf(JAR);
        if (i < 0) throw new IOException("game JAR not found in Rogue Commanders.cmd");
        out.set(i, RUN);
        return out;
    }

    /** the author's folder (and an earlier install folder) -> this folder, in the _mods text files */
    static void relocateTexts(Path mods, String slash, String previous) throws IOException {
        if (!Files.isDirectory(mods)) return;
        String back = slash.replace('/', '\\').replace("\\", "\\\\");
        List<Path> files;
        try (Stream<Path> s = Files.walk(mods)) {
            files = s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".json") || n.endsWith(".properties") || n.endsWith(".txt");
            }).collect(Collectors.toList());
        }
        for (Path p : files) {
            String text = Files.readString(p, StandardCharsets.UTF_8), moved = text.replace("D:/forge/", slash).replace("D:\\\\forge\\\\", back);
            if (previous != null && !previous.equals(slash)) moved = moved.replace(previous, slash)
                    .replace(previous.replace('/', '\\').replace("\\", "\\\\"), back);
            if (!moved.equals(text)) Files.writeString(p, moved, StandardCharsets.UTF_8);
        }
    }
}
