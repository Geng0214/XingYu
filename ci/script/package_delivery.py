"""Package the current signed revision; refuse stale APK metadata or unverified output."""
import datetime,hashlib,io,json,pathlib,re,shutil,subprocess,tarfile,zipfile,xml.etree.ElementTree as ET
repo=pathlib.Path(__file__).resolve().parents[2]
build=repo/'build/app';out=repo/'output';evidence=repo/'evidence/E7_HOME'
apk=build/'outputs/apk/release/app-release.apk'
revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip()
manifest=ET.parse(build/'intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
version=manifest.get(a+'versionName');code=int(manifest.get(a+'versionCode'))
assert version=='0.39.0' and code==40
# Evidence can be added after the source commit, but the packaged source must not have uncommitted edits.
changes=subprocess.check_output(['git','diff','--name-only','HEAD'],cwd=repo,text=True).splitlines()
untracked=subprocess.check_output(['git','ls-files','--others','--exclude-standard'],cwd=repo,text=True).splitlines()
assert all(p.startswith('evidence/') for p in changes+untracked),'Commit source/docs before packaging'
with zipfile.ZipFile(apk) as archive:
    assert revision in archive.read('META-INF/version-control-info.textproto').decode()
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
audit=json.loads((evidence/'apk-audit.json').read_text())
assert audit['status']=='PASS' and audit['sha256']==digest(apk)
tests=[ET.parse(p).getroot() for p in (build/'test-results/testDebugUnitTest').glob('TEST-*.xml')]
assert tests
counts={k:sum(int(t.get(k,'0')) for t in tests) for k in ('tests','failures','errors','skipped')}
assert counts['failures']==counts['errors']==counts['skipped']==0
assert counts['tests']>=563
for suite in ('HomeStateTest','HomeStorageTest','HomeUiTest','PlayOutfitsTest','HomeAdventureRewardTest'):
    assert any(t.get('name')=='com.ai.assistance.operit.'+suite for t in tests),suite
home_migration=json.loads((repo/'evidence/E11_SHARED_HOME/home-migration.json').read_text())
assert home_migration['status']=='PASS' and home_migration['schemaMatchesRoom34']
assert home_migration['migrationSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/SharedHomeMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
assert home_migration['daoSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/HomeStorage.kt').read_text(encoding='utf-8').encode()).hexdigest()
for suite in ('AdventureGameTest','AdventureIntegrationTest'):
    assert any(t.get('name')=='com.ai.assistance.operit.'+suite for t in tests),suite
for suite in ('XiangqiBoardTest','XiangqiGameTest','XiangqiLineTest','XiangqiThreatsTest','XiangqiWxfExamplesTest','XiangqiPlayIntegrationTest'):
    assert any(t.get('name')=='com.ai.assistance.operit.'+suite for t in tests),suite
for suite in ('CommunicationRoutesTest','ObservedAudioRouteTest','MediaAudioPolicyTest','MediaAudioHealthTest',
              'VoiceCaptureDeliveryTest','VideoFramePacingTest','BilibiliAudioProtocolTest'):
    assert any(t.get('name')=='com.ai.assistance.operit.'+suite for t in tests),suite
web_tests=ET.parse(evidence/'web-script-tests.xml').getroot()
assert len(list(web_tests.iter('testcase')))==9
assert not list(web_tests.iter('failure')) and not list(web_tests.iter('error')) and not list(web_tests.iter('skipped'))
assert any(t.get('name')=='com.ai.assistance.operit.SharedActivitiesTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.LocalMusicTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.VerifiedMediaReaderTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.BilibiliWatchingTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.GroupRequestContextTest' for t in tests)
group_migration=json.loads((repo/'evidence/E5D/group-migration.json').read_text(encoding='utf-8'))
assert group_migration['status']=='PASS' and group_migration['inboxAndScopedUsage']
assert group_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/GroupMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
assert group_migration['daoSourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/GroupStorage.kt').read_text(encoding='utf-8').encode()).hexdigest()
bilibili_migration=json.loads((repo/'evidence/E5C3B/bilibili-migration.json').read_text(encoding='utf-8'))
assert bilibili_migration['status']=='PASS'
assert bilibili_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/BilibiliRepository.kt').read_text(encoding='utf-8').encode()).hexdigest()
video_migration=json.loads((repo/'evidence/E5C3A/video-migration.json').read_text(encoding='utf-8'))
assert video_migration['status']=='PASS'
assert video_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/VideoMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
assert video_migration['daoSourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/MusicRepository.kt').read_text(encoding='utf-8').encode()).hexdigest()
music_migration=json.loads((repo/'evidence/E5C2/music-migration.json').read_text(encoding='utf-8'))
assert music_migration['status']=='PASS'
assert music_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/MusicMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
assert music_migration['daoSourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/MusicRepository.kt').read_text(encoding='utf-8').encode()).hexdigest()
activity_migration=json.loads((repo/'evidence/E5C1/activity-migration.json').read_text(encoding='utf-8'))
assert activity_migration['status']=='PASS'
assert activity_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/SharedActivityMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
assert activity_migration['daoSourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/SharedActivityRepository.kt').read_text(encoding='utf-8').encode()).hexdigest()
assert any(t.get('name')=='com.ai.assistance.operit.OwnedChatRequestTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.PcmPlaybackTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.PrivacyGateTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.BackupTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.UserPersonaTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.WorldbookTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.RelationshipTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.SupportDiagnosticsTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.VisionMessageTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.TextMaterialReaderTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.MaterialContextTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.StickerContextTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.ImageGenerationTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.ReadingDocumentReaderTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.ReadingContextTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.MessageExportTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.MemoryTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.MemoryExtractionTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.DiaryTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.EnvironmentTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.PlannerTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.ProactiveMessagesTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.ModelGenerationGateTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.SpeechServiceTest' for t in tests)
assert any(t.get('name')=='com.ai.assistance.operit.VoiceConversationTest' for t in tests)
assert all(any(t.get('name')=='com.ai.assistance.operit.'+name for t in tests) for name in ('PlayRulesTest','PlayGenerationTest','PlayCoordinatorTest','PlayStorageTest','JourneyTest','WordGuessGameTest','FourColorGameTest','TableGameResponsesTest','TablePlayIntegrationTest'))
play_migration=json.loads((evidence/'migration.json').read_text())
assert play_migration['status']=='PASS'
attachment_migration=json.loads((repo/'evidence/E3A/attachment-migration.json').read_text())
assert attachment_migration['status']=='PASS'
assert attachment_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/AttachmentMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
material_migration=json.loads((repo/'evidence/E3B/material-migration.json').read_text())
assert material_migration['status']=='PASS'
assert material_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/MaterialMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
sticker_migration=json.loads((repo/'evidence/E3C/sticker-migration.json').read_text())
assert sticker_migration['status']=='PASS'
assert sticker_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/StickerMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
image_generation_migration=json.loads((repo/'evidence/E3D/image-generation-migration.json').read_text())
assert image_generation_migration['status']=='PASS'
assert image_generation_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/ImageGenerationMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
reading_migration=json.loads((repo/'evidence/E3E/reading-migration.json').read_text())
assert reading_migration['status']=='PASS'
assert reading_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/ReadingMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
memory_migration=json.loads((repo/'evidence/E4A/memory-migration.json').read_text())
assert memory_migration['status']=='PASS'
assert memory_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/MemoryMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
memory_source_migration=json.loads((repo/'evidence/E4B/memory-source-migration.json').read_text())
assert memory_source_migration['status']=='PASS'
assert memory_source_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/MemorySourceMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
memory_recall_migration=json.loads((repo/'evidence/E4C/memory-recall-migration.json').read_text())
assert memory_recall_migration['status']=='PASS'
assert memory_recall_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/MemoryRecallMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
diary_migration=json.loads((repo/'evidence/E4D/diary-migration.json').read_text())
assert diary_migration['status']=='PASS'
assert diary_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/DiaryMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
environment_migration=json.loads((repo/'evidence/E4E/environment-migration.json').read_text())
assert environment_migration['status']=='PASS'
assert environment_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/EnvironmentMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
planner_migration=json.loads((repo/'evidence/E4F/planner-migration.json').read_text())
assert planner_migration['status']=='PASS'
assert planner_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/PlannerMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
proactive_migration=json.loads((repo/'evidence/E4G/proactive-migration.json').read_text())
assert proactive_migration['status']=='PASS'
assert proactive_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/ProactiveMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
speech_migration=json.loads((repo/'evidence/E5D/speech-migration.json').read_text())
assert speech_migration['status']=='PASS'
assert speech_migration['sourceSha256']==hashlib.sha256((repo/'app/src/main/java/com/ai/assistance/operit/data/db/SpeechMigration.kt').read_text(encoding='utf-8').encode()).hexdigest()
assert json.loads((repo/'evidence/E1/toolpkg-sql.json').read_text())['status']=='PASS'
assert json.loads((repo/'evidence/E1/storage-sql.json').read_text())['status']=='PASS'
for log in ('build-final-clean.log',):
    assert 'BUILD SUCCESSFUL' in (evidence/log).read_text(encoding='utf-8-sig')
