import json
import argparse
from datetime import datetime


def process_settings(config_path, output_file, params={}):
    # load json file with requests
    with open(config_path) as f:
        requests = json.load(f)

    # go through each item in the request list
    for item in requests:

        for key in params:
            item[key] = params[key]

        # automatic value replacements
        if 'snapshot_time' in item \
            and not item['snapshot_time']:
            item['snapshot_time'] = datetime.utcnow().isoformat(timespec='milliseconds') + 'Z'

    print(f'Processed forecast report requests:')
    print(json.dumps(requests, indent=2))

    # save the processed requests back to requests JSON
    with open(output_file, 'w') as f:
        json.dump(requests, f, indent=2)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--config-path', help='path to JSON file with requests', required=True)
    parser.add_argument('--output-file', help='path to JSON file where the requests will be saved')
    parser.add_argument('--params-json', default='{}', help='JSON string with parameters')
    args = parser.parse_args()
    if args.output_file is None:
        args.output_file = f'adjusted-{args.config_path}'
    params = json.loads(args.params_json)
    process_settings(args.config_path, args.output_file, params=params)
