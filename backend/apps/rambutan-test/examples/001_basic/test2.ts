class TestService {
	start() {
		console.warn('test');
	}
}


export class Test {
	constructor(private testService: TestService) {
	}

	test2({a: number, b: string}) {
	
	}

	test() {
		this.testService.start()
	}
}


function Test2() {
	new TestService().start();
}
