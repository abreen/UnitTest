.PHONY: run test clean
build_dir = build

build:
	javac -d $(build_dir) *.java

run: build
	java -cp .:$(build_dir) UnitTest

test: build
	java -cp .:$(build_dir) UnitTest UnitTestTest

clean:
	rm -rf $(build_dir)
