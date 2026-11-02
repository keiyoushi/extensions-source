import json
import time
from datetime import UTC, datetime, timedelta

from github_utils import REPO_NAME, run_gh

SOURCE_REPO = "keiyoushi/extensions-source"
PUBLISH_WORKFLOW = "build_push.yml"
PUBLISH_JOB = "Publish extension repo"
MIN_PUBLISH_AGE = timedelta(hours=1)
POLL_INTERVAL = 60
DELETE_INTERVAL = 1
RELEASE_RETRY_INTERVAL = 100


def get_json(endpoint: str):
    return json.loads(run_gh("api", endpoint))


def get_pages(endpoint: str) -> list[dict]:
    items = []
    page = 1
    separator = "&" if "?" in endpoint else "?"
    while True:
        batch = get_json(f"{endpoint}{separator}per_page=100&page={page}")
        items.extend(batch)
        if len(batch) < 100:
            return items
        page += 1


def get_workflow_runs() -> list[dict]:
    return get_json(
        f"repos/{SOURCE_REPO}/actions/workflows/{PUBLISH_WORKFLOW}/runs?per_page=20"
    )["workflow_runs"]


def get_latest_publish_job(runs: list[dict]) -> dict | None:
    publish_jobs = []
    for run in runs:
        jobs = get_json(
            f"repos/{SOURCE_REPO}/actions/runs/{run['id']}/jobs?per_page=100"
        )["jobs"]
        publish_jobs.extend(
            job
            for job in jobs
            if job["name"] == PUBLISH_JOB and job.get("conclusion") != "skipped"
        )

    if not publish_jobs:
        return None

    return max(publish_jobs, key=lambda job: job["completed_at"])


def wait_for_publish_window() -> None:
    while True:
        runs = get_workflow_runs()
        active_runs = [run for run in runs if run["status"] != "completed"]
        if active_runs:
            run = active_runs[0]
            print(f"CI run {run['id']} is {run['status']}; checking again in 60s")
            time.sleep(POLL_INTERVAL)
            continue

        job = get_latest_publish_job(runs)
        if job is None:
            print("No previous publish job found")
            return

        safe_at = datetime.fromisoformat(job["completed_at"]) + MIN_PUBLISH_AGE
        remaining = (safe_at - datetime.now(UTC)).total_seconds()
        if remaining <= 0:
            return

        print(
            f"Publish job {job['id']} completed less than an hour ago; "
            f"waiting until {safe_at.isoformat()}"
        )
        time.sleep(remaining)


def get_referenced_assets() -> set[str]:
    index = json.loads(
        run_gh(
            "api",
            "--header",
            "Accept: application/vnd.github.raw+json",
            f"repos/{REPO_NAME}/contents/index.json?ref=repo",
        )
    )
    return {
        extension["resources"][field]
        for extension in index["extensionList"]["extensions"]
        for field in ("apkUrl", "jarUrl")
    }


def delete(endpoint: str) -> None:
    started = time.monotonic()
    run_gh("api", "--method", "DELETE", endpoint, success_errors=("http 404",))
    time.sleep(max(0.0, DELETE_INTERVAL - (time.monotonic() - started)))


def try_delete_release(release: dict) -> bool:
    endpoint = f"repos/{REPO_NAME}/releases/{release['id']}"
    try:
        delete(endpoint)
    except RuntimeError as e:
        print(f"Could not delete release {release['tag_name']}: {e}")
        # A 502 doesn't always mean the delete was rolled back; sometimes it
        # still completes server-side.
        try:
            run_gh("api", endpoint)
        except RuntimeError as e:
            if "http 404" in str(e).lower():
                return True
        return False
    return True


def cleanup_releases(referenced: set[str]) -> tuple[int, int]:
    deleted_assets = 0
    deleted_releases = 0
    releases = get_pages(f"repos/{REPO_NAME}/releases")

    for release in releases:
        assets = get_pages(f"repos/{REPO_NAME}/releases/{release['id']}/assets")
        unreferenced = [
            asset for asset in assets if asset["browser_download_url"] not in referenced
        ]
        fully_unreferenced = len(unreferenced) == len(assets)

        # Fast path: every asset is unreferenced -> delete the release in one call,
        # which takes the assets with it.
        if assets and fully_unreferenced:
            print(
                f"Deleting release {release['tag_name']} "
                f"({len(assets)} unreferenced assets)"
            )
            if try_delete_release(release):
                deleted_assets += len(assets)
                deleted_releases += 1
                continue

        for i, asset in enumerate(unreferenced, 1):
            print(f"Deleting {release['tag_name']}/{asset['name']}")
            delete(f"repos/{REPO_NAME}/releases/assets/{asset['id']}")
            deleted_assets += 1

            # Releases with hundreds of assets time out (HTTP 502) when deleted
            # whole, so retry the fast path as the release shrinks.
            if (
                fully_unreferenced
                and i % RELEASE_RETRY_INTERVAL == 0
                and try_delete_release(release)
            ):
                deleted_assets += len(unreferenced) - i
                deleted_releases += 1
                break
        else:
            if fully_unreferenced:
                print(f"Deleting empty release {release['tag_name']}")
                delete(f"repos/{REPO_NAME}/releases/{release['id']}")
                deleted_releases += 1

    return deleted_assets, deleted_releases


def main() -> None:
    wait_for_publish_window()
    referenced_assets = get_referenced_assets()
    asset_count, release_count = cleanup_releases(referenced_assets)
    summary = (
        f"Deleted {asset_count} unreferenced assets and {release_count} empty releases"
    )
    print(summary)


if __name__ == "__main__":
    main()
