FROM scratch

ARG MAINTAINER
ARG GIT_COMMIT_ID

LABEL maintainer=${MAINTAINER} git.commit.id=${GIT_COMMIT_ID}

COPY target/staging /staging/
