FROM python:3.8

RUN mkdir -p /app/requirements

COPY ./requirements/*.txt  /app/requirements/

COPY setup.py README.org /app/

RUN cd /app/; pip install --no-cache -r requirements/base.txt

COPY placement_explorer /app/placement_explorer

RUN cd /app/; pip install -e .

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    FLASK_ENV=development \
    FLASK_APP="placement_explorer.app"

COPY scripts/run.sh /bin/run.sh

CMD /bin/run.sh
