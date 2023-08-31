
clc
clear
close all

country_model_folder = "model-template";

input_mapping_path = fullfile(country_model_folder, "mappings", "ea-input-mapping.json");
input_data_path = fullfile(country_model_folder, "tests", "all-input-data.json");

output_mapping_path = fullfile(country_model_folder, "mappings", "ea-output-mapping.json");
output_data_path = fullfile(country_model_folder, "tests", "ea-output-data.json");

analyst_mapping_path = fullfile(country_model_folder, "mappings", "ea-analyst-mapping.json");
analyst_data_path = fullfile(country_model_folder, "tests", "ea-analyst-data.json");

addpath iris-toolbox -end
addpath toolset -end
addpath model-infra -end
iris.startup("silent", true);

rehash path
apply_new_judgment_func = @apply_new_judgment;

[output_db, analyst_db] = modeler.test_run_forecast( ...
    country_model_folder, ...
    input_mapping_path, ...
    input_data_path, ...
    apply_new_judgment_func, ...
    output_mapping_path, ...
    output_data_path, ...
    analyst_mapping_path, ...
    analyst_data_path ...
);

