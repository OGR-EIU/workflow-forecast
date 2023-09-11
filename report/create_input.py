
import json
import argparse
from datetime import datetime


def process_settings(config_path, output_file, params):
    # load json file with settings
    with open(config_path) as f:
        settings = json.load(f)

    # go through each item in the request list
    for s in settings['json_request']:
        for key in params:
            s[key] = params[key]

    print(f'Processed forecast report settings:')
    print(json.dumps(settings, indent=2))

    # save the processed settings back to settings JSON
    with open(output_file, 'w') as f:
        json.dump(settings, f, indent=2)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--config-path', help='path to JSON file with settings', required=True)
    parser.add_argument('--output-file', help='path to JSON file where the step settings will be saved')
    parser.add_argument('--params-json', default='{}', help='JSON string with step parameters')
    args = parser.parse_args()
    if args.output_file is None:
        args.output_file = f'adjusted-{args.config_path}'
    params = json.loads(args.params_json)
    default_snapshot_time = datetime.utcnow().isoformat(timespec='milliseconds') + "Z"
    if "snapshot_time" not in params:
        params["snapshot_time"] = default_snapshot_time
    process_settings(args.config_path, args.output_file, params)
