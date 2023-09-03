
import os
import subprocess
import shutil
import sys
import json
import logging


_THIS_DIR = os.path.dirname(os.path.abspath(__file__))
_WORKFLOW_FORECAST_DIR = os.path.join(_THIS_DIR, "workflow-forecast", )
_ANALYST_DIR = os.path.join(_WORKFLOW_FORECAST_DIR, "analyst", )

_ANALYST_FILES = [
    "apply_new_judgment.m",
]


logging.basicConfig(level=logging.INFO, )


config_path = os.path.join(_THIS_DIR, "config.json")
with open(config_path, "r") as f:
    config = json.load(f)


branch_to_push = config["local-dependencies"]["workflow-forecast"]["branch"]
subprocess.run(["git", "switch", branch_to_push, ], cwd=_WORKFLOW_FORECAST_DIR, )
subprocess.run(["git", "pull", ], cwd=_WORKFLOW_FORECAST_DIR, )

#
# Copy ./file.m to workflow-forecast/analyst/file.m
#
for file_name in _ANALYST_FILES:
    src = os.path.join(_THIS_DIR, file_name, )
    dst = os.path.join(_ANALYST_DIR, file_name, )
    shutil.copyfile(src, dst, )
    subprocess.run(["git", "add", file_name, ], cwd=_ANALYST_DIR, )

subprocess.run(["git", "status"], cwd=_WORKFLOW_FORECAST_DIR, )
subprocess.run(["git", "commit", "-m", "Submitting forecast", ], cwd=_WORKFLOW_FORECAST_DIR, )
subprocess.run(["git", "push", "origin", branch_to_push, ], cwd=_WORKFLOW_FORECAST_DIR, )


