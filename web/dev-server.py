#!/usr/bin/env python3
"""Run the actual BWT backend and editor client on the Maven test classpath."""
import os
from pathlib import Path
import sys

root = Path(__file__).resolve().parent.parent
os.chdir(root)
classpath_file = root / "target/editor-classpath.txt"
if not classpath_file.exists():
    sys.exit("Run: mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/editor-classpath.txt")
classpath = os.pathsep.join([str(root / "target/test-classes"), str(root / "target/classes"), classpath_file.read_text().strip()])
os.execvp("java", ["java", "-cp", classpath, "dev.by1337.baf.web.EditorDevServer", *sys.argv[1:]])
