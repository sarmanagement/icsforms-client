#!/bin/bash
xvfb-run -a -s '-screen 0 1280x800x24' mvn -B -ntp -Dicsforms.desktopTests=true -Djava.awt.headless=false clean test
test_status=$?

xvfb-run -a -s '-screen 0 1280x800x24' mvn -B -ntp -Dicsforms.desktopTests=true -Djava.awt.headless=false -DskipTests package
package_status=$?

if [ "$test_status" -ne 0 ]; then
	exit "$test_status"
fi
exit "$package_status"
