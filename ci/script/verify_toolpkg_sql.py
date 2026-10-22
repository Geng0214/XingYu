"""Execute the actual 5->6 SQL and compare with Room's schema; this is not SQLCipher/JNI runtime QA."""
import json
import pathlib
import re
import sqlite3

repo = pathlib.Path(__file__).resolve().parents[2]
source = (repo / 'app/src/main/java/com/ai/assistance/operit/data/db/ToolPkgRepository.kt').read_text(encoding='utf-8').split('object ToolPkgMigration:')[1]
checks = re.findall(r'db.query\("([^"\n]+)"', source)
ddl = re.findall(r'db.execSQL\("([^"\n]+)"', source)
schemas = {v: json.loads((repo / f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text())['database'] for v in (5, 6)}

def create(version):
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    for entity in schemas[version]['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    return db

def insert(db, table, **values):
    db.execute(f"INSERT INTO {table} ({','.join(values)}) VALUES ({','.join('?' for _ in values)})", tuple(values.values()))

def shape(db):
    return {e['tableName']: (list(db.execute('PRAGMA table_info('+e['tableName']+')')),
                           list(db.execute('PRAGMA foreign_key_list('+e['tableName']+')')))
            for e in schemas[6]['entities']}

def valid(db):
    return all(not db.execute(q).fetchall() if q.startswith('PRAGMA') else db.execute(q).fetchone()[0] == 0 for q in checks)

db = create(5)
insert(db, 'roles', id='A', spaceId='space-A', name='A', prompt='private-persona', version=7)
insert(db, 'model_configs', id='model', name='model', provider='test', endpoint='https://example.invalid/chat', model='test', timeoutSeconds=90, credentialRef='preserved-ref', version=3)
insert(db, 'chats', id='chat', roleId='A', configId='model', title='private-title', createdAt=1, pinned=1)
insert(db, 'messages', id='answer', chatId='chat', requestId='request', kind='assistant', content='partial-private', state='GENERATING', createdAt=1, errorCode='', firstTokenMs=1, elapsedMs=1, favorite=1)
insert(db, 'role_appearance', roleId='A', spaceId='space-A', wallpaper=b'private-wallpaper', dimming=0.2, palette='rose')
insert(db, 'role_portraits', roleId='A', spaceId='space-A', kind='role', image=b'private-avatar')
insert(db, 'mcp_extensions', id='ext', name='Echo', endpoint='https://example.invalid/mcp', credentialRef='mcp-ref', timeoutMs=1000, enabled=1, version=4)
insert(db, 'extension_tools', extensionId='ext', name='echo', description='description', inputSchema='{}')
insert(db, 'role_tool_grants', roleId='A', spaceId='space-A', extensionId='ext', toolName='echo', enabled=1, version=3)
insert(db, 'request_tool_permissions', requestId='request', extensionId='ext', toolName='echo', roleId='A', spaceId='space-A', chatId='chat', assistantId='answer', configVersion=4, grantVersion=3, endpoint='https://example.invalid/mcp', credentialRef='mcp-ref', timeoutMs=1000, description='description', inputSchema='{}')
insert(db, 'tool_invocations', requestId='request', callId='call', roleId='A', spaceId='space-A', chatId='chat', assistantId='answer', extensionId='ext', toolName='echo', arguments='private-args', result='', state='RUNNING', errorCode='', createdAt=1, finishedAt=0)
db.commit()
before = {e['tableName']: list(db.execute('SELECT * FROM '+e['tableName'])) for e in schemas[5]['entities']}
assert len(checks) == 4 and len(ddl) == 1 and valid(db)
# Simulate individually corrupted scope/request references: each is detectable before any DDL.
for table, column in [('role_tool_grants', 'spaceId'), ('request_tool_permissions', 'requestId'), ('tool_invocations', 'chatId')]:
    with db:
        db.execute('UPDATE '+table+' SET '+column+"='foreign'")
        assert not valid(db)
        assert not db.execute("SELECT name FROM sqlite_master WHERE name='toolpkg_packages'").fetchall()
        db.rollback()
assert valid(db)
with db:
    for sql in ddl:
        db.execute(sql)
assert shape(db) == shape(create(6))
for table, rows in before.items():
    assert list(db.execute('SELECT * FROM '+table)) == rows
for n in range(2):
    insert(db, 'mcp_extensions', id=f'local-{n}', name='same-name', endpoint='toolpkg:sha256:'+'a'*64, credentialRef=None, timeoutMs=10000, enabled=0, version=1)
    insert(db, 'toolpkg_packages', extensionId=f'local-{n}', packageId='same-publisher-id', packageVersion='1.0.0', apiVersion='1.0.0', sha256='a'*64, archive=b'synthetic-private-package', installedAt=n)
assert db.execute('SELECT COUNT(*) FROM toolpkg_packages').fetchone()[0] == 2
db.execute("DELETE FROM mcp_extensions WHERE id='local-0'")
assert db.execute('SELECT extensionId FROM toolpkg_packages').fetchall() == [('local-1',)]
assert db.execute('SELECT credentialRef FROM model_configs').fetchone()[0] == 'preserved-ref'
assert not db.execute('PRAGMA foreign_key_check').fetchall()
report = dict(status='PASS', schemaMatchesRoomV6=True, allV5RowsPreserved=True, invalidGrantPermissionAndInvocationRejectedBeforeDdl=True,
              duplicatePublisherIdentityDoesNotOverwrite=True, uninstallCascadesOnlyOwnPackage=True,
              limitation='Host SQLite runs actual migration SQL. Android SQLCipher, repository, JNI and upgrade execution remain NOT_RUN.')
(repo / 'evidence/E1/toolpkg-sql.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
print(json.dumps(report, indent=2))
