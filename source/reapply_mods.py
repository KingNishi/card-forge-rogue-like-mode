"""
Re-applies the personal Forge tweaks after a Forge update.

  1. Commander deck size 99+1 -> 59+1   (DeckFormat.class inside the desktop + adventure JARs)
  2. Adventure: normal enemies give at least 4 cards   (res/adventure/common/world/enemies.json)
  3. Adventure: Hard sell factor stays at the original 0.25 (res/adventure/common/config.json)
  4. Developer mode on   (%APPDATA%/Forge/preferences/forge.preferences)
  5. Adventure: quest reputation rewards x2, no reputation penalties   (res/adventure/Shandalar/world/quests.json)
  6. Adventure: Swords to Plowshares in the "Swords, Plowshares, and Beyond" shop   (res/adventure/Shandalar/world/shops.json)
  7. Adventure: rotating town shops change hourly instead of daily   (MapStage.class in the adventure JAR, via PatchRotation.java)
  8. Adventure: arenas give +1 card for winning all rounds   (capital .tmx maps)
  9. Adventure: enemy avatars upscaled 2x with xBR (extra *_avatar2x.png pages) + arena size fix (ArenaScene.class)
 10. Adventure: enemies beaten 10+ times are defeated on touch (AutoWinMod.java + PatchAutoWin.java; not in arena/events)
 11. Adventure: every town shop re-rolls its shop type every hour (ShopHourMod.java + PatchShopHour.java)
 12. Adventure: custom player portraits (_mods/portraits/*.png, e.g. Megumin 96x96 -> extra pages in sprites/heroes/avatar.atlas)
     + big portraits keep 16x16 size on the mini map / in the arena (AvatarSizeMod.java + PatchAvatarSize.java)
 13. Adventure: hero map sprite replaced (sprites/heroes/elf_f.atlas -> Misa, floating witch in 2x resolution,
     same layout as Forge's sprites; the original elf_f.png stays, the new picture is elf_f@2x.png)
     + "@2x" sprite sheets are drawn at normal size (SpriteScaleMod.java + PatchSpriteScale.java)
 14. Adventure: illustrated enemy portraits from Magic, Hearthstone and custom fantasy art, 256x256 and smooth
     (art_avatars.py, ArtAvatars.java; sources in art_cache and portrait-sources,
     choices in art_plan.json and enemy_portrait_local_sources.json; arena size via step 12)
 15. Enemy/world sprite GPU resolution 2x, with heroes excluded (WorldSprite2xMod.java)
 16. Automatic card/token downloads use high-resolution PNG (HighResImagesMod.java)
 17. Local Squad token artwork is restored from _mods/token-images.
 18. Town/dungeon tile culling and camera edges follow zoom (MapZoomMod.java).
 19. Creature-shop signs use eight clear pictograms (shop_signs.py, ShopSignMod.java).
 20. Remaining original signs/overlays: isolated 4x upscaling and corrected M20/M21 crops.

Existing save files keep their own sell factor (reverted to 0.25) and are not touched.
Every step is idempotent: running it twice changes nothing the second time.
Originals are backed up once per file version into  <forge>/_backup_original_jars  and  <forge>/_backup_original_res.

Usage:  python reapply_mods.py            apply
        python reapply_mods.py --check    only report what would change
"""
import glob, hashlib, json, os, re, shutil, struct, subprocess, sys, tempfile, zipfile
from world_content import patch_world_content
from starter_decks import patch_starter_decks
from modern_skin import patch_modern_skin

# ----------------------------------------------------------------- settings
COMMANDER_MAIN_SIZE = 59      # minimum cards besides the commander (original: exactly 99)
COMMANDER_MAX_SIZE = 99       # maximum cards besides the commander
MIN_CARDS_NORMAL_ENEMY = 4    # guaranteed cards from non-boss enemies
HARD_SELL_FACTOR = 0.25       # Forge default (was modded to 0.5 before)
DEV_MODE = True
QUEST_REPUTATION_FACTOR = 2   # positive quest reputation rewards
QUEST_NO_PENALTIES = True     # reputation penalties (decline/fail) -> 0
AUTO_WIN_REQUIRED_WINS = 10   # wins against the same enemy before the duel can be skipped (0 = feature off is not supported; use a big number)
AVATAR_FILTER = 'Nearest'     # texture filter for the upscaled enemy avatars ('Linear' = softer, less pixelated)
ARENA_FULL_WIN_EXTRA_CARDS = 1  # extra cards for winning all arena rounds (Forge: 2 mythic rares)
SHOP_ROTATION_MINUTES = 60    # rotating town shops pick a new shop + new stock this often (Forge: once per day)
CUSTOM_PORTRAITS = {          # (atlas region, portrait no. 1-10) -> picture in _mods/portraits
    ('Elf_f', 10): 'gvene256.png',        # Gvene (Elf, female) -> own picture (made with portraits/convert_portrait.ps1)
}                                          # 'file.png' = whole file; ('file.png', n) = n x n picture at its top left
                                           # alternatives: ('megumin96.png', 96), 'megumin32.png' (self-made pixel art)
PORTRAIT_FILTER = 'MipMapLinearLinear,Linear'    # shrinking: smooth mipmaps; enlarging: Linear (use Nearest for pixel art)
HERO_SPRITES = ['elf_f']       # sprites/heroes/<name>.atlas replaced by _mods/portraits/sprites/<name>.atlas (+ <name>@2x.png)
                               # elf_f = Misa, floating witch, 32x32 frames (made with portraits/misa_sprite.py)
SHOP_ADDITIONS = {            # shop id in shops.json -> cards added to its first offer slot
    'White3': ['Swords to Plowshares'],   # "Swords, Plowshares, and Beyond" - its text filter misses the card it is named after
}

FORGE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_TEMP = os.path.join(FORGE, '_mods', 'build-temp')
os.makedirs(BUILD_TEMP, exist_ok=True)
tempfile.tempdir = BUILD_TEMP
CHECK_ONLY = '--check' in sys.argv
running_now = False
FAILURES = []
JAR_PATTERNS = ['forge-gui-desktop-*-jar-with-dependencies.jar', 'forge-gui-mobile-dev-*-jar-with-dependencies.jar']
DECKFORMAT = 'forge/deck/DeckFormat.class'


def say(tag, msg):
    print(f'  [{tag}] {msg}')
    if tag == '!!': FAILURES.append(str(msg))


def sha(data):
    return hashlib.sha1(data).hexdigest()[:10]


def backup_once(src_path, data, folder):
    """Keep a copy of the original file, one per distinct content."""
    os.makedirs(folder, exist_ok=True)
    name, ext = os.path.splitext(os.path.basename(src_path))
    dst = os.path.join(folder, f'{name}.{sha(data)}{ext}')
    if not os.path.exists(dst):
        with open(dst, 'wb') as f:
            f.write(data)
    return dst


def forge_running():
    try:
        out = subprocess.run(['wmic', 'process', 'where', "name='java.exe' or name='javaw.exe'", 'get', 'CommandLine'],
                             capture_output=True, text=True, timeout=20).stdout
    except Exception:
        try:
            out = subprocess.run(['powershell', '-NoProfile', '-Command',
                                  "Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | ForEach-Object CommandLine"],
                                 capture_output=True, text=True, timeout=30).stdout
        except Exception:
            return False
    return 'forge-gui' in out


# ----------------------------------------------------------------- 1. commander size in DeckFormat.class
def parse_constant_pool(cls):
    pos = 10
    count = struct.unpack('>H', cls[8:10])[0]
    cp = [None] * count
    i = 1
    while i < count:
        tag = cls[pos]; pos += 1
        if tag == 1:
            ln = struct.unpack('>H', cls[pos:pos + 2])[0]
            cp[i] = ('utf8', cls[pos + 2:pos + 2 + ln].decode('utf8', 'replace')); pos += 2 + ln
        elif tag in (3, 4): pos += 4
        elif tag in (5, 6): pos += 8; i += 1
        elif tag in (7, 8, 16, 19, 20): cp[i] = (tag, struct.unpack('>H', cls[pos:pos + 2])[0]); pos += 2
        elif tag in (9, 10, 11, 12, 17, 18): cp[i] = (tag,) + struct.unpack('>HH', cls[pos:pos + 4]); pos += 4
        elif tag == 15: pos += 3
        else: raise ValueError(f'unknown constant pool tag {tag}')
        i += 1
    return cp


def method_name(cp, idx):
    """Name of a Methodref constant (tag 10)."""
    e = cp[idx]
    if not e or e[0] != 10: return None
    nat = cp[e[2]]
    return cp[nat[1]][1]


def find_commander_size(cls):
    """Return byte offset of the size operand of `Commander(Range.is(<size>), ...)`, or None."""
    cp = parse_constant_pool(cls)
    str_idx = [i for i, e in enumerate(cp) if e and e[0] == 8 and cp[e[1]] == ('utf8', 'Commander')]
    hits = []
    for si in str_idx:
        needles = [bytes([0x13]) + struct.pack('>H', si)] + ([bytes([0x12, si])] if si < 256 else [])
        for nd in needles:
            start = 0
            while (p := cls.find(nd, start)) != -1:
                start = p + 1
                for q in range(p + len(nd), p + len(nd) + 8):          # enum ordinal push sits in between
                    if cls[q] == 0x10 and cls[q + 2] == 0xB8:          # bipush N ; invokestatic Integer.valueOf
                        m1 = struct.unpack('>H', cls[q + 3:q + 5])[0]
                        if cls[q + 5] == 0xB8:                         # invokestatic Range.is
                            m2 = struct.unpack('>H', cls[q + 6:q + 8])[0]
                            if method_name(cp, m1) == 'valueOf' and method_name(cp, m2) == 'is':
                                hits.append(q + 1)
                        break
    return hits[0] if len(hits) == 1 else None


def rewrite_jar(path, replacements):
    """Write a copy of the zip with some entries replaced, keeping order, dates and compression."""
    fd, tmp = tempfile.mkstemp(suffix='.jar', dir=os.path.dirname(path)); os.close(fd)
    try:
        with zipfile.ZipFile(path) as zin, zipfile.ZipFile(tmp, 'w') as zout:
            names = set()
            for info in zin.infolist():
                names.add(info.filename)
                data = replacements.get(info.filename)
                zout.writestr(info, data if data is not None else zin.read(info.filename))
            for name, data in replacements.items():          # new entries (e.g. mod classes)
                if name not in names:
                    zout.writestr(zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0)), data, compress_type=zipfile.ZIP_DEFLATED)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):                               # e.g. JAR locked by a running Forge: no 56 MB leftovers
            os.remove(tmp)


