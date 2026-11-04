"""Builds content.json for Rogue Commanders from cached EDHREC data (edhrec/*.json) and Forge's card database
(cards.tsv from DumpCards.java).

Per commander: a mid-power starter deck (24 lands + 35 spells, like a precon), three theme pools for the run packages
(top-3 EDHREC themes of that commander) and eight signature unlocks (its highest-synergy cards, only for itself).
Shared: the general unlock pool (popular EDHREC staples, tiered mittel/gut/stark, no Game Changers), generic pools for
run packages by function (removal, ramp, card draw, ...) and a legendary pool (Game Changers) for deep runs.
Every card is checked against Forge's colour identity.   python -I build_content.py
"""
import json, os, re, random, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ED = os.path.join(HERE, 'edhrec')

COMMANDERS = [
    ("Y'shtola, Night's Blessed", 'yshtola-nights-blessed', 'c_yshtola-nights-blessed.json',
     [('control', 'Control'), ('spellslinger', 'Spellslinger'), ('lifegain', 'Lifegain')],
     'Esper control: each noncreature spell with mana value 3 or more deals 2 damage and gains 2 life; draw cards at end of turn.'),
    ('Teval, the Balanced Scale', 'teval-the-balanced-scale', 'c_teval-the-balanced-scale.json',
     [('reanimator', 'Reanimator'), ('self-mill', 'Self-mill'), ('lands-matter', 'Lands')],
     'Sultai graveyard: Teval mills on attack, returns lands and makes Zombies whenever cards leave the graveyard.'),
    ('Fire Lord Azula', 'fire-lord-azula', 'c_fire-lord-azula.json',
     [('spellslinger', 'Spellslinger'), ('spell-copy', 'Spell copying'), ('aggro', 'Aggro')],
     'Grixis aggro: Azula makes mana on attack and copies every spell you cast during combat.'),
    ('Cloud, Ex-SOLDIER', 'cloud-ex-soldier', 'c_cloud-ex-soldier.json',
     [('equipment', 'Equipment'), ('voltron', 'Voltron'), ('extra-combats', 'Extra combats')],
     'Naya equipment: Cloud equips on entry, draws for each equipped creature and makes Treasures.'),
    ('Ureni of the Unwritten', 'ureni-of-the-unwritten', 'c_ureni-of-the-unwritten.json',
     [('dragons', 'Dragons'), ('ramp', 'Ramp'), ('flying', 'Flying')],
     'Temur Dragons: whenever Ureni enters or attacks, put a Dragon from the top eight cards onto the battlefield.'),
    ('Karolina Dean, Runaway', 'karolina-dean-runaway', 'c_karolina-dean-runaway.json',
     [('impulse-draw', 'Impulse draw'), ('exile', 'Exile'), ('burn', 'Burn')],
     'Five-color Marvel hero: flies, and every turn makes WUBRG that pays for exiled cards, abilities and anything not cast from the hand.'),
    ('Pantlaza, Sun-Favored', 'pantlaza-sun-favored', 'n_pantlaza-sun-favored.json',
     [('dinosaurs', 'Dinosaurs'), ('discover', 'Discover'), ('ramp', 'Ramp')],
     'Naya Dinosaurs: each Dinosaur that enters discovers a free spell based on its toughness.'),
    ('Jace, Multiverse Architect', 'jace-multiverse-architect', 'n_jace-multiverse-architect.json',
     [('tokens', 'Tokens'), ('planeswalkers', 'Planeswalker'), ('polymorph', 'Polymorph')],
     'Four-color planeswalker: Jace draws cards, protects himself from attacks and transforms creatures with Polymorph.'),
    ('The Wise Mothman', 'the-wise-mothman', 'n_the-wise-mothman.json',
     [('mill', 'Mill'), ('plus-1-plus-1-counters', '+1/+1 counters'), ('reanimator', 'Reanimator')],
     'Sultai radiation: everyone gets rad counters; each milled nonland card adds +1/+1 counters.'),
    ('Shang-Chi, Master of Kung Fu', 'shang-chi-master-of-kung-fu', 'n_shang-chi-master-of-kung-fu.json',
     [('ramp', 'Ramp'), ('mana-dorks', 'Mana-Dorks'), ('elves', 'Elves')],
     'Bonus commander: activate creature abilities immediately as though they had haste; Shang-Chi makes two mana for creature abilities.'),
]
BONUS = {'Shang-Chi, Master of Kung Fu'}

