Properties properties = new Properties()
File propertiesFile = new File("${basedir}/target/merged/out.properties")
List lines = propertiesFile.readLines()

assert lines.size() == 3
assert lines.getAt(0) == "test1=value1"
assert lines.getAt(1) == "test2=value2"
assert lines.getAt(2) == "test3=value3"