def patch_jars():
    print(f'1) Commander decks: {COMMANDER_MAIN_SIZE}-{COMMANDER_MAX_SIZE} cards + commander (DeckFormat in the JARs, PatchDeckFormatRange.java)')
    jars = sorted(set(sum((glob.glob(os.path.join(FORGE, p)) for p in JAR_PATTERNS), [])))
    # only the JARs the launchers actually start (old versions may still lie around)
    launchers = b''.join(open(f, 'rb').read() for f in glob.glob(os.path.join(FORGE, '*.cmd'))
                         + glob.glob(os.path.join(FORGE, '*.sh')) + glob.glob(os.path.join(FORGE, '*.exe')))
    used = [j for j in jars if os.path.basename(j).encode() in launchers]
    jars = used or jars
    if not jars:
        say('!!', 'no Forge JARs found'); return
    patcher = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'PatchDeckFormatRange.java')
    for jar in jars:
        name = os.path.basename(jar)
        with zipfile.ZipFile(jar) as z:
            if DECKFORMAT not in z.namelist():
                say('--', f'{name}: no DeckFormat.class'); continue
            cls = z.read(DECKFORMAT)
        tmp = tempfile.mkdtemp()
        try:
            src, dst = os.path.join(tmp, 'in.class'), os.path.join(tmp, 'out.class')
            open(src, 'wb').write(cls)
            r = subprocess.run([find_jdk_java()] + ASM_EXPORTS + [patcher, src, dst, str(COMMANDER_MAIN_SIZE), str(COMMANDER_MAX_SIZE)],
                               capture_output=True, text=True)
            if r.returncode == 2:
                say('ok', f'{name}: already {COMMANDER_MAIN_SIZE}-{COMMANDER_MAX_SIZE} + 1')
            elif r.returncode != 0 or not os.path.exists(dst):
                say('!!', f'{name}: deck size patch failed (Forge code changed?): {(r.stdout + r.stderr).strip()[:200]}')
            elif CHECK_ONLY:
                say('->', f'{name}: would set {COMMANDER_MAIN_SIZE}-{COMMANDER_MAX_SIZE} + 1')
            else:
                with open(jar, 'rb') as f:
                    backup_once(jar, f.read(), os.path.join(FORGE, '_backup_original_jars'))
                rewrite_jar(jar, {DECKFORMAT: open(dst, 'rb').read()})
                with zipfile.ZipFile(jar) as z:
                    assert z.testzip() is None
                say('OK', f'{name}: Commander decks {COMMANDER_MAIN_SIZE}-{COMMANDER_MAX_SIZE} + 1')
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


# ----------------------------------------------------------------- 2. enemy card rewards
CARD_TYPES = {'deckCard', 'card', 'randomCard', 'Card'}


def guaranteed(rewards):
    lo = 0; has_random = False
    for r in rewards:
        if r.get('type') in CARD_TYPES and r.get('probability', 1) >= 1:
            lo += r.get('count', 1)
            has_random |= r.get('addMaxCount', 0) > 0
    return lo, has_random


def patch_enemies():
    print('2) Card rewards of normal enemies (enemies.json)')
    path = os.path.join(FORGE, 'res', 'adventure', 'common', 'world', 'enemies.json')
    if not os.path.exists(path):
        say('!!', 'enemies.json not found'); return
    raw = open(path, 'rb').read()
    enemies = json.loads(raw.decode('utf8'))
    changed = 0
    for e in enemies:
        if e.get('boss'):
            continue
        rs = e.get('rewards') or []
        g = [r for r in rs if r.get('type') in CARD_TYPES and r.get('probability', 1) >= 1]
        if not g:
            rs.append({'type': 'deckCard', 'probability': 1, 'count': MIN_CARDS_NORMAL_ENEMY, 'addMaxCount': 4})
            e['rewards'] = rs; changed += 1; continue
        lo, has_random = guaranteed(rs)
        touched = False
        if lo < MIN_CARDS_NORMAL_ENEMY:
            tgt = next((r for r in g if r.get('type') == 'deckCard'), g[0])
            tgt['count'] = tgt.get('count', 1) + (MIN_CARDS_NORMAL_ENEMY - lo); touched = True
        if not has_random:   # give a chance for extra cards
            g[0]['addMaxCount'] = max(g[0].get('addMaxCount', 0), 4); touched = True
        changed += touched
    if changed == 0:
        say('ok', f'already applied (every normal enemy gives >= {MIN_CARDS_NORMAL_ENEMY} cards)'); return
    if CHECK_ONLY:
        say('->', f'would update {changed} normal enemies'); return
    backup_once(path, raw, os.path.join(FORGE, '_backup_original_res'))
    with open(path, 'w', encoding='utf8', newline='\n') as f:
        json.dump(enemies, f, ensure_ascii=False, indent=2)
    json.load(open(path, encoding='utf8'))
    say('OK', f'updated {changed} normal enemies -> at least {MIN_CARDS_NORMAL_ENEMY} cards')


# ----------------------------------------------------------------- 3. Hard sell factor in config.json
def patch_config():
    print('3) Hard sell factor for new games (config.json)')
    path = os.path.join(FORGE, 'res', 'adventure', 'common', 'config.json')
    if not os.path.exists(path):
        say('!!', 'config.json not found'); return
    raw = open(path, 'rb').read()
    hard = raw.find(b'"name": "Hard"')
    nxt = raw.find(b'"name":', hard + 5)
    nxt = len(raw) if nxt == -1 else nxt
    key = raw.find(b'"sellFactor"', hard, nxt)
    if hard == -1 or key == -1:
        say('!!', 'Hard difficulty / sellFactor not found - NOT patched'); return
    colon = raw.find(b':', key)
    end = colon + 1
    while raw[end:end + 1] in b' \t': end += 1
    stop = end
    while raw[stop:stop + 1] in b'0123456789.': stop += 1
    cur = float(raw[end:stop])
    if abs(cur - HARD_SELL_FACTOR) < 1e-9:
        say('ok', f'already {cur}'); return
    if CHECK_ONLY:
        say('->', f'would change {cur} -> {HARD_SELL_FACTOR}'); return
    backup_once(path, raw, os.path.join(FORGE, '_backup_original_res'))
    new = raw[:end] + str(HARD_SELL_FACTOR).encode() + raw[stop:]
    open(path, 'wb').write(new)
    d = [x for x in json.loads(new.decode('utf8'))['difficulties'] if x['name'] == 'Hard'][0]
    assert abs(d['sellFactor'] - HARD_SELL_FACTOR) < 1e-9
    say('OK', f'{cur} -> {HARD_SELL_FACTOR}')


# ----------------------------------------------------------------- 5. quest reputation rewards
STATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'state.json')


def load_state():
    try:
        return json.load(open(STATE, encoding='utf8'))
    except Exception:
        return {}


def save_state(st):
    json.dump(st, open(STATE, 'w', encoding='utf8'), indent=2)


def patch_quest_reputation():
    print('5) Quest reputation: rewards x%d, no penalties (quests.json)' % QUEST_REPUTATION_FACTOR)
    import re
    path = os.path.join(FORGE, 'res', 'adventure', 'Shandalar', 'world', 'quests.json')
    if not os.path.exists(path):
        say('!!', 'Shandalar quests.json not found'); return
    raw = open(path, 'rb').read()
    st = load_state()
    if sha(raw) in st.get('quests_patched', []):     # doubling is not idempotent, so remember our own output
        say('ok', 'already applied'); return
    pat = re.compile(rb'("addMapReputation"\s*:\s*)(-?\d+)')
    vals = [int(m.group(2)) for m in pat.finditer(raw)]
    pos = sum(v > 0 for v in vals); neg = sum(v < 0 for v in vals) if QUEST_NO_PENALTIES else 0
    if pos + neg == 0:
        say('!!', 'no reputation values found'); return
    if CHECK_ONLY:
        say('->', f'would multiply {pos} rewards by {QUEST_REPUTATION_FACTOR} and remove {neg} penalties'); return

    def repl(m):
        v = int(m.group(2))
        if v > 0: v *= QUEST_REPUTATION_FACTOR
        elif v < 0 and QUEST_NO_PENALTIES: v = 0
        return m.group(1) + str(v).encode()
    backup_once(path, raw, os.path.join(FORGE, '_backup_original_res'))
    new = pat.sub(repl, raw)
    json.loads(new.decode('utf8'))
    open(path, 'wb').write(new)
    st.setdefault('quests_patched', []).append(sha(new)); save_state(st)
    say('OK', f'{pos} reputation rewards x{QUEST_REPUTATION_FACTOR}, {neg} penalties removed')


# ----------------------------------------------------------------- 6. extra cards in shops
def patch_shops():
    print('6) Extra cards in shops (shops.json)')
    path = os.path.join(FORGE, 'res', 'adventure', 'Shandalar', 'world', 'shops.json')
    if not os.path.exists(path):
        say('!!', 'Shandalar shops.json not found'); return
    raw = open(path, 'rb').read()
    shops = json.loads(raw.decode('utf8'))
    todo = []
    for shop_name, cards in SHOP_ADDITIONS.items():
        shop = next((s for s in shops if s.get('name') == shop_name), None)
        if shop is None or not shop.get('rewards'):
            say('!!', f'shop {shop_name} not found'); continue
        text = json.dumps(shop, ensure_ascii=False)
        missing = [c for c in cards if json.dumps(c, ensure_ascii=False) not in text]
        if missing:
            todo.append((shop, missing))
        else:
            say('ok', f'{shop_name} ("{shop.get("description")}") already has {", ".join(cards)}')
    if not todo:
        return
    if CHECK_ONLY:
        for shop, missing in todo:
            say('->', f'would add {", ".join(missing)} to {shop["name"]}')
        return
    for shop, missing in todo:
        first = shop['rewards'][0]          # keep the original filter, add the cards to the same slot
        extra = [{'cardName': c} for c in missing]
        if first.get('type') == 'Union':
            first['cardUnion'] = (first.get('cardUnion') or []) + extra
        else:
            base = {k: v for k, v in first.items() if k not in ('count', 'type')}
            shop['rewards'][0] = {'type': 'Union', 'count': first.get('count', 1), 'cardUnion': [base] + extra}
    backup_once(path, raw, os.path.join(FORGE, '_backup_original_res'))
    with open(path, 'w', encoding='utf8', newline='\n') as f:
        json.dump(shops, f, ensure_ascii=False, indent=2)
    json.load(open(path, encoding='utf8'))
    for shop, missing in todo:
        say('OK', f'{shop["name"]} ("{shop.get("description")}"): added {", ".join(missing)}')


# ----------------------------------------------------------------- 7. rotating shops: hourly instead of daily
MAPSTAGE = 'forge/adventure/stage/MapStage.class'


def find_jdk_java():
    cands = []
    if os.environ.get('JAVA_HOME'):
        cands.append(os.path.join(os.environ['JAVA_HOME'], 'bin', 'java.exe'))
    cands += sorted(glob.glob('C:/Program Files/Java/jdk*/bin/java.exe'), reverse=True)
    for c in cands:
        if os.path.exists(c): return c
    return 'java'


def patch_rotation():
    print(f'7) Rotating town shops change every {SHOP_ROTATION_MINUTES} min instead of daily (adventure JAR)')
    jars = [j for j in glob.glob(os.path.join(FORGE, 'forge-gui-mobile-dev-*-jar-with-dependencies.jar'))]
    launchers = b''.join(open(f, 'rb').read() for f in glob.glob(os.path.join(FORGE, '*.cmd')) + glob.glob(os.path.join(FORGE, '*.sh')) + glob.glob(os.path.join(FORGE, '*.exe')))
    jars = [j for j in jars if os.path.basename(j).encode() in launchers] or jars
    if not jars:
        say('!!', 'adventure JAR not found'); return
    patcher = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'PatchRotation.java')
    for jar in jars:
        name = os.path.basename(jar)
        with zipfile.ZipFile(jar) as z:
            if MAPSTAGE not in z.namelist():
                say('--', f'{name}: no MapStage'); continue
            cls = z.read(MAPSTAGE)
        if b'toEpochDay' not in cls:
            say('ok', f'{name}: already patched'); continue
        if CHECK_ONLY:
            say('->', f'{name}: would switch shop rotation to every {SHOP_ROTATION_MINUTES} min'); continue
        tmp = tempfile.mkdtemp()
        src, dst = os.path.join(tmp, 'in.class'), os.path.join(tmp, 'out.class')
        open(src, 'wb').write(cls)
        exports = ['--add-exports', 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',
                   '--add-exports', 'java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']
        r = subprocess.run([find_jdk_java()] + exports + [patcher, src, dst, str(SHOP_ROTATION_MINUTES * 60_000)],
                           capture_output=True, text=True)
        if r.returncode != 0 or not os.path.exists(dst):
            say('!!', f'{name}: patcher failed (needs a JDK 17+): {(r.stdout + r.stderr).strip()[:200]}'); continue
        with open(jar, 'rb') as f:
            backup_once(jar, f.read(), os.path.join(FORGE, '_backup_original_jars'))
        rewrite_jar(jar, {MAPSTAGE: open(dst, 'rb').read()})
        with zipfile.ZipFile(jar) as z:
            assert z.testzip() is None and b'toEpochDay' not in z.read(MAPSTAGE)
        shutil.rmtree(tmp, ignore_errors=True)
        say('OK', f'{name}: shops rotate every {SHOP_ROTATION_MINUTES} min')


