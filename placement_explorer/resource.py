import json
import logging
import os

from placement_explorer import client
from placement_explorer import config


def collect():
    try:
        if config.PLACEMENT_EXPLORER_USE_FAKE_DATA:
            return json.load(open(os.path.join(os.path.dirname(__file__), 'fake-data.json')))
        else:
            clouds = [os.environ["OS_CLOUD"]]
            return {cloud: {"nodes": client.get_resources(cloud)} for cloud in clouds}
    except Exception as exc:
        logging.exception("Error happened during resoure collection")
        return {"error": str(exc)}