# WotC Game Changers (bracket 4 staples) and cards that are simply too strong for this mode's unlocks
GAME_CHANGERS = set('''Drannith Magistrate|Enlightened Tutor|Humility|Serra's Sanctum|Smothering Tithe|Teferi's Protection|Cyclonic Rift|
Consecrated Sphinx|Expropriate|Fierce Guardianship|Force of Will|Gifts Ungiven|Intuition|Jin-Gitaxias, Core Augur|Mystical Tutor|
Narset, Parter of Veils|Rhystic Study|Sway of the Stars|Thassa's Oracle|Urza, Lord High Artificer|Bolas's Citadel|Braids, Cabal Minion|
Demonic Tutor|Imperial Seal|Necropotence|Opposition Agent|Orcish Bowmasters|Tergrid, God of Fright|Vampiric Tutor|Ad Nauseam|
Jeska's Will|Gamble|Underworld Breach|Crop Rotation|Food Chain|Gaea's Cradle|Natural Order|Seedborn Muse|Survival of the Fittest|
Vorinclex, Voice of Hunger|Worldly Tutor|Aura Shards|Coalition Victory|Grand Arbiter Augustin IV|Kinnan, Bonder Prodigy|Notion Thief|
Winota, Joiner of Forces|Yuriko, the Tiger's Shadow|Chrome Mox|Field of the Dead|Glacial Chasm|Grim Monolith|Lion's Eye Diamond|
Mana Vault|Mishra's Workshop|Mox Diamond|Panoptic Mirror|The One Ring|The Tabernacle at Pendrell Vale|Ancient Tomb|Trouble in Pairs|
Biorhythm|Farewell|Mind Over Matter|Ancient Tomb|Bloom Tender|Deflecting Swat|Tainted Pact|Doomsday|Faerie Mastermind|
Underworld Breach|Mana Drain|Jeweled Lotus|Mana Crypt|Dockside Extortionist|Nadu, Winged Wisdom|Hullbreacher|Fastbond|
Flash|Protean Hulk|Sylvan Library|Esper Sentinel|Mox Opal|Lotus Petal|Dark Ritual|Wheel of Fortune|Timetwister|Time Warp|
Craterhoof Behemoth|Tooth and Nail|Expropriate|Rite of Replication|Insurrection|Avacyn's Pilgrim|Simian Spirit Guide|Elvish Spirit Guide|
Paradox Engine|Isochron Scepter|Dramatic Reversal|Peregrine Drake|Palinchron|Thousand-Year Storm'''.replace('\n', '').split('|'))
STARTER_ALWAYS = ['Sol Ring', 'Arcane Signet']
BASICS = {'W': 'Plains', 'U': 'Island', 'B': 'Swamp', 'R': 'Mountain', 'G': 'Forest'}
NAMES = {'W': 'Weiß', 'U': 'Blau', 'B': 'Schwarz', 'R': 'Rot', 'G': 'Grün'}


def load_cards():
    cards = {}
    with open(os.path.join(HERE, 'cards.tsv'), encoding='utf-8') as f:
        for line in f:
            p = line.rstrip('\n').split('\t')
            if len(p) < 6:
                continue
            cards[p[0]] = {'id': p[1], 'type': p[2], 'cmc': float(p[3] or 0), 'cmd': p[4] == '1', 'text': p[5]}
    return cards


CARDS = load_cards()


def norm(name):
    """EDHREC names double-faced cards 'A // B'; Forge stores the front face name."""
    if name in CARDS:
        return name
    front = name.split(' // ')[0]
    return front if front in CARDS else None


def fits(name, colors):
    c = CARDS.get(name)
    return c is not None and set(c['id']) <= set(colors)


def is_land(name):
    return 'Land' in CARDS[name]['type'].split(' - ')[0]


def is_basic(name):
    return CARDS[name]['type'].startswith('Basic')


def jload(name):
    with open(os.path.join(ED, name), encoding='utf-8') as f:
        return json.load(f)


def cardviews(d):
    out = []
    for cl in d['container']['json_dict']['cardlists']:
        for v in cl['cardviews']:
            out.append((cl.get('tag') or '', v))
    return out