# ----------------------------------------------------------------- 8. arena: more cards for a full win
def patch_arenas():
    import re
    print(f'8) Arenas: +{ARENA_FULL_WIN_EXTRA_CARDS} card(s) for winning all rounds (capital maps)')
    st = load_state()
    done = set(st.get('arenas_patched', []))
    files = [f for f in glob.glob(os.path.join(FORGE, 'res', 'adventure', '*', 'maps', 'map', '**', '*.tmx'), recursive=True)
             if 'debug' not in f.lower() and b'name="arena"' in open(f, 'rb').read()]
    changed = 0
    for f in files:
        raw = open(f, 'rb').read()
        rel = os.path.relpath(f, FORGE)
        if sha(raw) in done:
            say('ok', f'{rel}: already applied'); continue
        text = raw.decode('utf8')

        def bump_property(m):
            body = m.group(2)
            i = body.find('&quot;rewards&quot;')
            if i < 0: return m.group(0)
            start = body.find('[', i); depth = 0; rounds = []; cur = None
            for k in range(start, len(body)):
                ch = body[k]
                if ch == '[':
                    depth += 1
                    if depth == 2: cur = k
                elif ch == ']':
                    if depth == 2: rounds.append((cur, k + 1))
                    depth -= 1
                    if depth == 0: break
            if not rounds: return m.group(0)
            a, b = rounds[-1]
            last = re.sub(r'\{[^{}]*?&quot;type&quot;\s*:\s*&quot;card&quot;[^{}]*\}',
                          lambda o: re.sub(r'(&quot;count&quot;\s*:\s*)(\d+)', lambda c: c.group(1) + str(int(c.group(2)) + ARENA_FULL_WIN_EXTRA_CARDS), o.group(0)),
                          body[a:b])
            return m.group(1) + body[:a] + last + body[b:] + m.group(3)

        new = re.sub(r'(<property name="arena">)(.*?)(</property>)', bump_property, text, flags=re.S)
        if new == text:
            say('--', f'{rel}: no card reward in the final round'); continue
        if CHECK_ONLY:
            say('->', f'{rel}: would add {ARENA_FULL_WIN_EXTRA_CARDS} card(s) to the full-win reward'); continue
        backup_once(f, raw, os.path.join(FORGE, '_backup_original_res'))
        out = new.encode('utf8')
        open(f, 'wb').write(out)
        st.setdefault('arenas_patched', []).append(sha(out)); changed += 1
        say('OK', f'{rel}: full-win card reward +{ARENA_FULL_WIN_EXTRA_CARDS}')
    if changed: save_state(st)


# ----------------------------------------------------------------- 9. enemy avatars 2x (xBR) + arena size fix
ASM_EXPORTS = ['--add-exports', 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',
               '--add-exports', 'java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']


def adventure_jars():
    jars = glob.glob(os.path.join(FORGE, 'forge-gui-mobile-dev-*-jar-with-dependencies.jar'))
    launchers = b''.join(open(f, 'rb').read() for f in glob.glob(os.path.join(FORGE, '*.cmd')) + glob.glob(os.path.join(FORGE, '*.sh')) + glob.glob(os.path.join(FORGE, '*.exe')))
    return [j for j in jars if os.path.basename(j).encode() in launchers] or jars


def patch_class_in_jars(entry, patcher, label, extra_args=()):
    """Run an ASM patcher (exit 0 = patched, 2 = nothing to do) on one class inside the adventure JAR(s)."""
    mods = os.path.dirname(os.path.abspath(__file__))
    for jar in adventure_jars():
        name = os.path.basename(jar)
        with zipfile.ZipFile(jar) as z:
            if entry not in z.namelist():
                say('--', f'{name}: {entry} not found'); continue
            cls = z.read(entry)
        tmp = tempfile.mkdtemp()
        src, dst = os.path.join(tmp, 'in.class'), os.path.join(tmp, 'out.class')
        open(src, 'wb').write(cls)
        r = subprocess.run([find_jdk_java()] + ASM_EXPORTS + [os.path.join(mods, patcher), src, dst] + list(extra_args), capture_output=True, text=True)
        if r.returncode == 2:
            say('ok', f'{name}: {label} already patched')
        elif r.returncode != 0 or not os.path.exists(dst):
            say('!!', f'{name}: {label} patcher failed: {(r.stdout + r.stderr).strip()[:200]}')
        elif CHECK_ONLY:
            say('->', f'{name}: would patch {label}')
        else:
            with open(jar, 'rb') as f:
                backup_once(jar, f.read(), os.path.join(FORGE, '_backup_original_jars'))
            rewrite_jar(jar, {entry: open(dst, 'rb').read()})
            with zipfile.ZipFile(jar) as z:
                assert z.testzip() is None
            say('OK', f'{name}: {label} patched')
        shutil.rmtree(tmp, ignore_errors=True)


def patch_avatars():
    print('9) Enemy avatars upscaled 2x (xBR) + arena keeps their old size')
    mods = os.path.dirname(os.path.abspath(__file__))
    args = [os.path.join(FORGE, 'res', 'adventure'), '--exclude', 'heroes', '--filter', AVATAR_FILTER] + (['--check'] if CHECK_ONLY else [])
    r = subprocess.run([find_jdk_java(), os.path.join(mods, 'UpscaleAvatars.java')] + args, capture_output=True, text=True)
    for line in (r.stdout + r.stderr).strip().splitlines()[-4:]:
        say('ok' if 'upscaled 0 ' in line or line.startswith('would upscale 0') else ('->' if CHECK_ONLY else 'OK'), line)
    if not running_now:
        patch_class_in_jars('forge/adventure/scene/ArenaScene.class', 'PatchArenaAvatar.java', 'arena avatar size')
    else:
        print('   arena patch skipped - close Forge and run again')


# ----------------------------------------------------------------- 10. auto-win + hourly shops (Java mods)
def install_java_mod(label, java_file, const_subs, patches, extra_sources=()):
    """Compile <java_file> against the adventure JAR (with constants replaced), run the ASM patchers
    [(class entry, patcher.java), ...] and put everything into the JAR in one rewrite."""
    if running_now:
        print('   skipped - close Forge and run again'); return
    mods = os.path.dirname(os.path.abspath(__file__))
    java = find_jdk_java()
    javac = os.path.join(os.path.dirname(java), 'javac.exe')
    if not os.path.exists(javac):
        say('!!', 'javac not found - a JDK 17+ is required'); return
    for jar in adventure_jars():
        name = os.path.basename(jar)
        tmp = tempfile.mkdtemp()
        try:
            repl = {}
            with zipfile.ZipFile(jar) as z:
                entries = set(z.namelist())
                for cls, patcher in patches:
                    src, dst = os.path.join(tmp, 'in.class'), os.path.join(tmp, 'out.class')
                    open(src, 'wb').write(z.read(cls))
                    if os.path.exists(dst): os.remove(dst)
                    r = subprocess.run([java] + ASM_EXPORTS + [os.path.join(mods, patcher), src, dst], capture_output=True, text=True)
                    if r.returncode == 0: repl[cls] = open(dst, 'rb').read()
                    elif r.returncode != 2:
                        say('!!', f'{name}: {patcher} failed on {cls}: {(r.stdout + r.stderr).strip()[:200]}'); return
                srcdir, outdir = os.path.join(tmp, 'src'), os.path.join(tmp, 'out')
                os.makedirs(srcdir); os.makedirs(outdir)
                code = open(os.path.join(mods, java_file), encoding='utf8').read()
                # sources name the author's install folder; use the folder this script actually lives in
                code = code.replace('D:/forge/', FORGE.replace(os.sep, '/').rstrip('/') + '/')
                for pattern, value in const_subs:
                    code = re.sub(pattern, value, code)
                open(os.path.join(srcdir, java_file), 'w', encoding='utf8').write(code)
                sources = [os.path.join(srcdir, java_file)]
                for extra in extra_sources:
                    extra_code = open(os.path.join(mods, extra), encoding='utf8').read().replace('D:/forge/', FORGE.replace(os.sep, '/').rstrip('/') + '/')
                    extra_path = os.path.join(srcdir, extra)
                    open(extra_path, 'w', encoding='utf8').write(extra_code)
                    sources.append(extra_path)
                cp = jar + (os.pathsep + ORT_JAR if os.path.exists(ORT_JAR) else '')   # ONNX Runtime for CardUpscaler (2026-11-15)
                r = subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-nowarn', '-cp', cp, '-d', outdir] + sources, capture_output=True, text=True)
                if r.returncode != 0:
                    say('!!', f'{name}: compiling {java_file} failed: {(r.stdout + r.stderr).strip()[:300]}'); return
                for root, _, files in os.walk(outdir):
                    for f in files:
                        entry = os.path.relpath(os.path.join(root, f), outdir).replace(os.sep, '/')
                        data = open(os.path.join(root, f), 'rb').read()
                        if entry not in entries or z.read(entry) != data:
                            repl[entry] = data
            if not repl:
                say('ok', f'{name}: {label} already installed'); continue
            if CHECK_ONLY:
                say('->', f'{name}: would install {label} (' + ', '.join(sorted(os.path.basename(k) for k in repl)) + ')'); continue
            with open(jar, 'rb') as f:
                backup_once(jar, f.read(), os.path.join(FORGE, '_backup_original_jars'))
            rewrite_jar(jar, repl)
            with zipfile.ZipFile(jar) as z:
                assert z.testzip() is None
            say('OK', f'{name}: {label} (' + ', '.join(sorted(os.path.basename(k) for k in repl)) + ')')
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


def patch_autowin():
    print(f'10) Enemies beaten {AUTO_WIN_REQUIRED_WINS}+ times are defeated on touch (world map + dungeons, not arena/events)')
    install_java_mod(f'auto-win after {AUTO_WIN_REQUIRED_WINS} wins', 'AutoWinMod.java',
                     [(r'REQUIRED_WINS = \d+;', f'REQUIRED_WINS = {AUTO_WIN_REQUIRED_WINS};')],
                     [('forge/adventure/stage/WorldStage.class', 'PatchAutoWin.java'),
                      ('forge/adventure/stage/MapStage.class', 'PatchAutoWin.java')])


def patch_shop_hour():
    print(f'11) Every town shop re-rolls which shop it is every {SHOP_ROTATION_MINUTES} min (not only rotating ones)')
    install_java_mod(f'hourly shop re-roll', 'ShopHourMod.java',
                     [(r'PERIOD_MS = [\d_]+L;', f'PERIOD_MS = {SHOP_ROTATION_MINUTES * 60_000}L;')],
                     [('forge/adventure/stage/MapStage.class', 'PatchShopHour.java')])


# ----------------------------------------------------------------- 12. custom player portraits
HERO_ATLAS = os.path.join('res', 'adventure', 'common', 'sprites', 'heroes', 'avatar.atlas')


