
import git

model_repo = "@(model_repo_name)"
model_repo_commitish = "@(model_repo_commitish)"

model_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{model_repo}.git", f"{model-repo)", branch="main", filter="tree:0", no_checkout=True, )
model_repo.git.checkout(model_repo_commitish)

