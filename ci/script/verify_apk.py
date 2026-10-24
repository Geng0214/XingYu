"""Verify the actual APK and merged manifest, without printing credentials or private paths."""
import argparse,hashlib,json,pathlib,re,struct,subprocess,zipfile,xml.etree.ElementTree as ET
repo=pathlib.Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk',nargs='?',type=pathlib.Path,default=repo/'build/app/outputs/apk/release/app-release.apk')
parser.add_argument('--version-code',default='41')
parser.add_argument('--version-name',default='0.40.0')
parser.add_argument('--app-name',default='星语')
parser.add_argument('--stage',default='E17_FEATURE_PHASE1')
parser.add_argument('--report',default='apk-audit.json')
parser.add_argument('--aapt2',type=pathlib.Path,help='Android Build Tools 36 aapt2; otherwise read configured sdk.dir')
args=parser.parse_args()
assert re.fullmatch(r'[A-Z0-9_-]+',args.stage)
assert re.fullmatch(r'[a-z0-9-]+\.json',args.report)
apk=args.apk
manifest=repo/'build/app/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml'
root=ET.parse(manifest).getroot();a='{http://schemas.android.com/apk/res/android}'
assert root.attrib['package']=='cn.localfirst.companion'
assert root.get(a+'versionCode')==args.version_code and root.get(a+'versionName')==args.version_name
application=root.find('application')
assert application.get(a+'allowBackup')=='false'
assert application.get(a+'usesCleartextTraffic')=='false'
assert application.get(a+'debuggable') not in ('true','1')
assert application.get(a+'icon')=='@mipmap/ic_launcher'
assert application.get(a+'roundIcon')=='@mipmap/ic_launcher'
assert not application.findall('provider')
services=application.findall('service');assert len(services)==1
assert services[0].get(a+'name')=='com.ai.assistance.operit.proactive.ProactiveJobService'
assert services[0].get(a+'exported')=='false' and services[0].get(a+'permission')=='android.permission.BIND_JOB_SERVICE'
receivers=application.findall('receiver');assert len(receivers)==2
assert {r.get(a+'name') for r in receivers}=={'com.ai.assistance.operit.reminder.PlannerReminderReceiver','com.ai.assistance.operit.reminder.ScheduledMessageReceiver'}
for receiver in receivers:
    assert receiver.get(a+'exported')=='false'
    assert 'android.intent.action.BOOT_COMPLETED' in {i.get(a+'name') for i in receiver.findall('intent-filter/action')}
