from pathlib import Path
import zipfile,subprocess,json,hashlib,base64,struct,re
w=Path('work/compatible-engine');archive=w/'CPH2911-candidate.zip';size=archive.stat().st_size
with archive.open('rb') as f:
 f.seek(-6,2);sigstart,marker,comment=struct.unpack('<HHH',f.read(6));assert marker==65535;f.seek(-sigstart,2);sig=f.read(sigstart-6)
assert sig==(w/'ota-signature.der').read_bytes()
certs=re.findall(b'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----',(w/'ota-certificates.pem').read_bytes(),re.S)
donor=[subprocess.check_output(['openssl','x509','-outform','DER'],input=c) for c in certs]
trusted=[p for p in Path('work/firmware-donor').glob('x9-ota-trust-*.pem') if subprocess.check_output(['openssl','x509','-in',str(p),'-outform','DER']) in donor]
assert trusted,'Signer not in phone trust store'
with zipfile.ZipFile(archive) as z:
 info=z.getinfo('payload.bin');assert info.compress_type==0;props=dict(x.split('=',1) for x in z.read('payload_properties.txt').decode().splitlines() if '=' in x)
 with archive.open('rb') as f:
  f.seek(info.header_offset);h=f.read(30);n,e=struct.unpack_from('<HH',h,26)
 start=info.header_offset+30+n+e;end=start+info.file_size
signed=size-comment-2;ph=hashlib.sha256();zh=hashlib.sha256()
with (w/'signature-verification.log').open('wb') as err:
 verifier=subprocess.Popen(['openssl','cms','-verify','-binary','-inform','DER','-in',str(w/'ota-signature.der'),'-content','/dev/stdin','-nointern','-certfile',str(trusted[0]),'-noverify','-out','/dev/null'],stdin=subprocess.PIPE,stderr=err,stdout=subprocess.DEVNULL)
 pos=0
 with archive.open('rb') as f:
  while True:
   b=f.read(8*1024*1024)
   if not b:break
   zh.update(b);last=pos+len(b);l=max(pos,start);r=min(last,end)
   if l<r:ph.update(b[l-pos:r-pos])
   if pos<signed:verifier.stdin.write(b[:min(len(b),signed-pos)])
   pos=last
 verifier.stdin.close();status=verifier.wait()
r={'archive_bytes':size,'sha256':zh.hexdigest(),'ota_signature_valid':status==0,'signer_matches_phone_ota_trust':True,'payload_hash_matches':base64.b64encode(ph.digest()).decode()==props['FILE_HASH'],'download_hash_matches':zh.hexdigest()==json.loads((w/'download-result.json').read_text())['sha256']}
(w/'verification.json').write_text(json.dumps(r,indent=2));print(json.dumps(r),flush=True);assert all(r[k] for k in ['ota_signature_valid','signer_matches_phone_ota_trust','payload_hash_matches','download_hash_matches'])
(w/'images').mkdir(exist_ok=True)
with (w/'partition-extraction.log').open('wb') as log:subprocess.run(['payload-dumper-go','-c','4','-p','odm,vendor','-o',str(w/'images'),str(archive)],stdout=log,stderr=subprocess.STDOUT,check=True)
print('Verified and extracted ODM/vendor images',flush=True)
