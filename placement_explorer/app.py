from flask import Flask

import placement_explorer.resource

app = Flask(__name__)

@app.route("/")
def resource():
    return placement_explorer.resource.collect()
