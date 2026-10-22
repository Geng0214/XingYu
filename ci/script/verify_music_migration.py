"""Run shipped 24->25 DDL and scoped DAO reads on host SQLite, not Android SQLCipher."""
import hashlib,json,pathlib,re,sqlite3

repo=pathlib.Path(__file__).resolve().parents[2]
source=(repo/'app/src/main/java/com/ai/assistance/operit/data/db/MusicMigration.kt').read_text(encoding='utf-8')
dao=(repo/'app/src/main/java/com/ai/assistance/operit/data/db/MusicRepository.kt').read_text(encoding='utf-8')
schemas={v:json.loads((repo/f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text(encoding='utf-8'))['database'] for v in (24,25)}
ddl=re.findall(r'db\.execSQL\("([^"\n]+)"',source)
assert len(ddl)==8

def create(v):
    db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')
    for e in schemas[v]['entities']:
        db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
        if 'indices' in e:
            for i in e['indices']:db.execute(i['createSql'].replace('${TABLE_NAME}',e['tableName']))
    return db

def shape(db):
    return {t:{p:list(db.execute(f'PRAGMA {p}(`{t}`)')) for p in ('table_info','index_list','foreign_key_list')} for (t,) in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")}

def insert(db,table,values):
    fields=list(db.execute(f'PRAGMA table_info(`{table}`)'))
    row={f[1]:None if not f[3] else {'TEXT':'','INTEGER':0,'REAL':0.0,'BLOB':b''}[f[2]] for f in fields}
    row.update(values)
    db.execute(f"INSERT INTO `{table}` ({','.join('`'+k+'`' for k in row)}) VALUES ({','.join('?' for k in row)})",list(row.values()))

db=create(24)
insert(db,'model_configs',dict(id='cfg',name='test',provider='OpenAI compatible',endpoint='https://example.invalid',model='synthetic',timeoutSeconds=60,credentialRef='ref',version=1))
for k in ('a','b'):
    insert(db,'roles',dict(id=k,spaceId='space-'+k,name=k,prompt='persona',version=1))
    insert(db,'chats',dict(id='chat-'+k,roleId=k,configId='cfg',title=k))
    insert(db,'reading_documents',dict(id='book-'+k,roleId=k,spaceId='space-'+k,text='PRIVATE-'+k,version=1))
insert(db,'shared_activities',dict(id='existing-reading',roleId='a',spaceId='space-a',chatId='chat-a',readingDocumentId='book-a',kind='reading',state='paused',note='keep-note',version=7,offset=3))
db.commit()
before=shape(db);rows={t:list(db.execute(f'SELECT * FROM `{t}`')) for t in before}
db.execute('BEGIN')
for sql in ddl[:3]:db.execute(sql)
db.rollback();assert shape(db)==before
db.execute('BEGIN')
for sql in ddl:db.execute(sql)
db.commit();assert shape(db)==shape(create(25))
for t,expected in rows.items():assert list(db.execute(f'SELECT * FROM `{t}`'))==expected
assert db.execute('SELECT COUNT(*) FROM music_sessions').fetchone()[0]==0
for k in ('a','b'):
    insert(db,'music_tracks',dict(id='track-'+k,roleId=k,spaceId='space-'+k,displayName='PRIVATE-'+k,version=1,durationMs=10000,byteCount=4,sha256='a'*64))
    insert(db,'music_sessions',dict(id='music-'+k,roleId=k,spaceId='space-'+k,chatId='chat-'+k,trackId='track-'+k,state='paused',note='PRIVATE-'+k,version=1))
read=next(sql for sql in re.findall(r'@Query\("([^"\n]+)"\)',dao) if 's.id=:id' in sql)
for n in range(50):
    k='a' if n%2==0 else 'b';other='b' if k=='a' else 'a'
    params=dict(roleId=k,spaceId='space-'+k,chatId='chat-'+k,id='music-'+k)
    result=db.execute(read,params).fetchall();assert len(result)==1 and 'PRIVATE-'+k in result[0] and 'PRIVATE-'+other not in result[0]
    for changes in (dict(roleId=other),dict(spaceId='space-'+other),dict(chatId='chat-'+other),dict(id='music-'+other)):
        assert db.execute(read,params|changes).fetchall()==[]
try:
    db.execute("DELETE FROM music_tracks WHERE id='track-a'")
    raise AssertionError('Referenced track deleted')
except sqlite3.IntegrityError:pass
db.execute("DELETE FROM chats WHERE id='chat-a'")
assert db.execute("SELECT id FROM music_sessions WHERE id='music-a'").fetchall()==[]
assert db.execute("SELECT id FROM music_tracks WHERE id='track-a'").fetchall()
db.execute("DELETE FROM roles WHERE id='b'")
assert db.execute("SELECT id FROM music_sessions WHERE roleId='b'").fetchall()==[]
assert not db.execute('PRAGMA foreign_key_check').fetchall()
report=dict(status='PASS',sourceSha256=hashlib.sha256(source.encode()).hexdigest(),daoSourceSha256=hashlib.sha256(dao.encode()).hexdigest(),schemaMatchesRoomV25=True,legacyRowsPreserved=True,interruptedDdlRollback=True,alternatingScopedReads=50,crossOwnerRows=0,chatAndRoleCascade=True,referencedTrackDeleteRejected=True,limitation='Host SQLite DDL and actual DAO read SQL only. Android Room repository, SQLCipher, Keystore and UI remain NOT_RUN.')
folder=repo/'evidence/E5C2';folder.mkdir(parents=True,exist_ok=True)
(folder/'music-migration.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
