.PHONY: build run clean test setup

# 🔧 Environment setup
export PATH := /opt/homebrew/bin:$(PATH)
export JAVA_HOME := /opt/homebrew/opt/openjdk@21

# 🏗️ Build targets
build:
	@echo "🔨 Building Minaret mod..."
	./gradlew --warning-mode all build

clean:
	@echo "🧹 Cleaning build artifacts..."
	./gradlew clean

test:
	@echo "🧪 Running tests..."
	./gradlew test

run:
	@echo "🚀 Running in dev environment..."
	./gradlew runClient

# 📦 Setup development environment
setup:
	@echo "⚙️ Setting up development environment..."
	gradle wrapper --gradle-version=8.8
	chmod +x gradlew

# 🔍 Development helpers
check:
	@echo "✅ Checking mod integrity..."
	./gradlew check

jar:
	@echo "📦 Building distributable jar..."
	./gradlew build
	@echo "✨ Jar location: build/libs/"
