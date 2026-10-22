"""Fixture application: a notes API. Not a real service."""
from flask import Flask, jsonify, request

app = Flask(__name__)
NOTES: list[str] = []


@app.get("/health")
def health():
    return jsonify(status="UP")


@app.post("/notes")
def add_note():
    NOTES.append(request.get_json()["text"])
    return jsonify(count=len(NOTES)), 201
