"""Shared-world DDL and CAS on host SQLite; this is not Android/SQLCipher evidence."""
import hashlib
import argparse
import json
import pathlib
import re
import sqlite3

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", type=pathlib.Path, required=True, help="UTF-8 migration evidence JSON")
args = parser.parse_args()

repo = pathlib.Path(__file__).resolve().parents[2]
base = repo / "app/src/main/java/com/ai/assistance/operit/data/db"
source = (base / "SharedHomeMigration.kt").read_text(encoding="utf-8")
dao = (base / "HomeStorage.kt").read_text(encoding="utf-8")
ddl = re.findall(r'db\.execSQL\("(CREATE [^"\n]+)"', source)
assert len(ddl) == 4
assert "RoleEntity::class" not in dao
assert "Migration(33,34)" in source

def exported(version):
    return json.loads((repo / f"app/schemas/com.ai.assistance.operit.data.db.AppDatabase/{version}.json").read_text(encoding="utf-8"))["database"]["entities"]

def create(entities):
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    for table in entities:
        db.execute(table["createSql"].replace("${TABLE_NAME}", table["tableName"]))
        for index in table.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", table["tableName"]))
    return db

def shape(db):
    tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
    def indexes(name):
        # Compare schema meaning, not SQL whitespace or Room's identifier quoting.
        return sorted((row[1], row[2], row[3], row[4],
                       tuple(column[2] for column in db.execute(f"PRAGMA index_info('{row[1]}')")))
                      for row in db.execute(f"PRAGMA index_list('{name}')") if row[3] != "pk")
    return {name: (db.execute(f"PRAGMA table_info('{name}')").fetchall(),
                   sorted(db.execute(f"PRAGMA foreign_key_list('{name}')").fetchall()),
                   indexes(name))
            for name in tables}

db = create(exported(33))
# Retire development data explicitly, child before parent, as the production migration does.
for name in ("home_commands", "homes"):
    db.execute(f"DROP TABLE {name}")
for sql in ddl:
    db.execute(sql)
expected = create(exported(34))
assert shape(db) == shape(expected), "Shared-world DDL differs from Room's exported schema"
assert not db.execute("PRAGMA foreign_key_list(home_world)").fetchall()
db.execute("INSERT INTO roles (id,name,prompt,version,spaceId,openingMessage,defaultConfigId,replyStyle,userName,userPersona,personaEnabled,conversationMode) VALUES ('resident','Resident','Companion',1,'resident-space','',NULL,'short','','',0,'character')")
db.execute("INSERT INTO home_world VALUES ('shared-home','initial','saved',1,100,100)")
update = next(sql for sql in re.findall(r'@Query\("([^"\n]+)"\)', dao) if sql.startswith("UPDATE home_world"))
write_args = dict(home="shared-home", previous=1, json="next", next=2, now=101)
assert db.execute(update, write_args).rowcount == 1
assert db.execute(update, write_args).rowcount == 0
db.execute("INSERT INTO home_events VALUES ('event','shared-home',1,'action','description',101)")
try:
    db.execute("INSERT INTO home_events VALUES ('second','shared-home',1,'action','description',101)")
except sqlite3.IntegrityError:
    pass
else:
    raise AssertionError("Duplicate beforeVersion accepted")
# Roles have no ownership path to the world or its ledger.
assert db.execute("DELETE FROM roles WHERE id='resident'").rowcount == 1
assert db.execute("SELECT version FROM home_world").fetchone() == (2,)
assert db.execute("SELECT COUNT(*) FROM home_events").fetchone() == (1,)
assert not db.execute("PRAGMA foreign_key_check").fetchall()
report = dict(status="PASS", schemaMatchesRoom34=True, sharedWorldWithoutRoleOwner=True,
    duplicateVersionRejected=True, staleWriteRejected=True, worldSurvivesRoleDeletion=True,
    migrationSha256=hashlib.sha256(source.encode()).hexdigest(), daoSha256=hashlib.sha256(dao.encode()).hexdigest(),
    environment="host SQLite; not Android/SQLCipher or concurrent Room execution")
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
