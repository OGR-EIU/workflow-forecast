
import git
import json

if __name__ == "__main__":

    with open("config.json", "r") as f:
        config = json.load(f)

    _WORKFLOW_FORECAST_REPO = config["workflow_forecast_repo"]
    _WORKFLOW_FORECAST_REPO_SHA = config["workflow_forecast_repo_SHA"]

    _MODEL_REPO = config["model_repo"]
    _MODEL_REPO_SHA = config["model_repo_SHA"]

    workflow_forecast_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_WORKFLOW_FORECAST_REPO}.git", f"{_WORKFLOW_FORECAST_REPO}", filter="tree:0", no_checkout=True, )
    workflow_forecast_repo.git.checkout(f"{_WORKFLOW_FORECAST_REPO_SHA}")

    model_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_MODEL_REPO}.git", f"{_MODEL_REPO}", filter="tree:0", no_checkout=True, )
    model_repo.git.checkout(f"{_MODEL_REPO_SHA}")

