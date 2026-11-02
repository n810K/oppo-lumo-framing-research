from pathlib import Path
import subprocess,os,zipfile,json,hashlib
r=Path.cwd();w=r/'work/apk-mod-research';j=Path('/opt/homebrew/opt/openjdk@21');bt=Path('/PATH/TO/HOME/Library/Android/sdk/build-tools/36.0.0');a=Path('/PATH/TO/HOME/Library/Android/sdk/platforms/android-37.0/android.jar');lib=Path('/PATH/TO/HOME/.local/share/rea/jadx-headless-mcp-0.7.1-all.jar');env=dict(os.environ,JAVA_HOME=str(j))
for n in ['patcher','helper','dex']:(w/n).mkdir(exist_ok=True)
base=r/'outputs/Camera-config-timing-test.apk'
subprocess.run([str(j/'bin/javac'),'-source','8','-target','8','-cp',str(a),'-d',str(w/'helper'),str(w/'CameraMetadataTest.java')],check=True)
subprocess.run([str(bt/'d8'),'--lib',str(a),'--output',str(w/'dex'),*map(str,(w/'helper').rglob('*.class'))],env=env,check=True)
with zipfile.ZipFile(base) as src,zipfile.ZipFile(w/'metadata-unsigned.apk','w') as dst:
 for i in src.infolist():
  if i.filename.startswith('META-INF/') and (i.filename.endswith(('.RSA','.DSA','.EC','.SF')) or i.filename=='META-INF/MANIFEST.MF'):continue
  dst.writestr(i,(w/'classes35.dex').read_bytes() if i.filename=='classes35.dex' else (w/'classes30.dex').read_bytes() if i.filename=='classes30.dex' else (w/'classes8.dex').read_bytes() if i.filename=='classes8.dex' else (w/'classes28.dex').read_bytes() if i.filename=='classes28.dex' else (w/'classes12.dex').read_bytes() if i.filename=='classes12.dex' else (w/'classes23.dex').read_bytes() if i.filename=='classes23.dex' else src.read(i.filename))
 dst.writestr('classes38.dex',(w/'dex/classes.dex').read_bytes())
 dst.writestr('lib/arm64-v8a/libCameraCallbackRepair.so',(w/'agent/libCameraCallbackRepair.so').read_bytes())
 for donor in sorted((w/'donor-native').glob('*.so')):
  name='lib/arm64-v8a/'+donor.name
  assert name not in src.namelist(),name
  dst.writestr(name,donor.read_bytes())
subprocess.run([str(bt/'zipalign'),'-P','16','-f','4',str(w/'metadata-unsigned.apk'),str(w/'metadata-aligned.apk')],check=True)
out=r/'outputs/Camera-donor-adapter-test.apk'
subprocess.run([str(bt/'apksigner'),'sign','--ks',str(r/'work/camera-composition-lab.p12'),'--ks-key-alias','camera-composition-lab','--ks-pass','file:'+str(r/'work/lab-signing-password.txt'),'--v4-signing-enabled','false','--out',str(out),str(w/'metadata-aligned.apk')],env=env,check=True)
subprocess.run([str(bt/'apksigner'),'verify',str(out)],env=env,check=True)
subprocess.run([str(bt/'zipalign'),'-c','-P','16','4',str(out)],check=True)
with zipfile.ZipFile(base) as src,zipfile.ZipFile(out) as dst:
 changed=[n for n in src.namelist() if not n.startswith('META-INF/') and src.read(n)!=dst.read(n)];assert set(changed)=={'classes8.dex','classes12.dex','classes23.dex','classes28.dex','classes30.dex','classes35.dex'},changed
 added=[n for n in dst.namelist() if n not in src.namelist() and not n.startswith('META-INF/')];assert set(added)=={'classes38.dex','lib/arm64-v8a/libCameraCallbackRepair.so'}|{'lib/arm64-v8a/'+p.name for p in (w/'donor-native').glob('*.so')},added
 assert src.read('AndroidManifest.xml')==dst.read('AndroidManifest.xml')
result={'base':str(base),'output':str(out),'base_sha256':hashlib.sha256(base.read_bytes()).hexdigest(),'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'changed_existing_entries':changed,'added_entries':added,'manifest_resources_and_existing_native_libraries_unchanged':True,'signature_and_zip_alignment_verified':True,'device_tested':False,'installed':False,'reason_not_tested':'Pending standalone launch'}
(w/'build-verification.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
