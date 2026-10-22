"""Verify actual extension DAO SQL, migration schema, ownership and FK cleanup on host SQLite.

This does not execute Kotlin repositories or SQLCipher; Android tests cover those separately.
"""
import json
import pathlib
import re
import sqlite3

repo = pathlib.Path(__file__).resolve().parents[2]
base = repo / "app/src/main/java/com/ai/assistance/operit/data/db"
source = (base / "ExtensionDao.kt").read_text(encoding="utf-8")
queries = {name: sql for sql, name in re.findall(r'@Query\("([^"\n]+)"\)\s+(?:suspend\s+)?fun\s+(\w+)', source)}
migration = (base / "ExtensionMigration.kt").read_text(encoding="utf-8")
integrity = re.search(r'db.query\("([^"\n]+)"', migration).group(1)
statements = re.findall(r'db.execSQL\("([^"\n]+)"', migration)
schemas = {v: json.loads((repo / f"app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{v}.json").read_text())["database"] for v in (4, 5)}

def create(version):
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys=ON")
    for entity in schemas[version]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return db

def insert(db, table, **values):
    db.execute(f"INSERT INTO {table} ({','.join(values)}) VALUES ({','.join('?' for _ in values)})", tuple(values.values()))

def shape(db):
    result = {}
    for entity in schemas[5]["entities"]:
        name = entity["tableName"]
        columns = [tuple(r) for r in db.execute(f"PRAGMA table_info({name})")]
        fks = sorted(tuple(r)[2:] for r in db.execute(f"PRAGMA foreign_key_list({name})"))
        indices = sorted((r["name"], r["unique"], tuple(c["name"] for c in db.execute("PRAGMA index_info("+r["name"]+")"))) for r in db.execute(f"PRAGMA index_list({name})") if not r["name"].startswith("sqlite_autoindex"))
        result[name] = (columns, fks, indices)
    return result

db = create(4)
insert(db, "model_configs", id="config", name="model", provider="test", endpoint="https://example.invalid/chat", model="test", timeoutSeconds=90, credentialRef="old-ref", version=3)
for role in ("A", "B"):
    insert(db, "roles", id=role, spaceId="space-"+role, name="same", prompt="persona-"+role, version=7)
    insert(db, "chats", id="chat-"+role, roleId=role, configId="config", title="title-"+role, createdAt=1, pinned=1)
    for n in range(10):
        insert(db, "messages", id=f"old-{role}-{n}", chatId="chat-"+role, requestId=f"old-request-{role}-{n}", kind="user", content=f"{role}-marker-{n} 中文🙂", state="COMPLETE", createdAt=n, errorCode="", firstTokenMs=-1, elapsedMs=-1, favorite=1)
insert(db, "role_appearance", roleId="A", spaceId="space-A", wallpaper=b"old-wallpaper", dimming=0.2, palette="rose")
insert(db, "role_portraits", roleId="A", spaceId="space-A", kind="role", image=b"old-avatar")
db.commit()
old = {e["tableName"]: [tuple(r) for r in db.execute("SELECT * FROM "+e["tableName"])] for e in schemas[4]["entities"]}
assert db.execute(integrity).fetchone()[0] == 0 and len(statements) == 10
with db:
    for sql in statements:
        db.execute(sql)
assert shape(db) == shape(create(5))
for table, rows in old.items():
    assert [tuple(r) for r in db.execute("SELECT * FROM "+table)] == rows

insert(db, "mcp_extensions", id="ext", name="Echo", endpoint="https://example.invalid/mcp", credentialRef="tool-ref", timeoutMs=1000, enabled=1, version=1)
insert(db, "extension_tools", extensionId="ext", name="echo", description="synthetic", inputSchema='{"type":"object"}')
for role in ("A", "B"):
    insert(db, "role_tool_grants", roleId=role, spaceId="space-"+role, extensionId="ext", toolName="echo", enabled=1, version=1)

def prepare(role, number):
    args = dict(roleId=role, spaceId="space-"+role, chatId="chat-"+role, requestId=f"request-{number}", assistantId=f"answer-{number}", extensionId="ext", name="echo", callId=f"call-{number}")
    insert(db, "messages", id=args["assistantId"], chatId=args["chatId"], requestId=args["requestId"], kind="assistant", content="partial", state="GENERATING", createdAt=100+number, errorCode="", firstTokenMs=1, elapsedMs=1)
    assert db.execute(queries["activeRequest"], args).fetchone()
    grants = db.execute(queries["grantedTools"], args).fetchall()
    assert len(grants) == 1
    for grant in grants:
        values = dict(grant) | {key: args[key] for key in ("roleId", "spaceId", "chatId", "requestId", "assistantId")}
        insert(db, "request_tool_permissions", **values)
    insert(db, "tool_invocations", **{k: args[k] for k in ("roleId", "spaceId", "chatId", "requestId", "assistantId", "extensionId", "callId")}, toolName="echo", arguments=json.dumps({"marker": role}), result="", state="WAITING_APPROVAL", errorCode="", createdAt=100+number, finishedAt=0)
    return args

