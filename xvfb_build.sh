#!/bin/bash
xvfb-run -a -s '-screen 0 1280x800x24' mvn -B -ntp -Dicsforms.desktopTests=true -Djava.awt.headless=false clean package
