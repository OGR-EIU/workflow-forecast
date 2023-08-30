import json
import argparse
from datetime import datetime


def process_settings(config_path, output_file, params=None, ):

    params = params or {}
    
    # load json file with settings
    with open(config_path) as f:
        settings = json.load(f)

    # go through each item in the request list
    for i in range(len(settings['json_request'])):

        for key in params:
            settings['json_request'][i][key] = params[key]

        # automatic value replacements
        if 'snapshot_time' in settings['json_request'][i] \
            and not settings['json_request'][i]['snapshot_time']:
            settings['json_request'][i]['snapshot_time'] = datetime.utcnow().isoformat(timespec='milliseconds') + 'Z'

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
    process_settings(args.config_path, args.output_file, params=params)