lint=(build/'reports/lint-results-release.txt').read_text(encoding='utf-8')
assert '0 errors,' in lint
certificate='ac7283d4cc843d710a77f3c5b3f96ee3663793b78e8fb30c6a145b8e37f497fa'
signature=(evidence/'signature.txt').read_text(encoding='utf-8')
assert certificate in signature
assert (evidence/'alignment.txt').read_text(encoding='utf-8').strip()=='PASS: zipalign -c -P 16 4'
out.mkdir(exist_ok=True)
shutil.copyfile(apk,out/f'local-companion-{version}.apk')
install_name='\u5b89\u88c5\u8bf4\u660e.md'
shutil.copyfile(repo/'docs/使用与升级.md',out/install_name)
subprocess.run(['git','archive','--format=zip','--output='+str(out/'local-companion-source.zip'),revision],cwd=repo,check=True)
with zipfile.ZipFile(out/'local-companion-source.zip') as archive:
    assert not any(re.search(r'(?i)(?:^|/)(?:local\.properties|\.env)$|\.(?:jks|keystore|p12|pem|key|apk)$',n) for n in archive.namelist())
    expected_bytes=subprocess.check_output(['git','archive','--format=tar',revision],cwd=repo)
    with tarfile.open(fileobj=io.BytesIO(expected_bytes)) as expected:
        members=[m for m in expected.getmembers() if m.isfile()]
        assert set(archive.namelist())-{n for n in archive.namelist() if n.endswith('/')}=={m.name for m in members}
        assert all(archive.read(m.name)==expected.extractfile(m).read() for m in members)
    source_archive_files=len(members)