def category(name):
    """rough function tag from the oracle text, used for generic packages and balanced unlocks"""
    c = CARDS[name]
    t = c['text'].lower()
    typ = c['type']
    if 'Land' in typ.split(' - ')[0]:
        return 'lands'
    if re.search(r'counter target (spell|noncreature|creature spell|instant|activated)', t):
        return 'counter'
    if re.search(r'(destroy|exile) all|each (creature|other creature|nonland permanent)|all creatures get -', t):
        return 'wipe'
    if re.search(r'(destroy|exile) (target|up to one target|another target)|deals? \S+ damage to (any target|target creature)|'
                 r'target creature gets -|fights? |return target (creature|nonland permanent) to its owner', t):
        return 'removal'
    if re.search(r'search your library for an? (basic )?land|add \{|add one mana|add two mana|treasure token', t):
        return 'ramp'
    if re.search(r'search your library for a card|search your library for an? (creature|instant|sorcery|artifact|enchantment) card', t):
        return 'tutor'
    if re.search(r'hexproof|indestructible|protection from|phase out|phases out', t) and ('Equipment' in typ or 'Instant' in typ):
        return 'protect'
    if re.search(r'return (target|up to \w+ target) .*card.* from your graveyard|from your graveyard (to|onto) (your hand|the battlefield)', t):
        return 'recursion'
    if re.search(r'draws? (a|two|three|x|that many|cards)', t):
        return 'draw'
    return 'value'


def _t(n):
    return CARDS[n]['text'].lower()


# Themes named after a function only take cards that actually do it: EDHREC theme pages rank by synergy with the
# commander, so e.g. the "ramp" page of a dragon commander is mostly dragons (user: "how is this ramp").
# Tribal/mechanic themes (dragons, warriors, equipment-matters ...) keep the plain synergy ranking.
FUNCTION_RULES = {
    'ramp': lambda n: category(n) == 'ramp' and CARDS[n]['cmc'] <= 4,
    'mana-dorks': lambda n: 'Creature' in CARDS[n]['type'] and CARDS[n]['cmc'] <= 3
                            and re.search(r'add \{|add one mana|add two mana|mana of any', _t(n)) is not None,
    'burn': lambda n: re.search(r'deals? (\d+|x|that much) damage to (any target|target|another target|up to|'
                                r'each (opponent|creature|player)|that (player|creature)|any one target)', _t(n)) is not None,
    'tokens': lambda n: 'create' in _t(n) and 'token' in _t(n),
    'lifegain': lambda n: re.search(r'gains? (\d+|x|that much) life|lifelink', _t(n)) is not None,
    'mill': lambda n: re.search(r'\bmills?\b', _t(n)) is not None,
    'self-mill': lambda n: re.search(r'\bmills?\b|into your graveyard', _t(n)) is not None,
    'reanimator': lambda n: re.search(r'(graveyard|graveyards) (to|onto) the battlefield|from (a|your) graveyard (to|onto) the battlefield'
                                      r'|\bmills?\b|discard', _t(n)) is not None,
    'graveyard': lambda n: 'graveyard' in _t(n),
    'blink': lambda n: re.search(r'exile .{0,80}return (it|that card|them|those cards|the exiled card)', _t(n)) is not None,
    'impulse-draw': lambda n: re.search(r'exile the top .{0,120}(you may (play|cast)|until)', _t(n)) is not None,
    'exile': lambda n: 'exile' in _t(n),
    'equipment': lambda n: 'Equipment' in CARDS[n]['type'] or 'equip' in _t(n),
    'spellslinger': lambda n: 'Instant' in CARDS[n]['type'] or 'Sorcery' in CARDS[n]['type']
                              or re.search(r'whenever you cast an? (instant|sorcery|noncreature)', _t(n)) is not None,
    'artifacts': lambda n: 'Artifact' in CARDS[n]['type'] or 'artifact' in _t(n),
    'flying': lambda n: 'flying' in _t(n),
}


def fn_ok(key, n):
    rule = FUNCTION_RULES.get(key)
    return rule is None or bool(rule(n))


CATEGORY_NAMES = {'ramp': 'Mana & Ramp', 'removal': 'Removal', 'counter': 'Counterspells', 'wipe': 'Boardwipes',
                  'draw': 'Card advantage', 'protect': 'Protection', 'recursion': 'Recursion', 'tutor': 'Tutors',
                  'lands': 'Better lands', 'value': 'Bombs & Value'}


# ------------------------------------------------------------------ shared pools from EDHREC top lists
def top_cards():
    seen = {}
    for color in ['white', 'blue', 'black', 'red', 'green', 'colorless', 'lands', 'multicolor']:
        path = os.path.join(ED, f't_top_{color}.json')
        if not os.path.exists(path):
            continue
        for tag, v in cardviews(jload(path)):
            n = norm(v['name'])
            if not n or is_basic(n) if n else True:
                continue
            incl = v.get('num_decks', 0) / max(1, v.get('potential_decks', 1))
            if n not in seen or incl > seen[n]['incl']:
                seen[n] = {'incl': incl, 'decks': v.get('num_decks', 0)}
    return seen


