.PHONY: run clean

out:
	javac -d out *.java

run: out
	java -cp out UnitTest

clean:
	rm -rf out
