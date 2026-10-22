"""Execute source DAO SQL and migration statements against in-memory SQLite, never user data.

This supplements, but cannot replace, Room/SQLCipher Android instrumentation.
"""
import json, pathlib, re, sqlite3, uuid

repo = pathlib.Path(__file__).resolve().parents[2]
source = (repo / 'app/src/main/java/com/ai/assistance/operit/data/db/AppDatabase.kt').read_text(encoding='utf-8')
schemas = [json.loads((repo / f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text())['database'] for v in (1, 2)]
queries = dict((name, sql) for sql, name in re.findall(r'@Query\("([^"\n]+)"\)\s+(?:suspend\s+)?fun\s+(\w+)', source))
migration = source.split('val MIGRATION_1_2', 1)[1].split('fun open(', 1)[0]
checks = re.findall(r'db.query\("(SELECT COUNT[^"\n]+)"\)\.use', migration)
statements = re.findall(r'db.execSQL\("([^"\n]+)"', migration)
assert len(checks) == 2 and len(statements) == 6

def create(schema):
    db = sqlite3.connect(':memory:')
    for entity in schema['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    return db

def upgrade(db):
    with db:
        for sql in checks:
            assert db.execute(sql).fetchone()[0] == 0, 'invalid ownership'
        for sql in statements:
            if sql.startswith('UPDATE roles SET spaceId='):
                for (role_id,) in db.execute('SELECT id FROM roles').fetchall():
                    db.execute(sql, (str(uuid.uuid4()), role_id))
            else:
                db.execute(sql)

def shape(db):
    return {e['tableName']: (db.execute('PRAGMA table_info('+e['tableName']+')').fetchall(),
                            db.execute('PRAGMA index_list('+e['tableName']+')').fetchall()) for e in schemas[1]['entities']}

db = create(schemas[0])
db.execute("INSERT INTO model_configs VALUES ('config','test','OpenAI 兼容','https://example.com/v1/chat/completions','test',90,'synthetic-ref',4)")
for role in ('A', 'B'):
    db.execute('INSERT INTO roles VALUES (?,?,?,?)', (role, 'same', 'persona-'+role, 7))
    db.execute('INSERT INTO chats VALUES (?,?,?,?,?)', ('chat-'+role, role, 'config', 'title-'+role, 123))
    for n in range(10):
        for kind, suffix in (('user', 'u'), ('assistant', 'a')):
            db.execute('INSERT INTO messages VALUES (?,?,?,?,?,?,?,?,?,?)', (role+suffix+str(n), 'chat-'+role, role+str(n), kind,
                       role+'-marker-'+str(n), 'COMPLETE', n*2+(kind=='assistant'), '', 1, 2))
db.commit()
before_messages = db.execute('SELECT * FROM messages ORDER BY id').fetchall()
upgrade(db)
expected = create(schemas[1])
assert shape(db) == shape(expected), 'Migration schema differs from Room v2 export'
assert before_messages == db.execute('SELECT * FROM messages ORDER BY id').fetchall()
assert db.execute('SELECT credentialRef,version FROM model_configs').fetchone() == ('synthetic-ref', 4)
roles = {row[0]: row[1] for row in db.execute('SELECT id,spaceId FROM roles')}
assert all(roles.values()) and len(set(roles.values())) == 2

for n in range(50):
    own, other = ('A','B') if n%2 == 0 else ('B','A')
    params = dict(roleId=own, spaceId=roles[own], chatId='chat-'+own)
    rows = db.execute(queries['history'],params).fetchall()
    assert len(rows)==20 and all(other+'-marker' not in row[4] for row in rows)
    for method in ('chat','history','messages'):
        assert not db.execute(queries[method],params | {'chatId':'chat-'+other}).fetchall()
        assert not db.execute(queries[method],params | {'spaceId':roles[other]}).fetchall()
    before = db.execute('SELECT * FROM messages').fetchall()
    wrong = params | dict(chatId='chat-'+other, requestId=other+'0', id=other+'a0', text='injected', state='COMPLETE',error='',first=1,elapsed=2)
    assert db.execute(queries['updateActive'],wrong).rowcount==0
    db.execute(queries['deleteMessages'], params | {'chatId':'chat-'+other})
    db.execute(queries['deleteChat'], params | {'chatId':'chat-'+other})
    assert before == db.execute('SELECT * FROM messages').fetchall()

# State and identity guards must also hold for an active row.
db.execute("UPDATE messages SET state='GENERATING' WHERE id='Aa0'")
update=dict(roleId='A',spaceId=roles['A'],chatId='chat-A',requestId='A0',id='Aa0',text='partial',state='CANCELLED',error='',first=1,elapsed=2)
assert db.execute(queries['updateActive'],update | {'requestId':'B0'}).rowcount==0
assert db.execute(queries['updateActive'],update).rowcount==1
assert db.execute(queries['updateActive'],update | {'text':'late','state':'COMPLETE'}).rowcount==0
db.execute("UPDATE messages SET state='WAITING' WHERE id='Ba0'")
db.execute(queries['recoverInterrupted'])
assert db.execute("SELECT state,errorCode FROM messages WHERE id='Ba0'").fetchone()==('FAILED','INTERRUPTED')
for method in ('deleteRoleMessages','deleteRoleChats','deleteRole'):
    db.execute(queries[method],dict(roleId='A',spaceId=roles['A']))
assert db.execute('SELECT COUNT(*) FROM messages').fetchone()[0]==20
assert db.execute('SELECT COUNT(*) FROM model_configs').fetchone()[0]==1

broken=create(schemas[0])
broken.execute("INSERT INTO chats VALUES ('orphan','missing','missing','private-title',123)")
broken.commit()
before=broken.execute("SELECT sql FROM sqlite_master ORDER BY name").fetchall()
try:
    upgrade(broken)
    raise RuntimeError('Invalid ownership accepted')
except AssertionError as error:
    assert str(error)=='invalid ownership'
assert before==broken.execute("SELECT sql FROM sqlite_master ORDER BY name").fetchall()
assert broken.execute('SELECT title FROM chats').fetchone()[0]=='private-title'

report=dict(status='PASS', alternatingScopes=50, migrationSchemaMatchesRoomV2=True, existingRowsPreserved=True,
            orphanMigrationRejected=True, guardedWritesAndDeletes=True,
            limitation='Host SQLite in memory. Android Room transactions, SQLCipher, Keystore and APK upgrade require device tests.')
out=repo/'evidence/UI03/sql-host-check.json'
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
