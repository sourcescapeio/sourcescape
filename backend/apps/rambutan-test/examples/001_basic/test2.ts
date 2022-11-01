class TestService {
	start() {
		console.warn('test');
	}
}


class Test {
	constructor(private testService: TestService) {
	}

	test2({a: number, b: string}) {
	
	}

	test() {
		this.testService.start()
	}
}
