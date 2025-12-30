.PHONY: format lint validate

format:
	./gradlew :composeApp:ktlintFormat

lint:
	./gradlew :composeApp:ktlintCheck :composeApp:detekt :composeApp:lint

validate: lint
