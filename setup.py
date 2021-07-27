import io
import json
import os
import subprocess
import sys

from setuptools import find_packages, setup

BASE_DIR = os.path.abspath(os.path.dirname(__file__))

# PACKAGE_JSON = os.path.join(BASE_DIR, "superset-frontend", "package.json")
# with open(PACKAGE_JSON, "r") as package_file:
#     version_string = json.load(package_file)["version"]

with io.open("README.org", "r", encoding="utf-8") as f:
    long_description = f.read()

version = "0.1.0"

# def git_version():
#     try:
#         s = subprocess.check_output(["git", "describe", "HEAD"])
#         return s.decode().strip()
#     except Exception:
#         return ""

# version_string = git_version()

# VERSION_INFO_FILE = os.path.join(BASE_DIR, "superset", "static", "version_info.json")

# with open(VERSION_INFO_FILE, "w") as version_file:
#     json.dump(version_info, version_file)


setup(
    name="Placement-Explorer",
    description=(
        "A tool to visualize state of available and used resources for a cloud"
    ),
    long_description=long_description,
    long_description_content_type="text/markdown",
    version=version,
    packages=find_packages(),
    include_package_data=True,
    zip_safe=False,
    entry_points={
        "console_scripts": ["placement-explorer=placement_explorer.cli:main"]
    },
    install_requires=["flask", "os-sdk-light>=0.2.7"],
    python_requires="~=3.8",
    author="Andrey Volkov",
    author_email="m@amadev.ru",
    url="https://github.com/amadev/placement-explorer/",
    classifiers=["Programming Language :: Python :: 3.9",],
)
