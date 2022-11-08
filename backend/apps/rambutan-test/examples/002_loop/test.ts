function test1() {
	test2()
}


function test2() {
	test1()
	console.warn('test')
}
