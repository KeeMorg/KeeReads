#!/usr/bin/env python3
"""Offline project structural checks; Android compilation runs in GitHub Actions."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re, subprocess, sys, yaml
root=Path(__file__).resolve().parents[1]
java=sorted((root/'app/src/main/java').rglob('*.java'))
assert len(java)==3, [str(p) for p in java]
assert set(p.name for p in java)=={'MainActivity.java','Annotations.java','PdfSurface.java'}
for xml in (root/'app/src/main').rglob('*.xml'):
    ET.parse(xml)
    print('PASS XML:',xml.relative_to(root))
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
app=manifest.find('application')
assert app is not None
activity=app.find('activity')
assert activity is not None and activity.get(ns+'exported')=='true'
assert not manifest.findall('uses-permission'), 'Offline prototype should not request storage/internet permissions'
appgradle=(root/'app/build.gradle').read_text()
assert 'compileSdk 35' in appgradle
assert 'minSdk 24' in appgradle
assert 'targetSdk 35' in appgradle
assert 'id \'com.android.application\'' in (root/'build.gradle').read_text()
assert 'include \':app\'' in (root/'settings.gradle').read_text()
source={p.name:p.read_text() for p in java}
assert 'try(ParcelFileDescriptor fd' in source['MainActivity.java']
assert 'PdfDocument out=new PdfDocument();' in source['MainActivity.java']
assert 'try (JSONException ignored)' not in source['Annotations.java']
assert 'catch (JSONException ignored)' in source['Annotations.java']
assert 'pageRenderWorker.execute' in source['PdfSurface.java']
assert 'android.os.Build.VERSION.SDK_INT>=35' in source['PdfSurface.java']
workflow=(root/'.github/workflows/build-apk.yml').read_text()
with (root/'.github/workflows/build-apk.yml').open() as f: data=yaml.safe_load(f)
assert data.get('jobs',{}).get('build',{}).get('runs-on')=='ubuntu-24.04'
assert 'setup-android@v4' in workflow and 'setup-java@v5' in workflow
assert 'gradle --no-daemon :app:assembleDebug' in workflow
assert 'app/build/outputs/apk/debug/app-debug.apk' in workflow
assert not (root/'gradlew').exists(), 'Must document wrapper absence, not pretend it is installed'
for script in root.glob('*.sh'):
    subprocess.run(['bash','-n',str(script)],check=True)
proc=subprocess.run(['java','-cp',str(root/'qa'),'SyntaxCheck',*(str(p) for p in java)],text=True,capture_output=True)
assert proc.returncode==0,(proc.stdout,proc.stderr)
print(proc.stdout,end='')
features=(root/'FEATURES.md').read_text()
assert len(re.findall(r'^\|\s*\d+\s*\|',features,re.M))==43
print('PASS: Manifest, XML, Gradle settings, workflow, shell scripts, 43-feature inventory, source structure.')
print('NOTE: Android bytecode compilation, lint, installation and device testing require GitHub Actions/SDK.')
