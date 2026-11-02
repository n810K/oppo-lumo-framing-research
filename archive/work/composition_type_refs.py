import struct, zipfile, json
targets = ['com.oplus.feature.aicomposition.realscene.support', 'com.oplus.feature.aicomposition.inspiration.support', 'override_config_data']
hits=[]
with zipfile.ZipFile('work/OplusCamera-X9Pro.apk') as z:
 for name in z.namelist():
  if not name.endswith('.dex'): continue
  b=z.read(name)
  def u32(o): return struct.unpack_from('<I',b,o)[0]
  def u16(o): return struct.unpack_from('<H',b,o)[0]
  def leb(o):
   v=0;s=0
   while True:
    c=b[o];o+=1;v|=(c&127)<<s;s+=7
    if not c&128:return v,o
  n,off=u32(56),u32(60)
  strings=[]
  for i in range(n):
   p=u32(off+4*i);_,p=leb(p); strings.append(b[p:b.index(0,p)].decode('utf8','replace'))
  sought={i:s for i,s in enumerate(strings) if any(t in s for t in targets)}
  types=[strings[u32(u32(68)+4*i)] for i in range(u32(64))]
  fields={}
  for fi in range(u32(80)):
   f=u32(84)+8*fi
   if types[u16(f)]=='Lcom/oplus/camera/configure/CameraConfig;' and strings[u32(f+4)] in ['h','f']:
    fields[fi]=types[u16(f)]+'.'+strings[u32(f+4)]
  wanted_types={i:t for i,t in enumerate(types) if t in ['Lfc/d3;']}
  if not sought and not fields and not wanted_types:continue
  for ci in range(u32(96)):
   c=u32(100)+32*ci;cd=u32(c+24)
   if not cd:continue
   counts=[];p=cd
   for _ in range(4):v,p=leb(p);counts.append(v)
   for _ in range(counts[0]+counts[1]):_,p=leb(p);_,p=leb(p)
   for cnt in counts[2:]:
    mi=0
    for _ in range(cnt):
     delta,p=leb(p);mi+=delta;_,p=leb(p);code,p=leb(p)
     if not code:continue
     end=code+16+u32(code+12)*2
     for q in range(code+16,end,2):
      op=b[q]
      idx=u16(q+2) if op==26 and q+4<=end else u32(q+2) if op==27 and q+6<=end else -1
      if idx in sought:
       m=u32(92)+mi*8
       hits.append({'dex':name,'class':types[u16(m)][1:-1].replace('/','.'),'method':strings[u32(m+4)],'string':sought[idx]})
      idx2=u16(q+2) if q+4<=end else -1
      ref=fields.get(idx2) if 0x60<=op<=0x6d else wanted_types.get(idx2) if op in (0x1c,0x22) else None
      if ref:
       m=u32(92)+mi*8
       hits.append({'dex':name,'class':types[u16(m)][1:-1].replace('/','.'),'method':strings[u32(m+4)],'reference':ref})
print(json.dumps(hits,indent=2))
