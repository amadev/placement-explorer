from flask import Flask, jsonify, make_response

import placement_explorer.resource

app = Flask(__name__)


@app.route("/resource")
def resource():
    response = make_response(jsonify(placement_explorer.resource.collect()))
    response.headers.add("Access-Control-Allow-Origin", "*")
    return response


@app.route("/")
def home():
    return """
<!DOCTYPE html>
<html>
  <head>
    <link href="/static/site.min.css" rel="stylesheet" type="text/css">
  </head>
  <body>
    <div id="body-container" style="margin: 0 auto; max-width: 1600px; padding-top: 72px;">
      <div id="app"></div>
    </div>
    <script src="/static/app.js"></script>
  </body>
</html>
"""
