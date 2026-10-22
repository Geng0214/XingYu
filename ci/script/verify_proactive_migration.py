"""Execute shipped 21->22 proactive DDL on host SQLite; this does not claim Android SQLCipher execution."""
import hashlib,json,pathlib,re,sqlite3

repo=pathlib.Path(__file__).resolve().parents[2]
source_path=repo/"app/src/main/java/com/ai/assistance/operit/data/db/ProactiveMigration.kt"
source=source_path.read_text(encoding="utf-8")
check=re.search(r'db\.query\("(SELECT \(SELECT COUNT\(\*\)[^"\n]+)"\)',source)
ddl=re.findall(r'db\.execSQL\("([^"\n]+)"',source)
assert check and len(ddl)==12
schema_root=repo/"app/schemas/com.ai.assistance.operit.data.db.AppDatabase"
schemas={v:json.loads((schema_root/f"{v}.json").read_text(encoding="utf-8"))["database"] for v in (21,22)}

def create(version):
    db=sqlite3.connect(":memory:")
    for entity in schemas[version]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}",entity["tableName"]))
        for index in entity.get("indices",[]):db.execute(index["createSql"].replace("${TABLE_NAME}",entity["tableName"]))
    return db
def shape(db):
    tables=[r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    return {t:{"columns":list(db.execute(f"PRAGMA table_info(`{t}`)")),"indices":list(db.execute(f"PRAGMA index_list(`{t}`)"))} for t in tables}
def insert(db,table,values):db.execute(f"INSERT INTO `{table}` ({','.join(f'`{c}`' for c in values)}) VALUES ({','.join('?' for _ in values)})",tuple(values.values()))

db=create(21)
insert(db,"model_configs",{"id":"cfg","name":"model","provider":"OpenAI compatible","endpoint":"https://example.invalid/v1/chat/completions","model":"test","timeoutSeconds":60,"credentialRef":"ref","version":1,"inputPriceMicrosPerMillion":1,"outputPriceMicrosPerMillion":1,"priceCurrency":"USD","visionEnabled":0})
insert(db,"roles",{"id":"role","name":"name","prompt":"prompt","version":1,"spaceId":"space","openingMessage":"","defaultConfigId":"cfg","replyStyle":"short","userName":"","userPersona":"","personaEnabled":0,"conversationMode":"character"})
insert(db,"planner_entries",{"id":"plan","roleId":"role","spaceId":"space","kind":"task","title":"title","note":"","startsAt":1000,"endsAt":None,"zoneId":"Asia/Shanghai","completedAt":None,"reminderMinutesBefore":None,"shareWithRole":0,"version":1,"createdAt":1,"updatedAt":1})
db.commit();old_tables=[r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")];before={t:list(db.execute(f"SELECT * FROM `{t}`")) for t in old_tables};before_shape=shape(db)
db.execute("BEGIN");db.execute("UPDATE planner_entries SET spaceId='broken' WHERE id='plan'");assert db.execute(check.group(1)).fetchone()[0]>0;db.rollback();assert shape(db)==before_shape
db.execute("BEGIN");[db.execute(sql) for sql in ddl];db.commit();assert shape(db)==shape(create(22))
for table in old_tables:assert list(db.execute(f"SELECT * FROM `{table}`"))==before[table]
assert db.execute("SELECT COUNT(*) FROM proactive_role_settings").fetchone()[0]==0 and db.execute("SELECT COUNT(*) FROM proactive_requests").fetchone()[0]==0
report={"status":"PASS","sourceSha256":hashlib.sha256(source.encode()).hexdigest(),"schemaMatchesRoomV22":True,"allLegacyRowsPreserved":True,"legacySettings":0,"legacyRequests":0,"interruptedDdlRollback":True,"limitation":"Actual migration SQL on host SQLite. Room/SQLCipher/Keystore device migration remains NOT_RUN."}
path=repo/"evidence/E4G/proactive-migration.json";path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8");print(json.dumps(report,indent=2))
