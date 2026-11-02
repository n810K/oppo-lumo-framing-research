from pathlib import Path
import subprocess,time,json
w=Path('work/apk-mod-research');a=['adb','-s','YOUR_ADB_SERIAL'];pkg='com.n810.camera.aicomposition.lab'
with (w/'candidate-live-log.txt').open('wb') as f:
 logger=subprocess.Popen(a+['logcat','-T','1','-v','threadtime'],stdout=f,stderr=subprocess.STDOUT)
 try:
  launch=subprocess.check_output(a+['shell','am','start','-S','--attach-agent','/data/user/0/com.n810.camera.aicomposition.lab/files/libCameraExceptionTrace.so','-n',pkg+'/com.oplus.camera.Camera'],text=True);(w/'candidate-launch.txt').write_text(launch)
  time.sleep(12)
  pid=subprocess.check_output(a+['shell','pidof',pkg],text=True).strip();(w/'candidate-pid.txt').write_text(pid)
  for file,cmd in [('candidate-camera.txt',['shell','dumpsys','media.camera']),('candidate-app-log.txt',['logcat','-d','--pid='+pid,'-v','threadtime']),('candidate-preview.png',['exec-out','screencap','-p'])]:
   (w/file).write_bytes(subprocess.check_output(a+cmd))
  subprocess.run(a+['shell','uiautomator','dump','/data/local/tmp/n810-camera-mod-ui.xml'],check=True,stdout=subprocess.DEVNULL)
  (w/'candidate-ui.xml').write_bytes(subprocess.check_output(a+['exec-out','cat','/data/local/tmp/n810-camera-mod-ui.xml']))
 finally:logger.terminate();logger.wait()
print('Candidate startup evidence captured.')
