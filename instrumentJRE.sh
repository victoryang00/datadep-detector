#!/bin/bash
if [ -z "$INST_HOME" ]; then
INST_HOME=$JAVA_HOME;
fi
if [ -z "$INST_HOME" ]; then
echo "Error: Please set \$JAVA_HOME";
else
	echo "Ensuring instrumented JREs exist for tests... to refresh, do mvn clean\n";
	if [ ! -d "target/jre-inst" ]; then
		echo "Regenerating dependency instrumented JRE\n";
		java -noverify -jar target/DependencyDetector-0.0.1-SNAPSHOT.jar $INST_HOME target/jre-inst;
		chmod +x target/jre-inst/bin/*;
		chmod +x target/jre-inst/lib/*;
	else
		echo "Not regenerating dependency instrumented JRE\n";
	fi
fi
