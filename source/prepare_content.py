"""Hand selected archetype packages. Local Oracle is authoritative for names/identity.
No network lists or model-generated bulk pool is imported.
"""
from pathlib import Path
import json
import argparse
p=argparse.ArgumentParser();p.add_argument('--game',required=True);p.add_argument('--out',required=True);args=p.parse_args()
R=Path(args.game).resolve();H=Path(args.out).resolve();(H/'data').mkdir(parents=True,exist_ok=True)
db={p[0]:p for line in (R/'_mods/rogue-commanders/cards.tsv' if (R/'_mods/rogue-commanders/cards.tsv').exists() else Path(__file__).with_name('card-reference.tsv')).read_text(encoding='utf8').splitlines() if len(p:=line.split('\t'))>=6}
split=lambda s:s.split('|')
def ref(n,score=80):
 assert n in db,'Unknown card: '+n
 return dict(n=n,c=db[n][1],s=score)
def theme(key,name,cards,focus=False,extra=0):
 return dict(key=key,name=name,cards=[ref(n,95-i) for i,n in enumerate(split(cards))],focus=focus,rare=not focus,progression=extra>0,cost=30 if extra==2 else 0,requiredWins=3 if extra==2 else 0)
def commander(name,slug,colors,board,blurb,lands,starter,signatures,themes):
 c=dict(name=name,slug=slug,colors=colors,board=board,blurb=blurb,bonus=False,lands=split(lands),starter=split(starter),specific=[ref(n,100-i) for i,n in enumerate(split(signatures))],themes=themes,minimumPackages=5,alts=[],protectedStarter=['Chromatic Lantern'] if len(colors)==4 else [],scores={})
 for t in themes:
  for x in t['cards']:c['scores'][x['n']]=max(x['s'],c['scores'].get(x['n'],0))
 assert len(c['lands'])==24,(name,'lands',len(c['lands']))
 assert len(c['starter'])==35,(name,'starter',len(c['starter']))
 assert len(c['specific'])==16
 assert len(set(c['starter']))==35
 assert len({x['n'] for x in c['specific']})==16
 assert not set(c['starter']) & {x['n'] for x in c['specific']},'Signatures must expand starter'
 assert len(themes)==7 and sum(not t['progression'] for t in themes)==5
 names=set(c['starter']+c['lands']+[name]+[x['n'] for x in c['specific']]+[x['n'] for t in themes for x in t['cards']])
 for n in names:
  assert n in db,'Unknown '+n
  assert set(db[n][1])<=set(colors),(name,'off identity',n,db[n][1])
 return c