TOP = top_cards()
STAPLES = {n for n, i in TOP.items() if i['incl'] >= 0.10}


def price_map():
    out = {}
    for f in os.listdir(ED):
        if not f.endswith('.json'):
            continue
        try:
            d = jload(f)
            views = cardviews(d)
        except Exception:
            continue
        for tag, v in views:
            n = norm(v.get('name', ''))
            pr = v.get('prices') or {}
            vals = [x.get('price') for x in pr.values() if isinstance(x, dict) and isinstance(x.get('price'), (int, float))]
            if n and vals:
                out[n] = min(vals)
    return out


PRICE = price_map()

# lands every starter may use (cheap taplands, tri-lands, Command Tower); these never become unlocks
STARTER_LANDS = set('''Command Tower|Path of Ancestry|Exotic Orchard|Evolving Wilds|Terramorphic Expanse|Scoured Barrens|Tranquil Cove|
Dismal Backwater|Jungle Hollow|Rugged Highlands|Blossoming Sands|Bloodfell Caves|Swiftwater Cliffs|Thornwood Falls|Wind-Scarred Crag|
Azorius Guildgate|Dimir Guildgate|Rakdos Guildgate|Gruul Guildgate|Selesnya Guildgate|Orzhov Guildgate|Izzet Guildgate|Golgari Guildgate|
Boros Guildgate|Simic Guildgate|Arcane Sanctum|Crumbling Necropolis|Jungle Shrine|Savage Lands|Seaside Citadel|Nomad Outpost|
Mystic Monastery|Opulent Palace|Sandsteppe Citadel|Frontier Bivouac'''.replace(chr(10), '').split('|'))
STARTER_LANDS = {n for n in STARTER_LANDS if n in CARDS}

general_candidates = []
for n, info in TOP.items():
    if n in GAME_CHANGERS or n in STARTER_ALWAYS or n in STARTER_LANDS or CARDS[n]['cmd'] and 'Legendary Creature' in CARDS[n]['type']:
        continue
    general_candidates.append((info['incl'], info['decks'], n))
general_candidates.sort(reverse=True)

# general unlock pool: the most included staples, balanced by colour group and function
UNLOCK_PER_GROUP = {'W': 26, 'U': 26, 'B': 26, 'R': 26, 'G': 26, '': 26, 'L': 24}
groups = {k: [] for k in UNLOCK_PER_GROUP}
for incl, decks, n in general_candidates:
    g = 'L' if is_land(n) else (CARDS[n]['id'] if len(CARDS[n]['id']) <= 1 else None)
    if g is None or len(groups[g]) >= UNLOCK_PER_GROUP[g]:
        continue
    cat = category(n)
    if g != 'L' and sum(1 for x in groups[g] if x['cat'] == cat) >= 8:      # no colour full of one function
        continue
    groups[g].append({'n': n, 'c': CARDS[n]['id'], 'cat': cat, 'incl': round(incl, 3)})
unlocks = []
for g, lst in groups.items():
    lst.sort(key=lambda x: -x['incl'])
    k = len(lst)
    for i, x in enumerate(lst):             # tiers inside each group: ~15 % stark, 35 % gut, 50 % mittel
        x['tier'] = 'stark' if i < k * 0.15 else 'gut' if i < k * 0.5 else 'mittel'
        unlocks.append(x)
UNLOCK_NAMES = {x['n'] for x in unlocks}

# generic pools for run packages (run-only rewards): everything popular, by function
generic = {}
for incl, decks, n in general_candidates[:900]:
    cat = category(n)
    generic.setdefault(cat, []).append({'n': n, 'c': CARDS[n]['id'], 's': round(45 + 40 * min(1, incl / 0.35))})
for cat in generic:
    generic[cat] = generic[cat][:90]

# Functions that are rare among the top staples (protection, recursion, tutors, ...) get topped up per colour from the
# whole card pool, ranked by the commander rating, so every colour combination gets a full, on-topic package.
RATINGS = {}
_rp = os.path.join(HERE, '..', 'commander-rating', 'ratings.tsv')
if os.path.exists(_rp):
    for _line in open(_rp, encoding='utf-8'):
        _p = _line.rstrip('\n').split('\t')
        if len(_p) >= 2 and _p[1].isdigit():
            RATINGS[_p[0]] = int(_p[1])
RANKED_BY_RATING = sorted(((r, n) for n, r in RATINGS.items() if n in CARDS and not (CARDS[n]['cmd'] and 'Legendary Creature' in CARDS[n]['type'])),
                          reverse=True)
