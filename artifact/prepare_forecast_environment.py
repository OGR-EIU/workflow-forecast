
import json
import os
import shutil
import logging
import requests
import sys
import subprocess


_THIS_DIR = os.path.dirname(os.path.abspath(__file__), )
_MODEL_DIR = os.path.join(_THIS_DIR, "model", )
_WORKFLOW_FORECAST_DIR = os.path.join(_THIS_DIR, "workflow-forecast", )
_ENVIRONMENT_DIR = os.path.join(_WORKFLOW_FORECAST_DIR, "environment", )

_ANALYST_FILES = [
    "startup.m",
    "run_forecast.m",
    "apply_new_judgment.m",
    "submit_forecast.py",
]

_DATA_WAREHOUSE_URL = "https://eiu-dev.ogresearch.com/api"
_USERNAME = "test-user"
_PASSWORD = "t3stT#st"


def _install_dependency(folder:str, dep: dict, ) -> None:
    logging.info("Cloning " + folder, )
    shutil.rmtree(folder, ignore_errors=True, )
    command = ["git", "clone", ]
    if dep["branch"]:
        command += ["--branch", dep["branch"], "--depth", "1", ]
    else:
        command += ["--no-checkout", "--filter", "tree:0", ]
    command += [dep["url"], folder, ]
    subprocess.run(command)
    if dep["commitish"]:
        subprocess.run(["git", "checkout", dep["commitish"], ], cwd=folder, )


def _copy_matlab_environment_files() -> None:
    logging.info("Copying Matlab environment files")
    for file_name in _ANALYST_FILES:
        src = os.path.join(_ENVIRONMENT_DIR, file_name, )
        dst = os.path.join(_THIS_DIR, file_name, )
        shutil.copyfile(src, dst, )


def _request_data(request, base_url, username, password, timestamp, ) -> None:
    logging.info("Requesting forecast input data")
    for r in request:
        r["snapshot_time"] = timestamp
    #
    # Authenticate to get a token (JWT)
    url = f"{base_url}/authenticate"
    payload = {
        "username": username,
        "password": password,
    }
    headers = {"content-type": "application/json"}
    response = requests.post(url, data=json.dumps(payload), headers=headers, )
    token = response.json()["id_token"]
    response.raise_for_status()
    #
    # Post the data request
    url = f"{base_url}/data-requests/series/retrieve"
    headers = {"Authorization": "Bearer " + token, "content-type": "application/json"}
    response = requests.post(url, data=json.dumps(request), headers=headers, )
    #
    return response


def _postprocess_response(response, response_path, ) -> None:
    if response.status_code == 200:
        logging.info("Successfully retrieved forecast input data")
        with open(response_path, "wt", ) as f:
            json.dump(response.json(), f, indent=4, )
    elif response.status_code == 400:
        logging.critical("Bad request: please check if the requested keys exist")
    else:
        print(response.json(), file=sys.stderr)
    response.raise_for_status()


if __name__ == "__main__":

    config_path = os.path.join(_THIS_DIR, "config.json")
    logging.basicConfig(level=logging.INFO, )

    with open(config_path, "r") as f:
        config = json.load(f)

    for folder, dep in config["dependencies"].items():
        if folder and dep:
            _install_dependency(folder, dep, )

    request_path = os.path.join(_MODEL_DIR, "requests", "input-data-request.json", )
    with open(request_path, "r") as f:
        request = json.load(f)

    _copy_matlab_environment_files()

    timestamp = config["timestamp"]
    response = _request_data(request, _DATA_WAREHOUSE_URL, _USERNAME, _PASSWORD, timestamp, )

    response_path = os.path.join(_THIS_DIR, "input-data.json", )
    _postprocess_response(response, response_path, )

