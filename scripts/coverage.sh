#!/bin/sh
# usage: scripts/coverage.sh [--live]: line and branch coverage per source file.
#   Unit tests only by default. --live also runs ../tests/launch.sh (the wrapper's live server
#   test) with the JaCoCo agent in the server; a line counts as covered if either run covered it.
#   HTML reports: target/coverage/unit/, target/coverage/live/. MIN_LINE=<percent> fails below it.
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME_25:-$HOME/.sdkman/candidates/java/25.0.4-tem}
CLI="$JAVA_HOME/bin/java -jar $HOME/.m2/repository/org/jacoco/org.jacoco.cli/0.8.14/org.jacoco.cli-0.8.14-nodeps.jar"
rm -rf target/jacoco.exec target/jacoco-live target/coverage
mvn -o -q verify
mkdir -p target/coverage
$CLI report target/jacoco.exec --classfiles target/classes --sourcefiles src/main/java \
  --xml target/coverage/unit.xml --html target/coverage/unit --name "CuriousJumpPads unit tests" >/dev/null
if [ "$1" = "--live" ]; then
  COVERAGE=1 ../tests/launch.sh | grep -E "SUMMARY|FAIL|coverage"
  # Paper rewrites plugin classes on load, so the live report must use the classes it loaded.
  $CLI report target/jacoco-live/jacoco.exec --classfiles target/jacoco-live/classes \
    --sourcefiles src/main/java --xml target/coverage/live.xml --html target/coverage/live \
    --name "CuriousJumpPads live server test" >/dev/null
fi
python3 - "${MIN_LINE:-0}" target/coverage/*.xml <<'PY'
import sys, xml.etree.ElementTree as ET
from collections import defaultdict
lines = defaultdict(dict)  # file -> line -> [covered, branches covered, branches total]
for path in sys.argv[2:]:
    for pkg in ET.parse(path).getroot().iter('package'):
        for sf in pkg.iter('sourcefile'):
            f = (pkg.get('name').removeprefix('io/tjs/curiousjumppads').strip('/') + '/' + sf.get('name')).lstrip('/')
            for ln in sf.iter('line'):
                nr, ci = int(ln.get('nr')), int(ln.get('ci'))
                mb, cb = int(ln.get('mb')), int(ln.get('cb'))
                old = lines[f].get(nr, [False, 0, 0])
                lines[f][nr] = [old[0] or ci > 0, max(old[1], cb), max(old[2], mb + cb)]
def pct(c, t):
    return f"{100 * c / t:5.0f}%" if t else "    -"
runs = ' + '.join(p.rsplit('/', 1)[1].removesuffix('.xml') for p in sys.argv[2:])
print(f"coverage ({runs})")
print(f"{'source file':<28}{'lines':>8}{'branches':>10}")
T = [0, 0, 0, 0]
for f in sorted(lines):
    v = lines[f].values()
    c, t = sum(x[0] for x in v), len(v)
    bc, bt = sum(x[1] for x in v), sum(x[2] for x in v)
    T = [T[0] + c, T[1] + t, T[2] + bc, T[3] + bt]
    print(f"{f:<28}{pct(c, t):>8}{pct(bc, bt):>10}")
print(f"{'TOTAL':<28}{pct(T[0], T[1]):>8}{pct(T[2], T[3]):>10}   ({T[0]}/{T[1]} lines)")
sys.exit(1 if 100 * T[0] / T[1] < float(sys.argv[1]) else 0)
PY
