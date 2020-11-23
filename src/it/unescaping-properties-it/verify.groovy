Properties properties = new Properties()
File propertiesFile = new File("${basedir}/target/merged/out.properties")
List lines = propertiesFile.readLines()

assert lines.size() == 3
assert lines.grep("test1=value1").size() == 1
assert lines.grep("test2=value:2").size() == 1
assert lines.grep("test3=value=3").size() == 1
