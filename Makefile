

build-and-run-on-connected-android-device:
	./gradlew clean build
	./gradlew installDebug
	./gradlew runDebug
