
restoredefaultpath();

config = jsondecode(fileread("config.json"));
forecast_id = string(config.forecast_branch_name);

disp(" ")
disp(" ")
disp("==============================================================");
disp("Setting up forecast environment for [" + forecast_id + "]");
disp("Running " + mfilename("fullpath"));
disp(" ")
disp(" ")

echo startup on

addpath(fullfile("iris-toolbox"), "-end");
addpath(fullfile("toolset"), "-end");
addpath(fullfile("model-infra"), "-end");
iris.startup("silent", true);


env_paths = struct();
env_paths.this_dir = fileparts(mfilename("fullpath"));
env_paths.model_dir = fullfile(env_paths.this_dir, config.model);
env_paths.input_mapping_path = fullfile(env_paths.model_dir, "mappings", "input-data-mapping.json");
env_paths.output_mapping_path = fullfile(env_paths.model_dir, "mappings", "output-data-mapping.json");
env_paths.input_data_path = fullfile(env_paths.this_dir, "input-data.json");
env_paths.output_data_path = fullfile(env_paths.this_dir, "output-data.json");

[model, params] = modeler.create_model(env_paths.model_dir);

input_data_mapping = jsondecode(fileread(env_paths.input_mapping_path));
input_data = jsondecode(fileread(env_paths.input_data_path));
input_db = protocol_conversion.databank_from_response(input_data, input_data_mapping);

dates = struct();
dates.this_quarter = Dater.today(Frequency.QUARTERLY);
dates.end_simulation = dates.this_quarter + 5*4;
dates.start_hist = qq(2000,1);

input_db = modeler.prepare_input_data(model, input_db, dates);
dates = modeler.define_forecast_dates(dates, model, input_db);

setappdata(0, "env_paths", env_paths);
setappdata(0, "input_db", input_db);
setappdata(0, "model", model);
setappdata(0, "params", params);
setappdata(0, "dates", dates);
setappdata(0, "timestamp", string(config.timestamp));
setappdata(0, "forecast_id", forecast_id);

echo off

disp(" ")
disp(" ")
disp("==============================================================");
disp("Forecast environment ready for [" + forecast_id + "]");
disp(" ")
disp(" ")

