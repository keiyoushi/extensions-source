#!/usr/bin/env bash

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

UPSTREAM="upstream/main"
NOX="origin/nox/main"
MIRROR="origin/main"

echo
echo "=========================================="
echo " Project Nox - Safe Upstream Sync"
echo "=========================================="
echo

# ---------------------------------------------------------------------------
# 1. Segurança básica
# ---------------------------------------------------------------------------

CURRENT_BRANCH="$(git branch --show-current)"

if [ "$CURRENT_BRANCH" != "nox/main" ]; then
    echo "❌ Você precisa estar na branch nox/main."
    echo
    echo "Execute:"
    echo "  git switch nox/main"
    exit 2
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "❌ Existem alterações locais não commitadas."
    echo
    git status --short
    echo
    echo "Faça commit ou stash antes de sincronizar."
    exit 3
fi

echo "📥 Buscando atualizações..."

git fetch --quiet upstream main
git fetch --quiet origin main nox/main

# ---------------------------------------------------------------------------
# 2. Garante que local e GitHub Nox estão iguais
# ---------------------------------------------------------------------------

LOCAL_NOX="$(git rev-parse HEAD)"
REMOTE_NOX="$(git rev-parse "$NOX")"

if [ "$LOCAL_NOX" != "$REMOTE_NOX" ]; then
    echo
    echo "❌ Sua nox/main local não está igual à origin/nox/main."
    echo
    echo "Local:"
    echo "  $LOCAL_NOX"
    echo
    echo "GitHub:"
    echo "  $REMOTE_NOX"
    echo
    echo "Resolva/push suas alterações antes de sincronizar."
    exit 4
fi

# ---------------------------------------------------------------------------
# 3. Protege o mirror main
# ---------------------------------------------------------------------------

if ! git merge-base --is-ancestor "$MIRROR" "$UPSTREAM"; then
    echo
    echo "🚨 origin/main divergiu da Keiyoushi."
    echo
    echo "Isso NÃO deveria acontecer."
    echo "Nenhuma alteração será realizada."
    exit 5
fi

UPSTREAM_SHA="$(git rev-parse "$UPSTREAM")"
UPSTREAM_SHORT="$(git rev-parse --short "$UPSTREAM")"

echo
echo "Keiyoushi:"
echo "  $UPSTREAM_SHA"
echo

# ---------------------------------------------------------------------------
# 4. Verifica se já estamos sincronizados
# ---------------------------------------------------------------------------

if git merge-base --is-ancestor "$UPSTREAM" "$NOX"; then
    echo "✅ nox/main já contém a versão mais recente da Keiyoushi."

    if [ "$(git rev-parse "$MIRROR")" != "$UPSTREAM_SHA" ]; then
        echo
        echo "ℹ️ Apenas origin/main está atrás do upstream."
        echo "Use --apply para atualizar o mirror."
    fi

    if [ "${1:-}" = "--apply" ]; then
        if [ "$(git rev-parse "$MIRROR")" != "$UPSTREAM_SHA" ]; then
            echo
            echo "🔄 Atualizando mirror main..."
            git push origin "$UPSTREAM:main"
            echo "✅ main atualizada."
        fi
    fi

    exit 0
fi

# ---------------------------------------------------------------------------
# 5. Detector semântico da Nox
# ---------------------------------------------------------------------------

echo "🔍 Executando detector de colisões..."
echo

set +e
"$ROOT/.nox/scripts/check-upstream.sh"
CHECK_RESULT=$?
set -e

if [ "$CHECK_RESULT" -eq 10 ]; then
    echo
    echo "🚨 Sincronização BLOQUEADA."
    echo "Existe uma colisão entre Nox e Keiyoushi."
    exit 10
fi

if [ "$CHECK_RESULT" -ne 0 ]; then
    echo
    echo "❌ O detector retornou um erro inesperado."
    exit "$CHECK_RESULT"
fi

# ---------------------------------------------------------------------------
# 6. Dry-run real do Git
# ---------------------------------------------------------------------------

echo
echo "🧪 Simulando merge real com Git..."

if ! git merge-tree --write-tree --quiet "$NOX" "$UPSTREAM"; then
    echo
    echo "🚨 O Git encontrou um conflito real."
    echo "Nenhum arquivo foi alterado."
    exit 20
fi

echo "✅ Simulação de merge concluída sem conflitos."

# ---------------------------------------------------------------------------
# 7. Modo análise
# ---------------------------------------------------------------------------

if [ "${1:-}" != "--apply" ]; then
    echo
    echo "=========================================="
    echo " ✅ SINCRONIZAÇÃO SEGURA"
    echo "=========================================="
    echo
    echo "Nada foi alterado."
    echo
    echo "Para aplicar de verdade:"
    echo
    echo "  ./.nox/scripts/sync-upstream.sh --apply"
    echo
    exit 0
fi

# ---------------------------------------------------------------------------
# 8. Atualiza main = mirror perfeito da Keiyoushi
# ---------------------------------------------------------------------------

echo
echo "🔄 Atualizando origin/main..."

if [ "$(git rev-parse "$MIRROR")" != "$UPSTREAM_SHA" ]; then
    git push origin "$UPSTREAM:main"
else
    echo "main já está atualizada."
fi

# ---------------------------------------------------------------------------
# 9. Faz merge em nox/main
# ---------------------------------------------------------------------------

echo
echo "🔀 Integrando Keiyoushi em nox/main..."

if ! git merge \
    --no-ff \
    -m "sync: merge keiyoushi upstream ${UPSTREAM_SHORT}" \
    "$UPSTREAM"
then
    echo
    echo "🚨 O merge falhou inesperadamente."
    echo "Abortando..."
    git merge --abort 2>/dev/null || true
    exit 30
fi

# ---------------------------------------------------------------------------
# 10. Publica Nox
# ---------------------------------------------------------------------------

echo
echo "📤 Enviando nox/main para o GitHub..."

git push origin nox/main

echo
echo "=========================================="
echo " ✅ SINCRONIZAÇÃO CONCLUÍDA"
echo "=========================================="
echo
echo "Keiyoushi integrada:"
echo "  $UPSTREAM_SHORT"
echo
echo "main     → mirror da Keiyoushi"
echo "nox/main → Keiyoushi + alterações Project Nox"
echo
