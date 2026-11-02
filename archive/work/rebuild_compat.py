from pathlib import Path
import subprocess, os, shutil, sys
java='/opt/homebrew/opt/openjdk@21'
bt='/PATH/TO/HOME/Library/Android/sdk/build-tools/36.0.0/'
jar='/PATH/TO/HOME/Library/Android/sdk/platforms/android-37.0/android.jar'
env=dict(os.environ,JAVA_HOME=java)
dependencies=[str(p) for p in Path('work/shizuku-api').glob('*.jar')]
sources=[str(p) for p in Path('work/compat-src').rglob('*.java')]
subprocess.run([java+'/bin/javac','-source','8','-target','8','-cp',os.pathsep.join([jar]+dependencies),'-d','work/compat-classes',*sources],check=True)
classes=[str(p) for p in Path('work/compat-classes').rglob('*.class')]
subprocess.run([bt+'d8','--lib',jar,'--output','work/compat-dex',*classes,*dependencies],env=env,check=True)
shutil.copyfile('work/compat-dex/classes.dex','work/clone-decoded/classes35.dex')
subprocess.run([sys.executable,'work/build_camera.py',sys.argv[1]],check=True)
