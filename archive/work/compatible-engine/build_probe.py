from pathlib import Path
import shutil,subprocess,os,zipfile,json
r=Path.cwd();w=r/'work/compatible-engine/probe';w.mkdir(exist_ok=True)
shutil.copytree(r/'work/inference-trace/apu-info/src',w/'src',dirs_exist_ok=True)
p=w/'src/com/n810/camera/guideprobe/ProbeActivity.java';s=p.read_text();assert 'models/composition_lite2_config.yaml' in s;p.write_text(s.replace('models/composition_lite2_config.yaml','models-cph2911/composition_lite2_config.yaml'))
j=Path('/opt/homebrew/opt/openjdk@21');t=Path('/PATH/TO/HOME/Library/Android/sdk/build-tools/36.0.0');a=Path('/PATH/TO/HOME/Library/Android/sdk/platforms/android-37.0/android.jar');env=dict(os.environ,JAVA_HOME=str(j))
for d in ['classes','dex']:(w/d).mkdir(exist_ok=True)
subprocess.run([str(j/'bin/javac'),'-source','8','-target','8','-cp',str(a),'-d',str(w/'classes'),*map(str,(w/'src').rglob('*.java'))],check=True)
subprocess.run([str(t/'d8'),'--lib',str(a),'--output',str(w/'dex'),*map(str,(w/'classes').rglob('*.class'))],env=env,check=True)
base=r/'outputs/Camera-engine-diagnostic.apk';native=r/'work/compatible-engine/extracted/native';changes=[]
with zipfile.ZipFile(base) as z,zipfile.ZipFile(w/'unsigned.apk','w') as o:
 for i in z.infolist():
  if i.filename.startswith('META-INF/'):continue
  b=z.read(i.filename);n=Path(i.filename).name
  if i.filename=='classes.dex':b=(w/'dex/classes.dex').read_bytes()
  elif i.filename.startswith('lib/arm64-v8a/'):
   p=native/('libneuron_runtime.9.so' if n=='libneuron_runtime.so' else n)
   if p.is_file():b=p.read_bytes()
  if b!=z.read(i.filename):changes.append(i.filename)
  o.writestr(i,b)
subprocess.run([str(t/'zipalign'),'-P','16','-f','4',str(w/'unsigned.apk'),str(w/'aligned.apk')],check=True)
out=r/'outputs/Camera-engine-X10-dx5-test.apk'
subprocess.run([str(t/'apksigner'),'sign','--ks',str(r/'work/camera-composition-lab.p12'),'--ks-key-alias','camera-composition-lab','--ks-pass','file:'+str(r/'work/lab-signing-password.txt'),'--v4-signing-enabled','false','--out',str(out),str(w/'aligned.apk')],env=env,check=True)
subprocess.run([str(t/'apksigner'),'verify',str(out)],env=env,check=True)
with zipfile.ZipFile(base) as z,zipfile.ZipFile(out) as o:assert z.read('AndroidManifest.xml')==o.read('AndroidManifest.xml')
(w/'build.json').write_text(json.dumps({'changed_entries':changes,'manifest_unchanged':True,'output':str(out),'models_private_directory':'models-cph2911'},indent=2))
print('Built signed separate diagnostic APK; manifest unchanged.')
