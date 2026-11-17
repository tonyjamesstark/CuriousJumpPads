#!/bin/sh
# usage: scripts/gen-docs.sh: builds the API reference into docs/api/ (gitignored) for a local
# preview. The published site is built by .github/workflows/docs.yml.
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME_25:-$HOME/.sdkman/candidates/java/25.0.4-tem}
rm -rf target/site/apidocs
mvn -o -q javadoc:javadoc
rm -rf docs/api
cp -r target/site/apidocs docs/api
echo "docs/api: $(find docs/api -name '*.html' | wc -l) pages"
