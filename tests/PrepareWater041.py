from pathlib import Path
import shutil
import sys
import zipfile

# Loose copies avoid mixing signed game classes and unsigned woven classes in
# the same Java package. The installed game archive is opened read-only.
game, woven, fixture = map(Path, sys.argv[1:])
with zipfile.ZipFile(game) as archive:
    for name in archive.namelist():
        if name.startswith('zombie/iso/') and name.endswith('.class') and '/' not in name[len('zombie/iso/'):]:
            target = fixture / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(name))
for name in ('viewpoint/render/Meshes', 'viewpoint/render/ShadowPass',
             'viewpoint/platform/GlProgram', 'viewpoint/platform/Shaders',
             'viewpoint/render/TemporalPass', 'zombie/iso/IsoWater'):
    target = fixture / (name + '.class')
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(woven / (name + '.class'), target)
print('Water041 fixture: actual woven waterProjection and isolated game package.')
