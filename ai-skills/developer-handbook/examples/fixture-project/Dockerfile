FROM python:3.12-slim
WORKDIR /srv/app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app app
CMD ["flask", "--app", "app.main", "run", "--host", "0.0.0.0"]