files=[out/f'local-companion-{version}.apk',out/'local-companion-source.zip',out/install_name]
report=dict(generatedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),applicationId=manifest.get('package'),version=version,versionCode=code,
    sourceCommit=revision,certificateSha256=certificate,roomMigration='1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10 -> 11 -> 12 -> 13 -> 14 -> 15 -> 16 -> 17 -> 18 -> 19 -> 20 -> 21 -> 22 -> 23 -> 24 -> 25 -> 26 -> 27 -> 28',upgradeFrom=['0.1.0','0.2.0','0.3.0','0.4.0','0.5.0','0.6.0','0.7.0','0.8.0','0.9.0','0.10.0','0.11.0','0.12.0','0.13.0','0.14.0','0.15.0','0.16.0','0.17.0','0.18.0','0.19.0','0.20.0','0.21.0','0.22.0','0.23.0','0.24.0','0.25.0','0.26.0'],
    files=[dict(name=p.name,bytes=p.stat().st_size,sha256=digest(p)) for p in files],
    validation=dict(unitTests=counts,hostGroupMigration="PASS",sharedGroupPrivateCoordinator="PASS",androidGroupRoundTrip="NOT_RUN",groupEncryptionAtRest="NOT_RUN",groupUiAndRotation="NOT_RUN",groupLiveProviders="NOT_RUN",hostVideoMigration='PASS',androidVideoRoundTrip='NOT_RUN',videoPlaybackAndFocus='NOT_RUN',videoUiAndFilePicker='NOT_RUN',videoCrossDeviceRelink='NOT_RUN',hostBilibiliMigration='PASS',bilibiliDesktopProtocol='PASS_WITH_LIMITATIONS',bilibiliPlayback='NOT_RUN',bilibiliWebViewStorage='NOT_RUN',bilibiliUiFullscreen='NOT_RUN',bilibiliLifecycle='NOT_RUN',hostMusicMigration='PASS',androidMusicRoundTrip='NOT_RUN',musicPlaybackAndFocus='NOT_RUN',musicUiAndFilePicker='NOT_RUN',musicCrossDeviceRelink='NOT_RUN',hostActivityMigration='PASS',androidSharedActivityRoundTrip='NOT_RUN',sharedActivityEncryptionAtRest='NOT_RUN',sharedActivityUi='NOT_RUN',hostSpeechMigration='PASS',hostProactiveMigration='PASS',hostPlannerMigration='PASS',hostEnvironmentMigration='PASS',hostDiaryMigration='PASS',hostMemoryRecallMigration='PASS',hostMemorySourceMigration='PASS',hostMemoryMigration='PASS',hostReadingMigration='PASS',hostImageGenerationMigration='PASS',hostStickerMigration='PASS',hostMaterialMigration='PASS',hostAttachmentMigration='PASS',hostExtensionAndToolPkgSql='PASS',hostQuickJsModelRoundTrip='PASS',cleanBuild='PASS',finalValidation='PASS',apkAudit='PASS',signatureContinuity='PASS',androidTestCompilation='PASS',
        androidProactiveRoundTrip='NOT_RUN',proactiveEncryptionAtRest='NOT_RUN',proactiveUi='NOT_RUN',proactiveJobScheduling='NOT_RUN',proactiveDozeAndBoot='NOT_RUN',proactiveNotificationDelivery='NOT_RUN',liveProactiveProvider='NOT_RUN',androidPlannerRoundTrip='NOT_RUN',plannerEncryptionAtRest='NOT_RUN',plannerUi='NOT_RUN',reminderDelivery='NOT_RUN',reminderBootReschedule='NOT_RUN',notificationPermission='NOT_RUN',plannerChatInjection='NOT_RUN',androidEnvironmentRoundTrip='NOT_RUN',environmentEncryptionAtRest='NOT_RUN',environmentUi='NOT_RUN',liveOpenMeteo='NOT_RUN',environmentChatInjection='NOT_RUN',androidDiaryRoundTrip='NOT_RUN',diaryEncryptionAtRest='NOT_RUN',diaryUiAndFilePicker='NOT_RUN',liveDiaryGenerationProvider='NOT_RUN',androidMemoryRoundTrip='NOT_RUN',memoryEncryptionAtRest='NOT_RUN',memoryUiAndInjectionPreview='NOT_RUN',confirmedExtractionUi='NOT_RUN',memoryRecallConflictForgettingUi='NOT_RUN',liveMemoryExtractionProvider='NOT_RUN',
        androidSpeechConfigRoundTrip='NOT_RUN',speechEncryptionAtRest='NOT_RUN',microphonePermission='NOT_RUN',recordStopAndLimit='NOT_RUN',transcriptionDraftInsertion='NOT_RUN',speechPlaybackAndAudioFocus='NOT_RUN',speechBackgroundCancellation='NOT_RUN',continuousVoiceTwentyTurns='NOT_RUN',continuousVoiceInterrupt='NOT_RUN',continuousVoiceLifecycle='NOT_RUN',liveTranscriptionProvider='NOT_RUN',liveSynthesisProvider='NOT_RUN',deviceUpgrade='NOT_RUN',deviceStorage='NOT_RUN',messageExportFilePicker='NOT_RUN',androidMessageExportRoundTrip='NOT_RUN',androidReadingRoundTrip='NOT_RUN',readingFilePicker='NOT_RUN',liveReadingProviders='NOT_RUN',androidGeneratedImageRoundTrip='NOT_RUN',liveImageGenerationProvider='NOT_RUN',androidImageNormalization='NOT_RUN',androidImageRoundTrip='NOT_RUN',androidMaterialRoundTrip='NOT_RUN',androidStickerRoundTrip='NOT_RUN',androidStickerGifFirstFrame='NOT_RUN',androidFilePicker='NOT_RUN',liveLinkReading='NOT_RUN',liveVisionProviders='NOT_RUN',onboardingFlow='NOT_RUN',errorNavigation='NOT_RUN',diagnosticClipboard='NOT_RUN',androidQuickJsRuntime='NOT_RUN',systemAuthentication='NOT_RUN',lockLifecycleUi='NOT_RUN',androidBackupTransaction='NOT_RUN',crossDeviceBackup='NOT_RUN',backupFilePicker='NOT_RUN',androidPersonaIsolation='NOT_RUN',androidWorldbookIsolation='NOT_RUN',worldbookFilePicker='NOT_RUN',androidRelationshipIsolation='NOT_RUN',rawModeUi='NOT_RUN',visualQA='NOT_RUN',liveProviders='NOT_RUN',liveMcp='NOT_RUN',crossDeviceTemplate='NOT_RUN',threePersonObservation='NOT_RUN'),
    compatibility=dict(mcp='Streamable HTTP over HTTPS',toolPkg='schema 1 / API 1.0.0 / tools-v1 pure computation; no device/network/filesystem/UI Hook host APIs'),
    status='E7 home, outfits and adventure rewards development build; device acceptance pending; advanced themes and E8 continue; prior stages are not fully accepted')
