.DEFAULT_GOAL := help
SHELL := /bin/bash

# Java 21 is required, and an inherited JAVA_HOME pointing at another JDK is the
# usual reason a build silently compiles against the wrong release. This
# resolves 21 explicitly; a command-line override still wins:
#   make test JAVA_HOME=/path/to/jdk-21
#
# Note: `java_home -v 21` means "21 or newer" on macOS, so it happily returns a
# JDK 25 -- the Homebrew paths are checked first for that reason.
JAVA_HOME := $(shell \
	for candidate in /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21 \
	                 /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home; do \
		[ -x "$$candidate/bin/javac" ] && echo "$$candidate" && exit 0; \
	done; \
	/usr/libexec/java_home -v 21 2>/dev/null || echo "$$JAVA_HOME")
MVN := JAVA_HOME=$(JAVA_HOME) ./mvnw -B

.PHONY: help install dev dev-backend dev-frontend deps-up deps-down test test-backend test-frontend \
        test-infra lint lint-frontend format build build-backend build-frontend e2e docker-build \
        infra-synth infra-diff secret-scan clean

help: ## Show available commands
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

install: ## Install frontend and infra dependencies
	cd frontend && npm ci || (cd frontend && npm install)
	cd infra && npm ci || (cd infra && npm install)

deps-up: ## Start local dependencies (LocalStack)
	docker compose up -d

deps-down: ## Stop local dependencies
	docker compose down

dev: ## Run backend and frontend together (Ctrl-C stops both)
	@echo "Backend  -> http://localhost:8080"
	@echo "Frontend -> http://localhost:5173"
	@trap 'kill 0' EXIT INT TERM; \
		$(MAKE) dev-backend & \
		$(MAKE) dev-frontend & \
		wait

dev-backend: ## Run the Spring Boot backend with the local profile
	cd backend && $(MVN) spring-boot:run -Dspring-boot.run.profiles=local

dev-frontend: ## Run the Vite dev server
	cd frontend && npm run dev

test: test-backend test-frontend test-infra ## Run every unit/integration test

test-backend: ## Backend tests
	cd backend && $(MVN) test

test-frontend: ## Frontend unit tests
	cd frontend && npm run test

test-infra: ## CDK assertion tests
	cd infra && npm run test

lint: lint-frontend ## Lint everything
	cd infra && npx tsc --noEmit

lint-frontend: ## Lint and format-check the frontend
	cd frontend && npm run lint

format: ## Apply formatting
	cd frontend && npm run format

build: build-backend build-frontend ## Production builds

build-backend: ## Package the backend jar
	cd backend && $(MVN) clean package

build-frontend: ## Build the static frontend bundle
	cd frontend && npm run build

e2e: ## Playwright end-to-end tests (SEED mode, no provider calls)
	cd frontend && npm run build && npm run e2e

docker-build: ## Build the backend container image
	docker build -t listenspeak-backend:local ./backend

infra-synth: ## Synthesize the CloudFormation template
	cd infra && npx cdk synth

infra-diff: ## Diff the stack against the deployed state
	cd infra && npx cdk diff

secret-scan: ## Fail if anything that looks like a credential is committed
	./scripts/secret-scan.sh

clean: ## Remove build output
	cd backend && $(MVN) clean
	rm -rf frontend/dist frontend/coverage infra/cdk.out
