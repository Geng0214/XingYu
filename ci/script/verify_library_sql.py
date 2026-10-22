"""Exercise actual DAO SQL, migration preservation, literal search and scoped library mutations."""
import json,pathlib,re,sqlite3
repo=pathlib.Path(__file__).resolve().parents[2]
source=(repo/'app/src/main/java/com/ai/assistance/operit/data/db/AppDatabase.kt').read_text(encoding='utf-8')
schemas={v:json.loads((repo/f'app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json').read_text())['database'] for v in (3,4)}
def create(schema):
    db=sqlite3.connect(':memory:');db.row_factory=sqlite3.Row
    for e in schema['entities']:
        db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
        for i in e.get('indices',[]):db.execute(i['createSql'].replace('${TABLE_NAME}',e['tableName']))
    return db
def shape(db):
    return {e['tableName']:[tuple(r) for r in db.execute('PRAGMA table_info('+e['tableName']+')')] for e in schemas[4]['entities']}
db=create(schemas[3])
for r in ('A','B'):
    db.execute('INSERT INTO roles(id,name,prompt,version,spaceId) VALUES (?,?,?,7,?)',(r,'same','persona-'+r,'space-'+r))
db.execute("INSERT INTO model_configs VALUES ('config','test','OpenAI 兼容','https://example.com/v1/chat/completions','test',90,'existing-ref',4)")
for i,r in enumerate(('A','B')):
    db.execute("INSERT INTO chats(id,roleId,configId,title,createdAt,openingMessage) VALUES (?,?,'config','title',?,'greeting')",('chat-'+r,r,100+i))
    for n in range(120):
        db.execute("INSERT INTO messages(id,chatId,requestId,kind,content,state,createdAt,errorCode,firstTokenMs,elapsedMs) VALUES (?,?,?,'assistant',?,'COMPLETE',?,'',-1,-1)",(f'{r}-{n:03}',f'chat-{r}',f'request-{n}',f'{r}-marker-{n} 中文🙂 100% _ test',n))
db.execute("INSERT INTO role_appearance VALUES ('A','space-A',?,0.2,'rose')",(b'old-wallpaper',))
db.commit()
migration=source.split('val MIGRATION_3_4',1)[1].split('val MIGRATION_2_3',1)[0]
check=re.search(r'db.query\("([^"\n]+)"',migration).group(1)
statements=re.findall(r'db.execSQL\("([^"\n]+)"',migration)
assert len(statements)==3 and db.execute(check).fetchone()[0]==0
with db:
    for sql in statements:db.execute(sql)
assert shape(db)==shape(create(schemas[4]))
assert db.execute('SELECT COUNT(*) FROM messages WHERE favorite=0').fetchone()[0]==240
assert db.execute('SELECT credentialRef FROM model_configs').fetchone()[0]=='existing-ref'
assert db.execute('SELECT wallpaper FROM role_appearance').fetchone()[0]==b'old-wallpaper'
queries=dict((name,sql) for sql,name in re.findall(r'@Query\("([^"\n]+)"\)\s+(?:suspend\s+)?fun\s+(\w+)',source))
for i in range(50):
    r='A' if i%2==0 else 'B';other='B' if r=='A' else 'A'
    args=dict(roleId=r,spaceId='space-'+r,chatId='chat-'+r,messageId=r+'-000',favorite=1,pinned=1,query='中文🙂',favoritesOnly=0,limit=50,offset=0,kind='role')
    rows=db.execute(queries['searchMessages'],args).fetchall()
    assert len(rows)==50 and all(x['content'].startswith(r+'-') for x in rows)
    assert not db.execute(queries['searchMessages'],args|dict(query=other+'-marker')).fetchall()
    assert db.execute(queries['setFavorite'],args).rowcount==1
    assert db.execute(queries['setPinned'],args).rowcount==1
    for bad in (args|dict(spaceId='space-'+other),args|dict(chatId='chat-'+other,messageId=other+'-000')):
        assert db.execute(queries['setFavorite'],bad).rowcount==0
        assert db.execute(queries['setPinned'],bad).rowcount==0
    assert not db.execute(queries['searchMessages'],args|dict(spaceId='space-'+other)).fetchall()
args=dict(roleId='A',spaceId='space-A',query='',favoritesOnly=0,limit=50,offset=0)
pages=[[r['id'] for r in db.execute(queries['searchMessages'],args|dict(offset=offset))] for offset in (0,50,100)]
assert list(map(len,pages))==[50,50,20] and len(set(sum(pages,[])))==120
for literal in ('100%','_','中文🙂',"' OR 1=1 --"):
    result=db.execute(queries['searchMessages'],args|dict(query=literal)).fetchall()
    assert len(result)==(0 if literal.startswith("'") else 50)
assert len(db.execute(queries['searchMessages'],args|dict(favoritesOnly=1)).fetchall())==1
db.execute("UPDATE chats SET pinned=0 WHERE id='chat-B'")
assert db.execute(queries['inbox']).fetchone()['chatId']=='chat-A'
db.execute("UPDATE messages SET state='GENERATING' WHERE id='A-000'")
assert db.execute(queries['setFavorite'],dict(roleId='A',spaceId='space-A',chatId='chat-A',messageId='A-000',favorite=0)).rowcount==0
db.execute("INSERT INTO role_portraits VALUES ('A','space-A','role',?)",(b'private-image',))
assert db.execute(queries['portrait'],dict(roleId='A',spaceId='space-A',kind='role')).fetchone()['image']==b'private-image'
assert not db.execute(queries['portrait'],dict(roleId='A',spaceId='space-B',kind='role')).fetchall()
for sql in ('deleteMessages','deleteChat'):
    db.execute(queries[sql],dict(roleId='A',spaceId='space-A',chatId='chat-A'))
assert not db.execute(queries['searchMessages'],args|dict(favoritesOnly=1)).fetchall()
assert db.execute('SELECT COUNT(*) FROM messages').fetchone()[0]==120
db.execute(queries['deletePortraits'],dict(roleId='A',spaceId='space-A'))
assert db.execute('SELECT COUNT(*) FROM role_portraits').fetchone()[0]==0
broken=create(schemas[3]);broken.execute("INSERT INTO chats VALUES ('bad','missing','missing','title',1,'')");broken.commit()
assert broken.execute(check).fetchone()[0]>0
assert 'pinned' not in [r['name'] for r in broken.execute('PRAGMA table_info(chats)')]
report=dict(status='PASS',scopeAlternations=50,syntheticMessages=240,pages=[50,50,20],literalSearch=True,terminalFavoriteGuard=True,deleteRemovesFavorites=True,portraitIsolation=True,pinning=True,schemaMatchesRoomV4=True,oldWallpaperAndCredentialPreserved=True,orphanDetectedBeforeAlter=True,limitation='Host SQLite only; encrypted Android migration and UI remain device tests.')
out=repo/'evidence/CHAT04/library-sql.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report,indent=2))
