import ast,pathlib,urllib.request,io,zipfile,json,struct,subprocess,re,hashlib
w=pathlib.Path('work/compatible-engine');tree=ast.parse((w/'download_candidate.py').read_text());u=next(n.value.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='u' for t in n.targets));size=10859814526
class Remote(io.RawIOBase):
 def __init__(self):self.pos=0
 def seekable(self):return True
 def readable(self):return True
 def tell(self):return self.pos
 def seek(self,p,whence=0):self.pos=p if whence==0 else self.pos+p if whence==1 else size+p;return self.pos
 def read(self,n=-1):
  if n<0:n=size-self.pos
  n=min(n,size-self.pos)
  if not n:return b''
  assert 0<n<2*1024*1024
  a=self.pos;b=a+n-1
  with urllib.request.urlopen(urllib.request.Request(u,headers={'Range':f'bytes={a}-{b}'}),timeout=20) as r:
   assert r.status==206
   data=r.read(n);assert len(data)==n;self.pos+=n;return data
with zipfile.ZipFile(Remote()) as z:
 (w/'zip-entry-list.json').write_text(json.dumps([{'name':i.filename,'size':i.file_size,'offset':i.header_offset,'compression':i.compress_type} for i in z.infolist()],indent=2))
 for i in z.infolist():
  if i.filename.endswith('/metadata') or i.filename=='payload_properties.txt':
   b=z.read(i);name='ota-metadata.txt' if i.filename.endswith('/metadata') else i.filename;(w/name).write_bytes(b);print(name,b.decode())
f=Remote();f.seek(-6,2);s,marker,comment=struct.unpack('<HHH',f.read(6));assert marker==65535;f.seek(-s,2);(w/'ota-signature.der').write_bytes(f.read(s-6))
subprocess.run(['openssl','pkcs7','-inform','DER','-in',str(w/'ota-signature.der'),'-print_certs','-out',str(w/'ota-certificates.pem')],check=True)
certs=re.findall(b'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----',(w/'ota-certificates.pem').read_bytes(),re.S)
trust=[]
for p in pathlib.Path('work/firmware-donor').glob('x9-ota-trust-*.pem'):
 trust.append(subprocess.check_output(['openssl','x509','-in',str(p),'-outform','DER']))
match=[]
for c in certs:
 d=subprocess.check_output(['openssl','x509','-inform','PEM','-outform','DER'],input=c)
 match.append({'sha256':hashlib.sha256(d).hexdigest(),'matches_phone_ota_trust':d in trust})
(w/'certificate-match.json').write_text(json.dumps(match,indent=2));print('Certificate matches',match)
