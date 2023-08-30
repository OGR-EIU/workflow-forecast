
import git

model_repo = "@(model_repo)"
model_repo_ref = "@(model_repo_ref)"

model_repo = git.Repo.clone_from(f"git@github.com:OGR-EIU/{model_repo}.git", f"{model_repo}", filter="tree:0", no_checkout=True, )
model_repo.git.checkout(f"{model_repo_ref}")

