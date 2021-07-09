import os

from placement_explorer import client


def collect():
    try:
        clouds = [os.environ["OS_CLOUD"]]
        return {cloud: {"nodes": client.get_resources(cloud)} for cloud in clouds}
    except Exception as exc:
        return {"error": str(exc)}
