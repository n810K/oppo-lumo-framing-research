from pathlib import Path
import subprocess,xml.etree.ElementTree as E,re,time
A=['adb','-s','YOUR_ADB_SERIAL'];w=Path('work/apk-mod-research')
def tree():
 subprocess.run(A+['shell','uiautomator','dump','/data/local/tmp/n810-camera-mod-ui.xml'],stdout=subprocess.DEVNULL,check=True)
 return E.fromstring(subprocess.check_output(A+['exec-out','cat','/data/local/tmp/n810-camera-mod-ui.xml']))
def tap(n):
 b=list(map(int,re.findall(r'\d+',n.get('bounds'))));subprocess.run(A+['shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2)],check=True)
r=tree()
for n in r.iter('node'):
 if n.get('text')=='OK':tap(n);r=tree();break
with (w/'donor-composition-live.txt').open('wb') as f:
 logger=subprocess.Popen(A+['logcat','-T','1','-v','threadtime'],stdout=f,stderr=subprocess.STDOUT)
 try:
  found=False
  for n in r.iter('node'):
   if n.get('resource-id','').endswith('/ai_composition_switch_button'):tap(n);found=True;break
  print('Composition button tapped:',found)
  time.sleep(15)
  (w/'donor-composition.png').write_bytes(subprocess.check_output(A+['exec-out','screencap','-p']))
 finally:logger.terminate();logger.wait()
