.PHONY: build build-1.21.1 build-1.21.11 run clean test setup check jar

# Environment setup
export PATH := /opt/homebrew/bin:$(PATH)

# Build all versions
build:
	@echo "Building Minaret mod (all versions)..."
	./gradlew --warning-mode all build

# Build specific versions
build-1.21.1:
	@echo "Building for Minecraft 1.21.1..."
	./gradlew :versions:1.21.1:build

build-1.21.11:
	@echo "Building for Minecraft 1.21.11..."
	./gradlew :versions:1.21.11:build

clean:
	@echo "Cleaning build artifacts..."
	./gradlew clean

test:
	@echo "Running tests..."
	./gradlew test

run:
	@echo "Running in dev environment..."
	./gradlew :versions:1.21.1:runClient

setup:
	@echo "Setting up development environment..."
	gradle wrapper --gradle-version=8.14
	chmod +x gradlew

check:
	@echo "Checking mod integrity..."
	./gradlew check

jar: build
	@echo "Jar locations:"
	@ls -la versions/1.21.1/build/libs/*.jar 2>/dev/null || true
	@ls -la versions/1.21.11/build/libs/*.jar 2>/dev/null || true
