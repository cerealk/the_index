# RAR test fixtures

Copied unchanged from junrar's test resources (https://github.com/junrar/junrar, commit
`c831d1ed9eccf63083f2d4304149019d43f48c06`, `src/test/resources/com/github/junrar/`).
junrar is distributed under the UnRAR freeware license; see its `LICENSE` file.

No JVM library can write RAR, and the app must not depend on external programs, so the
tests use these instead of generating archives.

| File | Source path | Contents |
|---|---|---|
| `rar4.rar` | `rar4.rar` | RAR 4: `FILE1.TXT`, `FILE2.TXT` |
| `rar5.rar` | `rar5.rar` | RAR 5: `FILE1.TXT`, `FILE2.TXT` |
| `rar4-solid.rar` | `solid/rar4-solid.rar` | RAR 4, solid |
| `rar5-solid.rar` | `solid/rar5-solid.rar` | RAR 5, solid: `file1.txt` … `file8.txt` |
| `rar5-encrypted-junrar.rar` | `password/rar5-encrypted-junrar.rar` | RAR 5 with encrypted headers (password `junrar`) |

SHA-256:

```
91e21f4126181790429f5e5ecdefedae6d6883ed6b4b396fc94d4ed6b32c065a  rar4.rar
aaf1a7d974f302f181720280151053f15ef1119b13d2f7bf9fddf2163bbf1701  rar5.rar
b158d4b0e3506f04fcaff18db9b369b573fd54f313062c7b8c622cb33b9c5da4  rar4-solid.rar
d004997e805cb7bb47bd73904aae113f81ee1d82d80bed9372c5dd64d1684754  rar5-solid.rar
eedcde5498c9f7b7431b524b44f481184b08e64a7274a2f29d8b9d11850790b9  rar5-encrypted-junrar.rar
```
