%% Main script for running a model forecast locally
%
% * Launch Matlab
% * On startup, Matlab will compile the model and the input databank
% * Run this script to produce a forecast, a comparison report, and an output data file
% * Apply judgmental adjustments in `apply_new_judgment.m`
%


%% Clear workspace

clc
clear
close all


%% Prepare the workspace
%
% Load the model object, input databank, and configuration parameters
%

env_paths = getappdata(0, "env_paths");
model = getappdata(0, "model");
params = getappdata(0, "params");
input_db = getappdata(0, "input_db");
dates = getappdata(0, "dates");
timestamp = getappdata(0, "timestamp");
forecast_id = getappdata(0, "forecast_id");

rehash path
apply_new_judgment_func = @apply_new_judgment;


%% Simulate the model
%
% Simulate the model using the input databank and the judgmental
% adjustments
%

[reference_db, final_db] = modeler.run_forecast( ...
    model, ...
    params, ...
    input_db, ...
    dates, ...
    apply_new_judgment_func ...
);


%% Plot a comparison report
%
% Plot the current scenario against the reference scenario
%

[ch_variables, ch_residuals] = modeler.local_report( ...
    final_db ...
    , reference_db ...
    , dates ...
    , forecast_id ...
);


%% Save the output data in a protocol compliant JSON file

data_column = 1;
output_response = modeler.prepare_country_output_data( ...
    final_db, ...
    env_paths.output_mapping_path, ...
    env_paths.output_data_path, ...
    data_column, ...
    timestamp ...
);


