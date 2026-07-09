.PHONY: build test cli image up up-prod down it

build:          ## Build all modules (unit tests)
	mvn -B -DskipTests=false install

test:           ## Unit tests only
	mvn -B test

it:             ## Provisioner integration test (needs Docker)
	SD_IT_DOCKER=1 mvn -B -pl modules/provisioner test -Dtest=DockerMysqlProvisionerIT -Dsurefire.failIfNoSpecifiedTests=false

cli:            ## Build the smoke-test CLI uber-jar
	mvn -B -pl modules/cli -am -DskipTests package

image:          ## Build the runtime image (noVNC)
	tools/build-image.sh

up:             ## Dev stack (noVNC + MySQL, auto-unlock)
	docker compose -f docker/docker-compose.yml up -d --build

up-prod:        ## Prod stack (VNC password required)
	docker compose -f docker/docker-compose.prod.yml up -d --build

down:           ## Tear down dev stack
	docker compose -f docker/docker-compose.yml down
