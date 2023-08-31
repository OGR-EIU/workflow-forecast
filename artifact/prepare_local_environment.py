
import git
import json
import os
import shutil
import logging
import requests
import sys


BASE_URL = "https://eiu-dev.ogresearch.com/api"
USERNAME = "test-user"
PASSWORD = "t3stT#st"


def request_data(base_url, username, password, request, save_to):
    # authenticate to get a token (JWT)
    url = f'{base_url}/authenticate'
    payload = {
        'username': username,
        'password': password
    }
    headers = {'content-type': 'application/json'}
    response = requests.post(url, data=json.dumps(payload), headers=headers)
    token = response.json()['id_token']
    response.raise_for_status()
    #
    # Post the data request
    url = f'{base_url}/data-requests/series/retrieve'
    headers = {'Authorization': 'Bearer ' + token, 'content-type': 'application/json'}
    response = requests.post(url, data=json.dumps(request), headers=headers)
    return response



if __name__ == "__main__":

    this_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(this_dir, "config.json")
    logging.basicConfig(level=logging.INFO, )

    with open(config_path, "r") as f:
        config = json.load(f)

    _WORKFLOW_FORECAST_REPO = config["workflow_forecast_repo"]
    _WORKFLOW_FORECAST_REPO_SHA = config["workflow_forecast_repo_SHA"]

    _MODEL_REPO = config["model_repo"]
    _MODEL_REPO_SHA = config["model_repo_SHA"]

    _MODEL_INFRA_REPO = config["model_infra_repo"]
    _MODEL_INFRA_REPO_SHA = config["model_infra_repo_SHA"]

    _IRIS_TOOLBOX_REPO = config["iris_toolbox_repo"]
    _IRIS_TOOLBOX_REPO_SHA = config["iris_toolbox_repo_SHA"]

    _TOOLSET_REPO = config["toolset_repo"]
    _TOOLSET_REPO_SHA = config["toolset_repo_SHA"]

    _DATA_WAREHOUSE_CLIENT_REPO = config["data_warehouse_client_repo"]
    _DATA_WAREHOUSE_CLIENT_REPO_SHA = config["data_warehouse_client_repo_SHA"]

    logging.info(f"Cloning {_WORKFLOW_FORECAST_REPO} repo")
    shutil.rmtree(f"{_WORKFLOW_FORECAST_REPO}", ignore_errors=True, )
    workflow_forecast_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_WORKFLOW_FORECAST_REPO}.git", f"{_WORKFLOW_FORECAST_REPO}", filter="tree:0", no_checkout=True, )
    workflow_forecast_repo.git.checkout(f"{_WORKFLOW_FORECAST_REPO_SHA}")

    logging.info(f"Cloning {_MODEL_REPO} repo")
    shutil.rmtree(f"{_MODEL_REPO}", ignore_errors=True, )
    model_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_MODEL_REPO}.git", f"{_MODEL_REPO}", filter="tree:0", no_checkout=True, )
    model_repo.git.checkout(f"{_MODEL_REPO_SHA}")

    logging.info(f"Cloning {_MODEL_INFRA_REPO} repo")
    shutil.rmtree(f"{_MODEL_INFRA_REPO}", ignore_errors=True, )
    model_infra_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_MODEL_INFRA_REPO}.git", f"{_MODEL_INFRA_REPO}", filter="tree:0", no_checkout=True, )
    model_infra_repo.git.checkout(f"{_MODEL_INFRA_REPO_SHA}")

    logging.info(f"Cloning {_TOOLSET_REPO} repo")
    shutil.rmtree(f"{_TOOLSET_REPO}", ignore_errors=True, )
    toolset_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_TOOLSET_REPO}.git", f"{_TOOLSET_REPO}", filter="tree:0", no_checkout=True, )
    toolset_repo.git.checkout(f"{_TOOLSET_REPO_SHA}")

    logging.info(f"Cloning {_IRIS_TOOLBOX_REPO} repo")
    shutil.rmtree(f"{_IRIS_TOOLBOX_REPO}", ignore_errors=True, )
    iris_toolbox_repo = git.Repo.clone_from(f"git@github.com:IRIS-Solutions-Team/IRIS-Toolbox.git", f"{_IRIS_TOOLBOX_REPO}", filter="tree:0", no_checkout=True, )
    iris_toolbox_repo.git.checkout(f"{_IRIS_TOOLBOX_REPO_SHA}")

    logging.info(f"Cloning {_DATA_WAREHOUSE_CLIENT_REPO} repo")
    shutil.rmtree(f"{_DATA_WAREHOUSE_CLIENT_REPO}", ignore_errors=True, )
    data_warehouse_client_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{_DATA_WAREHOUSE_CLIENT_REPO}.git", f"{_DATA_WAREHOUSE_CLIENT_REPO}", filter="tree:0", no_checkout=True, )
    data_warehouse_client_repo.git.checkout(f"{_DATA_WAREHOUSE_CLIENT_REPO_SHA}")

    logging.info("Requesting forecast input data")
    with open(os.path.join(_MODEL_REPO, "requests", "input-data-request.json"), "rt") as f:
        request = json.load(f)

    response = request_data(BASE_URL, USERNAME, PASSWORD, request, )

    if response.status_code == 200:
        logging.info("Successfully retrieved forecast input data")
        print(json.dumps(response.json(), indent=4, ))
        with open("forecast-input-data.json", 'wt') as f:
            json.dump(response.json(), f, indent=4, )
    elif response.status_code == 400:
        loggin.critical("Bad request: please check if the requested keys exist")
    else:
        print(response.json(), file=sys.stderr)
    response.raise_for_status()


