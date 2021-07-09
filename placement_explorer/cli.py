import pprint
import sys

import click

import placement_explorer.resource


@click.group()
def main():
    pass


@main.command()
def resource():
    result = placement_explorer.resource.collect()
    pprint.pprint(result)
    if "error" in result:
        sys.exit(1)