MIN_PER_COLOUR = {'W': 12, 'U': 12, 'B': 12, 'R': 12, 'G': 12, '': 10}
_extra = sorted(((r, n) for n, r in RATINGS.items() if n in CARDS and not is_land(n) and n not in GAME_CHANGERS
                 and not (CARDS[n]['cmd'] and 'Legendary Creature' in CARDS[n]['type'])), reverse=True)
for cat, lst in generic.items():
    if cat in ('lands', 'value'):
        continue
    names = {x['n'] for x in lst}
    for col, need in MIN_PER_COLOUR.items():
        have = sum(1 for x in lst if x['c'] == col)
        for r, n in _extra:
            if have >= need:
                break
            if n in names or CARDS[n]['id'] != col or category(n) != cat:
                continue
            lst.append({'n': n, 'c': col, 's': round(45 + 32 * min(1, r / 100))})
            names.add(n)
            have += 1
legendary = [{'n': n, 'c': CARDS[n]['id'], 's': 92} for n in sorted(GAME_CHANGERS) if n in CARDS
             and not CARDS[n]['cmd'] and n not in ('Mana Crypt', 'Jeweled Lotus', 'Dockside Extortionist', 'Nadu, Winged Wisdom',
                                                  'Hullbreacher', 'Flash', 'Protean Hulk', 'Thassa\'s Oracle', 'Doomsday',
                                                  'Lion\'s Eye Diamond', 'Food Chain', 'Coalition Victory', 'Panoptic Mirror',
                                                  'Glacial Chasm', 'Mishra\'s Workshop', 'The Tabernacle at Pendrell Vale',
                                                  'Field of the Dead', 'Serra\'s Sanctum', 'Paradox Engine', 'Ad Nauseam',
                                                  'Underworld Breach', 'Grand Arbiter Augustin IV', 'Kinnan, Bonder Prodigy',
                                                  'Winota, Joiner of Forces', 'Yuriko, the Tiger\'s Shadow', 'Urza, Lord High Artificer',
                                                  'Braids, Cabal Minion', 'Tergrid, God of Fright', 'Jin-Gitaxias, Core Augur',
                                                  'Vorinclex, Voice of Hunger', 'Narset, Parter of Veils', 'Drannith Magistrate',
                                                  'Opposition Agent', 'Notion Thief', 'Humility', 'Trouble in Pairs', 'Biorhythm',
                                                  'Isochron Scepter', 'Dramatic Reversal', 'Peregrine Drake', 'Palinchron',
                                                  'Thousand-Year Storm', 'Insurrection', 'Tooth and Nail', 'Mind Over Matter')]


