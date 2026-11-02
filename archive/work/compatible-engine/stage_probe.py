from pathlib import Path
import subprocess,tarfile,io,json,hashlib
w=Path('work/compatible-engine');a=['adb','-s','YOUR_ADB_SERIAL'];pkg='com.n810.camera.guideprobe'
r=subprocess.run(a+['shell','pm','path','com.oplus.camera'],capture_output=True,text=True,check=True);active=r.stdout.strip().removeprefix('package:')
paths=[active,'/product/app/OplusCamera/OplusCamera.apk','/odm/etc/camera/config/oplus_camera_config']
r=subprocess.run(a+['shell','sha256sum',*paths],capture_output=True,text=True,check=True);(w/'phone-original-hashes-before.txt').write_text(r.stdout)
subprocess.run(a+['shell','am','force-stop',pkg],check=True)
subprocess.run(a+['install','-r','outputs/Camera-engine-X10-dx5-test.apk'],check=True)
p=subprocess.Popen(a+['exec-in','run-as',pkg,'tar','-x','-C','files'],stdin=subprocess.PIPE)
config=(w/'extracted/models/composition_lite2_config.yaml').read_text().replace('/odm/etc/camera/ai_composition/','/data/user/0/'+pkg+'/files/models-cph2911/')
with tarfile.open(fileobj=p.stdin,mode='w|') as t:
 for f in sorted((w/'extracted/models').iterdir()):
  name='models-cph2911/'+f.name
  if f.name=='composition_lite2_config.yaml':
   b=config.encode();i=tarfile.TarInfo(name);i.size=len(b);i.mode=0o600;t.addfile(i,io.BytesIO(b))
  else:t.add(f,arcname=name)
p.stdin.close();assert p.wait()==0
r=subprocess.run(a+['shell','run-as',pkg,'sh','-c','"sha256sum files/models-cph2911/*"'],capture_output=True,text=True,check=True);(w/'staged-model-hashes.txt').write_text(r.stdout)
print('Installed only the separate diagnostic app; staged candidate models in a new private directory.')
