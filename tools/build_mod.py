"""Rebuild editable helpers against the pinned release JAR, never overwrite input."""
from pathlib import Path
import argparse,subprocess,tempfile,zipfile
p=argparse.ArgumentParser();p.add_argument('--base-jar',required=True);p.add_argument('--jdk',required=True);p.add_argument('--out',required=True);a=p.parse_args()
base=Path(a.base_jar).resolve();out=Path(a.out).resolve();repo=Path(__file__).resolve().parent.parent
assert base!=out,'Choose a separate output JAR'
jdk=Path(a.jdk)/'bin';exports=['--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','--add-exports','java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED']
out.parent.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(dir=out.parent,prefix='forge-build-') as tmp:
 tmp=Path(tmp);classes=tmp/'classes';classes.mkdir();tools=tmp/'tools';tools.mkdir()
 assert tmp.resolve().is_relative_to(out.parent.resolve())
 sources=[str(x) for x in (repo/'source/mods').glob('*.java') if not x.name.startswith('Patch')]
 args=tmp/'sources.args';args.write_text('\n'.join('"'+x.replace('\\','/')+'"' for x in sources),encoding='utf8')
 subprocess.run([str(jdk/'javac.exe'),'--release','17','-encoding','UTF-8','-cp',str(base),'-d',str(classes),'@'+str(args)],check=True)
 for patcher_name,entry in [('PatchAttackTriggerRemoval','forge/ai/ComputerUtilCard.class'),('PatchRogueLifeLoss','forge/game/player/Player.class')]:
  patcher=repo/'source/mods'/(patcher_name+'.java')
  subprocess.run([str(jdk/'javac.exe'),*exports,'-d',str(tools),str(patcher)],check=True)
  original=tmp/(patcher_name+'.class');patched=classes/entry;patched.parent.mkdir(parents=True,exist_ok=True)
  with zipfile.ZipFile(base) as z:original.write_bytes(z.read(entry))
  subprocess.run([str(jdk/'java.exe'),*exports,'-cp',str(tools),patcher_name,str(original),str(patched)],check=True)
 replacements={x.relative_to(classes).as_posix():x.read_bytes() for x in classes.rglob('*.class')}
 out.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(base) as old,zipfile.ZipFile(out,'w') as new:
  for info in old.infolist():new.writestr(info,replacements.pop(info.filename,old.read(info.filename)))
  for name,data in replacements.items():new.writestr(name,data,compress_type=zipfile.ZIP_DEFLATED)
print('Built',out)