new=[]
new.append(commander('Teysa Karlov','teysa-karlov','WB','MID_Mobile',
 'Aristocrats: sacrifice expendable creatures, double death triggers, drain life and rebuild your board.',
 'Command Tower|Godless Shrine|Isolated Chapel|Caves of Koilos|Concealed Courtyard|Shattered Sanctum|Temple of Silence|Orzhov Basilica|Plains|Plains|Plains|Plains|Plains|Plains|Plains|Plains|Swamp|Swamp|Swamp|Swamp|Swamp|Swamp|Swamp|Swamp',
 'Sol Ring|Arcane Signet|Orzhov Signet|Talisman of Hierarchy|Viscera Seer|Carrion Feeder|Bartolomé del Presidio|Woe Strider|Blood Artist|Zulaport Cutthroat|Cruel Celebrant|Elas il-Kor, Sadistic Pilgrim|Bastion of Remembrance|Doomed Traveler|Hunted Witness|Shambling Ghast|Orzhov Enforcer|Ministrant of Obligation|Reassembling Skeleton|Persistent Specimen|Pawn of Ulamog|Midnight Reaper|Grim Haruspex|Village Rites|Deadly Dispute|Victimize|Unearth|Swords to Plowshares|Generous Gift|Mortify|Despark|Sign in Blood|Read the Bones|Ophiomancer|Syr Konrad, the Grim',
 'Skullclamp|Ashnod\'s Altar|Phyrexian Altar|Altar of Dementia|Pitiless Plunderer|Elenda, the Dusk Rose|Karmic Guide|Reveillark|Living Death|Yawgmoth, Thran Physician|Dictate of Erebos|Grave Pact|The Meathook Massacre|Nim Deathmantle|Liliana, Dreadhorde General|Diabolic Intent',[
 theme('sacrifice','Sacrifice outlets',"Viscera Seer|Carrion Feeder|Woe Strider|Bartolomé del Presidio|Yahenni, Undying Partisan|Ashnod's Altar|Phyrexian Altar|Altar of Dementia|Spawning Pit|Blasting Station|Fallen Ideal|Dimir House Guard",True),
 theme('death-drain','Death triggers',"Blood Artist|Zulaport Cutthroat|Cruel Celebrant|Elas il-Kor, Sadistic Pilgrim|Bastion of Remembrance|Syr Konrad, the Grim|Marionette Apprentice|Vindictive Vampire|Falkenrath Noble|The Meathook Massacre|Pawn of Ulamog|Sifter of Skulls",True),
 theme('fodder','Expendable bodies','Ophiomancer|Jadar, Ghoulcaller of Nephalia|Doomed Traveler|Hunted Witness|Orzhov Enforcer|Ministrant of Obligation|Elenda, the Dusk Rose|Requiem Angel|Hangarback Walker|Nested Shambler|Shambling Ghast|Black Market Connections',True),
 theme('recursion','Return from death',"Reassembling Skeleton|Persistent Specimen|Nether Traitor|Bloodghast|Victimize|Animate Dead|Necromancy|Reanimate|Unearth|Living Death|Sevinne's Reclamation|Nim Deathmantle"),
 theme('death-draw','Death into cards','Skullclamp|Dark Prophecy|Midnight Reaper|Grim Haruspex|Liliana, Dreadhorde General|Erebos, Bleak-Hearted|Plumb the Forbidden|Deadly Dispute|Village Rites|Diabolic Intent|Disciple of Bolas|Harvester of Souls'),
 theme('resilience','Resilience and answers',"Teferi's Protection|Flawless Maneuver|Unbreakable Formation|Selfless Spirit|Luminous Broodmoth|Malakir Rebirth|Swords to Plowshares|Anguished Unmaking|Path to Exile|Generous Gift|Damn|Loran of the Third Path",extra=1),
 theme('death-control','Advanced death engine',"Grave Pact|Dictate of Erebos|Butcher of Malakir|Yawgmoth, Thran Physician|Pitiless Plunderer|Revel in Riches|Bolas's Citadel|Smothering Abomination|Karmic Guide|Reveillark|Athreos, God of Passage|Teysa, Orzhov Scion",extra=2)
]))
new.append(commander('Breya, Etherium Shaper','breya-etherium-shaper','WUBR','WAR_Mobile',
 'Artifact engine: build Thopters, recycle artifacts and turn expendable machines into damage and control. No Voltron plan.',
 'Command Tower|Exotic Orchard|Raugrin Triome|Savai Triome|Xander\'s Lounge|Raffine\'s Tower|Hallowed Fountain|Watery Grave|Blood Crypt|Steam Vents|Godless Shrine|Sacred Foundry|Evolving Wilds|Terramorphic Expanse|Plains|Plains|Island|Island|Island|Island|Swamp|Swamp|Mountain|Mountain',
 'Sol Ring|Arcane Signet|Fellwar Stone|Chromatic Lantern|Azorius Signet|Rakdos Signet|Etherium Sculptor|Foundry Inspector|Ichor Wellspring|Chromatic Star|Chromatic Sphere|Terrarion|Myr Retriever|Junk Diver|Workshop Assistant|Servo Schematic|Ornithopter of Paradise|Solemn Simulacrum|Thought Monitor|Thoughtcast|Sai, Master Thopterist|Efficient Construction|Thopter Spy Network|Whirler Rogue|Myr Battlesphere|Emry, Lurker of the Loch|Goblin Engineer|Goblin Welder|Disciple of the Vault|Reckless Fireweaver|Marionette Apprentice|Swords to Plowshares|Dispatch|Counterspell|Negate',
 'Scrap Trawler|Krark-Clan Ironworks|Time Sieve|Thopter Foundry|Sword of the Meek|Cyberdrive Awakener|Simulacrum Synthesizer|Smothering Tithe|Urza, Lord High Artificer|Shimmer Dragon|Tezzeret, Master of the Bridge|Master Transmuter|Academy Ruins|Scrap Mastery|Ashnod\'s Altar|Oswald Fiddlebender',[
 theme('artifact-fodder','Cheap machines','Ichor Wellspring|Chromatic Star|Chromatic Sphere|Terrarion|Servo Schematic|Myr Retriever|Junk Diver|Workshop Assistant|Experimental Synthesizer|Ornithopter of Paradise|Etherium Sculptor|Foundry Inspector',True),
 theme('thopters','Thopters and tokens','Sai, Master Thopterist|Efficient Construction|Mirrodin Besieged|Sharding Sphinx|Thopter Assembly|Whirler Rogue|Myr Battlesphere|Hangarback Walker|Retrofitter Foundry|Thopter Shop|Master of Etherium|Steel Overseer',True),
 theme('artifact-recursion','Recycle the engine','Emry, Lurker of the Loch|Goblin Welder|Goblin Engineer|Scrap Trawler|Myr Retriever|Junk Diver|Workshop Assistant|Trading Post|Scrap Mastery|Open the Vaults|Academy Ruins|Buried Ruin',True),
 theme('artifact-draw','Machine intelligence','Thought Monitor|Thoughtcast|Reality Heist|Reverse Engineer|Shimmer Dragon|Vedalken Archmage|Jhoira, Weatherlight Captain|Thopter Spy Network|One with the Machine|Bident of Thassa|Idol of Oblivion|Losheel, Clockwork Scholar'),
 theme('artifact-payoffs','Engine payoffs','Cyberdrive Awakener|Rise and Shine|The Antiquities War|Masterful Replication|Mechanized Production|Simulacrum Synthesizer|Marionette Master|Marionette Apprentice|Disciple of the Vault|Reckless Fireweaver|Ingenious Artillerist|Impact Tremors'),
 theme('artifact-control','Artifact control','Dispatch|Swords to Plowshares|Vandalblast|Wear // Tear|Dovin\'s Veto|Swan Song|Whir of Invention|Reshape|Fabricate|Oswald Fiddlebender|Padeem, Consul of Innovation|Daretti, Scrap Savant',extra=1),
 theme('advanced-engine','Advanced machine engine','Krark-Clan Ironworks|Ashnod\'s Altar|Arcbound Ravager|Salvaging Station|Grinding Station|Thopter Foundry|Sword of the Meek|Time Sieve|Urza, Lord High Artificer|Master Transmuter|Tezzeret, Master of the Bridge|Nim Deathmantle',extra=2)
]))
new.append(commander('Eriette of the Charmed Apple','eriette-of-the-charmed-apple','WB','DSK_Mobile',
 'Aura control: charm opposing creatures so they cannot attack you; each Aura fuels your end-step life drain.',
 'Command Tower|Godless Shrine|Isolated Chapel|Caves of Koilos|Concealed Courtyard|Shattered Sanctum|Temple of Silence|Orzhov Basilica|Plains|Plains|Plains|Plains|Plains|Plains|Plains|Plains|Plains|Swamp|Swamp|Swamp|Swamp|Swamp|Swamp|Swamp',
 'Sol Ring|Arcane Signet|Orzhov Signet|Talisman of Hierarchy|Sram, Senior Edificer|Mesa Enchantress|Hateful Eidolon|Killian, Ink Duelist|Transcendent Envoy|Starfield Mystic|Danitha Capashen, Paragon|Pacifism|Planar Disruption|Arrest|Faith\'s Fetters|Heliod\'s Punishment|Candletrap|Ossification|Dead Weight|Mire\'s Grasp|Parasitic Impetus|Vampiric Link|Spirit Link|Trespasser\'s Curse|Rune of Mortality|Rune of Sustenance|Flickering Ward|Kaya\'s Ghostform|Hyena Umbra|Unquestioned Authority|Swords to Plowshares|Despark|Sign in Blood|Retether|Crystal Chimes',
 'Darksteel Mutation|Replenish|Hall of Heliod\'s Generosity|Greater Auramancy|Sphere of Safety|Sigil of the Empty Throne|Archon of Sun\'s Grace|Grim Guardian|Doomwake Giant|Starfield of Nyx|Gift of Immortality|Umbra Mystic|Kor Spiritdancer|Ondu Spiritdancer|Pearl-Ear, Imperial Advisor|Resurgent Belief',[
 theme('aura-control','Charm their creatures','Darksteel Mutation|Pacifism|Planar Disruption|Arrest|Faith\'s Fetters|Heliod\'s Punishment|Candletrap|Reprobation|Ossification|Dead Weight|Mire\'s Grasp|Minimus Containment',True),
 theme('aura-drain','Curses and drain','Parasitic Impetus|Vampiric Link|Spirit Link|Stab Wound|Trespasser\'s Curse|Grim Guardian|Underworld Coinsmith|Vow of Duty|Vow of Malice|Pariah|Unholy Indenture|Minion\'s Return',True),
 theme('enchantress','Aura card engines','Sram, Senior Edificer|Kor Spiritdancer|Mesa Enchantress|Hateful Eidolon|Killian, Ink Duelist|Transcendent Envoy|Starfield Mystic|Danitha Capashen, Paragon|Rune of Mortality|Rune of Sustenance|Unquestioned Authority|Light-Paws, Emperor\'s Voice',True),
 theme('aura-protection','Protect the charmer','Flickering Ward|Kaya\'s Ghostform|Gift of Immortality|Timely Ward|Hyena Umbra|Greater Auramancy|Shielded by Faith|Flickerform|Umbra Mystic|Swift Reconfiguration|Indestructibility|Gift of Doom'),
 theme('aura-recursion','Return your charms','Retether|Replenish|Resurgent Belief|Open the Vaults|Hall of Heliod\'s Generosity|Crystal Chimes|Starfield of Nyx|Auramancer|Boonweaver Giant|Sun Titan|Sevinne\'s Reclamation|Mantle of the Ancients'),
 theme('enchantment-board','Enchanted board','Sigil of the Empty Throne|Archon of Sun\'s Grace|Doomwake Giant|Sphere of Safety|Ghostly Prison|Blind Obedience|Authority of the Consuls|Swords to Plowshares|Anguished Unmaking|Damn|Loran of the Third Path|Omen of the Dead',extra=1),
 theme('aura-mastery','Advanced Aura mastery','Ondu Spiritdancer|Demonic Embrace|Angelic Destiny|Necromancy|Animate Dead|Dance of the Dead|Songbirds\' Blessing|Ethereal Armor|All That Glitters|Battle Mastery|Felidar Umbra|Spirit Mantle',extra=2)
]))
# A starter card is already available; reserve a separate 16th Eriette signature.
c=new[-1];c['specific']=[ref('Pearl-Ear, Imperial Advisor',90) if x['n']=='Mesa Enchantress' else x for x in c['specific']]
content=json.loads((R/'_mods/rogue-commanders/content.json').read_text(encoding='utf8'))
content['commanders']=[c for c in content['commanders'] if c['slug'] not in {x['slug'] for x in new}]
assert len(content['commanders'])==10
content['commanders'].extend(new)
(H/'data/commander-expansion.json').write_text(json.dumps(new,ensure_ascii=False,indent=1),encoding='utf8')
(H/'data/content.json').write_text(json.dumps(content,ensure_ascii=False,indent=1),encoding='utf8')
sig=json.loads((R/'_mods/rogue-commanders/signature_cards.json').read_text(encoding='utf8'))
for c in new:sig[c['slug']]=[x['n'] for x in c['specific']]
(H/'data/signature_cards.json').write_text(json.dumps(sig,ensure_ascii=False,indent=1),encoding='utf8')
audit=[]
for c in new:
 for role,names in [('starter',c['starter']),('lands',c['lands']),('signature',[x['n'] for x in c['specific']])]+[(t['key'],[x['n'] for x in t['cards']]) for t in c['themes']]:
  for n in names:audit.append(dict(commander=c['name'],package=role,card=n,identity=db[n][1],type=db[n][2],oracle=db[n][5]))
(H/'data/card-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=1),encoding='utf8')
print('Prepared',len(new),'commanders;',len(audit),'individually selected entries; 7 packages / 16 signatures each.')

