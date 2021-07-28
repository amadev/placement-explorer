import os


PLACEMENT_EXPLORER_USE_FAKE_DATA = bool(
    os.environ.get("PLACEMENT_EXPLORER_USE_FAKE_DATA", False)
)
