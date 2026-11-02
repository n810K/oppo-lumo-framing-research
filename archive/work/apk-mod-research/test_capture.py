from pathlib import Path
import subprocess,time,xml.etree.ElementTree as E,re,shlex
w=Path('work/apk-mod-research/capture-followup');w.mkdir(exist_ok=True)
a=['adb','-s','YOUR_ADB_SERIAL'];pkg='com.n810.camera.aicomposition.lab'
def ui():
 subprocess.run(a+['shell','uiautomator','dump','/data/local/tmp/n810-camera-mod-ui.xml'],check=True,stdout=subprocess.DEVNULL)
 return E.fromstring(subprocess.check_output(a+['exec-out','cat','/data/local/tmp/n810-camera-mod-ui.xml']))
def tap(n):
 x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));subprocess.run(a+['shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
root=ui()
for n in root.iter('node'):
 if n.get('text')=='OK' and n.get('package')=='android':tap(n);root=ui();break
node=next(n for n in root.iter('node') if n.get('resource-id')==pkg+':id/shutter_button')
start=int(subprocess.check_output(a+['shell','date','+%s'],text=True).strip())
with (w/'capture-log.txt').open('wb') as f:
 logger=subprocess.Popen(a+['logcat','-T','1','-v','threadtime'],stdout=f,stderr=subprocess.STDOUT)
 try:
  tap(node);time.sleep(12)
  q=['content','query','--uri','content://media/external/images/media','--projection','_id:_display_name:date_added:relative_path:owner_package_name','--where','date_added>='+str(start)]
  (w/'new-images.txt').write_bytes(subprocess.check_output(a+['shell',shlex.join(q)],stderr=subprocess.STDOUT))
  (w/'capture-screen.png').write_bytes(subprocess.check_output(a+['exec-out','screencap','-p']))
  (w/'pid.txt').write_bytes(subprocess.run(a+['shell','pidof',pkg],capture_output=True).stdout)
 finally:logger.terminate();logger.wait()
print('One capture attempt completed; check logs and MediaStore for actual result.')
