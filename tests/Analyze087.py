from pathlib import Path
import json,statistics
p=Path(__file__).resolve().parent.parent
samples={}
for kind in ['baseline','current']:
 rows=[]
 for run in range(1,4):
  text=(p/f'research/compat087/bench-{kind}-{run}.log').read_text(encoding='utf-8-sig')
  assert 'GameAccess087 semantics PASS:' in text
  rows.append(json.loads(next(s.split('BENCH087 ',1)[1] for s in text.splitlines() if s.startswith('BENCH087 '))))
 samples[kind]=dict(nsPerTriplet=statistics.median(r['nsPerTriplet'] for r in rows),bytesPerTriplet=statistics.median(r['bytesPerTriplet'] for r in rows),runs=rows)
before,after=samples['baseline'],samples['current']
assert after['bytesPerTriplet']<before['bytesPerTriplet']*.25,(before,after)
report=dict(kind='shared reflection set/get/call CPU microbenchmark; not game FPS',baselineVersion='0.2.8',version='0.2.9',samples=samples,speedRatio=before['nsPerTriplet']/after['nsPerTriplet'],allocationReduction=1-after['bytesPerTriplet']/before['bytesPerTriplet'],directBufferAllocationRemovedPerSampledFrame=75272,actualFPSMeasured=False)
(p/'research/build087-performance.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f"Performance087 PASS: three-run median shared-access CPU {before['nsPerTriplet']:.2f} -> {after['nsPerTriplet']:.2f} ns/triplet; allocation {before['bytesPerTriplet']:.2f} -> {after['bytesPerTriplet']:.2f} bytes/triplet; not a game FPS measurement.")
