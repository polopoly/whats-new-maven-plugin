File whatsnew = new File(basedir, "target/generated-resources/whatsnew.html")

assert whatsnew.isFile(), "File whatsnew.html not found"
String content = whatsnew.text

// Check exclude works
assert content.indexOf('Excluded feature') == -1, 'Whats new contains excluded note: Excluded feature'

// Check include works
assert content.indexOf('This is an example issue with a cat image') > 0, 'Whats new missing note: This is an example issue with a cat image'

// Check image works
assert content.indexOf('<img src="whatsnew-images/GO-259.jpg"') > 0, 'Whats new missing cat image'