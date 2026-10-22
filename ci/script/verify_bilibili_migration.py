"""Execute production online-watching DDL and DAO reads using host SQLite."""
import hashlib,json,pathlib,re,sqlite3
repo=pathlib.Path(__file__).resolve().parents[2]
path=repo/'app/src/main/java/com/ai/assistance/operit/data/db/BilibiliRepository.kt'
source=path.read_text(encoding='utf-8')
schemas={v:json.loads((repo/f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text(encoding='utf-8'))['database'] for v in (26,27)}
ddl=re.findall(r'db\.execSQL\("([^"\n]+)"',source)
assert len(ddl)==4

def create(v):
    db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')
    for e in schemas[v]['entities']:
        db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
        for i in e.get('indices',[]):db.execute(i['createSql'].replace('${TABLE_NAME}',e['tableName']))
    return db

def shape(db):
    return {t:{p:list(db.execute(f'PRAGMA {p}(`{t}`)')) for p in ('table_info','index_list','foreign_key_list')} for (t,) in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")}

def insert(db,table,values):
    fields=list(db.execute(f'PRAGMA table_info(`{table}`)'))
    row={f[1]:None if not f[3] else {'TEXT':'','INTEGER':0,'REAL':0.0,'BLOB':b''}[f[2]] for f in fields};row.update(values)
    db.execute(f"INSERT INTO `{table}` ({','.join('`'+k+'`' for k in row)}) VALUES ({','.join('?' for k in row)})",list(row.values()))

db=create(26)
insert(db,'model_configs',dict(id='cfg',name='test',provider='OpenAI compatible',endpoint='https://example.invalid',model='synthetic',timeoutSeconds=60,credentialRef='ref',version=1))
for k in ('a','b'):
    insert(db,'roles',dict(id=k,spaceId='space-'+k,name=k,prompt='persona',version=1))
    insert(db,'chats',dict(id='chat-'+k,roleId=k,configId='cfg',title=k))
insert(db,'music_tracks',dict(id='legacy-track',roleId='a',spaceId='space-a',displayName='KEEP-VIDEO',mimeType='video/mp4',durationMs=3000,byteCount=100,sha256='a'*64,sourceUri='content://synthetic/video',version=7,mediaKind='video',videoWidth=1920,videoHeight=1080))
insert(db,'music_sessions',dict(id='legacy-session',roleId='a',spaceId='space-a',chatId='chat-a',trackId='legacy-track',state='paused',positionMs=1234,note='KEEP-NOTE',version=8))
db.commit();before=shape(db);rows={t:list(db.execute(f'SELECT * FROM `{t}`')) for t in before}
db.execute('BEGIN')
for sql in ddl[:2]:db.execute(sql)
db.rollback();assert shape(db)==before
for sql in ddl:db.execute(sql)
db.commit();assert shape(db)==shape(create(27))
for t,expected in rows.items():assert list(db.execute(f'SELECT * FROM `{t}`'))==expected
assert db.execute('SELECT COUNT(*) FROM bilibili_watches').fetchone()[0]==0
for k in ('a','b'):
    insert(db,'bilibili_watches',dict(id='watch-'+k,roleId=k,spaceId='space-'+k,chatId='chat-'+k,title='PRIVATE-'+k,videoId='BV1B7411m7LV',part=1,positionMs=1234,durationMs=590000,note='NOTE-'+k,playbackToken='run-'+k,version=4))
queries=re.findall(r'@Query\("([^"\n]+)"\)',source)
read=next(q for q in queries if 'w.id=:id' in q)
for n in range(50):
    k='a' if n%2==0 else 'b';other='b' if k=='a' else 'a'
    params=dict(roleId=k,spaceId='space-'+k,chatId='chat-'+k,id='watch-'+k)
    result=db.execute(read,params).fetchall();assert len(result)==1 and 'PRIVATE-'+k in result[0] and 'PRIVATE-'+other not in result[0]
    for changes in (dict(roleId=other),dict(spaceId='space-'+other),dict(chatId='chat-'+other),dict(id='watch-'+other)):
        assert db.execute(read,params|changes).fetchall()==[]
db.execute(next(q for q in queries if q.startswith('UPDATE bilibili_watches')))
assert list(db.execute('SELECT positionMs,playbackToken,version FROM bilibili_watches'))==[(1234,None,5),(1234,None,5)]
db.execute("DELETE FROM chats WHERE id='chat-a'");assert db.execute("SELECT id FROM bilibili_watches WHERE id='watch-a'").fetchall()==[]
db.execute("DELETE FROM roles WHERE id='b'");assert db.execute("SELECT id FROM bilibili_watches WHERE id='watch-b'").fetchall()==[]
assert not db.execute('PRAGMA foreign_key_check').fetchall()
report=dict(status='PASS',sourceSha256=hashlib.sha256(source.encode()).hexdigest(),schemaMatchesRoomV27=True,legacyRowsPreserved=True,interruptedDdlRollback=True,alternatingScopedReads=50,crossOwnerRows=0,interruptedPlaybackTokenCleared=True,chatAndRoleCascade=True,limitation='Host SQLite actual DDL and DAO SQL. Android SQLCipher, WebView and installation remain NOT_RUN.')
folder=repo/'evidence/E5C3B';folder.mkdir(parents=True,exist_ok=True)
(folder/'bilibili-migration.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
