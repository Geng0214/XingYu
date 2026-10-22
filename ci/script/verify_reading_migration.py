"""Execute shipped 14->15 DDL on host SQLite; this does not claim Android SQLCipher execution."""
import hashlib
import json
import pathlib
import re
import sqlite3


repo = pathlib.Path(__file__).resolve().parents[2]
source_path = repo / "app/src/main/java/com/ai/assistance/operit/data/db/ReadingMigration.kt"
source = source_path.read_text(encoding="utf-8")
check = re.search(r'db\.query\("(SELECT \(SELECT COUNT\(\*\)[^"\n]+)"\)', source)
ddl = re.findall(r'db\.execSQL\("([^"\n]+)"', source)
assert check and len(ddl) == 3

schema_root = repo / "app/schemas/com.ai.assistance.operit.data.db.AppDatabase"
schemas = {version: json.loads((schema_root / f"{version}.json").read_text(encoding="utf-8"))["database"] for version in (14, 15)}


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


db = create(14)
insert(db, "model_configs", {"id": "cfg", "name": "model", "provider": "OpenAI 兼容", "endpoint": "https://example.invalid/v1/chat/completions",
    "model": "test", "timeoutSeconds": 60, "credentialRef": "original-ref", "version": 3, "inputPriceMicrosPerMillion": None,
    "outputPriceMicrosPerMillion": None, "priceCurrency": "USD", "visionEnabled": 1})
insert(db, "roles", {"id": "role", "name": "name", "prompt": "original role", "version": 9, "spaceId": "space", "openingMessage": "hello",
    "defaultConfigId": "cfg", "replyStyle": "short", "userName": "me", "userPersona": "private mask", "personaEnabled": 1, "conversationMode": "character"})
insert(db, "chats", {"id": "chat", "roleId": "role", "configId": "cfg", "title": "original title", "createdAt": 1, "openingMessage": "hello", "pinned": 1})
insert(db, "messages", {"id": "user", "chatId": "chat", "requestId": "request", "kind": "user", "content": "original private body", "state": "COMPLETE",
    "createdAt": 2, "errorCode": "", "firstTokenMs": -1, "elapsedMs": -1, "bubbleStyle": "paragraph", "favorite": 1})
insert(db, "message_materials", {"messageId": "user", "chatId": "chat", "requestId": "request", "kind": "file", "displayName": "original.md",
    "sourceUrl": None, "mimeType": "text/markdown", "text": "original material", "originalChars": 17, "truncated": 0, "sha256": "a" * 64})
insert(db, "image_generation_configs", {"id": "image-cfg", "name": "image", "endpoint": "https://example.invalid/v1/images/generations", "model": "gpt-image-1",
    "timeoutSeconds": 90, "credentialRef": "image-ref", "version": 1, "size": "1024x1024", "priceMicros": None, "priceCurrency": "USD"})
insert(db, "generated_images", {"id": "generated", "roleId": "role", "spaceId": "space", "chatId": "chat", "configId": "image-cfg", "configVersion": 1,
    "model": "gpt-image-1", "prompt": "original prompt", "requestedSize": "1024x1024", "priceMicros": None, "priceCurrency": "USD", "state": "FAILED",
    "errorCode": "SERVER", "mimeType": "", "width": 0, "height": 0, "sha256": "", "data": None, "createdAt": 3, "finishedAt": 4, "elapsedMs": 1})
db.commit()
before_shape = shape(db)
old_tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
before_rows = {table: list(db.execute(f"SELECT * FROM `{table}`")) for table in old_tables}
assert valid(db)

for sql in ("UPDATE messages SET chatId='missing-chat' WHERE id='user'", "UPDATE message_materials SET requestId='other' WHERE messageId='user'",
            "UPDATE generated_images SET spaceId='foreign-space' WHERE id='generated'"):
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
assert shape(db) == shape(create(15))
for table in old_tables:
    assert list(db.execute(f"SELECT * FROM `{table}`")) == before_rows[table]
assert db.execute("SELECT COUNT(*) FROM reading_documents").fetchone()[0] == 0
assert valid(db)

report = {
    "status": "PASS",
    "sourceSha256": hashlib.sha256(source.encode()).hexdigest(),
    "schemaMatchesRoomV15": True,
    "allLegacyTablesAndRowsPreserved": True,
    "newReadingDocuments": 0,
    "invalidPreMigrationAssociationsRejected": 3,
    "interruptedDdlRollback": True,
    "limitation": "Actual migration SQL on host SQLite. Room/SQLCipher/Keystore device migration remains NOT_RUN.",
}
path = repo / "evidence/E3E/reading-migration.json"
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
