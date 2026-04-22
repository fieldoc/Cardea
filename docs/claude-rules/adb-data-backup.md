# ADB Data Backup Safety

Load when backing up, restoring, or manipulating the Room DB from a device. **CRITICAL — data loss risk.**

## The core hazard

**NEVER use `adb shell run-as ... cat <binary> > /tmp/local`** — Git Bash CR/LF translation silently corrupts SQLite DBs. File looks valid (correct size, SQLite header) but B-tree pages are mangled and unrecoverable.

## Safe backup

1. `adb shell "run-as com.hrcoach cp databases/hr_coach_db /data/local/tmp/hr_coach_backup.db"` (also `-shm`, `-wal`)
2. `adb pull //data/local/tmp/hr_coach_backup.db /tmp/hr_coach_backup.db` (double-slash prevents Git Bash path translation)
3. Verify: `sqlite3 /tmp/hr_coach_backup.db "SELECT COUNT(*) FROM workouts"`
4. Cleanup: `adb shell "rm /data/local/tmp/hr_coach_backup.db*"`

## Safe restore

1. `adb shell am force-stop com.hrcoach`
2. `adb push /tmp/hr_coach_backup.db //data/local/tmp/hr_coach_restore.db` (also `-shm`, `-wal`)
3. `adb shell "run-as com.hrcoach cp /data/local/tmp/hr_coach_restore.db databases/hr_coach_db"` (also `-shm`, `-wal`)
4. Cleanup temp files, launch app, verify.

## Git Bash path gotchas

- `adb push/pull`: prefix `//data/` (double-slash) — otherwise `/data/` → `C:/Program Files/Git/data/`.
- `firebase database:get /path`: prefix `MSYS_NO_PATHCONV=1`.
- `adb shell "..."` commands run on-device — unaffected.
