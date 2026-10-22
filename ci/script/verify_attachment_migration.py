"""Execute shipped 10->11 DDL on host SQLite; this does not claim Android SQLCipher execution."""
import hashlib
import json
import pathlib
import re
import sqlite3


repo = pathlib.Path(__file__).resolve().parents[2]
source_path = repo / "app/src/main/java/com/ai/assistance/operit/data/db/AttachmentMigration.kt"
source = source_path.read_text(encoding="utf-8")
check = re.search(r'db\.query\("(SELECT \(SELECT COUNT\(\*\)[^"\n]+)"\)', source)
ddl = re.findall(r'db\.execSQL\("([^"\n]+)"', source)
assert check and len(ddl) == 4

schema_root = repo / "app/schemas/com.ai.assistance.operit.data.db.AppDatabase"
schemas = {
    version: json.loads((schema_root / f"{version}.json").read_text(encoding="utf-8"))["database"]
    for version in (10, 11)
}


def create(version: int) -> sqlite3.Connection:
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    for entity in schemas[version]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return db


def shape(db: sqlite3.Connection) -> dict:
    tables = [row[0] for row in db.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
    )]
    return {
        table: {
            "columns": list(db.execute(f"PRAGMA table_info(`{table}`)")),
            "indices": list(db.execute(f"PRAGMA index_list(`{table}`)")),
            "foreignKeys": list(db.execute(f"PRAGMA foreign_key_list(`{table}`)")),
        }
        for table in tables
    }


def valid(db: sqlite3.Connection) -> bool:
    return db.execute(check.group(1)).fetchone()[0] == 0


db = create(10)
db.execute(
    "INSERT INTO model_configs "
    "(id,name,provider,endpoint,model,timeoutSeconds,credentialRef,version,inputPriceMicrosPerMillion,"
    "outputPriceMicrosPerMillion,priceCurrency) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
    ("cfg", "original model", "OpenAI 兼容", "https://example.invalid/v1/chat/completions", "vision-model", 60,
     "original-ref", 7, 1000, 2000, "CNY"),
)
db.execute(
    "INSERT INTO roles "
    "(id,name,prompt,version,spaceId,openingMessage,defaultConfigId,replyStyle,userName,userPersona,personaEnabled,"
    "conversationMode) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
    ("role", "original role", "original prompt", 7, "space", "hello", "cfg", "short", "me", "private", 1, "character"),
)
db.execute(
    "INSERT INTO chats (id,roleId,configId,title,createdAt,openingMessage,pinned) VALUES (?,?,?,?,?,?,?)",
    ("chat", "role", "cfg", "original title", 2, "hello", 1),
)
db.execute(
    "INSERT INTO messages "
    "(id,chatId,requestId,kind,content,state,createdAt,errorCode,firstTokenMs,elapsedMs,bubbleStyle,favorite) "
    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
    ("message", "chat", "request", "user", "original private body", "COMPLETE", 3, "", 0, 0, "short", 1),
)
db.commit()
before_shape = shape(db)
before_rows = {
    "model": db.execute("SELECT * FROM model_configs").fetchall(),
    "role": db.execute("SELECT * FROM roles").fetchall(),
    "chat": db.execute("SELECT * FROM chats").fetchall(),
    "message": db.execute("SELECT * FROM messages").fetchall(),
}
assert valid(db)

for sql in (
    "UPDATE model_configs SET version=0",
    "UPDATE messages SET chatId='missing-chat'",
):
    db.execute("BEGIN")
    db.execute(sql)
    assert not valid(db)
    db.rollback()
    assert shape(db) == before_shape

db.execute("BEGIN")
for sql in ddl[:2]:
    db.execute(sql)
db.rollback()
assert shape(db) == before_shape

db.execute("BEGIN")
for sql in ddl:
    db.execute(sql)
db.commit()

assert shape(db) == shape(create(11))
assert db.execute("SELECT visionEnabled FROM model_configs").fetchall() == [(0,)]
assert db.execute("SELECT COUNT(*) FROM message_attachments").fetchone()[0] == 0
assert db.execute("PRAGMA foreign_key_check").fetchall() == []
assert db.execute(
    "SELECT id,name,provider,endpoint,model,timeoutSeconds,credentialRef,version,"
    "inputPriceMicrosPerMillion,outputPriceMicrosPerMillion,priceCurrency FROM model_configs"
).fetchall() == before_rows["model"]
assert db.execute("SELECT * FROM roles").fetchall() == before_rows["role"]
assert db.execute("SELECT * FROM chats").fetchall() == before_rows["chat"]
assert db.execute("SELECT * FROM messages").fetchall() == before_rows["message"]
assert valid(db)

report = {
    "status": "PASS",
    "sourceSha256": hashlib.sha256(source.encode()).hexdigest(),
    "schemaMatchesRoomV11": True,
    "originalConfigRoleChatMessagePreserved": True,
    "legacyVisionEnabled": False,
    "newAttachmentRows": 0,
    "invalidPreMigrationAssociationsRejected": 2,
    "interruptedDdlRollback": True,
    "limitation": "Actual migration SQL on host SQLite. Room/SQLCipher/Keystore device migration remains NOT_RUN.",
}
path = repo / "evidence/E3A/attachment-migration.json"
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