def png_size(data):
    assert data[1:4] == b'PNG', 'not a PNG'
    return struct.unpack('>II', data[16:24])


def build_portrait_atlas(orig, customs):
    """Atlas text with the chosen regions moved onto their own pages. Region order stays the same
    (Forge picks portraits by position), so the rest of the original page is repeated after each custom page."""
    lines = orig.split(chr(10))
    out, page_header, in_header, seen = [], [], True, {}
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():                                   # blank line: a new page follows
            out.append(line); page_header, in_header = [], True; i += 1; continue
        if in_header and (not page_header or (':' in line and not line.startswith(' '))):
            page_header.append(line); out.append(line); i += 1; continue   # file name / size / format / ...
        in_header = False
        name = line.strip()
        block = [line]; i += 1
        while i < len(lines) and lines[i].startswith(' '):    # xy / size / ... of this region
            block.append(lines[i]); i += 1
        seen[name] = seen.get(name, 0) + 1
        if (name, seen[name]) not in customs:
            out += block; continue
        file, (pw, ph), (w, h), filt = customs[(name, seen[name])]
        out += ['', file, f'size: {pw},{ph}', 'format: RGBA8888', f'filter: {filt}', 'repeat: none',
                name, '  xy: 0, 0', f'  size: {w}, {h}']
        if i < len(lines) and lines[i].strip():                # continue the original page after it
            out += [''] + page_header
    return chr(10).join(out)


def patch_portraits():
    print('12) Custom player portraits (' + ', '.join(f'{n} #{k} = {f if isinstance(f, str) else f[0]}' for (n, k), f in CUSTOM_PORTRAITS.items()) + ')')
    mods = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(FORGE, HERO_ATLAS)
    if not os.path.exists(path):
        say('!!', 'avatar.atlas not found'); return
    cur = open(path, 'rb').read()
    orig_copy = os.path.join(mods, 'portraits', 'avatar.atlas.orig')
    if b'avatar_custom_' not in cur:                           # untouched (or new Forge version): remember it
        if not CHECK_ONLY: open(orig_copy, 'wb').write(cur)
        orig = cur
    elif os.path.exists(orig_copy):
        orig = open(orig_copy, 'rb').read()
    else:
        say('!!', 'avatar.atlas is modded but portraits/avatar.atlas.orig is missing'); return
    customs, files = {}, {}
    for (name, num), pic in CUSTOM_PORTRAITS.items():
        pic, region = (pic, None) if isinstance(pic, str) else pic
        src = os.path.join(mods, 'portraits', pic)
        if not os.path.exists(src):
            say('!!', f'{src} not found'); return
        data = open(src, 'rb').read()
        file = f'avatar_custom_{name}_{num}.png'
        page = png_size(data)
        customs[(name, num)] = (file, page, (region, region) if region else page, PORTRAIT_FILTER)
        files[os.path.join(os.path.dirname(path), file)] = data
    want = build_portrait_atlas(orig.decode('utf8'), customs).encode('utf8')
    for (name, num) in customs:
        if f'avatar_custom_{name}_{num}.png'.encode() not in want:
            say('!!', f'{name} #{num} not in avatar.atlas'); return
    todo = {f: d for f, d in files.items() if not os.path.exists(f) or open(f, 'rb').read() != d}
    if want != cur: todo[path] = want
    if not todo:
        say('ok', 'portraits already installed')
    elif CHECK_ONLY:
        say('->', 'would write ' + ', '.join(os.path.basename(f) for f in todo))
    else:
        backup_once(path, orig, os.path.join(FORGE, '_backup_original_res'))
        for f, d in todo.items():
            open(f, 'wb').write(d)
        say('OK', 'wrote ' + ', '.join(os.path.basename(f) for f in todo))
    install_java_mod('big portrait size fix', 'AvatarSizeMod.java', [],
                     [('forge/adventure/scene/MapViewScene.class', 'PatchAvatarSize.java'),
                      ('forge/adventure/scene/ArenaScene.class', 'PatchAvatarSize.java')])


# ----------------------------------------------------------------- 13. custom hero map sprites
def patch_hero_sprites():
    print('13) Hero map sprites replaced (' + ', '.join(HERO_SPRITES) + ')')
    mods = os.path.dirname(os.path.abspath(__file__))
    src_dir = os.path.join(mods, 'portraits', 'sprites')
    dst_dir = os.path.join(FORGE, 'res', 'adventure', 'common', 'sprites', 'heroes')
    files = {}
    for name in HERO_SPRITES:
        atlas = os.path.join(src_dir, name + '.atlas')
        if not os.path.exists(atlas):
            say('!!', f'{atlas} not found'); return
        lines = open(atlas, encoding='utf8').read().split(chr(10))
        pages = [l.strip() for i, l in enumerate(lines) if l.strip() and (i == 0 or not lines[i - 1].strip())]
        for f in [name + '.atlas'] + pages:
            files[os.path.join(dst_dir, f)] = open(os.path.join(src_dir, f), 'rb').read()
    todo = {f: d for f, d in files.items() if not os.path.exists(f) or open(f, 'rb').read() != d}
    if not todo:
        say('ok', 'sprites already installed')
    elif CHECK_ONLY:
        say('->', 'would write ' + ', '.join(os.path.basename(f) for f in todo))
    else:
        for f, d in todo.items():
            if os.path.exists(f):
                backup_once(f, open(f, 'rb').read(), os.path.join(FORGE, '_backup_original_res'))
            open(f, 'wb').write(d)
        say('OK', 'wrote ' + ', '.join(os.path.basename(f) for f in todo))
    install_java_mod('2x map sprites', 'SpriteScaleMod.java', [],
                     [('forge/adventure/character/CharacterSprite.class', 'PatchSpriteScale.java')])


# ----------------------------------------------------------------- 14. enemy portraits from card art
def patch_art_avatars():
    print('14) Illustrated enemy portraits (256x256, smooth; Magic, Hearthstone, custom art)')
    mods = os.path.dirname(os.path.abspath(__file__))
    if not os.path.exists(os.path.join(mods, 'art_plan.json')) or not os.path.isdir(os.path.join(mods, 'art_cache')):
        say('!!', 'no art yet - run: python art_avatars.py --plan  and  --fetch'); return
    env = dict(os.environ, JAVA_EXE=find_jdk_java())
    r = subprocess.run([sys.executable, os.path.join(mods, 'art_avatars.py'), '--install'] + (['--check'] if CHECK_ONLY else []),
                       capture_output=True, text=True, env=env)
    for line in (r.stdout + r.stderr).strip().splitlines()[-3:]:
        quiet = line.startswith('would build 0 portraits and change 0') or line.startswith('0 portraits built, 0 atlases')
        say('ok' if quiet else ('->' if CHECK_ONLY else 'OK'), line.strip())


def patch_world_sprites():
    print('15) Enemy and world sprites: 2x GPU resolution, original map size; heroes excluded')
    install_java_mod('calm detailed terrain materials', 'CalmTerrainMod.java', [], [])
    install_java_mod('2x enemy/world graphics', 'WorldSprite2xMod.java', [],
                     [('com/badlogic/gdx/graphics/GLTexture.class', 'PatchWorldSprite2x.java'),
                      ('forge/adventure/stage/WorldBackground.class', 'PatchWorldSprite2x.java')])
    if not running_now:
        patch_class_in_jars('com/badlogic/gdx/graphics/GLTexture.class', 'PatchEnemyFilter.java', 'smooth enemy scaling')


# ----------------------------------------------------------------- 4. developer mode
def patch_highres_downloads():
    print('16) Automatic card/token downloads: high-resolution Scryfall PNG')
    install_java_mod('high-resolution card downloads', 'HighResImagesMod.java', [],
                     [('forge/util/ImageFetcher.class', 'PatchHighResImages.java'),
                      ('forge/util/ImageUtil.class', 'PatchHighResImages.java'),
                      ('forge/gui/download/CdnUuidCache.class', 'PatchHighResImages.java')])


def patch_token_images():
    print('17) Local Squad token artwork')
    from install_token_images import install
    install(CHECK_ONLY)


def patch_map_zoom():
    print('18) Town/dungeon zoom: draw the entire visible map and respect map edges')
    install_java_mod('zoom-aware map rendering', 'MapZoomMod.java', [],
                     [('forge/adventure/stage/PointOfInterestMapRenderer.class', 'PatchMapZoom.java')])


def patch_shop_signs():
    print('19) Clear creature-shop signs with high-resolution pictograms')
    if running_now:
        print('   skipped - close Forge and run again'); return
    from shop_signs import install, ASSETS
    if not (ASSETS / 'clear_shop_signs.png').exists():
        say('!!', 'shop-sign artwork not found'); return
    install_java_mod('clear shop-sign size', 'ShopSignMod.java', [],
                     [('forge/adventure/character/TextureSprite.class', 'PatchShopSigns.java')])
    if not CHECK_ONLY:
        for jar in adventure_jars():
            with zipfile.ZipFile(jar) as z:
                if 'forge/adventure/util/ShopSignMod.class' not in z.namelist() or b'forge/adventure/util/ShopSignMod' not in z.read('forge/adventure/character/TextureSprite.class'):
                    raise RuntimeError('Shop-sign sizing patch failed; artwork not installed')
    install(CHECK_ONLY)


def patch_upscaled_shop_signs():
    print('20) Original shop signs: 4x source resolution, padding, corrected crops')
    if running_now:
        print('   skipped - close Forge and run again'); return
    from upscale_shop_signs import install
    install(CHECK_ONLY)


def patch_witcher_music():
    print('21) Witcher 3 soundtrack: exploration, towns, caves, duels and bosses')
    from witcher_music import install
    install(CHECK_ONLY)
    install_java_mod('Witcher music playlists', 'WitcherMusicMod.java', [],
                     [('forge/sound/MusicPlaylist.class', 'PatchWitcherMusic.java')])


def patch_modern_hud():
    print('22) Modern Adventure HUD: active commander and cards outside active deck')
    if not os.path.exists(os.path.join(FORGE, '_mods', 'modern-hud', 'ui.ttf')):
        raise RuntimeError('Modern HUD font not found')
    install_java_mod('RPG frames and main menu', 'RpgThemeMod.java', [],
                     [('forge/adventure/scene/StartScene.class', 'PatchRpgMenu.java'),
                      ('forge/adventure/scene/InventoryScene.class', 'PatchRpgMenu.java')])
    install_java_mod('HUD layout and opacity preferences', 'HudCustomization.java', [], [])
    install_java_mod('modern Adventure HUD', 'ModernHudMod.java', [],
                     [('forge/adventure/stage/GameHUD.class', 'PatchModernHud.java')])


def patch_draft_rating():
    print('23) Adventure deck editor: Draft Rating display and sorting')
    install_java_mod('Adventure draft-rating sorting', 'DraftRatingMod.java', [],
                     [('forge/deck/FDeckEditor$CardManagerPage.class', 'PatchDraftRating.java')])


def patch_floating_mage():
    print('24) Floating mage: 128px art, smooth hover, 10% larger visual, original collisions')
    install_java_mod('high-resolution sprite dimensions', 'SpriteScaleMod.java', [],
                     [('forge/adventure/character/CharacterSprite.class', 'PatchSpriteScale.java')])
    install_java_mod('floating mage animation', 'FloatingMageMod.java', [],
                     [('forge/adventure/character/CharacterSprite.class', 'PatchFloatingMage.java')])
    if running_now:
        print('   artwork skipped - close Forge and run again'); return
    for name in ['elf_f.atlas', 'floating-mage@8x.png']:
        src=os.path.join(FORGE, '_mods', 'floating-mage', name)
        dst=os.path.join(FORGE, 'res', 'adventure', 'common', 'sprites', 'heroes', name)
        if not os.path.exists(src):raise RuntimeError('Floating mage artwork missing')
        if not CHECK_ONLY:
            if os.path.exists(dst):backup_once(dst,open(dst,'rb').read(),os.path.join(FORGE,'_backup_original_res'))
            shutil.copy2(src,dst)


