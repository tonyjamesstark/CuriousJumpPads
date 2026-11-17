#!/bin/sh
# usage: scripts/audit-docs.sh: fails if documentation is missing or stale.
#   1. Javadoc: no doclint warnings (every class and non-private member documented).
#   2. docs/commands.md names every permission, config key and command literal.
#   3. Relative links in README.md and docs/*.md point at files and headings that exist.
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME_25:-$HOME/.sdkman/candidates/java/25.0.4-tem}
fail=0

echo "== 1. Javadoc coverage"
rm -rf target/site/apidocs
out=$(mvn -o javadoc:javadoc 2>&1)
echo "$out" | grep -q "BUILD SUCCESS" || { echo "$out" | tail -20; exit 1; }
warnings=$(echo "$out" | grep -c "warning:")
echo "$out" | grep "warning:" | sed 's|.*/src/main/java/||'
echo "javadoc warnings: $warnings"
[ "$warnings" -eq 0 ] || fail=1

echo "== 2. docs/commands.md names everything"
python3 - <<'PY' || fail=1
import re, sys, pathlib
doc = pathlib.Path('docs/commands.md').read_text()
names = set()
plugin = pathlib.Path('src/main/resources/plugin.yml').read_text()
names |= {('permission', p) for p in re.findall(r'^  (jumppads\.[\w.*]+):', plugin, re.M)}
config = pathlib.Path('src/main/resources/config.yml').read_text()
names |= {('config key', k) for k in re.findall(r'^([\w-]+):', config, re.M)}
for f in pathlib.Path('src/main/java').rglob('*.java'):
    names |= {('command word', w) for w in re.findall(r'Commands\.literal\("(\w+)"\)', f.read_text())}
missing = sorted(n for n in names if f'`{n[1]}' not in doc and f' {n[1]}' not in doc)
for kind, name in missing:
    print(f'missing {kind}: {name}')
print(f'{len(names)} names checked, {len(missing)} missing')
sys.exit(1 if missing else 0)
PY

echo "== 3. Markdown links"
python3 - <<'PY' || fail=1
import re, sys, pathlib
def slug(h):
    return re.sub(r'[^\w\- ]', '', h.strip().lower()).replace(' ', '-')
def anchors(path):
    return {slug(m) for m in re.findall(r'^#+ (.+)$', path.read_text(), re.M)}
bad = 0
files = [pathlib.Path('README.md'), *sorted(pathlib.Path('docs').glob('*.md'))]
for f in files:
    for target in re.findall(r'\]\(([^)\s]+)\)', f.read_text()):
        if re.match(r'\w+:', target) or target.startswith('api/'):
            continue
        path, _, anchor = target.partition('#')
        dest = (f.parent / path).resolve() if path else f.resolve()
        if not dest.exists():
            print(f'{f}: broken link {target}'); bad += 1
        elif anchor and dest.suffix == '.md' and anchor not in anchors(dest):
            print(f'{f}: missing heading {target}'); bad += 1
print(f'{len(files)} files checked, {bad} broken links')
sys.exit(1 if bad else 0)
PY

[ $fail -eq 0 ] && echo "AUDIT PASSED" || echo "AUDIT FAILED"
exit $fail
