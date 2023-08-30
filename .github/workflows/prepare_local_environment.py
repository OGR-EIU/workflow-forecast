
import git
import json
import os
import shutil

if __name__ == "__main__":

    this_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(this_dir, "config.json")

    with open(config_path, "r") as f:
        config = json.load(f)

    _WORKFLOW_FORECAST_REPO = config["workflow_forecast_repo"]
    _WORKFLOW_FORECAST_REPO_SHA = config["workflow_forecast_repo_SHA"]

    _MODEL_REPO = config["model_repo"]
    _MODEL_REPO_SHA = config["model_repo_SHA"]

    _MODEL_INFRA_REPO = config["model_infra_repo"]
    _MODEL_INFRA_SHA = config["model_infra_repo_SHA"]

    shutil.rmtree(f"{_WORKFLOW_FORECAST_REPO}", ignore_errors=True, )
    workflow_forecast_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_WORKFLOW_FORECAST_REPO}.git", f"{_WORKFLOW_FORECAST_REPO}", filter="tree:0", no_checkout=True, )
    workflow_forecast_repo.git.checkout(f"{_WORKFLOW_FORECAST_REPO_SHA}")

    shutil.rmtree(f"{_MODEL_REPO}", ignore_errors=True, )
    model_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_MODEL_REPO}.git", f"{_MODEL_REPO}", filter="tree:0", no_checkout=True, )
    model_repo.git.checkout(f"{_MODEL_REPO_SHA}")

    shutil.rmtree(f"{_MODEL_INFRA_REPO}", ignore_errors=True, )
    model_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_MODEL_INFRA_REPO}.git", f"{_MODEL_INFRA_REPO}", filter="tree:0", no_checkout=True, )
    model_repo.git.checkout(f"{_MODEL_INFR_REPO_SHA}")