# ------------------------------------------------------------------ commanders
def build_commander(name, slug, page, themes, blurb):
    colors = CARDS[name]['id']
    d = jload(page)
    synergy = {}
    for tag, v in cardviews(d):
        n = norm(v['name'])
        if n and fits(n, colors) and n != name:
            incl = v.get('num_decks', 0) / max(1, v.get('potential_decks', 1))
            s = v.get('synergy', 0) or 0
            if n not in synergy or s > synergy[n][0]:
                synergy[n] = (s, incl)
    # sixteen signature unlocks: highest synergy, not a staple of the shared pools, no Game Changer, no land
    sig = [n for n, (s, incl) in sorted(synergy.items(), key=lambda kv: -(kv[1][0] * 0.7 + kv[1][1] * 0.3))
           if n not in GAME_CHANGERS and n not in UNLOCK_NAMES and n not in STARTER_ALWAYS and not is_land(n)
           and s >= 0.15][:16]

    _sig_path = os.path.join(HERE, 'signature_cards.json')
    if os.path.exists(_sig_path):
        sig = json.load(open(_sig_path, encoding='utf-8')).get(slug, sig)

    # starter: average deck minus staples, signature cards and Game Changers; 35 spells by synergy
    avg = jload(f'avg_{slug}.json')['deck']['cards']
    spells, lands_avg = [], []
    for typ, lst in avg.items():
        for n0, cnt in lst:
            n = norm(n0)
            if not n or not fits(n, colors) or n == name:
                continue
            (lands_avg if is_land(n) else spells).append(n)
    reserved = GAME_CHANGERS | UNLOCK_NAMES | set(sig) | set(STARTER_ALWAYS)
    pool = [n for n in spells if n not in reserved and PRICE.get(n, 0) <= 6]
    pool.sort(key=lambda n: -(synergy.get(n, (0, 0))[0]))
    starter = list(STARTER_ALWAYS) + pool[:33]
    if len(starter) < 35:   # fill from the commander page by inclusion
        extra = [n for n, (s, incl) in sorted(synergy.items(), key=lambda kv: -kv[1][1])
                 if n not in reserved and n not in starter and not is_land(n) and PRICE.get(n, 0) <= 6]
        starter += extra[:35 - len(starter)]

    # lands: Command Tower, cheap duals/tri-lands of these colours, then basics by colour weight
    nonbasic = ['Command Tower'] if len(colors) > 1 else []
    for n in sorted(STARTER_LANDS):
        if n in nonbasic or not fits(n, colors) or n == 'Command Tower':
            continue
        if n in ('Path of Ancestry', 'Exotic Orchard') or (len(CARDS[n]['id']) >= 2 and CARDS[n]['id'] in colors_subsets(colors)):
            nonbasic.append(n)
    random.Random(slug).shuffle(nonbasic)
    nonbasic = (['Command Tower'] if 'Command Tower' in nonbasic else []) + [n for n in nonbasic if n != 'Command Tower']
    nonbasic = nonbasic[:min(9, 2 + 2 * len(colors))]
    pips = {c: 1 for c in colors}
    for n in starter:
        for ch in CARDS[n]['id']:
            pips[ch] = pips.get(ch, 0) + 1
    if name == 'Karolina Dean, Runaway':
        pips['R'] += 8                               # the commander itself costs only red
    nbasic = 24 - len(nonbasic)
    total = sum(pips.values())
    basics = []
    for ch in colors:
        basics += [BASICS[ch]] * max(1, round(nbasic * pips[ch] / total))
    while len(basics) > nbasic:
        basics.remove(max(set(basics), key=basics.count))
    while len(basics) < nbasic:
        basics.append(BASICS[max(pips, key=pips.get)])
    lands = nonbasic + basics

    # theme pools for run packages (run-only): on-theme cards by synergy; staples belong to the generic packages
    used = set(starter) | set(sig)
    total_decks = d['container']['json_dict']['card'].get('num_decks', 0)
    min_decks = 15 if total_decks >= 5000 else 3          # rarely played commanders (bonus picks) have small theme pages
    per_theme = []
    for key, label in themes:
        path = os.path.join(ED, f'th_{slug}__{key}.json')
        scored = {}
        for tag, v in cardviews(jload(path)):
            n = norm(v['name'])
            if (not n or n == name or not fits(n, colors) or is_land(n) or n in used or n in GAME_CHANGERS
                    or n in STARTER_ALWAYS or n in UNLOCK_NAMES or n in STAPLES or v.get('num_decks', 0) < min_decks
                    or not fn_ok(key, n)):
                continue
            syn = v.get('synergy', 0) or 0
            incl = v.get('num_decks', 0) / max(1, v.get('potential_decks', 1))
            val = syn + 0.35 * incl
            if n not in scored or val > scored[n]:
                scored[n] = val
        per_theme.append(scored)
    owner = {}
    for i, scored in enumerate(per_theme):          # main themes (first three) keep their cards, rare themes get the rest
        for n, v in scored.items():
            if n not in owner or (i < 3) > (owner[n] < 3) or ((i < 3) == (owner[n] < 3) and v > per_theme[owner[n]][n]):
                owner[n] = i
    # thin themes (rarely played commanders) are topped up with this commander's own high-synergy cards
    taken = set(owner)
    extra = [n for n, (syn, incl) in sorted(synergy.items(), key=lambda kv: -(kv[1][0] + .35 * kv[1][1]))
             if n not in taken and n not in used and not is_land(n) and n not in GAME_CHANGERS and n not in STARTER_ALWAYS
             and n not in UNLOCK_NAMES and n not in STAPLES]
    for i in range(len(themes)):
        fitting = [n for n in extra if fn_ok(themes[i][0], n)]
        while sum(1 for n in owner if owner[n] == i) < 15 and fitting:
            n = fitting.pop(0); extra.remove(n); owner[n] = i; per_theme[i][n] = synergy[n][0] + .35 * synergy[n][1]
    # function themes that are still thin: the most played cards with that function in these colours (staples allowed)
    for i, (key, label) in enumerate(themes):
        if key not in FUNCTION_RULES:
            continue
        for r, n in RANKED_BY_RATING:
            if sum(1 for x in owner if owner[x] == i) >= 20:
                break
            if (n in owner or n in used or n == name or not fits(n, colors) or is_land(n) or n in GAME_CHANGERS
                    or n in STARTER_ALWAYS or n in UNLOCK_NAMES or not fn_ok(key, n)):
                continue
            owner[n] = i; per_theme[i][n] = .4 * r / 100
    theme_out = []
    for i, (key, label) in enumerate(themes):
        best = sorted(((n, v) for n, v in per_theme[i].items() if owner[n] == i), key=lambda kv: -kv[1])[:30]
        theme_out.append({'key': key, 'name': label,
                          'cards': [{'n': n, 's': round(50 + 38 * min(1, max(0, v) / 0.6))} for n, v in best]})
    alts = [n for n, (syn, incl) in sorted(synergy.items(), key=lambda kv: -kv[1][1])
            if n not in reserved and n not in starter and not is_land(n) and PRICE.get(n, 0) <= 6
            and all(n not in [x['n'] for x in t['cards']] for t in theme_out)][:24]
    scores = {n: 5 for n in lands if is_basic(n)}
    scores.update({n: 20 for n in lands if not is_basic(n)})
    for i, n in enumerate(starter):
        scores[n] = 40 if n in STARTER_ALWAYS else round(34 - 20 * i / max(1, len(starter)))
    for i, n in enumerate(alts):
        scores.setdefault(n, round(30 - 12 * i / max(1, len(alts))))
    return {'name': name, 'slug': slug, 'colors': colors, 'blurb': blurb, 'bonus': name in BONUS, 'lands': lands, 'starter': starter, 'alts': alts,
            'themes': theme_out, 'specific': [{'n': n, 's': 78} for n in sig], 'scores': scores}


