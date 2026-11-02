from pathlib import Path
import hashlib,struct,zlib,json

old='com.oplus.camera.aicompositionlab'
new='com.n810.camera.aicomposition.lab'
assert len(old)==len(new)
base=Path('work/clone-decoded')
counts={}
for p in base.glob('classes*.dex'):
    raw=p.read_bytes(); count=raw.count(old.encode())
    if not count:continue
    data=bytearray(raw.replace(old.encode(),new.encode()))
    data[12:32]=hashlib.sha1(data[32:]).digest()
    data[8:12]=struct.pack('<I',zlib.adler32(data[12:])&0xffffffff)
    p.write_bytes(data);counts[p.name]=count
for p in [base/'AndroidManifest.xml',*base.joinpath('res').rglob('*.xml')]:
    try:text=p.read_text()
    except UnicodeError:continue
    updated=text.replace(old,new)
    if p.name=='AndroidManifest.xml':updated=updated.replace('Camera Composition Lab','n810 Camera')
    if updated!=text:p.write_text(updated)
for name in ['clone-identities.tsv','clone-identities.json']:
    p=Path('work')/name;p.write_text(p.read_text().replace(old,new))
Path('work/n810-rename-check.json').write_text(json.dumps({'package':new,'dex_string_replacements':counts},indent=2))
print('n810 identity applied to',len(counts),'DEX files')
