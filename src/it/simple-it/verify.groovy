File whatsnew = new File(basedir, "target/generated-resources/whatsnew.html")

assert whatsnew.isFile(), "File whatsnew.html not found"
String content = whatsnew.text

// Check exclude works
assert content.indexOf('Create RELENG-10-4-1') == -1, 'Whatsnew contains excluded note, Create RELENG-10-4-1'

// Check include works
assert content.indexOf('The version of maven-assembly-plugin used has been updated in order to avoid intermittent build failures') > 0, 'Whats new missing maven-assembly-plugin release note'