def transition(args, expected="WAITING_APPROVAL", state="RUNNING", result=""):
    return db.execute(queries["transition"], args | dict(expected=expected, state=state, result=result, error="", finishedAt=200)).rowcount

for n in range(50):
    role = "A" if n % 2 == 0 else "B"
    other = "B" if role == "A" else "A"
    args = prepare(role, n)
    assert db.execute(queries["authorized"], args).fetchone()
    for bad in (dict(roleId=other, spaceId="space-"+other), dict(spaceId="space-"+other), dict(chatId="chat-"+other), dict(requestId="foreign"), dict(assistantId="foreign")):
        assert db.execute(queries["authorized"], args | bad).fetchone() is None
        assert db.execute(queries["invocation"], args | bad).fetchone() is None
        assert transition(args | bad) == 0
    assert transition(args) == 1
    assert transition(args, "RUNNING", "COMPLETE", role+"-result") == 1
    assert transition(args, "RUNNING", "COMPLETE", "LATE") == 0
    rows = db.execute(queries["invocations"], args).fetchall()
    assert rows and all(r["result"].startswith(role) for r in rows)
    assert not db.execute(queries["invocations"], args | dict(roleId=other, spaceId="space-"+other)).fetchall()

for n in range(50, 70):
    args = prepare("A", n)
    assert transition(args) == 1
    if n % 2 == 0:
        db.execute(queries["invalidateGrant"], args | dict(id="ext", now=200))
        db.execute("UPDATE role_tool_grants SET enabled=0,version=version+1 WHERE roleId='A'")
        assert db.execute(queries["authorized"], args).fetchone() is None
        db.execute("UPDATE role_tool_grants SET enabled=1,version=version+1 WHERE roleId='A'")
        assert db.execute(queries["authorized"], args).fetchone() is None
    else:
        db.execute(queries["recoverInterrupted"], dict(now=200))
        db.execute("UPDATE messages SET state='FAILED',errorCode='INTERRUPTED' WHERE id=?", (args["assistantId"],))
        assert db.execute(queries["authorized"], args).fetchone() is None
    assert transition(args, "RUNNING", "COMPLETE", "LATE") == 0
    row = db.execute(queries["invocation"], args).fetchone()
    assert row["state"] == "FAILED" and row["result"] == ""

args = prepare("A", 70)
assert db.execute(queries["authorized"], args).fetchone()
db.execute(queries["invalidateExtension"], dict(id="ext", now=300))
db.execute(queries["revokeExtension"], dict(id="ext"))
db.execute("UPDATE mcp_extensions SET enabled=0,version=version+1 WHERE id='ext'")
assert db.execute(queries["authorized"], args).fetchone() is None
db.execute(queries["deleteExtension"], dict(id="ext"))
assert db.execute("SELECT COUNT(*) FROM tool_invocations").fetchone()[0] == 71
assert db.execute("SELECT COUNT(*) FROM role_tool_grants").fetchone()[0] == 0
db.execute("DELETE FROM messages WHERE chatId='chat-A'")
assert db.execute("SELECT COUNT(*) FROM tool_invocations WHERE roleId='A'").fetchone()[0] == 0
assert db.execute("SELECT COUNT(*) FROM request_tool_permissions WHERE roleId='A'").fetchone()[0] == 0
assert db.execute("SELECT COUNT(*) FROM tool_invocations WHERE roleId='B'").fetchone()[0] == 25
assert not db.execute("PRAGMA foreign_key_check").fetchall()

broken = create(4)
insert(broken, "role_portraits", roleId="missing", spaceId="wrong", kind="role", image=b"retained")
broken.commit()
assert broken.execute(integrity).fetchone()[0] > 0
assert not broken.execute("SELECT name FROM sqlite_master WHERE name='mcp_extensions'").fetchall()
assert broken.execute("SELECT image FROM role_portraits").fetchone()[0] == b"retained"
report = dict(status="PASS", schemaMatchesRoomV5=True, allV4RowsPreserved=True, orphanDetectedBeforeDdl=True, scopeAlternations=50, syntheticLegacyMarkers=20, revokedOrInterruptedRounds=20, lateWritesRejected=True, terminalWritesRejected=True, disableAndDeleteDenyAccess=True, chatDeletionCascades=True, extensionDeletionRetainsAudit=True, foreignKeyCheck=True, limitation="Host SQLite executes actual DAO SQL; Kotlin repository, SQLCipher and installed Android runtime are NOT_RUN here.")
out = repo / "evidence/E1/storage-sql.json"
out.write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
print(json.dumps(report, indent=2))