def patch_achievement_rewards():
    print('25) Adventure achievements: eight new objectives and one quality-scaled booster per trophy')
    install_java_mod('one-time achievement booster rewards', 'AchievementBoosterMod.java', [],
                     [('forge/localinstance/achievements/AchievementCollection.class', 'PatchAchievementRewards.java'),
                      ('forge/adventure/stage/GameHUD.class', 'PatchAchievementRewards.java')])
    install_java_mod('eight Adventure achievements', 'AdventureRewardAchievements.java', [],
                     [('forge/localinstance/achievements/AdventureAchievements.class', 'PatchAchievementRewards.java')])


def patch_enemy_sprite_detail():
    print('26) Enemy and town NPC map sprites: frame-isolated 4x xBR, original shapes and animations')
    if CHECK_ONLY:
        say('ok', 'cached sprite detail can be rebuilt after the update'); return
    folder=os.path.join(FORGE,'_mods','enemy-sprites4x','build')
    os.makedirs(folder,exist_ok=True)
    java=find_jdk_java(); javac=os.path.join(os.path.dirname(java),'javac.exe')
    jar=adventure_jars()[0]
    sources=[os.path.join(FORGE,'_mods',s) for s in ('UpscaleAvatars.java','BuildEnemySprites.java','BuildHeroSprites.java')]
    subprocess.run([javac,'-encoding','UTF-8','-cp',jar,'-d',folder]+sources,check=True,capture_output=True)
    for builder in ('BuildEnemySprites','BuildHeroSprites'):   # enemies + town NPCs (hero sheets, not the player's sprite)
        result=subprocess.run([java,'-Xmx2g','-cp',jar+os.pathsep+folder,builder],check=True,capture_output=True,text=True)
        for line in result.stdout.splitlines()[-2:]:say('OK',line)


def patch_librarium_enemies():
    print('26b) Enemy map sprites from the Ækashics Librarium (hydra stays a hydra; mapping in librarium_plan.json)')
    mods = os.path.dirname(os.path.abspath(__file__))
    if not os.path.isdir(os.path.join(mods, 'librarium')) or not os.path.exists(os.path.join(mods, 'librarium_plan.json')):
        say('!!', 'Librarium pack or mapping missing (_mods/librarium, librarium_plan.json)'); return
    subprocess.run([sys.executable, os.path.join(mods, 'librarium_map.py')], capture_output=True, text=True)   # your picks from the review page
    r = subprocess.run([sys.executable, os.path.join(mods, 'librarium_build.py')] + (['--check'] if CHECK_ONLY else []),
                       capture_output=True, text=True)
    for line in (r.stdout + r.stderr).strip().splitlines()[-3:]:
        quiet = line.strip().endswith(' ok') and 'built' not in line and 'would' not in line and 'error' not in line
        say('ok' if quiet else ('->' if CHECK_ONLY else 'OK'), line.strip())


def patch_map_style():
    print('33) Map screen and HUD mini map: calm painted map (roads and town/dungeon symbols kept sharp)')
    install_java_mod('painted map', 'MapStyleMod.java', [],
                     [('forge/adventure/scene/MapViewScene.class', 'PatchMapStyle.java'),
                      ('forge/adventure/stage/GameHUD.class', 'PatchMapStyle.java')])


def patch_combat_style():
    print('27) RPG combat controls and native sleeves unlocked by achievements')
    install_java_mod('achievement sleeve unlock counter', 'AchievementBoosterMod.java', [], [])
    entries=['forge/deck/FDeckEditor$1.class','forge/screens/constructed/SleeveSelector.class',
             'forge/screens/match/MatchScreen.class','forge/screens/match/views/VGameMenu.class',
             'forge/screens/match/views/VPrompt.class','forge/screens/match/views/VPhaseIndicator.class',
             'forge/screens/match/views/VPlayerPanel.class','forge/menu/FDropDownMenu.class',
             'forge/menu/FDropDown.class','forge/menu/FMenuItem.class','forge/toolbox/FButton.class',
             'forge/screens/match/MatchController.class', 'forge/menu/FMenuBar.class', 'forge/menu/FMenuTab.class',
             'forge/screens/match/views/VPhaseIndicator$PhaseLabel.class',
             'forge/screens/match/views/VPlayerPanel$InfoTab.class',
             'forge/screens/match/views/VPlayerPanel$LifeLabel.class']
    install_java_mod('RPG combat menus and achievement sleeves', 'CombatStyleMod.java', [],
                     [(entry,'PatchCombatStyle.java') for entry in entries])


def patch_modern_atmosphere():
    print('28) Overworld biome weather: sand, ash, forest motes, rain and mountain heat')
    install_java_mod('cosmetic biome weather', 'BiomeWeatherMod.java', [],
                     [('forge/adventure/stage/WorldStage.class','PatchBiomeWeather.java'),
                      ('forge/adventure/stage/WorldBackground.class','PatchBiomeWeather.java')])
    print('29) Modern duel arena, muted phase indicators and clean button skins')
    entries=['forge/assets/FSkinTexture.class','forge/toolbox/FButton.class',
             'forge/menu/FMenuBar.class','forge/menu/FMenuTab.class',
             'forge/screens/match/views/VPhaseIndicator$PhaseLabel.class',
             'forge/screens/match/views/VPlayerPanel$InfoTab.class',
             'forge/screens/match/views/VPlayerPanel$LifeLabel.class',
             'forge/toolbox/FCardPanel$CardDestroyedAnimation.class',
             'forge/toolbox/FCardPanel$CardTransformAnimation.class']
    install_java_mod('modern battlefield presentation', 'ModernBattlefieldMod.java', [],
                     [(entry,'PatchModernBattlefield.java') for entry in entries], extra_sources=['FantasyButtonArt.java','BattleUiSettings.java'])


def patch_combat_preferences():
    print('30) Smooth card motion and uncluttered full-card images')
    if running_now:
        say('--', 'close Forge before updating its preferences'); return
    path=os.path.join(os.environ.get('APPDATA',''),'Forge','preferences','forge.preferences')
    if not os.path.exists(path): return
    raw=open(path,'rb').read(); updated=raw
    for key,value in {'UI_CARD_PLAY_ANIMATION_STYLE':'Slide','UI_ANIMATED_CARD_TAPUNTAP':'true',
                      'UI_OVERLAY_CARD_NAME':'false','UI_OVERLAY_CARD_ID':'false'}.items():
        pattern=rb'(?m)^'+key.encode()+rb'=[^\r\n]*'
        line=(key+'='+value).encode()
        if re.search(pattern,updated): updated=re.sub(pattern,line,updated)
        else: updated+=b'\r\n'+line+b'\r\n'
    if updated==raw: say('ok','already set'); return
    if CHECK_ONLY: say('->','would enable smooth card flights/taps and disable duplicate name/ID overlays'); return
    shutil.copy2(path,path+'.bak-modern-presentation')
    open(path,'wb').write(updated)
    say('OK','card flights/tap animation enabled; duplicate name/ID overlays disabled')


def patch_arena_motion():
    print('31) Arena-inspired card motion: eased placement, tap rotation and clean exits')
    install_java_mod('smooth Adventure card animations','ArenaMotionMod.java',[],
                     [(entry,'PatchArenaMotion.java') for entry in
                      ['forge/screens/match/CardFlightOverlay.class','forge/screens/match/CardFlightOverlay$Flight.class',
                       'forge/screens/match/views/VZoneDisplay.class','forge/screens/match/views/VCardDisplayArea$CardAreaPanel.class',
                       'forge/toolbox/FCardPanel$CardTapAnimation.class','forge/toolbox/FCardPanel$CardUnTapAnimation.class',
                       'forge/toolbox/FCardPanel$CardTransformAnimation.class']])
    install_java_mod('manual battlefield card zoom','BattleZoomMod.java',[],
                     [(entry,'PatchBattleZoom.java') for entry in
                      ['forge/screens/match/MatchScreen.class','forge/screens/match/MatchScreen$FieldScroller.class',
                       'forge/Forge$MainInputProcessor.class','forge/game/mulligan/MulliganService.class',
                       'forge/game/mulligan/LondonMulligan.class']])
    install_java_mod('Arena-style Adventure battle HUD','ArenaBattleHudMod.java',[],
                     [(entry,'PatchArenaBattleHud.java') for entry in
                      ['forge/screens/match/views/VPlayerPanel.class','forge/screens/match/views/VPrompt.class',
                       'forge/screens/match/views/VZoneDisplay.class','forge/screens/match/views/VPrompt$MessageLabel.class',
                       'forge/menu/FMenuBar.class','forge/screens/match/MatchScreen.class',
                       'forge/screens/match/MatchScreen$FieldScroller.class','forge/screens/match/views/VAvatar.class']], extra_sources=['RogueCommandersScene.java','RogueForgeAchievements.java','FantasyButtonArt.java','BattleUiSettings.java','RogueLan.java', 'CardUpscaler.java'])
    install_java_mod('remove dangerous attack engines before attackers', 'AttackTriggerRemovalMod.java', [],
                     [('forge/ai/ComputerUtilCard.class', 'PatchAttackTriggerRemoval.java')])
    # user 2026-11-16: in 1v1 vs AI / LAN with 3-4 players the AI attacks and removes by threat (lethal, no blockers,
    # biggest board / mana value / commander) instead of a random pick; only while a RogueLan match runs
    install_java_mod('threat-aware AI targets in 1v1 vs AI / LAN', 'ThreatAiMod.java', [],   # installed 2026-11-16 ("alle updates")
                     [('forge/ai/AiAttackController.class', 'PatchThreatAi.java'),
                      ('forge/ai/ComputerUtilCard.class', 'PatchThreatAi.java')])
    install_java_mod('no order prompt for replacement effects whose order cannot matter', 'ReplacementAutoMod.java', [],
                     [('forge/player/PlayerControllerHuman.class', 'PatchReplacementAuto.java')])
    patch_graphics_voice_assets()


def patch_graphics_voice_assets():
    print('31b) Dungeon art adapter and validated incremental commander voices/backgrounds')
    if running_now:
        say('--', 'close Forge before installing graphics/voice assets'); return
    install_java_mod('animated dungeon art adapter', 'DungeonMapArt.java', [], [])
    from apply_graphics_voice_20261016 import apply
    say('->' if CHECK_ONLY else 'OK', apply(FORGE, CHECK_ONLY))


def patch_map_light():
    print('36) Towns and dungeons: high-res ground materials, shop signs on top, torch light, wall shadows, glowing fire/lava, fog')
    install_java_mod('town/dungeon lighting', 'MapLightMod.java', [],
                     [('forge/adventure/stage/PointOfInterestMapRenderer.class', 'PatchMapLight.java')])


def patch_dungeon_run():
    print('37) Dungeon Run in every inn (replaces Draft/Sealed/Jumpstart): 3 commanders, 6 rounds, 8-card packages')
    folder = os.path.join(FORGE, '_mods', 'dungeon-run')
    subprocess.run([sys.executable, '-I', os.path.join(folder, 'content.py')], check=True, capture_output=True)
    install_java_mod('Dungeon Run', 'DungeonRunScene.java', [],
                     [('forge/adventure/scene/InnScene.class', 'PatchDungeonRun.java'),
                      ('forge/adventure/util/AdventureEventController.class', 'PatchDungeonRun.java')])


