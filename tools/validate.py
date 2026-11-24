"""Static sanity checks (NOT a compiler). Run from the repo root: python tools/validate.py"""
import re, sys, pathlib, xml.dom.minidom

root = pathlib.Path(__file__).resolve().parent.parent
errors, notes = [], []

# 1) XML well-formedness
xmls = list((root / "android").rglob("*.xml"))
for f in xmls:
    try: xml.dom.minidom.parse(str(f))
    except Exception as e: errors.append(f"XML {f.relative_to(root)}: {e}")
notes.append(f"{len(xmls)} XML files parsed")

# 2) Kotlin files: bracket balance (strings/comments stripped), import resolution
kt = list((root / "android/app/src").rglob("*.kt"))
def strip(src):
    """Character scanner: removes comments, string and char literal contents (handles raw strings and ${} templates loosely)."""
    out = []; i = 0; n = len(src)
    while i < n:
        ch = src[i]
        if src.startswith("//", i):
            while i < n and src[i] != "\n": i += 1
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2); i = n if j < 0 else j + 2
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3); i = n if j < 0 else j + 3
            while i < n and src[i] == '"': i += 1
            out.append('""')
        elif ch == '"':
            i += 1
            while i < n and src[i] != '"':
                if src[i] == "\\": i += 1
                elif src.startswith("${", i):
                    d = 0
                    while i < n:
                        if src[i] == "{": d += 1
                        elif src[i] == "}":
                            d -= 1
                            if d == 0: break
                        i += 1
                i += 1
            i += 1; out.append('""')
        elif ch == "'":
            j = i + 1
            if j < n and src[j] == "\\": j += 1
            j += 1
            if j < n and src[j] == "'": i = j + 1; out.append("' '")
            else: out.append(ch); i += 1
        else:
            out.append(ch); i += 1
    return "".join(out)
defined = set()
for f in kt:
    s = f.read_text()
    pkg = re.search(r'^package\s+([\w.]+)', s, re.M).group(1)
    for m in re.finditer(r'^\s*(?:(?:data|sealed|enum|private|internal|abstract|open)\s+)*(?:class|interface|object|fun|val|var)\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)', s, re.M):
        defined.add(f"{pkg}.{m.group(1)}")
    for m in re.finditer(r'(?:data\s+)?object\s+(\w+)', s): defined.add(f"{pkg}.{m.group(1)}")
for f in kt:
    s = f.read_text(); c = strip(s)
    for o, cl in ("{}", "()", "[]"):
        if c.count(o) != c.count(cl): errors.append(f"Bracket mismatch {o}{cl} in {f.relative_to(root)}: {c.count(o)} vs {c.count(cl)}")
    for imp in re.findall(r'^import\s+(com\.jinwoo\.ai\.[\w.]+)', s, re.M):
        if imp not in defined and not imp.endswith(('.R', '.BuildConfig')):
            errors.append(f"Unresolved project import {imp} in {f.relative_to(root)}")
notes.append(f"{len(kt)} Kotlin files checked for bracket balance and project-import resolution")

# 3) Resource references
res = {"drawable": set(), "raw": set(), "mipmap": set(), "xml": set(), "color": set(), "style": set(), "string": set()}
for f in (root / "android/app/src").rglob("res/*/*.*"):
    kind = f.parent.name.split("-")[0]
    if kind in res: res[kind].add(f.stem)
for f in (root / "android/app/src").rglob("res/values/*.xml"):
    s = f.read_text()
    res["color"] |= set(re.findall(r'<color name="(\w+)"', s)); res["string"] |= set(re.findall(r'<string name="(\w+)"', s))
    res["style"] |= {x.replace('.', '_') for x in re.findall(r'<style name="([\w.]+)"', s)}
for f in kt:
    for kind, name in re.findall(r'\bR\.(drawable|raw|mipmap|string|color)\.(\w+)', f.read_text()):
        if name not in res[kind]: errors.append(f"Missing resource R.{kind}.{name} used in {f.name}")
man = (root / "android/app/src/main/AndroidManifest.xml").read_text()
for kind, name in re.findall(r'@(drawable|mipmap|xml|string|color)/(\w+)', man) + re.findall(r'@(style)/([\w.]+)', man):
    n = name.replace('.', '_') if kind == 'style' else name
    if n not in res[kind]: errors.append(f"Manifest references missing @{kind}/{name}")
for cls in re.findall(r'android:name="\.([\w.]+)"', man):
    if not any(f.name == cls.split('.')[-1] + ".kt" for f in kt): errors.append(f"Manifest class .{cls} has no source file")
notes.append("resource + manifest references checked")

# 4) Required deliverable files
required = ["README.md", ".gitignore", "android/settings.gradle.kts", "android/build.gradle.kts", "android/gradlew", "android/gradlew.bat",
 "android/gradle/wrapper/gradle-wrapper.properties", "android/app/build.gradle.kts", "backend/requirements.txt", "backend/.env.example",
 "backend/README.md", "backend/app/main.py", "assets/README.md", "assets/sounds/activation.wav", "docs/INSTALLATION.md", "docs/API_SETUP.md",
 "docs/WAKE_WORD_SETUP.md", "docs/TROUBLESHOOTING.md", "docs/MOBILE_AND_TERMUX.md", "backend/termux/3_start_backend.sh", ".github/workflows/build-apk.yml"]
for r in required:
    if not (root / r).exists(): errors.append(f"Missing required file {r}")
if (root / "backend/.env").exists(): errors.append("backend/.env must not exist in the package")
for f in root.rglob("*"):
    if f.is_file() and f.suffix in {".kt", ".py", ".md", ".kts", ".xml", ".example"}:
        if re.search(r'(sk-[A-Za-z0-9]{20,}|AIza[0-9A-Za-z_\-]{30,})', f.read_text(errors="ignore")): errors.append(f"Possible API key in {f}")
notes.append("required files + secret scan checked")

print("\n".join("OK  " + n for n in notes))
if errors: print("\nPROBLEMS:"); print("\n".join(" - " + e for e in errors)); sys.exit(1)
print("\nNo problems found by static checks (this is NOT a compile).")
