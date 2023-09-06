import argparse
import logging as _lg
import datetime as _dt
import json as _js
import numpy as _np
import irispie as _ip

import os as _os
import sys as _sy
cwd = _os.getcwd()
dir_above = _os.path.join(cwd, "..")
_sy.path.append(dir_above)
import toolset.protocol_conversion as _tp


def qq_today():
    """
    Returns this quarterly period.
    """
    today = _dt.date.today()
    return _ip.qq(today.year, 1 + _np.floor((today.month - 1)/3))


def runner(response_path: str, mapping_path: str):
    """
    Runner method used to check NaNs on the forecast horizon.

    Parameters
    ----------
    @param response_path: response file path
    @param mapping_path: mapping file path
    """
    with open(mapping_path, "rt") as fid:
        mapping = _js.load(fid)

    with open(response_path, "rt") as fid:
        response = _js.load(fid)
        db = _tp.databank_from_response(response, mapping)
    
    names = db._get_names()
    for name in names:
        if _np.isnan(db[name](qq_today() + 1 >> db[name].end_date)).any():
            _lg.warning(f"Series {name} is missing data on the forecast horizon.")


# run the script
if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument("--response-path", type=str, required=True)
    p.add_argument("--mapping-path", type=str, required=True)
    args = p.parse_args()
    runner(response_path=args.response_path, mapping_path=args.mapping_path)