def patch_enemy_intro():
    print('39) Battle introductions: original artwork, opponent text and a dark gold encounter panel')
    if not CHECK_ONLY:
        subprocess.run([sys.executable, '-I', os.path.join(FORGE, '_mods', 'prepare_enemy_intro.py')], check=True, capture_output=True)
    install_java_mod('readable high-resolution enemy introductions', 'EnemyIntroMod.java', [],
                     [('forge/adventure/scene/DuelScene.class', 'PatchEnemyIntro.java')])


ORT_JAR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'card-upscale', 'onnxruntime-1.17.3.jar')
ORT_MODEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'card-upscale', 'model', 'realesr-general-x4v3.onnx')


def patch_onnx_runtime():
    """Sharp card pictures on every PC (user 2026-11-15): ONNX Runtime (MIT, classes + Windows x64 library only) and the
    Real-ESRGAN general x4v3 model (BSD-3) go into the game JAR; CardUpscaler (Rogue step) uses them."""
    print('38a) AI card picture upscaler: ONNX Runtime + Real-ESRGAN model in the JAR')
    if not (os.path.exists(ORT_JAR) and os.path.exists(ORT_MODEL)):
        say('!!', 'card-upscale/onnxruntime jar or model missing'); return
    add = {}
    with zipfile.ZipFile(ORT_JAR) as z:
        for n in z.namelist():
            if n.endswith('/') or n.startswith('META-INF/'): continue
            if n.startswith('ai/onnxruntime/native/') and not n.startswith('ai/onnxruntime/native/win-x64/'): continue
            if n.startswith('ai/') or n == 'ThirdPartyNotices.txt':
                add['ai/onnxruntime/ThirdPartyNotices.txt' if n == 'ThirdPartyNotices.txt' else n] = z.read(n)
    add['rogue/realesr-general-x4v3.onnx'] = open(ORT_MODEL, 'rb').read()
    lic = os.path.join(os.path.dirname(ORT_MODEL), 'LICENSE-Real-ESRGAN.txt')
    if os.path.exists(lic): add['rogue/LICENSE-Real-ESRGAN.txt'] = open(lic, 'rb').read()
    for jar in adventure_jars():
        name = os.path.basename(jar)
        with zipfile.ZipFile(jar) as z:
            have = set(z.namelist())
            repl = {k: v for k, v in add.items() if k not in have or z.read(k) != v}
        if not repl:
            say('ok', f'{name}: upscaler runtime already installed'); continue
        if CHECK_ONLY or running_now:
            say('->', f'{name}: would add {len(repl)} upscaler files'); continue
        rewrite_jar(jar, repl)
        say('OK', f'{name}: upscaler runtime ({len(repl)} files)')


def patch_rogue_commanders():
    patch_onnx_runtime()
    print('38) Rogue Commanders: endless roguelite mode in the Adventure main menu (content: _mods/rogue-commanders)')
    folder = os.path.join(FORGE, '_mods', 'rogue-commanders')
    # Validate data even for --check; apply only with Forge closed, through this step.
    if os.path.exists(os.path.join(folder, 'consolidated_packages.json')):
        if running_now and not CHECK_ONLY:
            say('!!', 'Close Forge before applying consolidated packages'); return
        args = [sys.executable, os.path.join(folder, 'consolidated_packages.py')]
        if CHECK_ONLY: args.append('--check')
        r = subprocess.run(args, capture_output=True, text=True)
        if r.returncode != 0:
            say('!!', 'Consolidated package validation failed: ' + (r.stdout + r.stderr)[-1200:]); return
        say('ok', r.stdout.strip())
    if not os.path.exists(os.path.join(folder, 'content.json')):
        say('!!', 'rogue-commanders/content.json missing - run build_content.py'); return
    adv = os.path.join(FORGE, 'res', 'adventure')
    rogue = {"type": "TextButton", "name": "Rogue", "text": "Rogue Commanders", "selectable": True}
    for plane in sorted(os.listdir(adv)):
        ui = os.path.join(adv, plane, 'ui')
        for fname, portrait in (('start_menu.json', False), ('start_menu_portrait.json', True)):
            path = os.path.join(ui, fname)
            if not os.path.exists(path):
                continue
            data = json.load(open(path, encoding='utf-8'))
            els = data.get('elements', [])
            if any(e.get('name') == 'Rogue' for e in els):
                continue
            names = [e.get('name') for e in els]
            if 'Load' not in names:
                continue
            if portrait:                     # one more row: slightly smaller rows so everything still fits
                for e in els:
                    if e.get('type') == 'TextButton':
                        if e.get('height') == 48: e['height'] = 40
                        if e.get('yOffset') == 8: e['yOffset'] = 6
                btn = dict(rogue, width=238, height=40, x=16, yOffset=6)
            else:                            # Rogue takes the Save row; Save and Resume share the Continue row
                btn = dict(rogue, width=160, height=30, x=160, y=100)
                for e in els:
                    if e.get('name') == 'Save': e.update(x=160, y=140, width=78)
                    if e.get('name') == 'Resume': e.update(x=242, y=140, width=78)
            els.insert(names.index('Load') + 1, btn)
            if not CHECK_ONLY:
                backup_once(path, open(path, 'rb').read(), os.path.join(FORGE, '_backup_original_res'))
                json.dump(data, open(path, 'w', encoding='utf-8'), indent=2)
            say('ok', f'{plane}/ui/{fname}: Rogue Commanders button')
    common = os.path.join(adv, 'common', 'ui')
    for fname, w, h, image in (('rogue_commanders.json', 480, 270, 'ui/title_bg.png'),
                               ('rogue_commanders_portrait.json', 270, 480, 'ui/title_bg_portrait.png')):
        layout = {"width": w, "height": h, "yDown": True,
                  "elements": [{"type": "Image", "image": image, "width": w, "height": h}]}
        path = os.path.join(common, fname)
        if not CHECK_ONLY and (not os.path.exists(path) or json.load(open(path, encoding='utf-8')) != layout):
            json.dump(layout, open(path, 'w', encoding='utf-8'), indent=2)
            say('ok', f'common/ui/{fname} written')
    install_java_mod('Rogue Commanders', 'RogueCommandersScene.java', [],
                     [('forge/adventure/scene/StartScene.class', 'PatchRogueCommanders.java'),
                      ('forge/gamemodes/net/client/NetGameController.class', 'PatchRogueLanNet.java'),
                      ('forge/ImageKeys.class', 'PatchRogueLanNet.java'),
                      ('forge/game/mulligan/MulliganService.class', 'PatchRogueLanNet.java'),
                      ('forge/game/GameRules.class', 'PatchRogueLanNet.java'),
                      ('forge/card/CardZoom.class', 'PatchRogueLanNet.java')], extra_sources=['RogueForgeAchievements.java','FantasyButtonArt.java','BattleUiSettings.java','RogueLan.java','CardUpscaler.java'])
    # user 2026-11-16: closing the window ends the game process (LAN netty threads kept invisible games alive)
    install_java_mod('game process ends with its window', 'ExitMod.java', [],
                     [('forge/app/Main.class', 'PatchExitMod.java')])
    install_java_mod('Rogue commander achievement life-loss observer', 'RogueCommandersScene.java', [],
                     [('forge/game/player/Player.class', 'PatchRogueLifeLoss.java')], extra_sources=['RogueForgeAchievements.java','FantasyButtonArt.java','BattleUiSettings.java','RogueLan.java','CardUpscaler.java'])


def patch_commander_rating():
    print('39) Card views: Commander rating (EDHREC rank) instead of draft rating, small corner badge, prices only in the shop')
    folder = os.path.join(FORGE, '_mods', 'commander-rating')
    if not os.path.exists(os.path.join(folder, 'ratings.tsv')):
        r = subprocess.run([sys.executable, '-I', os.path.join(folder, 'build_ratings.py')], capture_output=True, text=True)
        if r.returncode != 0:
            say('!!', 'commander-rating/ratings.tsv missing and could not be built: ' + (r.stdout + r.stderr).strip()[-200:]); return
    install_java_mod('Commander rating', 'CommanderRatingMod.java', [],
                     [('forge/itemmanager/views/ImageView$ItemInfo.class', 'PatchCommanderRating.java'),
                      ('forge/itemmanager/ColumnDef.class', 'PatchCommanderRating.java'),
                      ('forge/adventure/scene/AdventureDeckEditor$AdventureCardManager$1.class', 'PatchCommanderRating.java')])


def patch_dev_mode():
    print('40) Developer mode hook (inactive unless Forge is started with -Dforge.devmode=PORT, see _mods/devmode/forge_dev.py)')
    install_java_mod('developer mode', 'DevModeMod.java', [], [('forge/Forge.class', 'PatchDevMode.java'),
                     # Forge bug: parallel world generation corrupts Config's shared path builder -> new game freezes
                     ('forge/adventure/util/Config.class', 'PatchConfigThreadSafe.java')])
    # "Rogue Commanders" launcher: same JVM options as forge-adventure.cmd plus -Dforge.autocontinue=rogue,
    # which skips the start menus and opens Rogue Commanders with the running run (DevModeMod.autoContinue)
    java_line = None
    for line in open(os.path.join(FORGE, 'forge-adventure.cmd'), encoding='utf-8', errors='replace'):
        if line.strip().lower().startswith('java ') and ' -jar ' in line:
            java_line = line.strip()
    if not java_line:
        say('!!', 'Rogue Commanders launcher: java line not found in forge-adventure.cmd'); return
    args = java_line[5:].replace(' -jar ', ' -Dforge.autocontinue=rogue -jar ', 1)
    crlf = chr(13) + chr(10)
    cmd = crlf.join(['@echo off',
                     'rem Rogue Commanders: starts Forge and opens Rogue Commanders directly (written by _mods/reapply_mods.py)',
                     'pushd %~dp0', 'start "" javaw ' + args, 'popd', ''])
    old_cmd = os.path.join(FORGE, 'Forge Continue.cmd')
    if os.path.exists(old_cmd) and not CHECK_ONLY:
        os.remove(old_cmd)
    target = os.path.join(FORGE, 'Rogue Commanders.cmd')
    current = open(target, encoding='utf-8', errors='replace', newline='').read() if os.path.exists(target) else None
    if current != cmd:
        if not CHECK_ONLY:
            open(target, 'w', encoding='utf-8', newline='').write(cmd)
        say('ok', 'Rogue Commanders.cmd written')
    icon = os.path.join(FORGE, 'forge-adventure.exe')
    ps = ("$d=[Environment]::GetFolderPath('Desktop');"
          "$old=Join-Path $d 'Forge Continue.lnk';if(Test-Path $old){Remove-Item $old};"
          "$l=Join-Path $d 'Rogue Commanders.lnk';$s=(New-Object -ComObject WScript.Shell).CreateShortcut($l);"
          "$s.TargetPath='" + target + "';$s.WorkingDirectory='" + FORGE + "';$s.WindowStyle=7;"
          "$s.IconLocation='" + icon + ",0';$s.Description='Forge: direkt in Rogue Commanders';$s.Save();$l")
    if not CHECK_ONLY and not os.environ.get('FORGE_NO_SHORTCUT'):     # test copies must not take over the desktop shortcut
        r = subprocess.run(['powershell', '-NoProfile', '-Command', ps], capture_output=True, text=True)
        say('ok', 'desktop shortcut: ' + (r.stdout.strip() or r.stderr.strip()[:160]))


