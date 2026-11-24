from pathlib import Path
import zipfile,json
p=Path.cwd();f=p/'build/VPIsometricLighting.jar'
with zipfile.ZipFile(f) as z:
 classes=[n for n in z.namelist() if n.endswith('.class')]
 assert len(classes)==188,len(classes)
 forbidden=[b'NativeDiagnostics',b'vpiso.diagnostics025',b'vpiso.diagnostics026',b'enable-diagnostics',b'VPISO diagnostic image writer',b'glReadPixels']
 for name in classes:
  data=z.read(name)
  for marker in forbidden:assert marker not in data,(name,marker)
 assert 'vpiso/NativeVehicleHints.class' in classes
for name in ['025诊断采样.cmd','恢复017.cmd','diagnostics025.ps1','诊断025说明.md','src/vpiso/NativeDiagnostics.java','tests/NativeDiagnosticsProbe.java','tests/DiagnosticsBoot026Probe.java']:
 assert not (p/name).exists(),name
print(f'057 artifact cleanup PASS: {len(classes)} classes; diagnostic sampler, switches, GPU readback and writer absent; obsolete CMD/scripts/tests removed.')