activities=application.findall('activity');assert len(activities)==1
permissions=[x.get(a+'name') for x in root.findall('uses-permission')]
expected_permissions={'android.permission.INTERNET','cn.localfirst.companion.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
if int(args.version_code)>=6:expected_permissions.add('android.permission.USE_BIOMETRIC')
if int(args.version_code)>=24:expected_permissions.update({'android.permission.POST_NOTIFICATIONS','android.permission.RECEIVE_BOOT_COMPLETED'})
if int(args.version_code)>=25:expected_permissions.add('android.permission.ACCESS_NETWORK_STATE')
if int(args.version_code)>=26:expected_permissions.add('android.permission.RECORD_AUDIO')
if int(args.version_code)>=34:expected_permissions.add('android.permission.MODIFY_AUDIO_SETTINGS')
assert set(permissions)==expected_permissions
# JNI method lookups use original names even when the enclosing Kotlin code has no direct call.
mapping=(repo/'build/app/outputs/mapping/release/mapping.txt').read_text(encoding='utf-8')
for name in ['GodotIO','nativeapi.GodotNativeBridge','io.file.FileAccessHandler']:
    full='org.godotengine.godot.'+name
    assert full+' -> '+full+':' in mapping,'Godot JNI class was renamed: '+name
# Inspect the manifest inside the supplied APK as well as Gradle's merged source manifest.
aapt2=args.aapt2
if aapt2 is None:
    sdk=next(line.split('=',1)[1].strip() for line in (repo/'local.properties').read_text().splitlines() if line.startswith('sdk.dir='))
    aapt2=pathlib.Path(sdk.replace('\\:',':').replace('\\\\','\\'))/'build-tools/36.0.0/aapt2.exe'
tree=subprocess.check_output([str(aapt2),'dump','xmltree',str(apk),'--file','AndroidManifest.xml'],text=True,encoding='utf-8')
badging=subprocess.check_output([str(aapt2),'dump','badging',str(apk)],text=True,encoding='utf-8')
assert "application-label:'"+args.app_name+"'" in badging,'Incorrect app display name'
tags=re.findall(r'^\s*E: (\S+)',tree,re.M)
assert tags.count('application')==tags.count('activity')==1
assert tags.count('receiver')==2 and tags.count('service')==1 and 'provider' not in tags
assert 'PlannerReminderReceiver' in tree and 'ScheduledMessageReceiver' in tree
assert 'ProactiveJobService' in tree and 'android.permission.BIND_JOB_SERVICE' in tree
assert 'A: package="cn.localfirst.companion"' in tree
for attr,value in [('versionCode',args.version_code),('versionName','"'+args.version_name+'"'),('allowBackup','false'),('fullBackupContent','false'),('usesCleartextTraffic','false')]:
    assert re.search(r':'+attr+r'\(0x[0-9a-f]+\)='+re.escape(value)+r'(?:\s|$)',tree),attr
assert not re.search(r':debuggable\([^)]*\)=true',tree)
actual_permissions=re.findall(r'E: uses-permission [^\n]*\n\s+A: [^\n]*:name\([^)]*\)="([^"]+)"',tree)
assert set(actual_permissions)==expected_permissions
with zipfile.ZipFile(apk) as archive:
    names=archive.namelist()
    manifest_hash=hashlib.sha256(archive.read('AndroidManifest.xml')).hexdigest()
    assert not any('AppDatabase/' in n or 'androidTest' in n for n in names)
    forbidden=[n for n in names if re.search(r'(?i)\.(jks|keystore|p12|pkcs12|pem|key|apk|zip|aar|onnx)$',n)]
    assert not forbidden,forbidden
    assert 'assets/THIRD-PARTY-NOTICES.txt' in names
    assert archive.read('assets/THIRD-PARTY-NOTICES.txt')==(repo/'app/src/main/assets/THIRD-PARTY-NOTICES.txt').read_bytes()
    if int(args.version_code)>=39:
        # Resource shrinking renames files; resolve the shipped resource table instead of trusting source filenames.
        resources=subprocess.check_output([str(aapt2),'dump','resources',str(apk)],text=True,encoding='utf-8')
        atlas=re.findall(r'drawable/adventure_atlas\s*\n\s*\(nodpi\) \(file\) (res/\S+) type=PNG',resources)
        assert len(atlas)==1,'Missing adventure map atlas'
        atlas_bytes=archive.read(atlas[0])
        assert atlas_bytes[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',atlas_bytes[16:24])==(1254,1254)
        brand_art=re.findall(r'drawable/ic_xingyu_art\s*\n\s*\(nodpi\) \(file\) (res/\S+)',resources)
        assert len(brand_art)==1,'Missing app icon artwork'
        assert archive.read(brand_art[0])==(repo/'app/src/main/res/drawable-nodpi/ic_xingyu_art.webp').read_bytes(),'Incorrect app icon artwork'
    if int(args.version_code)>=34:
        for name in ['bilibili-audio.js','bilibili-audio-worklet.js']:
            assert archive.read('assets/'+name)==(repo/'app/src/main/assets'/name).read_bytes()
    builtin=['manifest.json','main.js','packages/time.js']
    assert {n for n in names if n.startswith('assets/toolpkg/')}=={'assets/toolpkg/time/'+n for n in builtin}
    for n in builtin:
        assert archive.read('assets/toolpkg/time/'+n)==(repo/'app/src/main/assets/toolpkg/time'/n).read_bytes()
    natives=[n for n in names if n.endswith('.so')]
    assert set(natives)=={'lib/arm64-v8a/libsqlcipher.so','lib/arm64-v8a/libandroidx.graphics.path.so','lib/arm64-v8a/libquickjsjni.so','lib/arm64-v8a/libgodot_android.so','lib/arm64-v8a/libc++_shared.so'}
    assert archive.read('assets/home.pck')==(repo/'build/generated/home-assets/home.pck').read_bytes(),'Incorrect Godot scene pack'
    assert b'Godot Engine 4.7.2' in archive.read('assets/THIRD-PARTY-NOTICES.txt')
    native_reports=[]
    for n in natives:
        data=archive.read(n)
        hits=len(re.findall(rb'[A-Za-z]:[\\/](?:Users|M0)[\\/]',data))
        assert hits==0
        # Verify ELF PT_LOAD alignment required by 16 KB Android devices.
        assert data[:5]==b'\x7fELF\x02'
        phoff=struct.unpack_from('<Q',data,32)[0];entry_size,count=struct.unpack_from('<HH',data,54)
        alignments=[]
        for i in range(count):
            off=phoff+i*entry_size
            if struct.unpack_from('<I',data,off)[0]==1:alignments.append(struct.unpack_from('<Q',data,off+48)[0])
        assert min(alignments)>=16384,(n,alignments)
        native_reports.append({'name':n,'sha256':hashlib.sha256(data).hexdigest(),'windowsPathHits':hits,'loadSegmentAlignments':alignments})
report={'apk':apk.name,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
    'versionCode':int(args.version_code),'versionName':args.version_name,'appName':args.app_name,'appIconArtworkVerified':True,
    'entryCount':len(names),'permissions':permissions,'exportedActivityCount':1,'services':1,'receivers':2,'providers':0,
    'homeEngine':'Godot 4.7.2','homePackSha256':hashlib.sha256((repo/'build/generated/home-assets/home.pck').read_bytes()).hexdigest(),
    'binaryManifestSha256':manifest_hash,'binaryManifestVerifiedWithAapt2':True,
    'forbiddenAssets':forbidden,'nativeLibraries':native_reports,'status':'PASS',
    'limitations':'Static package validation only. Installation, Keystore behavior and plaintext storage scans require a real Android device.'}
folder=repo/'evidence'/args.stage
folder.mkdir(parents=True,exist_ok=True)
(folder/args.report).write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
