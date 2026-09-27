build:
	mvn -q package -DskipTests

run:
	mvn -q compile exec:java -Dexec.mainClass="tema.apd.NewsAggregator" -Dexec.args="$(ARGS)"

clean:
	mvn -q clean