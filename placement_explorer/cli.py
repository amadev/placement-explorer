import click


@click.group()
def main():
    pass


@main.command()
def resource():
    print("placement-explorer: ok")
