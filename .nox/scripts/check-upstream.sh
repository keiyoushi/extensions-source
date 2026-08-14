#!/usr/bin/env bash

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

echo
echo "=========================================="
echo " Project Nox - Upstream Collision Checker"
echo "=========================================="
echo

# Não analisar com arquivos locais ainda não commitados.
if [ -n "$(git status --porcelain)" ]; then
    echo "❌ Existem alterações locais ainda não commitadas."
    echo
    git status --short
    echo
    echo "Faça commit/stash antes de executar novamente."
    exit 2
fi

echo "📥 Atualizando referências..."

git fetch --quiet upstream main
git fetch --quiet origin nox/main

UPSTREAM="upstream/main"
NOX="origin/nox/main"

BASE="$(git merge-base "$UPSTREAM" "$NOX")"

echo
echo "Base comum:"
echo "  $BASE"
echo

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

UPSTREAM_FILES="$TMP_DIR/upstream-files"
NOX_FILES="$TMP_DIR/nox-files"

UPSTREAM_UNITS="$TMP_DIR/upstream-units"
NOX_UNITS="$TMP_DIR/nox-units"

COLLISIONS="$TMP_DIR/collisions"

git diff --name-only "$BASE..$UPSTREAM" > "$UPSTREAM_FILES"
git diff --name-only "$BASE..$NOX" > "$NOX_FILES"

normalize_path() {
    local path="$1"

    case "$path" in

        # Metadados internos da Nox nunca contam como colisão
        .nox/*)
            return
            ;;

        # Uma extensão inteira é considerada uma unidade
        src/*/*/*)
            echo "$path" | cut -d/ -f1-3
            ;;

        src/*/*)
            echo "$path"
            ;;

        # Tema multisrc inteiro
        lib-multisrc/*/*)
            echo "$path" | cut -d/ -f1-2
            ;;

        # Bibliotecas compartilhadas
        lib/*/*)
            echo "$path" | cut -d/ -f1-2
            ;;

        # Áreas globais: qualquer alteração simultânea merece revisão
        common/*)
            echo "GLOBAL:common"
            ;;

        core/*)
            echo "GLOBAL:core"
            ;;

        compiler/*)
            echo "GLOBAL:compiler"
            ;;

        gradle/*)
            echo "GLOBAL:gradle"
            ;;

        .github/*)
            echo "GLOBAL:.github"
            ;;

        # Arquivos importantes da raiz
        build.gradle.kts)
            echo "GLOBAL:build.gradle.kts"
            ;;

        settings.gradle.kts)
            echo "GLOBAL:settings.gradle.kts"
            ;;

        gradle.properties)
            echo "GLOBAL:gradle.properties"
            ;;

        *)

            # Outros arquivos são comparados individualmente
            echo "FILE:$path"
            ;;
    esac
}

while IFS= read -r file; do
    [ -z "$file" ] && continue
    normalize_path "$file"
done < "$UPSTREAM_FILES" | sort -u > "$UPSTREAM_UNITS"

while IFS= read -r file; do
    [ -z "$file" ] && continue
    normalize_path "$file"
done < "$NOX_FILES" | sort -u > "$NOX_UNITS"

comm -12 "$UPSTREAM_UNITS" "$NOX_UNITS" > "$COLLISIONS"

UPSTREAM_COUNT="$(wc -l < "$UPSTREAM_FILES")"
NOX_COUNT="$(wc -l < "$NOX_FILES")"
COLLISION_COUNT="$(wc -l < "$COLLISIONS")"

echo "📊 Resultado"
echo
echo "Arquivos alterados pela Keiyoushi: $UPSTREAM_COUNT"
echo "Arquivos alterados pela Nox:       $NOX_COUNT"
echo "Colisões detectadas:               $COLLISION_COUNT"
echo

if [ "$UPSTREAM_COUNT" -eq 0 ]; then
    echo "✅ Nenhuma atualização nova encontrada na Keiyoushi."
    exit 0
fi

echo "📦 Unidades alteradas pela Keiyoushi:"
echo

if [ -s "$UPSTREAM_UNITS" ]; then
    sed 's/^/  • /' "$UPSTREAM_UNITS"
else
    echo "  nenhuma"
fi

echo

if [ "$COLLISION_COUNT" -gt 0 ]; then

    echo "🚨 ATENÇÃO: COLISÃO DETECTADA"
    echo
    echo "A Keiyoushi e a Project Nox alteraram as mesmas unidades:"
    echo

    sed 's/^/  ⚠ /' "$COLLISIONS"

    echo
    echo "Nenhum merge foi realizado."
    echo "Essas alterações precisam ser revisadas."
    echo

    exit 10
fi

echo "✅ Nenhuma colisão detectada."
echo
echo "A atualização parece segura para integração."
echo
echo "IMPORTANTE: este script apenas analisou."
echo "Nenhum merge ou push foi realizado."
echo
