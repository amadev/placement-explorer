import os
import logging

from placement_explorer import client


def collect():
    try:
        clouds = [os.environ["OS_CLOUD"]]
        return {cloud: {"nodes": client.get_resources(cloud)} for cloud in clouds}
    except Exception as exc:
        logging.exception("Error happened during resoure collection")
        return {"error": str(exc)}
