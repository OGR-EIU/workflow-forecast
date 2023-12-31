
function [plan2, mdb2, jdb] = apply_new_judgment(model, plan1, mdb1, dates)

    % Specify judgmental adjustments between the "START JUDGMENT HERE" and
    % "END JUDGMENT HERE" lines.

    %(
    plan2 = plan1;
    mdb2 = mdb1;

    res_names = access(model, "transition-shocks");
    var_names = access(model, "transition-variables");
    %)

    res_addon = struct();
    res_over = struct();
    res_endog = struct();
    var_exog = struct();

    %(
    for n = res_names
        res_addon.(n) = Series();
        res_over.(n) = Series();
        res_endog.(n) = Series();
    end

    for n = var_names
        var_exog.(n) = Series();
    end
    %)

    % START JUDGMENT HERE =============================================
    %
    % Use `res_over` for the values of residuals
    %
    % Use `res_addon` for additive adjustments to residuals
    %
    % Use a combination of (`var_exog`, `res_endog`) for exogenizing a
    % variable and endogenizing a residual
    %



    % END JUDGMENT HERE ===============================================

    %(
    res_addon = databank.clip(res_addon, dates.simulation_range);
    res_over = databank.clip(res_over, dates.simulation_range);
    res_endog = databank.clip(res_endog, dates.simulation_range);
    var_exog = databank.clip(var_exog, dates.simulation_range);

    for n = databank.fieldNames(res_addon)
        if ~ismember(n, res_names)
            error("This is not a valid residual name: %s", n);
        end
        if isempty(res_addon.(n))
            continue
        end
        res_addon.(n) = fillMissing(res_addon.(n), dates.simulation_range, 0);
        mdb2.(n) = mdb2.(n) + res_addon.(n);
    end

    for n = databank.fieldNames(res_over)
        if ~ismember(n, res_names)
            error("This is not a valid residual name: %s", n);
        end
        if isempty(res_over.(n))
            continue
        end
        mdb2.(n) = vertcat(mdb2.(n), res_over.(n));
    end

    for n = databank.fieldNames(res_endog)
        if ~ismember(n, res_names)
            error("This is not a valid residual name: %s", n);
        end
        if isempty(res_endog.(n))
            continue
        end
        dates = find(res_endog.(n)~=0 & ~isnan(res_endog.(n)));
        if isempty(dates)
            continue
        end
        plan2 = endogenize(plan2, dates, n);
    end

    jdb = struct();
    for n = databank.fieldNames(var_exog)
        if ~ismember(n, var_names)
            error("This is not a valid variable name: %s", n);
        end
        if isempty(var_exog.(n))
            continue
        end
        dates = find(~isnan(var_exog.(n)));
        if isempty(dates)
            continue
        end
        plan2 = exogenize(plan2, dates, n);
        mdb2.(n) = vertcat(mdb2.(n), var_exog.(n));
        if ~isfield(jdb, n)
            jdb.(n) = Series();
        end
        jdb.(n) = vertcat(jdb.(n), var_exog.(n));
    end
    %)

end%

