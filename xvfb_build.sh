#!/bin/bash
xvfb-run -a -s '-screen 0 1280x800x24' mvn -Dicsforms.desktopTests=true -Djava.awt.headless=false -Dtest=UiSupportDesktopIntegrationTest clean test package
