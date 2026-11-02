from pathlib import Path
import subprocess,os,zipfile,json,hashlib
r=Path.cwd();w=r/'work/apk-mod-research';j=Path('/opt/homebrew/opt/openjdk@21');bt=Path('/PATH/TO/HOME/Library/Android/sdk/build-tools/36.0.0');a=Path('/PATH/TO/HOME/Library/Android/sdk/platforms/android-37.0/android.jar');lib=Path('/PATH/TO/HOME/.local/share/rea/jadx-headless-mcp-0.7.1-all.jar');env=dict(os.environ,JAVA_HOME=str(j))
for n in ['patcher','helper','dex']:(w/n).mkdir(exist_ok=True)
base=r/'outputs/Camera-config-timing-test.apk'
with zipfile.ZipFile(base) as z:
 assert 'classes37.dex' in z.namelist() and 'classes38.dex' not in z.namelist()
 (w/'classes35.dex').write_bytes(z.read('classes35.dex'))
subprocess.run([str(j/'bin/javac'),'-cp',str(lib),'-d',str(w/'patcher'),str(w/'PatchCameraMetadata.java')],check=True)
subprocess.run([str(j/'bin/java'),'-cp',str(w/'patcher')+os.pathsep+str(lib),'PatchCameraMetadata',str(w/'classes35.dex')],check=True)
subprocess.run([str(j/'bin/javac'),'-source','8','-target','8','-cp',str(a),'-d',str(w/'helper'),str(w/'CameraMetadataTest.java')],check=True)
subprocess.run([str(bt/'d8'),'--lib',str(a),'--output',str(w/'dex'),*map(str,(w/'helper').rglob('*.class'))],env=env,check=True)
with zipfile.ZipFile(base) as src,zipfile.ZipFile(w/'metadata-unsigned.apk','w') as dst:
 for i in src.infolist():
  if i.filename.startswith('META-INF/') and (i.filename.endswith(('.RSA','.DSA','.EC','.SF')) or i.filename=='META-INF/MANIFEST.MF'):continue
  dst.writestr(i,(w/'classes35.dex').read_bytes() if i.filename=='classes35.dex' else src.read(i.filename))
 dst.writestr('classes38.dex',(w/'dex/classes.dex').read_bytes())
subprocess.run([str(bt/'zipalign'),'-P','16','-f','4',str(w/'metadata-unsigned.apk'),str(w/'metadata-aligned.apk')],check=True)
out=r/'outputs/Camera-metadata-test.apk'
subprocess.run([str(bt/'apksigner'),'sign','--ks',str(r/'work/camera-composition-lab.p12'),'--ks-key-alias','camera-composition-lab','--ks-pass','file:'+str(r/'work/lab-signing-password.txt'),'--v4-signing-enabled','false','--out',str(out),str(w/'metadata-aligned.apk')],env=env,check=True)
subprocess.run([str(bt/'apksigner'),'verify',str(out)],env=env,check=True)
subprocess.run([str(bt/'zipalign'),'-c','-P','16','4',str(out)],check=True)
with zipfile.ZipFile(base) as src,zipfile.ZipFile(out) as dst:
 changed=[n for n in src.namelist() if not n.startswith('META-INF/') and src.read(n)!=dst.read(n)];assert changed==['classes35.dex'],changed
 added=[n for n in dst.namelist() if n not in src.namelist() and not n.startswith('META-INF/')];assert added==['classes38.dex'],added
 assert src.read('AndroidManifest.xml')==dst.read('AndroidManifest.xml')
result={'base':str(base),'output':str(out),'base_sha256':hashlib.sha256(base.read_bytes()).hexdigest(),'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'changed_existing_entries':changed,'added_entries':added,'manifest_resources_and_native_libraries_unchanged':True,'signature_and_zip_alignment_verified':True,'device_tested':False,'installed':False,'reason_not_tested':'No device visible to adb devices during research on November 2, 2026'}
(w/'build-verification.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
