"""Execute shipped 17->18 memory recall DDL on host SQLite; this does not claim Android SQLCipher execution."""
import hashlib
import json
import pathlib
import re
import sqlite3

repo = pathlib.Path(__file__).resolve().parents[2]
source_path = repo / "app/src/main/java/com/ai/assistance/operit/data/db/MemoryRecallMigration.kt"
source = source_path.read_text(encoding="utf-8")
check = re.search(r'db\.query\("(SELECT COUNT\(\*\)[^"\n]+)"\)', source)
ddl = re.findall(r'db\.execSQL\("([^"\n]+)"', source)
assert check and len(ddl) == 5

schema_root = repo / "app/schemas/com.ai.assistance.operit.data.db.AppDatabase"
schemas = {version: json.loads((schema_root / f"{version}.json").read_text(encoding="utf-8"))["database"] for version in (17, 18)}


def create(version: int) -> sqlite3.Connection:
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    for entity in schemas[version]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return db


def shape(db: sqlite3.Connection) -> dict:
    tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    return {table: {"columns": list(db.execute(f"PRAGMA table_info(`{table}`)")), "indices": list(db.execute(f"PRAGMA index_list(`{table}`)")),
                    "foreignKeys": list(db.execute(f"PRAGMA foreign_key_list(`{table}`)"))} for table in tables}


def insert(db: sqlite3.Connection, table: str, values: dict) -> None:
    columns = ",".join(f"`{column}`" for column in values)
    db.execute(f"INSERT INTO `{table}` ({columns}) VALUES ({','.join('?' for _ in values)})", tuple(values.values()))


def valid(db: sqlite3.Connection) -> bool:
    return db.execute(check.group(1)).fetchone()[0] == 0 and not db.execute("PRAGMA foreign_key_check").fetchall()


db = create(17)
insert(db, "model_configs", {"id": "cfg", "name": "model", "provider": "OpenAI 兼容", "endpoint": "https://example.invalid/v1/chat/completions",
    "model": "test", "timeoutSeconds": 60, "credentialRef": "original-ref", "version": 3, "inputPriceMicrosPerMillion": None,
    "outputPriceMicrosPerMillion": None, "priceCurrency": "USD", "visionEnabled": 0})
insert(db, "roles", {"id": "role", "name": "name", "prompt": "original role", "version": 9, "spaceId": "space", "openingMessage": "hello",
    "defaultConfigId": "cfg", "replyStyle": "short", "userName": "me", "userPersona": "private mask", "personaEnabled": 1, "conversationMode": "character"})
insert(db, "chats", {"id": "chat", "roleId": "role", "configId": "cfg", "title": "original title", "createdAt": 1, "openingMessage": "hello", "pinned": 0})
insert(db, "messages", {"id": "user", "chatId": "chat", "requestId": "request", "kind": "user", "content": "original private body", "state": "COMPLETE",
    "createdAt": 2, "errorCode": "", "firstTokenMs": -1, "elapsedMs": -1, "bubbleStyle": "paragraph", "favorite": 0})
insert(db, "memory_entries", {"id": "memory", "roleId": "role", "spaceId": "space", "kind": "preference", "title": "tea",
    "content": "original memory", "enabled": 1, "priority": 50, "source": "manual", "version": 2, "createdAt": 3, "updatedAt": 4,
    "sourceChatId": None, "sourceRequestId": None, "sourceUserMessageId": None, "sourceAssistantMessageId": None, "sourceModel": None,
    "sourceConfigVersion": None, "sourceMessageAt": None, "extractedAt": None})
db.commit()
before_shape = shape(db)
old_tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
before_rows = {table: list(db.execute(f"SELECT * FROM `{table}`")) for table in old_tables}
assert valid(db)

invalid = (
    "UPDATE memory_entries SET spaceId='foreign' WHERE id='memory'",
    "UPDATE memory_entries SET source='unknown' WHERE id='memory'",
    "UPDATE memory_entries SET sourceChatId='chat' WHERE id='memory'",
    "UPDATE memory_entries SET source='extracted',sourceModel='model',sourceConfigVersion=1,sourceMessageAt=1,extractedAt=2,sourceChatId='chat' WHERE id='memory'",
)
for sql in invalid:
    db.execute("BEGIN")
    db.execute(sql)
    assert not valid(db)
    db.rollback()
    assert shape(db) == before_shape

db.execute("BEGIN")
db.execute(ddl[0])
db.rollback()
assert shape(db) == before_shape

db.execute("BEGIN")
for sql in ddl:
    db.execute(sql)
db.commit()
assert shape(db) == shape(create(18))
for table in old_tables:
    if table != "memory_entries":
        assert list(db.execute(f"SELECT * FROM `{table}`")) == before_rows[table]
memory = db.execute("SELECT * FROM memory_entries WHERE id='memory'").fetchone()
assert memory[:20] == before_rows["memory_entries"][0]
assert memory[20] == "always" and memory[21:] == (None, None, None)

report = {
    "status": "PASS",
    "sourceSha256": hashlib.sha256(source.encode()).hexdigest(),
    "schemaMatchesRoomV18": True,
    "allLegacyRowsPreserved": True,
    "legacyRecallMode": "always",
    "legacyExpiryAndForgettingAreNull": True,
    "invalidPreMigrationAssociationsRejected": len(invalid),
    "interruptedDdlRollback": True,
    "limitation": "Actual migration SQL on host SQLite. Room/SQLCipher/Keystore device migration remains NOT_RUN.",
}
path = repo / "evidence/E4C/memory-recall-migration.json"
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
