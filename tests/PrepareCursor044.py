"""Unsigned, isolated package peers for exercising selected actual woven game classes."""
from pathlib import Path
import shutil
import sys
import zipfile

game, woven, destination = map(Path, sys.argv[1:4])
kind = sys.argv[4]
classes = {
    'input': ['zombie/characters/component/CharacterInputComponent.class'],
    'preview': ['viewpoint/LootLua.class', 'zombie/iso/IsoCell.class',
                'zombie/Lua/LuaManager$GlobalObject.class', 'zombie/ui/UIManager.class'],
}[kind]
packages = {name.rpartition('/')[0] for name in classes if name.startswith('zombie/')}
with zipfile.ZipFile(game) as z:
    names = [name for name in z.namelist() if name.endswith('.class') and name.rpartition('/')[0] in packages]
    for name in names:
        path = destination / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(z.read(name))
for name in classes:
    path = destination / name
    path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(woven / name, path)
print(f'Native cursor {kind} fixture: {len(names)} unsigned native package peers; {len(classes)} selected actual woven classes.')
