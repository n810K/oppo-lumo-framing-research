from pathlib import Path
import subprocess, os, sys

version=sys.argv[1]
assert version.startswith('7.006.125-lab') and version.removeprefix('7.006.125-lab').isdigit()
p=Path('work/clone-decoded/apktool.yml')
lines=p.read_text().splitlines()
p.write_text('\n'.join('  versionName: '+version if x.startswith('  versionName:') else x for x in lines)+'\n')
Path('work/clone-decoded/AndroidManifest.xml').touch()
env=dict(os.environ,JAVA_HOME='/opt/homebrew/opt/openjdk@21')
bt='/PATH/TO/HOME/Library/Android/sdk/build-tools/36.0.0/'
with open('work/build-'+version+'.log','w') as log:
    subprocess.run(['apktool','b','-p','work/apktool-framework','-o','work/n810-Camera-unsigned.apk','work/clone-decoded'],env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
subprocess.run([bt+'zipalign','-P','16','-f','4','work/n810-Camera-unsigned.apk','work/n810-Camera-aligned.apk'],check=True)
subprocess.run([bt+'apksigner','sign','--ks','work/camera-composition-lab.p12','--ks-key-alias','camera-composition-lab','--ks-pass','file:work/lab-signing-password.txt','--v4-signing-enabled','false','--out','outputs/n810-Camera.apk','work/n810-Camera-aligned.apk'],env=env,check=True)
subprocess.run([bt+'apksigner','verify','outputs/n810-Camera.apk'],env=env,check=True)
subprocess.run([bt+'zipalign','-c','-P','16','4','outputs/n810-Camera.apk'],check=True)
badging=subprocess.check_output([bt+'aapt','dump','badging','outputs/n810-Camera.apk'],text=True)
assert "application-label:'Camera'" in badging
assert "package: name='com.n810.camera.aicomposition.lab'" in badging
assert "versionName='"+version+"'" in badging
print('Built and verified '+version,flush=True)
