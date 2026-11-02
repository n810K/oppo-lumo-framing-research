from pathlib import Path
import xml.etree.ElementTree as ET
import json

base=Path('work/clone-decoded')
ns='http://schemas.android.com/apk/res/android'
a='{'+ns+'}'
ET.register_namespace('android',ns)
p=base/'AndroidManifest.xml'
tree=ET.parse(p); root=tree.getroot(); app=root.find('application')
original=ET.parse('work/decompiled/AndroidManifest.xml').getroot()
old=original.get('package'); new='com.oplus.camera.aicompositionlab'
assert old=='com.oplus.camera' and root.get('package') in [old,new]
mapping={old:new}
for e in original.findall('permission'):
    name=e.get(a+'name')
    mapping[name]=name.replace(old,new,1) if name.startswith(old) else name+'.aicompositionlab'
for e in original.find('application').findall('provider'):
    for name in e.get(a+'authorities','').split(';'):
        if name:
            mapping[name]=name.replace(old,new,1) if name.startswith(old) else name+'.aicompositionlab'
for e in root.iter():
    if e.tag in ['application','activity','activity-alias','service','receiver','provider']:
        for key in ['name','targetActivity']:
            v=e.get(a+key)
            if v and (v.startswith('.') or '.' not in v):
                e.set(a+key,old+('.' if not v.startswith('.') else '')+v)
    for k,v in list(e.attrib.items()):
        if k==a+'authorities':e.set(k,';'.join(mapping.get(s,s) for s in v.split(';')))
        elif k in [a+'permission',a+'readPermission',a+'writePermission'] or e.tag in ['permission','uses-permission'] and k==a+'name':
            e.set(k,mapping.get(v,v))
root.set('package',new)
app.set(a+'label','Camera Composition Lab')
for e in app:
    if any(c.get(a+'name')=='android.intent.category.LAUNCHER' for c in e.findall('intent-filter/category')):
        e.set(a+'label','Camera Composition Lab')
tree.write(p,encoding='utf-8',xml_declaration=True)
Path('work/clone-identities.tsv').write_text(''.join(k+'\t'+v+'\n' for k,v in mapping.items()))
Path('work/clone-identities.json').write_text(json.dumps(mapping,indent=2))

def replace_value(v):
    if v in mapping:return mapping[v]
    for src,dst in mapping.items():
        if v.startswith('content://'+src+'/') or v=='content://'+src:
            return 'content://'+dst+v[len('content://'+src):]
    return v

changed=0
for p in (base/'res').rglob('*.xml'):
    if p.parent.name.startswith('raw'):continue
    t=ET.parse(p);dirty=False
    for e in t.getroot().iter():
        for k,v in list(e.attrib.items()):
            nv=replace_value(v)
            if nv!=v:e.set(k,nv);dirty=True
        if e.text:
            nv=replace_value(e.text)
            if nv!=e.text:e.text=nv;dirty=True
    if dirty:t.write(p,encoding='utf-8',xml_declaration=True);changed+=1
Path('work/clone-identities.tsv').write_text(''.join(k+'\t'+v+'\n' for k,v in mapping.items()))
Path('work/clone-identities.json').write_text(json.dumps(mapping,indent=2))
print('Package:',new,'isolated identities:',len(mapping),'resource files updated:',changed)