def colors_subsets(colors):
    out = set()
    cs = list(colors)
    for i in range(len(cs)):
        for j in range(i + 1, len(cs)):
            out.add(''.join(c for c in 'WUBRG' if c in (cs[i], cs[j])))
            for k in range(j + 1, len(cs)):
                out.add(''.join(c for c in 'WUBRG' if c in (cs[i], cs[j], cs[k])))
    return out


EXTRA_THEMES = {'yshtola-nights-blessed': [('burn', 'Burn'), ('enchantress', 'Enchantments')],'teval-the-balanced-scale': [('graveyard', 'Graveyard'), ('tokens', 'Tokens')],'fire-lord-azula': [('storm', 'Storm'), ('burn', 'Burn')],'cloud-ex-soldier': [('artifacts', 'Artifacts'), ('modified-creatures', 'Modified creatures')],'ureni-of-the-unwritten': [('extra-combats', 'Extra combats'), ('blink', 'Blink')],'karolina-dean-runaway': [('legends', 'Legends'), ('ramp', 'Ramp')],'pantlaza-sun-favored': [('blink', 'Blink'), ('aggro', 'Aggro')],'jace-multiverse-architect': [('control', 'Control'), ('empower-jace', 'Empower Jace')],'the-wise-mothman': [('mutants', 'Mutants'), ('rad-counters', 'Rad counters')],'shang-chi-master-of-kung-fu': [('aggro', 'Aggro'), ('plus-1-plus-1-counters', '+1/+1 counters')]}
commanders = [build_commander(n, sl, pg, th + EXTRA_THEMES.get(sl, []), bl) for n, sl, pg, th, bl in COMMANDERS]
for c in commanders:
    for i, t in enumerate(c['themes']):
        t['rare'] = i >= 3                     # themes 4-5: offered less often
from custom_themes import add_custom_themes   # main themes EDHREC has no page for (e.g. Azula's combat tricks)
for c in commanders:
    add_custom_themes(c)
# hand-reviewed packages (review/<slug>.json, checked card by card, EDHREC only as an idea) replace the generated themes
for c in commanders:
    path = os.path.join(HERE, 'review', c['slug'] + '.json')
    if not os.path.exists(path):
        continue
    reviewed = json.load(open(path, encoding='utf-8'))
    themes = []
    for i, t in enumerate(reviewed['themes']):
        cards = [{'n': x['n'], 's': int(x['s'])} for x in t['cards'] if x['n'] in CARDS and fits(x['n'], c['colors'])]
        theme = {'key': t['key'], 'name': t['name'], 'rare': i >= 3, 'cards': cards}
        if t.get('focus') or t['key'] == 'combat-tricks':
            theme['focus'] = True; theme['rare'] = False
        themes.append(theme)
    c['themes'] = themes
    print('reviewed packages:', c['name'], [(t['key'], len(t['cards'])) for t in themes])
# Explicit per-card generic curation; preserve these decisions on a rebuild.
curation_path = os.path.join(HERE, 'review', 'generic-curation.json')
if os.path.exists(curation_path):
    curation = json.load(open(curation_path, encoding='utf-8'))
    for key, names in curation['remove'].items():
        excluded = set(names)
        generic[key] = [x for x in generic.get(key, []) if x['n'] not in excluded]
    for pool in generic.values():
        for card in pool:
            card.update(curation.get('restrictions', {}).get(card['n'], {}))
