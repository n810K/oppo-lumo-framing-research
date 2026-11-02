from pathlib import Path
import os,subprocess,zipfile,json,sys
r=Path.cwd();w=r/'work/liveguide-app';j=Path('/opt/homebrew/opt/openjdk@21');t=Path('/PATH/TO/HOME/Library/Android/sdk/build-tools/36.0.0');a=Path('/PATH/TO/HOME/Library/Android/sdk/platforms/android-37.0/android.jar');env=dict(os.environ,JAVA_HOME=str(j))
for d in ['classes','dex','build']:(w/d).mkdir(exist_ok=True)
subprocess.run([str(j/'bin/javac'),'-source','8','-target','8','-cp',str(a),'-d',str(w/'classes'),*map(str,(w/'src').rglob('*.java'))],check=True)
subprocess.run([str(t/'d8'),'--lib',str(a),'--output',str(w/'dex'),*map(str,(w/'classes').rglob('*.class'))],env=env,check=True)
subprocess.run([str(t/'aapt2'),'compile','--dir',str(w/'res'),'-o',str(w/'build/resources.zip')],check=True)
subprocess.run([str(t/'aapt2'),'link','-I',str(a),'--manifest',str(w/'AndroidManifest.xml'),'--version-code','2','--version-name','0.2-live-guide','-o',str(w/'build/unsigned.apk'),str(w/'build/resources.zip')],check=True)
with zipfile.ZipFile(w/'build/unsigned.apk','a',compression=zipfile.ZIP_STORED) as z,zipfile.ZipFile(r/'outputs/Camera-engine-X10-dx5-test.apk') as native:
 z.write(w/'dex/classes.dex','classes.dex')
 for n in native.namelist():
  if n.startswith('lib/') and n.endswith('.so'):z.writestr(n,native.read(n))
 for p in (r/'work/compatible-engine/extracted/models').iterdir():z.write(p,'assets/models/'+p.name)
subprocess.run([str(t/'zipalign'),'-P','16','-f','4',str(w/'build/unsigned.apk'),str(w/'build/aligned.apk')],check=True)
out=r/'outputs/Camera-Live-guide.apk'
subprocess.run([str(t/'apksigner'),'sign','--ks',str(r/'work/camera-composition-lab.p12'),'--ks-key-alias','camera-composition-lab','--ks-pass','file:'+str(r/'work/lab-signing-password.txt'),'--v4-signing-enabled','false','--out',str(out),str(w/'build/aligned.apk')],env=env,check=True)
subprocess.run([str(t/'apksigner'),'verify',str(out)],env=env,check=True)
subprocess.run([str(t/'zipalign'),'-c','-P','16','4',str(out)],check=True)
badging=subprocess.check_output([str(t/'aapt'),'dump','badging',str(out)],text=True);assert "package: name='com.n810.camera.liveguide'" in badging
permissions=subprocess.check_output([str(t/'aapt'),'dump','permissions',str(out)],text=True);assert 'android.permission.INTERNET' not in permissions
(w/'build/permissions.txt').write_text(permissions);print(out,out.stat().st_size)
