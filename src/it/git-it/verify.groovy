File whatsnew = new File(basedir, "target/generated-resources/whatsnew.html")

assert whatsnew.isFile(), "File whatsnew.html not found"
String content = whatsnew.text

// Check date ordering works
assert content.indexOf('1970-02-10') != -1 && content.indexOf('1970-02-09') != -1, 'Git date rewrite failed, old dates not found'
assert content.indexOf('1970-02-10') < content.indexOf('1970-02-09'), 'Wrong order, 1970-02-10 should be before 1970-02-09'

// Check exclude works
assert content.indexOf('GO-38') == -1, 'Git exclude does not work, found GO-38 (The maximum number of keys in the statistics analyzers used by Greenfield Online has been increased.)'
