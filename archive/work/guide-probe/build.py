from pathlib import Path
import os
import subprocess
import zipfile
import sys

root = Path(__file__).resolve().parent
project = root.parent.parent
java = Path('/opt/homebrew/opt/openjdk@21')
tools = Path('/PATH/TO/HOME/Library/Android/sdk/build-tools/36.0.0')
android = Path('/PATH/TO/HOME/Library/Android/sdk/platforms/android-37.0/android.jar')
env = dict(os.environ, JAVA_HOME=str(java))
version = int(sys.argv[1]) if len(sys.argv) > 1 else 3
for d in ['classes', 'dex', 'build']:
    (root / d).mkdir(exist_ok=True)
subprocess.run([str(java / 'bin/javac'), '-source', '8', '-target', '8', '-cp', str(android),
                '-d', str(root / 'classes'), *map(str, (root / 'src').rglob('*.java'))], check=True)
subprocess.run([str(tools / 'd8'), '--lib', str(android), '--output', str(root / 'dex'),
                *map(str, (root / 'classes').rglob('*.class'))], env=env, check=True)
unsigned = root / 'build/unsigned.apk'
subprocess.run([str(tools / 'aapt2'), 'link', '-I', str(android), '--manifest', str(root / 'AndroidManifest.xml'),
                '--version-code', str(version), '--version-name', f'0.{version}-engine-test', '-o', str(unsigned)], check=True)
with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as z:
    z.write(root / 'dex/classes.dex', 'classes.dex')
    for f in sorted((project / 'work/firmware-donor/extracted/odm/lib64').glob('*.so')):
        z.write(f, 'lib/arm64-v8a/' + f.name)
    for f in sorted((root / 'native').glob('*.so')):
        z.write(f, 'lib/arm64-v8a/' + f.name)
aligned = root / 'build/aligned.apk'
subprocess.run([str(tools / 'zipalign'), '-P', '16', '-f', '4', str(unsigned), str(aligned)], check=True)
output = project / 'outputs/n810-Camera-engine-test.apk'
subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(project / 'work/camera-composition-lab.p12'),
                '--ks-key-alias', 'camera-composition-lab', '--ks-pass', 'file:' + str(project / 'work/lab-signing-password.txt'),
                '--v4-signing-enabled', 'false', '--out', str(output), str(aligned)], env=env, check=True)
subprocess.run([str(tools / 'apksigner'), 'verify', str(output)], env=env, check=True)
subprocess.run([str(tools / 'zipalign'), '-c', '-P', '16', '4', str(output)], check=True)
badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(output)], text=True)
assert "package: name='com.n810.camera.guideprobe'" in badging
permissions = subprocess.check_output([str(tools / 'aapt'), 'dump', 'permissions', str(output)], text=True)
requested = [line.strip() for line in permissions.splitlines() if 'uses-permission' in line]
assert requested == ["uses-permission: name='mediatek.permission.ACCESS_APU_SYS'"], permissions
print('Built and verified engine test with only normal APU permission:', output)
