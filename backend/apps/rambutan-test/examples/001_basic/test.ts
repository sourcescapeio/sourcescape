import { Controller, Post} from '@nestjs/common'
import { Test } from './test2';

@Controller('app')
class Hello {
	constructor(private test: Test) {
	}  

  @Post('/test')
  async doSomething() {    
    this.test.test();
    return true;
  }
}