content = {'version': 1, 'commanders': commanders,
           'generic': [{'key': k, 'name': CATEGORY_NAMES[k], 'cards': v} for k, v in generic.items()],
           'unlocks': unlocks, 'legendary': legendary}
# Preserve reviewed balance edits and package-selection baselines on regeneration.
_balance_path = os.path.join(HERE, 'balance_packages.json')
if os.path.exists(_balance_path):
    _balance = json.load(open(_balance_path, encoding='utf-8'))
    for _cmd in content['commanders']:
        _cmd.update(_balance.get(_cmd['slug'], {}))

# Hand-picked unlockable commanders are versioned independently of the legacy pool builder.
_expansion_path = os.path.join(HERE, 'commander-expansion.json')
if os.path.exists(_expansion_path):
    _expansion = json.load(open(_expansion_path, encoding='utf-8'))
    _known = {c['name'] for c in content['commanders']}
    content['commanders'].extend(c for c in _expansion if c['name'] not in _known)

# +10-15 hand-picked cards per package (Codex + Claude, 2026-10-15, package-pools-20261015; checked against the package rules)
_pools_path = os.path.join(HERE, 'pool_expansion.json')
if os.path.exists(_pools_path):
    _pools = json.load(open(_pools_path, encoding='utf-8'))
    for _cmd in content['commanders']:
        _adds = _pools['commanders'].get(_cmd['slug'], {})
        for _t in _cmd['themes']:
            _have = {x['n'] for x in _t['cards']}
            for _x in _adds.get(_t['key'], []):
                if _x['n'] not in _have:
                    _t['cards'].append(_x); _have.add(_x['n'])
                    _cmd.setdefault('scores', {})[_x['n']] = max(_cmd['scores'].get(_x['n'], 0), _x['s'])
    for _g in content['generic']:
        _have = {x['n'] for x in _g['cards']}
        _g['cards'].extend(x for x in _pools['generic'].get(_g['key'], []) if x['n'] not in _have)

# starter decks 59 -> 70 cards (user, 2026-10-15)
sys.path.insert(0, HERE)
from apply_starter_overrides import apply as _starter_overrides
_starter_overrides(content)   # hand-built starters (Azula, 2026-10-15)
from starter_lands import apply as _starter_lands
_starter_lands(content, quiet=True)   # untapped / condition duals, shocks only for 4+ colours (2026-10-18)
from aragorn_packages import apply as _aragorn_packages
_aragorn_packages(content, quiet=True)   # Aragorn: mana-base + Fellowship packages (2026-10-18)
from extend_starters import extend as _extend_starters
_extend_starters(content)

from apply_commander_reworks import apply as _apply_commander_reworks
_apply_commander_reworks(content)

from apply_rarity_playtest import apply as _apply_rarity_playtest
_apply_rarity_playtest(content)
from apply_package_power_review import apply as _apply_package_power_review
if not os.path.exists(os.path.join(HERE, 'consolidated_packages.json')):
    _apply_package_power_review(content) # legacy patches assert obsolete fixed tier sizes
from tier_overrides import apply as _apply_tier_overrides   # single-card tier corrections (2026-10-18)
_apply_tier_overrides(content)
# Final curated 30/40-card commander and 50-card neutral pools (2026-10-26).
from consolidated_packages import apply as _apply_consolidated_packages
_apply_consolidated_packages(content)
# no Power 9 anywhere (user, 2026-10-15) - applied last, after every overlay
from banned_cards import strip as _strip_banned
_strip_banned(content)

with open(os.path.join(HERE, 'content.json'), 'w', encoding='utf-8') as f:
    json.dump(content, f, ensure_ascii=False, indent=1)

# report
print('general unlocks:', len(unlocks), {t: sum(1 for x in unlocks if x['tier'] == t) for t in ('mittel', 'gut', 'stark')})
print('  by function:', {k: sum(1 for x in unlocks if x['cat'] == k) for k in CATEGORY_NAMES})
print('generic pools:', {k: len(v) for k, v in generic.items()}, 'legendary:', len(legendary))
for c in commanders:
    print(f"{c['name'][:28]:28} {c['colors']:5} lands {len(c['lands'])} starter {len(c['starter'])} "
          f"themes {[len(t['cards']) for t in c['themes']]} specific {len(c['specific'])}: {', '.join(x['n'] for x in c['specific'][:4])} ...")