report['upgradeFrom'].extend(['0.27.0','0.27.1','0.28.0','0.29.0','0.30.0','0.31.0','0.32.0'])
report['upgradeFrom'].extend(['0.33.0','0.34.0','0.35.0','0.36.0','0.37.0','0.38.0'])
report['backupVersion']=31
report['validation'].update(hostHomeMigration='PASS',homeRulesAndLedger='PASS',homeBackupAndOutfits='PASS',
    adventureRewardProofAndRemap='PASS',androidHomeRoundTrip='NOT_RUN',androidRewardConcurrency='NOT_RUN',
    homeEncryptionAtRest='NOT_RUN',homeUiAndTalkBack='NOT_RUN',homeLifecycle='NOT_RUN',
    finalValidation='PENDING_DEVICE_ACCEPTANCE')
report['validation'].update(adventureRulesAndContext='PASS',adventureCancellation='PASS',adventureBackupCompatibility='PASS',
    androidAdventureRoundTrip='NOT_RUN',adventureEncryptionAtRest='NOT_RUN',adventureUi='NOT_RUN',adventureLiveProviders='NOT_RUN')
report['sourceArchiveMatchesGitArchive']=True
report['sourceArchiveFiles']=source_archive_files
report['validation'].update(xiangqiRulesAndContext='PASS',xiangqiCancellation='PASS',xiangqiBackupCompatibility='PASS',
    xiangqiAllFormalCases='NOT_RUN',androidXiangqiRoundTrip='NOT_RUN',xiangqiEncryptionAtRest='NOT_RUN',xiangqiUi='NOT_RUN',xiangqiLiveProviders='NOT_RUN')
