"""Execute shipped 20->21 planner DDL on host SQLite; this does not claim Android SQLCipher execution."""
import hashlib,json,pathlib,re,sqlite3

repo=pathlib.Path(__file__).resolve().parents[2]
source_path=repo/"app/src/main/java/com/ai/assistance/operit/data/db/PlannerMigration.kt"
source=source_path.read_text(encoding="utf-8")
check=re.search(r'db\.query\("(SELECT COUNT\(\*\)[^"\n]+)"\)',source)
ddl=re.findall(r'db\.execSQL\("([^"\n]+)"',source)
assert check and len(ddl)==6
schema_root=repo/"app/schemas/com.ai.assistance.operit.data.db.AppDatabase"
schemas={v:json.loads((schema_root/f"{v}.json").read_text(encoding="utf-8"))["database"] for v in (20,21)}

def create(version):
    db=sqlite3.connect(":memory:");db.execute("PRAGMA foreign_keys=ON")
    for entity in schemas[version]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}",entity["tableName"]))
        for index in entity.get("indices",[]):db.execute(index["createSql"].replace("${TABLE_NAME}",entity["tableName"]))
    return db

def shape(db):
    tables=[r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    return {t:{"columns":list(db.execute(f"PRAGMA table_info(`{t}`)")),"indices":list(db.execute(f"PRAGMA index_list(`{t}`)")),"foreignKeys":list(db.execute(f"PRAGMA foreign_key_list(`{t}`)"))} for t in tables}

def insert(db,table,values):
    db.execute(f"INSERT INTO `{table}` ({','.join(f'`{c}`' for c in values)}) VALUES ({','.join('?' for _ in values)})",tuple(values.values()))

def valid(db):return db.execute(check.group(1)).fetchone()[0]==0 and not db.execute("PRAGMA foreign_key_check").fetchall()

db=create(20)
insert(db,"model_configs",{"id":"cfg","name":"model","provider":"OpenAI compatible","endpoint":"https://example.invalid/v1/chat/completions","model":"test","timeoutSeconds":60,"credentialRef":"ref","version":1,"inputPriceMicrosPerMillion":None,"outputPriceMicrosPerMillion":None,"priceCurrency":"USD","visionEnabled":0})
insert(db,"roles",{"id":"role","name":"name","prompt":"prompt","version":1,"spaceId":"space","openingMessage":"","defaultConfigId":"cfg","replyStyle":"short","userName":"","userPersona":"","personaEnabled":0,"conversationMode":"character"})
insert(db,"environment_profiles",{"roleId":"role","spaceId":"space","timeContextEnabled":1,"weatherContextEnabled":0,"provider":"open-meteo","locationId":None,"locationName":None,"admin1":None,"country":None,"countryCode":None,"latitude":None,"longitude":None,"timezone":None,"weatherTime":None,"temperature":None,"apparentTemperature":None,"humidity":None,"precipitation":None,"weatherCode":None,"windSpeed":None,"todayMin":None,"todayMax":None,"todayPrecipitationProbability":None,"fetchedAt":None,"version":1,"updatedAt":10})
db.commit();before_shape=shape(db);old_tables=[r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")];before_rows={t:list(db.execute(f"SELECT * FROM `{t}`")) for t in old_tables};assert valid(db)
db.execute("BEGIN");db.execute("UPDATE environment_profiles SET spaceId='foreign'");assert not valid(db);db.rollback();assert shape(db)==before_shape
db.execute("BEGIN");db.execute(ddl[0]);db.rollback();assert shape(db)==before_shape
db.execute("BEGIN");[db.execute(sql) for sql in ddl];db.commit();assert shape(db)==shape(create(21))
for table in old_tables:assert list(db.execute(f"SELECT * FROM `{table}`"))==before_rows[table]
assert db.execute("SELECT COUNT(*) FROM planner_entries").fetchone()[0]==0 and db.execute("SELECT COUNT(*) FROM cycle_records").fetchone()[0]==0
report={"status":"PASS","sourceSha256":hashlib.sha256(source.encode()).hexdigest(),"schemaMatchesRoomV21":True,"allLegacyRowsPreserved":True,"legacyPlannerRows":0,"legacyCycleRows":0,"invalidPreMigrationAssociationsRejected":1,"interruptedDdlRollback":True,"limitation":"Actual migration SQL on host SQLite. Room/SQLCipher/Keystore device migration remains NOT_RUN."}
path=repo/"evidence/E4F/planner-migration.json";path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8");print(json.dumps(report,indent=2))
