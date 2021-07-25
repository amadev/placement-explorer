from flask import Flask

import placement_explorer.resource

app = Flask(__name__)


@app.route("/resource")
def resource():
    return placement_explorer.resource.collect()


# @app.route('/static/<path:path>')
# def send_js(path):
#     return send_from_directory('static', path)


@app.route("/")
def home():
    return """
<!DOCTYPE html>
<html>
  <head>
    <link href="/static/site.min.css" rel="stylesheet" type="text/css">
  </head>
  <body>
    <div id="app"></div>
    <script src="/static/app.js"></script>
  </body>
</html>
"""
