"""Collect upstream Forge rule / AI / card-script fixes made after our base snapshot 2.0.16-SNAPSHOT-10.09
(user 2026-10-18: "apply die Game-Engine-Fixes von normal Forge" / "guck dir den Code der neuesten Version an und patch
meine Engine"). Works from the local source checkout _mods/forge-src-1009 (git, base commit c26a2e938f = 10.09 build).

Only engine and card fixes - no GUI, netplay, undo/replay or layout commits (those broke our HUD mods in 10.14), no new
cards / editions (they need engine features we do not take).
  cards  : every MODIFIED card / token script since the base, latest upstream version; "accept" = hashes of every older
           upstream version, so a script is only replaced while it is still an upstream version (never a local edit)
  java   : whole files pinned at a fix commit whose only change since the base is that fix
  hunks  : base file + only the changed lines of the fix commit (files with unrelated upstream changes in between)
Run:  git -C ../forge-src-1009 fetch origin master --filter=blob:none   then   python -I fetch_upstream.py"""
import hashlib, json, subprocess
from pathlib import Path

HERE = Path(__file__).parent
SRC = HERE.parent / 'forge-src-1009'
BASE, HEAD = 'c26a2e938f', 'origin/master'
JAVA = {   # file -> fix commit (whole file at that commit = base + exactly this fix)
    'forge-ai/src/main/java/forge/ai/ability/VentureAi.java': 'd7b211dc9b',      # AI: judge dungeon rooms as triggers
    'forge-ai/src/main/java/forge/ai/ability/GoadAi.java': 'f73f382084',         # AI: don't goad its own creatures
    'forge-ai/src/main/java/forge/ai/ability/ChangeZoneAi.java': 'ca64cc928e',   # Eureka / Hypergenesis AI
    'forge-game/src/main/java/forge/game/ability/AbilityUtils.java': '55685587d3',          # simultaneous entry
    'forge-game/src/main/java/forge/game/trigger/TriggerHandler.java': '55685587d3',
}
HUNKS = {   # file -> fix commits whose changed lines go onto the base version
    'forge-ai/src/main/java/forge/ai/ComputerUtilMana.java': ['53b59838c7'],     # AI: commander tax for modal back faces
    'forge-gui/src/main/java/forge/player/PlayerControllerHuman.java': ['c263787525'],   # bottom-of-library order
    'forge-game/src/main/java/forge/game/zone/MagicStack.java': ['7901b6648b'],  # storm count shown on the stack
}


def git(*a, binary=False):
    r = subprocess.run(['git', '-C', str(SRC)] + list(a), capture_output=True)
    if r.returncode: raise RuntimeError(' '.join(a) + ': ' + r.stderr.decode('utf-8', 'replace')[:300])
    return r.stdout if binary else r.stdout.decode('utf-8', 'replace')


def norm(b): return b.replace(b'\r\n', b'\n').strip()
def sha(b): return hashlib.sha256(norm(b)).hexdigest()


def save(rel, data):
    p = HERE / rel; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(data)


def apply_hunks(text, patch):
    """replace each hunk's old lines by its new lines, trimming context until the old block is unique in the text"""
    hunks, cur = [], None
    for l in patch.splitlines():
        if l.startswith('@@'):
            cur = []; hunks.append(cur); continue
        if cur is None or l.startswith('\\'): continue
        cur.append(l)
    for h in hunks:
        old = [l[1:] for l in h if not l.startswith('+')]
        new = [l[1:] for l in h if not l.startswith('-')]
        a = z = 0
        while True:
            o = '\n'.join(old[a:len(old) - z]); n = '\n'.join(new[a:len(new) - z])
            if o and text.count(o) == 1:
                text = text.replace(o, n); break
            if a + z >= len(old) - 1 or not o:
                raise RuntimeError('hunk does not apply cleanly:\n' + '\n'.join(h)[:600])
            if text.count(o) == 0:   # context changed upstream: drop context lines, keep the changed core
                if a <= z: a += 1
                else: z += 1
            else:                    # ambiguous: would need more context, never less
                raise RuntimeError('hunk is ambiguous:\n' + '\n'.join(h)[:600])
    return text


manifest = {'base': BASE, 'head': git('rev-parse', '--short', HEAD).strip(), 'cards': [], 'java': []}
# 1. card / token scripts
for row in git('diff', '--name-status', BASE, HEAD, '--', 'forge-gui/res/cardsfolder', 'forge-gui/res/tokenscripts').splitlines():
    status, path = row.split('\t')[0], row.split('\t')[-1]
    if status != 'M': continue   # new cards need engine features we do not take
    rel = path[len('forge-gui/res/'):]
    commits = git('log', '--format=%h', BASE + '..' + HEAD, '--', path).split()   # newest first
    accept = {sha(git('show', BASE + ':' + path, binary=True))}
    for c in commits[1:]: accept.add(sha(git('show', c + ':' + path, binary=True)))   # older upstream versions
    save(rel, git('show', HEAD + ':' + path, binary=True))
    why = '; '.join(git('log', '-1', '--format=%s', c).strip() for c in reversed(commits))
    manifest['cards'].append({'file': rel, 'commits': list(reversed(commits)), 'why': why, 'accept': sorted(accept)})
# 2. whole java files
for path, c in JAVA.items():
    name = path.split('/')[-1]
    save('java/' + name, git('show', c + ':' + path, binary=True))
    save('java/' + name + '.base', git('show', BASE + ':' + path, binary=True))
    manifest['java'].append({'file': name, 'commit': c, 'upstream': path, 'mode': 'file'})
# 3. base + fix hunks
for path, commits in HUNKS.items():
    name = path.split('/')[-1]
    base = git('show', BASE + ':' + path, binary=True).decode('utf-8')
    crlf = '\r\n' in base
    text = base.replace('\r\n', '\n')
    for c in commits:
        text = apply_hunks(text, git('show', '--format=', c, '--', path))
    if crlf: text = text.replace('\n', '\r\n')
    save('java/' + name + '.base', base.encode('utf-8'))
    save('java/' + name, text.encode('utf-8'))
    manifest['java'].append({'file': name, 'commit': ','.join(commits), 'upstream': path, 'mode': 'hunks'})
(HERE / 'manifest.json').write_text(json.dumps(manifest, indent=1), encoding='utf-8')
print('head', manifest['head'], '| cards', len(manifest['cards']), '| java', len(manifest['java']))
