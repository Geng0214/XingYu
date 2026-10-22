"""Verify the actual v2->v3 SQL and image scope queries in memory, without user data."""
import json,pathlib,re,sqlite3
repo=pathlib.Path(__file__).resolve().parents[2]
source=(repo/'app/src/main/java/com/ai/assistance/operit/data/db/AppDatabase.kt').read_text(encoding='utf-8')
schemas=[json.loads((repo/f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text())['database'] for v in (2,3)]
def create(schema):
    db=sqlite3.connect(':memory:')
    for e in schema['entities']:
        db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
        for index in e.get('indices',[]):db.execute(index['createSql'].replace('${TABLE_NAME}',e['tableName']))
    return db
def shape(db):
    return {e['tableName']:(db.execute('PRAGMA table_info('+e['tableName']+')').fetchall(),db.execute('PRAGMA index_list('+e['tableName']+')').fetchall()) for e in schemas[1]['entities']}
db=create(schemas[0])
for r in ('A','B'):db.execute('INSERT INTO roles(id,name,prompt,version,spaceId,openingMessage,defaultConfigId) VALUES (?,?,?,7,?,?,NULL)',(r,'same','persona-'+r,'space-'+r,'hello'))
db.execute("INSERT INTO model_configs VALUES ('config','test','OpenAI 兼容','https://example.com/v1/chat/completions','test',90,'existing-ref',4)")
db.execute("INSERT INTO chats VALUES ('chat-A','A','config','title',123,'greeting')")
db.execute("INSERT INTO messages VALUES ('m','chat-A','r','assistant','existing-text','COMPLETE',123,'',1,2)")
db.commit()
migration=source.split('val MIGRATION_2_3',1)[1].split('val MIGRATION_1_2',1)[0]
statements=re.findall(r'db.execSQL\("([^"\n]+)"',migration)
assert len(statements)==3
with db:
    for sql in statements:db.execute(sql)
expected=create(schemas[1])
assert shape(db)==shape(expected)
assert db.execute('SELECT content,bubbleStyle FROM messages').fetchone()==('existing-text','paragraph')
assert db.execute("SELECT version,spaceId,replyStyle FROM roles WHERE id='A'").fetchone()==(7,'space-A','short')
assert db.execute('SELECT credentialRef FROM model_configs').fetchone()[0]=='existing-ref'
db.execute("INSERT INTO role_appearance VALUES ('A','space-A',?,0.25,'rose')",(b'synthetic-image-pixels',))
queries=dict((name,sql) for sql,name in re.findall(r'@Query\("([^"\n]+)"\)\s+(?:suspend\s+)?fun\s+(\w+)',source))
assert db.execute(queries['readAppearance'],dict(roleId='A',spaceId='space-A')).fetchone()[2]==b'synthetic-image-pixels'
for scope in (dict(roleId='B',spaceId='space-A'),dict(roleId='A',spaceId='space-B'),dict(roleId='B',spaceId='space-B')):
    assert not db.execute(queries['readAppearance'],scope).fetchall()
# The current inbox includes v4 pinning; its execution is covered by verify_library_sql.py.
db.execute(queries['deleteAppearance'],dict(roleId='A',spaceId='space-A'))
assert db.execute('SELECT COUNT(*) FROM role_appearance').fetchone()[0]==0
report=dict(status='PASS',schemaMatchesRoomV3=True,oldTextAndCredentialsPreserved=True,oldMessagesKeepParagraphStyle=True,roleSpaceUnchanged=True,wallpaperScopeGuards=True,limitation='In-memory host SQLite; Android encrypted image storage, migration and visual behavior require device validation.')
out=repo/'evidence/UI03/migration3-host-check.json'
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