def patch_modern_results():
    print('41) Match result screen: modern victory/defeat panel; Adventure and Rogue matches always return to their scene')
    install_java_mod('modern result screen', 'ModernWinLose.java', [],
                     [('forge/screens/match/winlose/ViewWinLose.class', 'PatchModernWinLose.java')])


def patch_stack_clearance():
    print('42) Open stack list never covers a player portrait (Arena-style battle layout: targeting the enemy face)')
    install_java_mod('stack clearance', 'StackClearanceMod.java', [],
                     [('forge/screens/match/views/VStack.class', 'PatchStackClearance.java')])


def patch_mana_pool_layout():
    print('43) Battle HUD: mana pool symbols in one centred row when the pool area is wide (no clipped, untappable cells)')
    install_java_mod('mana pool layout', 'ManaPoolLayoutMod.java', [],
                     [('forge/screens/match/views/VManaPool.class', 'PatchManaPoolLayout.java')])


def patch_zone_tabs():
    print('44) Battle HUD: modern zone tabs (hand, graveyard, library, flashback, exile) with crisp icons from _mods/zone-icons')
    if not os.path.exists(os.path.join(FORGE, '_mods', 'zone-icons', 'hand.png')):
        say('!!', 'zone-icons missing - run _mods/zone-icons/build_zone_icons.py'); return
    install_java_mod('zone tab style', 'ZoneTabStyleMod.java', [],
                     [('forge/screens/match/views/VPlayerPanel$InfoTab.class', 'PatchZoneTabStyle.java')])
    # match menu "Players" view in the same modern style (Claude 2026-10-16)
    # semi-transparent stack popup (user 2026-10-17)
    install_java_mod('stack style', 'StackStyleMod.java', [],
                     [('forge/screens/match/views/VStack.class', 'PatchStackStyle.java'),
                      ('forge/screens/match/views/VStack$StackInstanceDisplay.class', 'PatchStackStyle.java')])
    install_java_mod('game log style', 'LogStyleMod.java', [],
                     [('forge/screens/match/views/VLog.class', 'PatchLogStyle.java'),
                      ('forge/screens/match/views/VLog$LogEntryDisplay.class', 'PatchLogStyle.java')])   # turn bands (2026-10-18)
    install_java_mod('players view style', 'PlayersStyleMod.java', [],
                     [('forge/screens/match/views/VPlayers$PlayerInfoPanel.class', 'PatchPlayersStyle.java')])
    # card yes/no dialogs: only the card, no opaque panel/swipe strip (user 2026-10-17)
    install_java_mod('card dialog style', 'CardDialogMod.java', [],
                     [('forge/toolbox/FDialog.class', 'PatchCardDialog.java'),
                      ('forge/toolbox/FChoiceList.class', 'PatchCardDialog.java'),   # + translucent match choice lists
                      ('forge/toolbox/DualListBox.class', 'PatchCardDialog.java'),   # Enter takes a single candidate (2026-10-18)
                      ('forge/toolbox/FOptionPane.class', 'PatchCardDialog.java')])


def patch_match_menu():
    print('45) Battle HUD: one round menu button instead of the top menu bar; modern transparent prompt bar')
    install_java_mod('match menu button', 'MatchMenuMod.java', [],
                     [('forge/screens/match/MatchScreen.class', 'PatchMatchMenu.java'), ('forge/menu/FDropDown.class', 'PatchMatchMenu.java')],
                     extra_sources=['DeckPeekMod.java','BattleUiSettings.java'])
    install_java_mod('prompt bar style', 'PromptStyleMod.java', [],
                     [('forge/screens/match/views/VPrompt.class', 'PatchPromptStyle.java'),
                      ('forge/screens/match/views/VPrompt$MessageLabel.class', 'PatchPromptStyle.java'),
                      ('forge/toolbox/FButton.class', 'PatchPromptStyle.java')], extra_sources=['FantasyButtonArt.java','BattleUiSettings.java'])
    # popup menus in a duel (stack item menu: Auto-Yield / Yield to stack / Resolve entire stack) - user 2026-10-18
    install_java_mod('popup menu style', 'MenuStyleMod.java', [],
                     [('forge/menu/FMenuItem.class', 'PatchMenuStyle.java'), ('forge/menu/FPopupMenu.class', 'PatchMenuStyle.java')])


def patch_transition_style():
    print('46) Loading screens: modern gold spinner and slim progress bar instead of the old Forge logo')
    install_java_mod('loading screen style', 'TransitionStyleMod.java', [],
                     [('forge/screens/TransitionScreen$BGAnimation.class', 'PatchTransitionStyle.java')])


DIGITAL_REPRINT_SETS = ['Pioneer Masters.txt']   # MTG Arena only, but typed "Reprint" by Forge


def patch_digital_sets():
    print('47b) Arena-only sets that Forge types as paper reprints are marked Online (no Arena card pictures)')
    for fname in DIGITAL_REPRINT_SETS:
        path = os.path.join(FORGE, 'res', 'editions', fname)
        if not os.path.exists(path):
            say('--', f'{fname} not found'); continue
        raw = open(path, 'rb').read()
        if b'\nType=Online' in raw:
            say('ok', f'{fname}: already Online'); continue
        if b'\nType=' not in raw:
            say('!!', f'{fname}: no Type= line'); continue
        if CHECK_ONLY:
            say('->', f'{fname}: would set Type=Online'); continue
        raw = re.sub(rb'\nType=[^\r\n]*', b'\nType=Online', raw, count=1)
        open(path, 'wb').write(raw)
        say('OK', f'{fname}: Type=Online')


# AI hints in Forge card scripts: entry in res/cardsfolder/cardsfolder.zip -> [(old, new)]
CARD_SCRIPT_FIXES = {
    # Jace's combat tax: the AI only pays {2} when it would actually attack this turn (Forge's UnlessAI$ WillAttack)
    'j/jace_multiverse_architect.txt': [('UnlessPayer$ TriggeredPlayer | RememberObjects$ TriggeredPlayer',
                                         'UnlessPayer$ TriggeredPlayer | UnlessAI$ WillAttack | RememberObjects$ TriggeredPlayer')],
}


def patch_upstream_fixes():
    """Rule / AI / card-script fixes of upstream Forge made after our base snapshot 10.09 (user 2026-10-18: "apply die
    Game-Engine-Fixes von normal Forge" / "patch meine Engine mit der neuesten Version"). Only engine and card fixes -
    no GUI/netplay/undo/layout commits, those broke the battle HUD in 10.14. Sources: _mods/upstream-fixes
    (fetch_upstream.py from the local checkout _mods/forge-src-1009, manifest.json). A card script is replaced only while
    ours is still an upstream version (base or an older fix); Java fixes are compiled against our JAR, and classes that
    our own bytecode patchers change get that patch applied before the comparison (POST_PATCH)."""
    print('47d) Upstream Forge fixes after 10.09: rules engine, AI, card scripts (see _mods/upstream-fixes/manifest.json)')
    if running_now:
        print('   skipped - close Forge and run again'); return
    mods = os.path.dirname(os.path.abspath(__file__))
    up = os.path.join(mods, 'upstream-fixes')
    man = json.load(open(os.path.join(up, 'manifest.json'), encoding='utf-8'))
    norm = lambda b: b.replace(b'\r\n', b'\n').strip()
    digest = lambda b: hashlib.sha256(norm(b)).hexdigest()
    # 1. card scripts (cardsfolder.zip) and token scripts (files)
    zpath = os.path.join(FORGE, 'res', 'cardsfolder', 'cardsfolder.zip')
    texts = {}
    with zipfile.ZipFile(zpath) as z:
        names = set(z.namelist())
        for card in man['cards']:
            rel = card['file']; fixed = open(os.path.join(up, rel), 'rb').read(); accept = set(card.get('accept', []))
            if rel.startswith('tokenscripts/'):
                dst = os.path.join(FORGE, 'res', rel.replace('/', os.sep)); cur = open(dst, 'rb').read() if os.path.exists(dst) else b''
                if norm(cur) == norm(fixed): continue
                if digest(cur) not in accept: say('!!', f'{rel}: not an upstream version - not replaced'); continue
                if not CHECK_ONLY: open(dst, 'wb').write(fixed)
                say('OK' if not CHECK_ONLY else '->', f'{rel} ({card["why"][:80]})'); continue
            entry = rel[len('cardsfolder/'):]
            if entry not in names: say('--', f'{entry} not in cardsfolder.zip'); continue
            cur = z.read(entry)
            if norm(cur) == norm(fixed): continue
            if digest(cur) not in accept: say('!!', f'{entry}: not an upstream version - not replaced'); continue
            texts[entry] = fixed
    if texts:
        if CHECK_ONLY:
            say('->', f'would update {len(texts)} card script(s): ' + ', '.join(sorted(os.path.basename(t)[:-4] for t in texts)))
        else:
            with open(zpath, 'rb') as f:
                backup_once(zpath, f.read(), os.path.join(FORGE, '_backup_original_jars'))
            tmp = zpath + '.tmp'
            with zipfile.ZipFile(zpath) as src, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as dst:
                for info in src.infolist():
                    dst.writestr(info, texts.get(info.filename, src.read(info.filename)))
            os.replace(tmp, zpath)
            say('OK', f'{len(texts)} card script(s): ' + ', '.join(sorted(os.path.basename(t)[:-4] for t in texts)))
    else:
        say('ok', 'card scripts up to date')
    # 2. engine / AI classes compiled from the fixed upstream sources against our JAR
    POST_PATCH = {'forge/player/PlayerControllerHuman.class': 'PatchReplacementAuto.java'}   # our own ASM patch on that class
    java = find_jdk_java(); javac = os.path.join(os.path.dirname(java), 'javac.exe')
    srcs = [os.path.join(up, 'java', j['file']) for j in man['java']]
    for jar in adventure_jars():
        name = os.path.basename(jar); tmp = tempfile.mkdtemp()
        try:
            r = subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-implicit:none', '-cp', jar, '-d', tmp] + srcs,
                               capture_output=True, text=True)
            if r.returncode != 0:
                say('!!', f'{name}: upstream fixes do not compile against this JAR: {(r.stdout + r.stderr).strip()[:400]}'); return
            for entry, patcher in POST_PATCH.items():
                cls = os.path.join(tmp, entry.replace('/', os.sep))
                if os.path.exists(cls):
                    out = cls + '.patched'
                    pr = subprocess.run([java] + ASM_EXPORTS + [os.path.join(mods, patcher), cls, out], capture_output=True, text=True)
                    if pr.returncode == 0 and os.path.exists(out): os.replace(out, cls)
                    elif pr.returncode != 2: say('!!', f'{patcher} failed on the upstream {entry}: {(pr.stdout + pr.stderr).strip()[:200]}'); return
            repl = {}
            with zipfile.ZipFile(jar) as z:
                entries = set(z.namelist())
                for root, _, files in os.walk(tmp):
                    for f in files:
                        if f.endswith('.patched'): continue
                        entry = os.path.relpath(os.path.join(root, f), tmp).replace(os.sep, '/')
                        data = open(os.path.join(root, f), 'rb').read()
                        if entry not in entries or z.read(entry) != data: repl[entry] = data
            if not repl:
                say('ok', f'{name}: upstream engine fixes already installed'); continue
            if CHECK_ONLY:
                say('->', f'{name}: would install upstream fixes (' + ', '.join(sorted({os.path.basename(k).split('$')[0] for k in repl})) + ')'); continue
            with open(jar, 'rb') as f:
                backup_once(jar, f.read(), os.path.join(FORGE, '_backup_original_jars'))
            rewrite_jar(jar, repl)
            say('OK', f'{name}: upstream engine fixes (' + ', '.join(sorted({os.path.basename(k).split('$')[0] for k in repl})) + ')')
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


