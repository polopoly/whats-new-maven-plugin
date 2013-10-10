File whatsnew = new File(basedir, "target/generated-resources/whatsnew.html")

assert whatsnew.isFile(), "File whatsnew.html not found"
String content = whatsnew.text

// Check exclude works
assert content.indexOf('GO-238') != -1, 'Whats new missing GO-238, committed on version 10.8.1 should be included in 10.8'

// Check include works
assert content.indexOf('GO-219') != -1, 'Whats new missing GO-219, committed on version 10.8.0 should be included in 10.8'
