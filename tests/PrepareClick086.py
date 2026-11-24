from pathlib import Path
import shutil,sys,zipfile
game,woven,destination=map(Path,sys.argv[1:4])
with zipfile.ZipFile(game) as z:
 for n in z.namelist():
  if n.endswith('.class') and n.rpartition('/')[0]=='zombie/ui':
   f=destination/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(z.read(n))
shutil.copyfile(woven/'zombie/ui/UIManager.class',destination/'zombie/ui/UIManager.class')
for n in ['interact/MouseTargets','platform/Shaders','platform/GlProgram','render/Meshes']:
 f=destination/'viewpoint'/(n+'.class');f.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(woven/'viewpoint'/(n+'.class'),f)
print('Click086 fixture: unsigned UI package peers and exact current woven UI/pick/shader consumers.')
