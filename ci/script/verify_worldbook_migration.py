"""Execute shipped 7->8 DDL on host SQLite; this does not claim Android SQLCipher execution."""
import hashlib,json,pathlib,re,sqlite3
repo=pathlib.Path(__file__).resolve().parents[2]
source=(repo/'app/src/main/java/com/ai/assistance/operit/data/db/WorldbookMigration.kt').read_text(encoding='utf-8')
ownership=(repo/'app/src/main/java/com/ai/assistance/operit/data/db/PersonaMigration.kt').read_text(encoding='utf-8')
assert 'PersonaMigration.checkOwnership(db)' in source
checks=re.findall(r'"(SELECT COUNT\(\*\)[^"\n]+)"',ownership)
ddl=re.findall(r'db.execSQL\("([^"\n]+)"',source)
assert len(checks)==8 and len(ddl)==2
schemas={v:json.loads((repo/f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text())['database'] for v in (7,8)}
def create(version):
    db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')
    for e in schemas[version]['entities']:
        db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
        for index in e.get('indices',[]):db.execute(index['createSql'].replace('${TABLE_NAME}',e['tableName']))
    return db
def shape(db):
    return {name:(list(db.execute(f'PRAGMA table_info({name})')),list(db.execute(f'PRAGMA index_list({name})')))
            for name, in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")}
def valid(db):return not db.execute('PRAGMA foreign_key_check').fetchall() and all(db.execute(q).fetchone()[0]==0 for q in checks)
db=create(7)
records={
 'model_configs':[['cfg','model','OpenAI','https://example.invalid/v1/chat/completions','test',60,'original-ref',3]],
 'roles':[['r','name','original role',9,'space','greeting','cfg','short','persona','private mask',1]],
 'chats':[['chat','r','cfg','original title',1,'greeting',1]],
 'messages':[['answer','chat','request','assistant','partial original','GENERATING',2,'',1,3,'short',1]],
 'role_appearance':[['r','space',b'original-wallpaper',0.2,'rose']],
 'role_portraits':[['r','space','self',b'original-portrait']],
 'mcp_extensions':[['ext','remote','https://example.invalid/mcp','original-ref-mcp',1000,1,4]],
 'extension_tools':[['ext','echo','description','{}']],
 'role_tool_grants':[['r','space','ext','echo',1,3]],
 'request_tool_permissions':[['request','ext','echo','r','space','chat','answer',4,3,'https://example.invalid/mcp','original-ref-mcp',1000,'description','{}']],
 'tool_invocations':[['request','call','r','space','chat','answer','ext','echo','arguments','result','RUNNING','',1,0]],
 'toolpkg_packages':[['ext','package','1.0.0','1.0.0','a'*64,b'original-package',1]]}
for table,rows in records.items():
    for row in rows:db.execute(f'INSERT INTO {table} VALUES ({",".join("?" for _ in row)})',row)
db.commit();before=shape(db);assert valid(db)
for table,column in [('chats','configId'),('messages','chatId'),('roles','defaultConfigId'),('role_appearance','spaceId'),('role_portraits','spaceId'),('role_tool_grants','spaceId'),('request_tool_permissions','requestId'),('tool_invocations','chatId')]:
    db.execute('BEGIN');db.execute(f"UPDATE {table} SET {column}='foreign'")
    assert not valid(db);db.rollback();assert shape(db)==before
db.execute('BEGIN');db.execute(ddl[0]);db.rollback();assert shape(db)==before
db.execute('BEGIN')
for sql in ddl:db.execute(sql)
db.commit();assert shape(db)==shape(create(8))
for table,rows in records.items():assert db.execute(f'SELECT * FROM {table}').fetchall()==[tuple(row) for row in rows]
assert db.execute('SELECT COUNT(*) FROM worldbook_entries').fetchone()[0]==0
assert valid(db)
report=dict(status='PASS',sourceSha256=hashlib.sha256(source.encode()).hexdigest(),ownershipSourceSha256=hashlib.sha256(ownership.encode()).hexdigest(),
            schemaMatchesRoomV8=True,allTwelveOriginalTablesPreserved=True,invalidAssociationsRejected=8,interruptedDdlRollback=True,newWorldbookEmpty=True,
            limitation='Actual migration SQL on host SQLite. Room/SQLCipher/Keystore device migration remains NOT_RUN.')
path=repo/'evidence/E2D/worldbook-migration.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,indent=2))
