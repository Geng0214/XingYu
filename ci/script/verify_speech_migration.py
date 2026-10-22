"""Execute shipped 22->23 speech DDL on host SQLite; this does not claim Android SQLCipher execution."""
import argparse,hashlib,json,pathlib,re,sqlite3

repo=pathlib.Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--stage",default="E5B_FIX")
args=parser.parse_args()
assert re.fullmatch(r"[A-Z0-9_-]+",args.stage)
source_path=repo/"app/src/main/java/com/ai/assistance/operit/data/db/SpeechMigration.kt"
source=source_path.read_text(encoding="utf-8")
ddl=re.findall(r'db\.execSQL\("([^"\n]+)"',source)
assert len(ddl)==1
schema_root=repo/"app/schemas/com.ai.assistance.operit.data.db.AppDatabase"
schemas={v:json.loads((schema_root/f"{v}.json").read_text(encoding="utf-8"))["database"] for v in (22,23)}

def create(version):
    db=sqlite3.connect(":memory:");db.execute("PRAGMA foreign_keys=ON")
    for entity in schemas[version]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}",entity["tableName"]))
        for index in entity.get("indices",[]):db.execute(index["createSql"].replace("${TABLE_NAME}",entity["tableName"]))
    return db

def shape(db):
    tables=[r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    return {t:{"columns":list(db.execute(f"PRAGMA table_info(`{t}`)")),"indices":list(db.execute(f"PRAGMA index_list(`{t}`)")),"foreignKeys":list(db.execute(f"PRAGMA foreign_key_list(`{t}`)"))} for t in tables}

db=create(22)
db.execute("INSERT INTO model_configs VALUES ('cfg','model','OpenAI compatible','https://example.invalid/v1/chat/completions','test',60,'ref',1,NULL,NULL,'USD',0)")
db.execute("INSERT INTO roles VALUES ('role','name','prompt',1,'space','',NULL,'short','','',0,'character')")
db.commit();before_shape=shape(db);old_tables=list(before_shape);before_rows={t:list(db.execute(f"SELECT * FROM `{t}`")) for t in old_tables}
assert not db.execute("PRAGMA foreign_key_check").fetchall()
db.execute("BEGIN");db.execute(ddl[0]);db.rollback();assert shape(db)==before_shape
db.execute("BEGIN");db.execute(ddl[0]);db.commit();assert shape(db)==shape(create(23))
for table in old_tables:assert list(db.execute(f"SELECT * FROM `{table}`"))==before_rows[table]
assert db.execute("SELECT COUNT(*) FROM speech_service_configs").fetchone()[0]==0
report={"status":"PASS","sourceSha256":hashlib.sha256(source.encode()).hexdigest(),"schemaMatchesRoomV23":True,"allLegacyRowsPreserved":True,"legacySpeechConfigs":0,"interruptedDdlRollback":True,"limitation":"Actual migration SQL on host SQLite. Room/SQLCipher/Keystore device migration remains NOT_RUN."}
path=repo/"evidence"/args.stage/"speech-migration.json";path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8");print(json.dumps(report,indent=2))