report['validation'].update(tableGameRulesAndContext='PASS',tableGameCancellation='PASS',tableGameBackupCompatibility='PASS',androidTableGameRoundTrip='NOT_RUN',tableGameEncryptionAtRest='NOT_RUN',tableGameUi='NOT_RUN',tableGameLiveProviders='NOT_RUN')
report['validation'].update(journeyRulesAndContext='PASS',journeyCancellation='PASS',journeyBackupCompatibility='PASS',androidJourneyRoundTrip='NOT_RUN')
report['roomMigration']+=' -> 29 -> 30'
report['validation'].update(hostPlayMigration='PASS',playApprovalAndCancellation='PASS',androidPlayUpgrade='NOT_RUN',androidPlayUi='NOT_RUN',playLiveProviders='NOT_RUN')
report['validation'].update(webAudioScriptTests='PASS',androidCommunicationRoutes='NOT_RUN',
    androidLegacySco='NOT_RUN',androidMediaActualRoute='NOT_RUN',androidWebViewMuteAndPcm='NOT_RUN',
    webNativeUniqueAudibleOutput='NOT_RUN',webAudioVideoSync='NOT_RUN',legacyVideoSync='NOT_RUN',
    headsetPlugUnplugAndBluetooth='NOT_RUN',audioRouteUiAccessibility='NOT_RUN')
for path in (out/'delivery.json',evidence/'delivery.json'):path.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
