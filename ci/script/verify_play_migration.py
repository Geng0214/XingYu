"""Execute production play migration and scoped DAO statements on host SQLite, not SQLCipher."""
import hashlib
import json
import pathlib
import re
import sqlite3

repo = pathlib.Path(__file__).resolve().parents[2]
migration = repo / 'app/src/main/java/com/ai/assistance/operit/data/db/PlayMigration.kt'
storage = repo / 'app/src/main/java/com/ai/assistance/operit/data/db/PlayStorage.kt'
source = migration.read_text(encoding='utf-8')
dao = storage.read_text(encoding='utf-8')
ddl = re.findall(r'db\.execSQL\("([^"\n]+)"', source)
assert len(ddl) == 9
schemas = {v: json.loads((repo / f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text(encoding='utf-8'))['database'] for v in (28, 29)}

def create(version):
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    for entity in schemas[version]['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    return db

def shape(db):
    return {t: {p: list(db.execute(f'PRAGMA {p}(`{t}`)')) for p in ('table_info', 'index_list', 'foreign_key_list')} for (t,) in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")}

def insert(db, table, values):
    fields = list(db.execute(f'PRAGMA table_info(`{table}`)'))
    row = {f[1]: None if not f[3] else {'TEXT': '', 'INTEGER': 0, 'REAL': 0.0, 'BLOB': b''}[f[2]] for f in fields}
    row.update(values)
    db.execute(f"INSERT INTO `{table}` ({','.join('`'+k+'`' for k in row)}) VALUES ({','.join('?' for k in row)})", list(row.values()))

db = create(28)
insert(db, 'model_configs', dict(id='cfg', credentialRef='KEEP-REFERENCE', version=7))
for role in ('A', 'B'):
    insert(db, 'roles', dict(id=role, spaceId='space-'+role, name='same', prompt='KEEP-PERSONA-'+role))
    insert(db, 'chats', dict(id='chat-'+role, roleId=role, configId='cfg', title='KEEP-CHAT-'+role))
    insert(db, 'messages', dict(id='message-'+role, chatId='chat-'+role, requestId='legacy-'+role, kind='assistant', content='KEEP-MESSAGE-'+role, state='COMPLETE'))
db.commit()
before = shape(db)
old_rows = {t: list(db.execute(f'SELECT * FROM `{t}`')) for t in before}
db.execute('BEGIN')
for sql in ddl[:5]:
    db.execute(sql)
db.rollback()
assert shape(db) == before
for query in re.findall(r'db\.query\("([^"\n]+)"', source):
    result = db.execute(query).fetchall()
    assert result == [] if query.startswith('PRAGMA') else result == [(0,)]
for sql in ddl:
    db.execute(sql)
db.commit()
assert shape(db) == shape(create(29))
for table, rows in old_rows.items():
    assert list(db.execute(f'SELECT * FROM `{table}`')) == rows
assert all(db.execute(f'SELECT COUNT(*) FROM {t}').fetchone()[0] == 0 for t in ('play_sessions', 'play_requests'))

def session(role, **extra):
    return dict(id='game-'+role, roleId=role, spaceId='space-'+role, chatId='chat-'+role, title='game', stateJson='{}', version=11, status='ACTIVE') | extra

for role in ('A', 'B'):
    insert(db, 'play_sessions', session(role))
    for n in range(10):
        insert(db, 'play_requests', dict(requestId=f'{role}-{n}', activityId='game-'+role, roleId=role, spaceId='space-'+role, chatId='chat-'+role,
               assistantId=f'assistant-{role}-{n}', activityVersion=n+1, content=f'ONLY_{role}_{n}', state='COMPLETE'))
db.commit()
queries = re.findall(r'@Query\("([^"\n]+)"\)', dao)
read = next(q for q in queries if q.startswith('SELECT q.*') and 'q.requestId=:request' in q)
history = next(q for q in queries if q.startswith('SELECT q.*') and 'q.requestId=:request' not in q)
update = next(q for q in queries if q.startswith('UPDATE play_requests'))
for n in range(50):
    role = 'A' if n % 2 == 0 else 'B'
    other = 'B' if role == 'A' else 'A'
    params = dict(id='game-'+role, role=role, space='space-'+role, chat='chat-'+role, request=f'{role}-{n%10}')
    rows = db.execute(history, params).fetchall()
    assert len(rows) == 10 and all('ONLY_'+other not in str(row) for row in rows)
    assert len(db.execute(read, params).fetchall()) == 1
    for field, wrong in [('id', 'game-'+other), ('role', other), ('space', 'space-'+other), ('chat', 'chat-'+other), ('request', other+'-0')]:
        assert db.execute(read, params | {field: wrong}).fetchall() == []

for bad in [session('A', id='bad-space', spaceId='space-B'), session('A', id='bad-chat', chatId='chat-B')]:
    try:
        insert(db, 'play_sessions', bad)
        raise AssertionError('Expected composite foreign key rejection')
    except sqlite3.IntegrityError:
        db.rollback()
try:
    insert(db, 'play_requests', dict(requestId='bad-request', activityId='game-A', roleId='B', spaceId='space-B', chatId='chat-B', assistantId='bad-assistant'))
    raise AssertionError('Expected request ownership rejection')
except sqlite3.IntegrityError:
    db.rollback()

for n in range(20):
    rid = f'pending-{n}'
    version = 100+n
    db.execute("UPDATE play_sessions SET requestId=?,version=? WHERE id='game-A'", (rid, version))
    insert(db, 'play_requests', dict(requestId=rid, activityId='game-A', roleId='A', spaceId='space-A', chatId='chat-A', activityVersion=version, assistantId='reply-'+rid, state='WAITING'))
    args = dict(role='A', space='space-A', chat='chat-A', id='game-A', request=rid, billing='{}', state='GENERATING', text='PARTIAL', error='', first=1, elapsed=2)
    assert db.execute(update, args).rowcount == 1
    assert db.execute(update, args | dict(role='B')).rowcount == 0
    db.execute("UPDATE play_sessions SET requestId=NULL,version=version+1 WHERE id='game-A'")
    assert db.execute(update, args | dict(text='LATE', elapsed=3)).rowcount == 0
    assert db.execute('SELECT content FROM play_requests WHERE requestId=?', (rid,)).fetchone()[0] == 'PARTIAL'
db.commit()
db.execute("DELETE FROM chats WHERE id='chat-A'")
assert db.execute("SELECT COUNT(*) FROM play_sessions WHERE roleId='A'").fetchone()[0] == 0
assert db.execute("SELECT COUNT(*) FROM play_requests WHERE roleId='A'").fetchone()[0] == 0
assert db.execute("SELECT COUNT(*) FROM play_requests WHERE roleId='B'").fetchone()[0] == 10
assert db.execute("SELECT credentialRef FROM model_configs WHERE id='cfg'").fetchone()[0] == 'KEEP-REFERENCE'
assert db.execute('PRAGMA foreign_key_check').fetchall() == []
print(json.dumps(dict(status='PASS',migrationSha256=hashlib.sha256(source.encode()).hexdigest(),daoSha256=hashlib.sha256(dao.encode()).hexdigest(),schemaMatchesRoom29=True,
    oldRowsPreserved=True,ddlRollback=True,alternatingScopedReads=50,markersPerRole=10,lateUpdateRounds=20,compositeForeignKeyRejection=True,chatCascade=True,
    environment='host SQLite; not Android or SQLCipher'), indent=2))
