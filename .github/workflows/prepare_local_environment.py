
import git

_WORKFLOW_FORECAST_REPO = "@(workflow_forecast_repo)"
_WORKFLOW_FORECAST_REPO_SHA = "@(workflow_forecast_repo_sha)"

_MODEL_REPO = "@(model_repo)"
_MODEL_REPO_SHA = "@(model_repo_sha)"


if __name__ == "__main__":

    workflow_forecast_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_WORKFLOW_FORECAST_REPO}.git", f"{_WORKFLOW_FORECAST_REPO}", filter="tree:0", no_checkout=True, )
    workflow_forecast_repo.git.checkout(f"{_WORKFLOW_FORECAST_REPO_SHA}")

    model_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_MODEL_REPO}.git", f"{_MODEL_REPO}", filter="tree:0", no_checkout=True, )
    model_repo.git.checkout(f"{_MODEL_REPO_SHA}")

