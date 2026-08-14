import subprocess
import os
import re

BASE = r"D:\IdeaProjects\个人项目\ai-vibe-coding\mynanobot\my-first-nanobot-server"
SRC_MAIN = "src/main/java/com/example/usermanagement"
SRC_TEST = "src/test/java/com/example/usermanagement"

files_main = subprocess.check_output(
    ["git", "ls-tree", "-r", "--name-only", "HEAD", SRC_MAIN], cwd=BASE, text=True
).splitlines()
files_test = subprocess.check_output(
    ["git", "ls-tree", "-r", "--name-only", "HEAD", SRC_TEST], cwd=BASE, text=True
).splitlines()

def to_miniforum(git_path):
    if git_path.startswith("src/main/java/com/example/usermanagement/"):
        rel = git_path[len("src/main/java/com/example/usermanagement/"):]
        return os.path.join(BASE, "src/main/java/com/tkzou/miniforum", rel)
    if git_path.startswith("src/test/java/com/example/usermanagement/"):
        rel = git_path[len("src/test/java/com/example/usermanagement/"):]
        return os.path.join(BASE, "src/test/java/com/tkzou/miniforum", rel)
    return None

all_files = files_main + files_test

for git_path in all_files:
    content = subprocess.check_output(["git", "show", f"HEAD:{git_path}"], cwd=BASE).decode("utf-8")
    # rewrite package name
    content = content.replace("com.example.usermanagement", "com.tkzou.miniforum")
    # fix the main application class name if referenced
    content = content.replace("UserManagementApplication", "MiniForumApplication")
    dest = to_miniforum(git_path)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "w", encoding="utf-8") as f:
        f.write(content)
    print("RESTORED:", dest)

print("DONE")
