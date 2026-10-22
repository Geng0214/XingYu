"""Run production group migration DDL and scoped DAO queries on host SQLite, not Android."""
import argparse,hashlib,json,pathlib,re,sqlite3
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--stage",default="E5D")
args=parser.parse_args()
assert re.fullmatch(r"[A-Z0-9_-]+",args.stage)
repo=pathlib.Path(__file__).resolve().parents[2]
migration=repo/'app/src/main/java/com/ai/assistance/operit/data/db/GroupMigration.kt'
dao=repo/'app/src/main/java/com/ai/assistance/operit/data/db/GroupStorage.kt'
source=migration.read_text(encoding='utf-8');dao_source=dao.read_text(encoding='utf-8')
ddl=re.findall(r'db\.execSQL\("([^"\n]+)"',source);assert len(ddl)==17
schemas={v:json.loads((repo/f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text(encoding='utf-8'))['database'] for v in (27,28)}
def create(version):
    db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')
    for entity in schemas[version]['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
        for index in entity.get('indices',[]):db.execute(index['createSql'].replace('${TABLE_NAME}',entity['tableName']))
    return db
def shape(db):
    return {t:{p:list(db.execute(f'PRAGMA {p}(`{t}`)')) for p in ('table_info','index_list','foreign_key_list')} for (t,) in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")}
def insert(db,table,values):
    fields=list(db.execute(f'PRAGMA table_info(`{table}`)'))
    row={f[1]:None if not f[3] else {'TEXT':'','INTEGER':0,'REAL':0.0,'BLOB':b''}[f[2]] for f in fields};row.update(values)
    db.execute(f"INSERT INTO `{table}` ({','.join('`'+k+'`' for k in row)}) VALUES ({','.join('?' for k in row)})",list(row.values()))
db=create(27)
insert(db,'model_configs',dict(id='cfg',name='test',provider='openai',endpoint='https://example.invalid',model='synthetic',timeoutSeconds=60,credentialRef='ref',version=1))
for k in ('a','b'):
    insert(db,'roles',dict(id=k,spaceId='private-'+k,name=k,prompt='PRIVATE-'+k,version=1))
    insert(db,'chats',dict(id='private-chat-'+k,roleId=k,configId='cfg',title='PRIVATE-'+k))
insert(db,'bilibili_watches',dict(id='legacy-watch',roleId='a',spaceId='private-a',chatId='private-chat-a',title='KEEP-WATCH',videoId='BV1B7411m7LV',part=2,positionMs=1234,durationMs=590000,note='KEEP-NOTE',version=7))
db.commit();before=shape(db);old_rows={t:list(db.execute(f'SELECT * FROM `{t}`')) for t in before}
db.execute('BEGIN')
for sql in ddl[:8]:db.execute(sql)
db.rollback();assert shape(db)==before
for sql in ddl:db.execute(sql)
db.commit();assert shape(db)==shape(create(28))
for t,rows in old_rows.items():assert list(db.execute(f'SELECT * FROM `{t}`'))==rows
assert all(db.execute(f'SELECT COUNT(*) FROM `{t}`').fetchone()[0]==0 for t in ('group_rooms','group_members','group_requests','group_messages'))
for group in ('A','B'):
    insert(db,'group_rooms',dict(groupId=group,spaceId='group-space-'+group,title='GROUP-'+group,userName='user',version=1))
    for role in ('a','b'):insert(db,'group_members',dict(id=group+'-'+role,groupId=group,spaceId='group-space-'+group,roleId=role,roleSpaceId='private-'+role,name=role,configId='cfg'))
    for n in range(10):
        rid=f'{group}-request-{n}';uid=f'{group}-user-{n}';aid=f'{group}-reply-{n}'
        insert(db,'group_requests',dict(requestId=rid,groupId=group,spaceId='group-space-'+group,groupVersion=1,participantId=group+'-a',roleId='a',roleSpaceId='private-a',roleVersion=1,configId='cfg',configVersion=1,provider='openai',model='synthetic',userId=uid,assistantId=aid,createdAt=2*n+1,estimatedPromptTokens=100,currency='USD',terminalState='COMPLETE'))
        for mid,kind,author,at in ((uid,'user',None,2*n),(aid,'assistant',group+'-a',2*n+1)):
            insert(db,'group_messages',dict(id=mid,groupId=group,spaceId='group-space-'+group,requestId=rid,kind=kind,participantId=author,speakerName='same',content=f'ONLY_{group}_{n}',state='COMPLETE',createdAt=at,firstTokenMs=-1,elapsedMs=-1))
db.commit()
queries=re.findall(r'@Query\("([^"\n]+)"\)',dao_source)
history=next(q for q in queries if q.startswith('SELECT m.* FROM group_messages'))
request=next(q for q in queries if 'q.requestId=:request' in q)
members=next(q for q in queries if q.startswith('SELECT m.* FROM group_members'))
inbox=next(q for q in queries if q.startswith('SELECT g.groupId,g.spaceId,g.title,'))
usage=next(q for q in queries if q.startswith('SELECT q.*') and 'WHERE g.groupId=:id' in q and 'LIMIT 100' in q)
assert len(db.execute(inbox).fetchall())==2
for n in range(50):
    group='A' if n%2==0 else 'B';other='B' if group=='A' else 'A';params=dict(id=group,space='group-space-'+group,request=group+'-request-0')
    rows=db.execute(history,params).fetchall();assert len(rows)==20
    assert all('ONLY_'+other not in str(row) and 'PRIVATE-' not in str(row) for row in rows)
    assert len(db.execute(members,params).fetchall())==2
    assert len(db.execute(request,params).fetchall())==1
    assert len(db.execute(usage,params).fetchall())==10
    assert db.execute(usage,params|dict(space="group-space-"+other)).fetchall()==[]
    assert db.execute(history,params|dict(space='private-a')).fetchall()==[]
    assert db.execute(history,params|dict(space='group-space-'+other)).fetchall()==[]
    assert db.execute(request,params|dict(request=other+'-request-0')).fetchall()==[]
# A foreign group/message pair cannot even be inserted under the composite foreign key.
try:
    insert(db,'group_messages',dict(id='foreign',groupId='B',spaceId='group-space-B',requestId='A-request-0',kind='foreign'))
    raise AssertionError('Expected foreign-key rejection')
except sqlite3.IntegrityError:db.rollback()
db.execute('BEGIN')
try:
    insert(db,'group_requests',dict(requestId='rollback',groupId='A',spaceId='group-space-A',userId='rollback-u',assistantId='rollback-a'))
    insert(db,'group_messages',dict(id='rollback-u',groupId='A',spaceId='group-space-A',requestId='rollback',kind='user'))
    insert(db,'group_messages',dict(id='rollback-a',groupId='B',spaceId='group-space-B',requestId='rollback',kind='assistant'))
    raise AssertionError('Expected rollback')
except sqlite3.IntegrityError:db.rollback()
assert db.execute("SELECT * FROM group_requests WHERE requestId='rollback'").fetchall()==[]
try:db.execute("DELETE FROM roles WHERE id='a'");raise AssertionError('Member deletion must use repository transaction')
except sqlite3.IntegrityError:db.rollback()
db.execute("DELETE FROM group_rooms WHERE groupId='A'")
for table in ('group_members','group_requests','group_messages'):assert db.execute(f"SELECT COUNT(*) FROM {table} WHERE groupId='A'").fetchone()[0]==0
assert db.execute("SELECT COUNT(*) FROM group_messages WHERE groupId='B'").fetchone()[0]==20
assert not db.execute('PRAGMA foreign_key_check').fetchall()
report=dict(status='PASS',sourceSha256=hashlib.sha256(source.encode()).hexdigest(),daoSourceSha256=hashlib.sha256(dao_source.encode()).hexdigest(),schemaMatchesRoomV28=True,
    legacyRowsPreserved=True,emptyGroupTablesAfterUpgrade=True,interruptedDdlRollback=True,alternatingScopedReads=50,crossGroupRows=0,
    inboxAndScopedUsage=True,compositeForeignKeys=True,requestAndMessagesRollback=True,roleDeletionRequiresMembershipCleanup=True,groupCascade=True,
    limitation='Host SQLite executes production DDL and DAO SQL. Kotlin repository transactions and SQLCipher/device storage are compiled but not executed here.')
folder=repo/'evidence'/args.stage;folder.mkdir(parents=True,exist_ok=True)
(folder/'group-migration.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
