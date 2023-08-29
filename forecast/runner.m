function runner(model_dir, input_mapping_path, input_data_path, output_mapping_path, output_data_path, is_tc)
    addpath("../../iris");
    addpath("../../toolset");
    addpath("../../model-infra");
    iris.startup;

    % check if running from TC
    if is_tc
        % wrapping for TC build
        % this is saved for auditing purposes
        try
            disp(pwd);
            modeler.test_run_forecast( ...
                model_dir ...
                , input_mapping_path ...
                , input_data_path ...
                , input_tunes_path ...
                , output_mapping_path ...
                , output_data_path ...
            );
            disp('Success');
            restoredefaultpath;
            exit(0);
        catch Exc
            disp(Exc.getReport);
            exit(2);
        end
    else
        modeler.test_run_forecast( ...
            model_dir ...
            , input_mapping_path ...
            , input_data_path ...
            , input_tunes_path ...
            , output_mapping_path ...
            , output_data_path ...
        );
    end
end
