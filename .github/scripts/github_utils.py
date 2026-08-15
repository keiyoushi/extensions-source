import subprocess
import time

REPO_NAME = "keiyoushi/extensions"
RETRY_ATTEMPTS = 4
RETRY_BASE_DELAY = 60


def run_gh(*args: str, success_errors: tuple[str, ...] = ()) -> str:
    attempt = 1
    delay = RETRY_BASE_DELAY
    while True:
        result = subprocess.run(
            ["gh", *args],
            capture_output=True,
            encoding="utf-8",
            check=False,
        )
        if result.returncode == 0:
            return result.stdout.strip()

        error = result.stderr.lower()
        if any(success_error in error for success_error in success_errors):
            return result.stdout.strip()

        if "secondary rate limit" in error and attempt < RETRY_ATTEMPTS:
            retry_delay = delay
            delay *= 2
        elif "api rate limit exceeded" in error and attempt < RETRY_ATTEMPTS:
            rate_limit = subprocess.run(
                ["gh", "api", "rate_limit", "--jq", ".resources.core.reset"],
                capture_output=True,
                encoding="utf-8",
                check=False,
            )
            retry_delay = RETRY_BASE_DELAY
            if rate_limit.returncode == 0:
                retry_delay = max(
                    int(rate_limit.stdout.strip()) - int(time.time()) + 10,
                    RETRY_BASE_DELAY,
                )
        else:
            raise RuntimeError(f"gh {' '.join(args)} failed: {result.stderr.strip()}")

        print(
            f"GitHub rate limit hit; retrying in {retry_delay}s "
            f"(attempt {attempt}/{RETRY_ATTEMPTS})"
        )
        time.sleep(retry_delay)
        attempt += 1
