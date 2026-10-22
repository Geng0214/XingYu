"""Run shipped 23->24 DDL and scoped DAO reads on host SQLite, not Android SQLCipher."""
import hashlib,json,pathlib,re,sqlite3

repo=pathlib.Path(__file__).resolve().parents[2]
source=(repo/'app/src/main/java/com/ai/assistance/operit/data/db/SharedActivityMigration.kt').read_text(encoding='utf-8')
dao=(repo/'app/src/main/java/com/ai/assistance/operit/data/db/SharedActivityRepository.kt').read_text(encoding='utf-8')
schemas={v:json.loads((repo/f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text(encoding='utf-8'))['database'] for v in (23,24)}
ddl=re.findall(r'db\.execSQL\("([^"\n]+)"',source)
assert len(ddl)==5

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

db=create(23)
insert(db,'model_configs',dict(id='cfg',name='test',provider='OpenAI compatible',endpoint='https://example.invalid',model='synthetic',timeoutSeconds=60,credentialRef='ref',version=1))
for k in ('a','b'):
    insert(db,'roles',dict(id=k,spaceId='space-'+k,name=k,prompt='persona',version=1))
    insert(db,'chats',dict(id='chat-'+k,roleId=k,configId='cfg',title=k))
    insert(db,'reading_documents',dict(id='book-'+k,roleId=k,spaceId='space-'+k,text='PRIVATE-'+k,version=1))
db.commit()
before=shape(db);rows={t:list(db.execute(f'SELECT * FROM `{t}`')) for t in before}
db.execute('BEGIN')
for sql in ddl[:3]:db.execute(sql)
db.rollback();assert shape(db)==before
db.execute('BEGIN')
for sql in ddl:db.execute(sql)
db.commit();assert shape(db)==shape(create(24))
for t,expected in rows.items():assert list(db.execute(f'SELECT * FROM `{t}`'))==expected
assert db.execute('SELECT COUNT(*) FROM shared_activities').fetchone()[0]==0
for k in ('a','b'):
    insert(db,'shared_activities',dict(id='activity-'+k,roleId=k,spaceId='space-'+k,chatId='chat-'+k,readingDocumentId='book-'+k,kind='reading',state='active',note='PRIVATE-'+k,version=1))
read=next(sql for sql in re.findall(r'@Query\("([^"\n]+)"\)',dao) if 'a.id=:id' in sql)
for n in range(50):
    k='a' if n%2==0 else 'b';other='b' if k=='a' else 'a'
    params=dict(roleId=k,spaceId='space-'+k,chatId='chat-'+k,id='activity-'+k)
    result=db.execute(read,params).fetchall();assert len(result)==1 and 'PRIVATE-'+k in result[0] and 'PRIVATE-'+other not in result[0]
    for changes in (dict(roleId=other),dict(spaceId='space-'+other),dict(chatId='chat-'+other),dict(id='activity-'+other)):
        assert db.execute(read,params|changes).fetchall()==[]
try:
    db.execute("DELETE FROM reading_documents WHERE id='book-a'")
    raise AssertionError('Referenced book deleted')
except sqlite3.IntegrityError:pass
db.execute("DELETE FROM chats WHERE id='chat-a'")
assert db.execute("SELECT id FROM shared_activities WHERE id='activity-a'").fetchall()==[]
assert db.execute("SELECT id FROM reading_documents WHERE id='book-a'").fetchall()
db.execute("DELETE FROM roles WHERE id='b'")
assert db.execute("SELECT id FROM shared_activities WHERE roleId='b'").fetchall()==[]
assert not db.execute('PRAGMA foreign_key_check').fetchall()
report=dict(status='PASS',sourceSha256=hashlib.sha256(source.encode()).hexdigest(),daoSourceSha256=hashlib.sha256(dao.encode()).hexdigest(),schemaMatchesRoomV24=True,legacyRowsPreserved=True,interruptedDdlRollback=True,alternatingScopedReads=50,crossOwnerRows=0,chatAndRoleCascade=True,referencedBookDeleteRejected=True,limitation='Host SQLite DDL and actual DAO read SQL only. Android Room repository, SQLCipher, Keystore and UI remain NOT_RUN.')
folder=repo/'evidence/E5C1';folder.mkdir(parents=True,exist_ok=True)
(folder/'activity-migration.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
