"""
Prints the head commit of the last successful push run of a workflow that is
still part of the checked-out branch, to diff the build matrix against.

Replaces nrwl/nx-set-shas, which treats *any* API error as "commit not on the
branch" and silently falls back to the empty tree: that full-rebuild base
can't see deleted modules, so they stayed in the published index. Here, only
a commit genuinely missing from the branch is skipped; API errors are retried
on rate limits by run_gh and fail the job otherwise, so the next push retries
from the same (still correct) base.
"""

import json
import os
import subprocess
import sys

from github_utils import run_gh

EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
# At most PER_PAGE * MAX_PAGES runs are checked to find a good base commit.
PER_PAGE = 100
MAX_PAGES = 10


def is_on_branch(sha: str) -> bool:
    """
    The checkout has full history (fetch-depth: 0), so a commit that isn't in the
    clone is unreachable from every branch, i.e. main was force-pushed past it.

    Args:
        sha (str): The commit SHA to check.

    Raises:
        RuntimeError: If git merge-base fails for some reason other than the commit not being an ancestor of HEAD.

    Returns:
        bool: True if the commit is reachable from HEAD, False otherwise.
    """
    if (
        subprocess.run(
            ["git", "cat-file", "-e", f"{sha}^{{commit}}"],
            capture_output=True,
            check=False,
        ).returncode
        != 0
    ):
        return False

    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", sha, "HEAD"],
        capture_output=True,
        encoding="utf-8",
        check=False,
    )
    if result.returncode not in (0, 1):
        raise RuntimeError(f"git merge-base failed for {sha}: {result.stderr.strip()}")
    return result.returncode == 0


def find_base_sha(repo: str, workflow: str, branch: str) -> str:
    seen_runs = 0
    for page in range(1, MAX_PAGES + 1):
        runs = json.loads(
            run_gh(
                "api",
                f"repos/{repo}/actions/workflows/{workflow}/runs"
                f"?branch={branch}&event=push&status=success"
                f"&per_page={PER_PAGE}&page={page}",
            )
        )["workflow_runs"]

        for run in runs:
            if is_on_branch(run["head_sha"]):
                print(f"Last successful run: {run['html_url']}", file=sys.stderr)
                return run["head_sha"]

        seen_runs += len(runs)
        if len(runs) < PER_PAGE:
            break

    # Only reached when no successful run exists or none of them is on the branch
    # anymore (history rewritten). A full rebuild is correct here: publish-repo.py
    # then replaces the whole index with the build output, so deletions aren't lost.
    print(
        f"No successful run on '{branch}' among {seen_runs} runs; "
        "falling back to the empty tree (full rebuild)",
        file=sys.stderr,
    )
    return EMPTY_TREE


def main() -> None:
    _, workflow = sys.argv
    base = find_base_sha(
        os.environ["GITHUB_REPOSITORY"],
        workflow,
        os.getenv("GITHUB_REF_NAME", "main"),
    )
    print(base)


if __name__ == "__main__":
    main()
