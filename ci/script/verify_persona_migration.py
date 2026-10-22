"""Run the actual persona migration SQL on host SQLite; Android SQLCipher remains a separate gate."""
import hashlib,json,pathlib,re,sqlite3
repo=pathlib.Path(__file__).resolve().parents[2]
source=(repo/'app/src/main/java/com/ai/assistance/operit/data/db/PersonaMigration.kt').read_text(encoding='utf-8')
checks=re.findall(r'"(SELECT COUNT\(\*\)[^"\n]+)"',source)
ddl=re.findall(r'db.execSQL\("([^"\n]+)"',source)
assert len(checks)==8 and len(ddl)==3
schemas={v:json.loads((repo/f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text())['database'] for v in (6,7)}
def create(version):
    db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')
    for entity in schemas[version]['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
        if 'indices' in entity:
            for index in entity['indices']:db.execute(index['createSql'].replace('${TABLE_NAME}',entity['tableName']))
    return db
def shape(db):return {e['tableName']:list(db.execute('PRAGMA table_info('+e['tableName']+')')) for e in schemas[7]['entities']}
def valid(db):return not db.execute('PRAGMA foreign_key_check').fetchall() and all(db.execute(q).fetchone()[0]==0 for q in checks)
db=create(6)
rows={
 'model_configs':[['cfg','model','OpenAI','https://example.invalid/v1/chat/completions','test',60,'original-ref',3]],
 'roles':[['r','name','original role',9,'space','greeting','cfg','short']],
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
for table,items in rows.items():
    for row in items:db.execute(f'INSERT INTO {table} VALUES ({",".join("?" for _ in row)})',row)
db.commit();before=shape(db)
assert valid(db)
for table,column in [('chats','configId'),('messages','chatId'),('roles','defaultConfigId'),('role_appearance','spaceId'),('role_portraits','spaceId'),('role_tool_grants','spaceId'),('request_tool_permissions','requestId'),('tool_invocations','chatId')]:
    db.execute('BEGIN');db.execute(f"UPDATE {table} SET {column}='foreign'")
    assert not valid(db);db.rollback();assert shape(db)==before
# SQLite DDL is transactional here: a failure after the first ALTER must retain the old shape.
db.execute('BEGIN');db.execute(ddl[0]);db.rollback();assert shape(db)==before
db.execute('BEGIN')
for sql in ddl:db.execute(sql)
db.commit();assert shape(db)==shape(create(7))
for entity in schemas[6]['entities']:
    table=entity['tableName'];columns=','.join(f"`{f['columnName']}`" for f in entity['fields'])
    assert list(db.execute(f'SELECT {columns} FROM {table}'))==[tuple(row) for row in rows[table]]
assert db.execute('SELECT userName,userPersona,personaEnabled FROM roles').fetchall()==[('','',0)]
assert valid(db)
report=dict(status='PASS',sourceSha256=hashlib.sha256(source.encode()).hexdigest(),schemaMatchesRoomV7=True,
    allTwelveTablesPreserved=True,invalidAssociationsRejected=8,interruptedDdlRollback=True,newPersonasEmptyDisabled=True,
    limitation='Actual Kotlin SQL on host SQLite only. Room/SQLCipher/Keystore device migration remains NOT_RUN.')
path=repo/'evidence/E2C/persona-migration.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,indent=2))