def patch_card_scripts():
    print('47c) Card scripts: AI hints (Jace, Multiverse Architect: pay the attack tax only when attacking)')
    path = os.path.join(FORGE, 'res', 'cardsfolder', 'cardsfolder.zip')
    if not os.path.exists(path):
        say('--', 'cardsfolder.zip not found'); return
    with zipfile.ZipFile(path) as z:
        texts = {}
        for entry, subs in CARD_SCRIPT_FIXES.items():
            if entry not in z.namelist():
                say('--', f'{entry} not in cardsfolder.zip'); continue
            text = z.read(entry).decode('utf-8')
            changed = text
            for old, new in subs:
                if new in changed:
                    continue
                if old not in changed:
                    say('!!', f'{entry}: script changed upstream, hint not applied'); break
                changed = changed.replace(old, new)
            if changed != text:
                texts[entry] = changed.encode('utf-8')
            else:
                say('ok', f'{entry}: already patched')
    if not texts:
        return
    if CHECK_ONLY:
        say('->', 'would patch ' + ', '.join(texts)); return
    with open(path, 'rb') as f:
        backup_once(path, f.read(), os.path.join(FORGE, '_backup_original_jars'))
    tmp = path + '.tmp'
    with zipfile.ZipFile(path) as src, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            dst.writestr(info, texts.get(info.filename, src.read(info.filename)))
    os.replace(tmp, path)
    say('OK', 'card scripts: ' + ', '.join(texts))


# Forge's Default AI almost never attacks into a trade (0 %), so it sits still even when two attackers would kill a
# planeswalker or force a chump block (user 2026-10-16). Moderate values, not the all-in "Reckless" profile.
AI_AGGRESSION = {'CHANCE_TO_ATTACK_INTO_TRADE': '60', 'ATTACK_INTO_TRADE_WHEN_TAPPED_OUT': 'true',
                 'RANDOMLY_ATKTRADE_ONLY_ON_LOWER_LIFE_PRESSURE': 'false', 'CHANCE_TO_ATKTRADE_WHEN_OPP_HAS_MANA': '50'}


def patch_ai_aggression():
    print('47d) AI profile Default: attacks into favourable trades and planeswalkers more often')
    path = os.path.join(FORGE, 'res', 'ai', 'Default.ai')
    if not os.path.exists(path):
        say('--', 'Default.ai not found'); return
    raw = open(path, 'rb').read(); updated = raw
    for key, value in AI_AGGRESSION.items():
        pattern = rb'(?m)^' + key.encode() + rb'=[^\r\n]*'
        line = (key + '=' + value).encode()
        updated = re.sub(pattern, line, updated) if re.search(pattern, updated) else updated + b'\n' + line + b'\n'
    if updated == raw:
        say('ok', 'Default.ai already aggressive'); return
    if CHECK_ONLY:
        say('->', 'would update Default.ai'); return
    backup_once(path, raw, os.path.join(FORGE, '_backup_original_jars'))
    open(path, 'wb').write(updated)
    say('OK', 'Default.ai: ' + ', '.join(f'{k}={v}' for k, v in AI_AGGRESSION.items()))


def patch_card_art_pref():
    print('47) Card pictures: latest PAPER printing only (no Arena/MTGO digital-only editions with digital frames)')
    path = os.path.join(os.environ.get('APPDATA', ''), 'Forge', 'preferences', 'forge.preferences')
    if not os.path.exists(path):
        say('--', 'forge.preferences not found (start Forge once first)'); return
    raw = open(path, 'rb').read()
    want = b'UI_PREFERRED_ART=LATEST_ART_CORE_EXPANSIONS_REPRINT_ONLY'
    if want in raw:
        say('ok', 'already set'); return
    if CHECK_ONLY:
        say('->', 'would set ' + want.decode()); return
    shutil.copy2(path, path + '.bak-reapply')
    lines = [l for l in raw.splitlines() if not l.startswith(b'UI_PREFERRED_ART=')]
    nl = bytes([13, 10]) if bytes([13, 10]) in raw else bytes([10])
    raw = nl.join(lines) + nl + want + nl
    open(path, 'wb').write(raw)
    say('OK', want.decode())


def patch_deck_editor_style():
    print('48) Adventure deck editor in the modern style (header, search, filter chips, combo boxes, groups, tab rail)')
    install_java_mod('deck editor style', 'DeckEditorStyleMod.java', [],
                     [(entry, 'PatchDeckEditorStyle.java') for entry in [
                         'forge/adventure/scene/AdventureDeckEditor.class',
                         'forge/adventure/scene/AdventureDeckEditor$AdventureDeckHeader.class',
                         'forge/adventure/scene/AdventureDeckEditor$AdventureDeckSectionPage.class',
                         'forge/adventure/scene/AdventureDeckEditor$AdventureDraftPackPage.class',
                         'forge/adventure/scene/AdventureDeckEditor$CollectionAutoSellPage.class',
                         'forge/adventure/scene/AdventureDeckEditor$CollectionCatalogPage.class',
                         'forge/adventure/scene/AdventureDeckEditor$ContentPreviewPage.class',
                         'forge/adventure/scene/AdventureDeckEditor$StoreCatalogPage.class',
                         'forge/screens/TabPageScreen$TabHeader.class',
                         'forge/deck/FDeckEditor$DeckHeader.class',
                         'forge/screens/TabPageScreen$TabPage$Tab.class',
                         'forge/toolbox/FLabel.class',
                         'forge/toolbox/FComboBox.class',
                         'forge/toolbox/FTextField.class',
                         'forge/itemmanager/filters/TextSearchFilter$SearchField.class',
                         'forge/itemmanager/views/ImageView$Group.class',
                         'forge/itemmanager/views/ImageView$ItemInfo.class',
                         'forge/itemmanager/views/ItemView$Scroller.class',
                         'forge/itemmanager/views/ImageView.class',
                     ]])


def patch_prefs():
    print('4) Developer mode (forge.preferences)')
    path = os.path.join(os.environ.get('APPDATA', ''), 'Forge', 'preferences', 'forge.preferences')
    if not os.path.exists(path):
        say('!!', 'forge.preferences not found (start Forge once first)'); return
    raw = open(path, 'rb').read()
    want = b'DEV_MODE_ENABLED=' + (b'true' if DEV_MODE else b'false')
    other = b'DEV_MODE_ENABLED=' + (b'false' if DEV_MODE else b'true')
    if want in raw:
        say('ok', 'already set'); return
    if CHECK_ONLY:
        say('->', 'would set ' + want.decode()); return
    shutil.copy2(path, path + '.bak-reapply')
    if other in raw:
        raw = raw.replace(other, want)
    else:
        nl = b'\r\n' if b'\r\n' in raw else b'\n'
        raw = raw.rstrip(b'\r\n') + nl + want + nl
    open(path, 'wb').write(raw)
    say('OK', want.decode())


def patch_rogue_sounds():
    print('46) Rogue Commanders sounds: trigger chimes (rogue-commanders/sounds -> res/sound)')
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'rogue-commanders', 'sounds')
    dst = os.path.join(FORGE, 'res', 'sound')
    for name in sorted(os.listdir(src)) if os.path.isdir(src) else []:
        if not name.endswith('.wav'): continue
        a, b = os.path.join(src, name), os.path.join(dst, name)
        data = open(a, 'rb').read()
        if os.path.exists(b) and open(b, 'rb').read() == data:
            say('ok', f'{name} already in res/sound'); continue
        if CHECK_ONLY:
            say('->', f'would copy {name}'); continue
        os.makedirs(dst, exist_ok=True); open(b, 'wb').write(data); say('OK', f'{name} -> res/sound')


def patch_battle_layout_fixes():
    print('32) Automatic battlefield zoom, centered hands/field rows, small land fans, safe hover previews, full action buttons and styled choices')
    choices=['forge/toolbox/FChoiceList.class','forge/toolbox/FList.class',
             'forge/toolbox/FTextField.class','forge/toolbox/FOptionPane.class',
             'forge/toolbox/FDialog.class','forge/toolbox/ListChooser.class',
             'forge/itemmanager/CardManager$CardListItemRenderer.class']
    install_java_mod('combat choice/search palette','CombatStyleMod.java',[],
                     [(entry,'PatchCombatStyle.java') for entry in choices])
    install_java_mod('populated battle layout fixes','BattleLayoutFixMod.java',[],
                     [(entry,'PatchBattleLayoutFix.java') for entry in
                      ['forge/screens/match/views/VField.class',
                       'forge/screens/match/views/VField$1.class',
                       'forge/screens/match/views/VCardDisplayArea.class',
                       'forge/screens/match/views/VZoneDisplay.class',
                       'forge/screens/match/MatchScreen.class','forge/screens/match/TargetingOverlay.class',
                       'forge/toolbox/FButton.class','forge/toolbox/FList.class',
                       'forge/screens/match/views/VCardDisplayArea$CardAreaPanel.class','forge/toolbox/FCardPanel.class']], extra_sources=['FantasyButtonArt.java','BattleUiSettings.java'])


if __name__ == '__main__':
    print(f'Forge folder: {FORGE}' + ('   (check only)' if CHECK_ONLY else ''))
    running = not CHECK_ONLY and forge_running()
    running_now = running
    if running:
        print('\nForge is running: JARs and preferences are skipped (Forge has them open/would overwrite them).')
        print('Data files are patched now and take effect after the next Forge start.\n')
    for step in (patch_jars, patch_enemies, patch_config, patch_prefs, patch_quest_reputation, patch_shops, patch_rotation, patch_arenas, patch_avatars, patch_autowin, patch_shop_hour, patch_portraits, patch_art_avatars, patch_world_sprites, patch_highres_downloads, patch_token_images, patch_map_zoom, patch_shop_signs, patch_upscaled_shop_signs, patch_witcher_music, patch_modern_hud, patch_draft_rating, patch_floating_mage, patch_achievement_rewards, patch_enemy_sprite_detail, patch_librarium_enemies, patch_combat_style, patch_modern_atmosphere, patch_combat_preferences, patch_arena_motion, patch_map_style, patch_map_light, patch_dungeon_run, patch_world_content, patch_starter_decks, patch_commander_rating, patch_rogue_commanders, patch_dev_mode, patch_modern_skin, patch_modern_results, patch_stack_clearance, patch_mana_pool_layout, patch_zone_tabs, patch_match_menu, patch_transition_style, patch_card_art_pref, patch_digital_sets, patch_card_scripts, patch_upstream_fixes, patch_ai_aggression, patch_rogue_sounds):
        if running and step in (patch_jars, patch_prefs, patch_rotation, patch_card_art_pref, patch_card_scripts):
            print(f'{step.__name__}: skipped - close Forge and run again to apply'); continue
        try:
            step()
        except Exception as ex:
            say('!!', f'failed: {ex!r}')
    try:
        patch_battle_layout_fixes()
    except Exception as ex:
        say('!!', f'failed: {ex!r}')
    try:
        patch_enemy_intro()
    except Exception as ex:
        say('!!', f'failed enemy intro: {ex!r}')
    if FAILURES:
        print(f'\nIncomplete: {len(FAILURES)} error(s). Review the messages above; not all mods were applied.')
        sys.exit(1)
    if running:
        print('\nIncomplete: Forge was running. Close it and run this script again to apply the skipped patches.')
        sys.exit(2)
    print('\nDone. ' + ('Check complete; game files were not changed.' if CHECK_ONLY else 'Start Forge normally.'))
